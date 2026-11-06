/*******************************************************************************
 *
 *    Copyright 2026 Adobe. All rights reserved.
 *    This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License. You may obtain a copy
 *    of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software distributed under
 *    the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *    OF ANY KIND, either express or implied. See the License for the specific language
 *    governing permissions and limitations under the License.
 *
 ******************************************************************************/
package com.adobe.cq.commerce.graphql.client.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlRequestException;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.CircuitBreakerService;
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.ServiceUnavailableException;
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.ServiceUnavailablePolicy;
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.SocketTimeoutException;
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.SocketTimeoutPolicy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.failsafe.CircuitBreakerOpenException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end tests of the circuit breaker: a real {@link GraphqlClientImpl} with a real Apache HTTP client calls a local HTTP
 * server. The {@link Mode} of the server decides how it answers: normally, slowly (longer than the socket timeout), with HTTP
 * 503, with HTTP 400 or with a GraphQL error.
 * <p>
 * Test settings: socket timeout {@value #SOCKET_TIMEOUT_MS} ms, failure threshold 2 for the socket timeout and HTTP 503
 * breakers, open delay {@value #OPEN_DELAY_MS} ms doubling up to 4 x that value. A request rejected by an open breaker must
 * fail within {@value #FAST_FAIL_MS} ms. To let a breaker become half-open, the tests wait for the open delay plus
 * {@value #WAIT_MARGIN_MS} ms. The margins leave room for slow CI machines; if the tests still become flaky, increase these
 * values together.
 */
public class CircuitBreakerIntegrationTest {

    private static final int SOCKET_TIMEOUT_MS = 500;
    private static final int SLOW_RESPONSE_MS = 3000;
    private static final long OPEN_DELAY_MS = 1000;
    private static final long WAIT_MARGIN_MS = 300;
    private static final long FAST_FAIL_MS = 250;
    private static final String RESPONSE = "{\"data\":{\"text\":\"ok\",\"count\":1}}";
    private static final String GRAPHQL_ERROR_RESPONSE = "{\"errors\":[{\"message\":\"Internal server error\"}]}";
    private static final Class<?>[] LOGGED_CLASSES = { FaultTolerantExecutor.class, CircuitBreakerService.class,
        SocketTimeoutPolicy.class, ServiceUnavailablePolicy.class };

    private enum Mode {
        OK,
        SLOW,
        UNAVAILABLE,
        BAD_REQUEST,
        GRAPHQL_ERROR
    }

    private static class Data {
        String text;
        Integer count;
    }

    private static class Error {
        String message;
    }

    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger healthyHits = new AtomicInteger();
    private final List<GraphqlClientImpl> clients = new ArrayList<>();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final List<Level> previousLevels = new ArrayList<>();
    private final CountDownLatch shutdown = new CountDownLatch(1);
    private volatile Mode mode = Mode.OK;
    private HttpServer server;
    private ExecutorService serverThreads;
    private GraphqlClientImpl client;

    @Before
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/graphql", this::handle);
        server.createContext("/healthy", exchange -> {
            healthyHits.incrementAndGet();
            respond(exchange, 200, RESPONSE);
        });
        serverThreads = Executors.newCachedThreadPool();
        server.setExecutor(serverThreads);
        server.start();

        logs.start();
        for (Class<?> loggedClass : LOGGED_CLASSES) {
            Logger logger = (Logger) LoggerFactory.getLogger(loggedClass);
            previousLevels.add(logger.getLevel());
            logger.setLevel(Level.DEBUG);
            logger.addAppender(logs);
        }
    }

    @After
    public void tearDown() {
        shutdown.countDown();
        clients.forEach(GraphqlClientImpl::deactivate);
        for (int i = 0; i < LOGGED_CLASSES.length; i++) {
            Logger logger = (Logger) LoggerFactory.getLogger(LOGGED_CLASSES[i]);
            logger.detachAppender(logs);
            logger.setLevel(previousLevels.get(i));
        }
        server.stop(0);
        serverThreads.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        hits.incrementAndGet();
        try {
            if (mode == Mode.SLOW) {
                shutdown.await(SLOW_RESPONSE_MS, TimeUnit.MILLISECONDS);
            }
            switch (mode) {
                case UNAVAILABLE:
                    respond(exchange, 503, "Service Unavailable");
                    break;
                case BAD_REQUEST:
                    respond(exchange, 400, "Bad Request");
                    break;
                case GRAPHQL_ERROR:
                    respond(exchange, 200, GRAPHQL_ERROR_RESPONSE);
                    break;
                default:
                    respond(exchange, 200, RESPONSE);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // the client closed the connection after its socket timeout
        } finally {
            exchange.close();
        }
    }

    private static void respond(HttpExchange exchange, int status, String content) throws IOException {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        // avoid reusing connections that the test server may already have closed
        exchange.getResponseHeaders().add("Connection", "close");
        exchange.getResponseHeaders().add("Content-Type", status == 200 ? "application/json" : "text/plain");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private GraphqlClientImpl activate(String url, boolean faultTolerant) throws Exception {
        return activate("it", url, faultTolerant);
    }

    private GraphqlClientImpl activate(String identifier, String url, boolean faultTolerant) throws Exception {
        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setIdentifier(identifier);
        config.setUrl(url);
        config.setAllowHttpProtocol(true);
        config.setSocketTimeout(SOCKET_TIMEOUT_MS);
        config.setConnectionTimeout(SOCKET_TIMEOUT_MS);
        config.setEnableFaultTolerantFallback(faultTolerant);
        // FAILURE_THRESHOLD:INITIAL_DELAY_MS:MAX_DELAY_MS:DELAY_MULTIPLIER (success threshold not set, so the default 1 is used)
        String progressive = "2:" + OPEN_DELAY_MS + ":" + OPEN_DELAY_MS * 4 + ":2.0";
        config.setCircuitBreakerSocketTimeout(progressive);
        config.setCircuitBreakerServiceUnavailable(progressive);

        GraphqlClientImpl graphqlClient = new GraphqlClientImpl();
        HttpClientBuilderFactory builderFactory = mock(HttpClientBuilderFactory.class);
        when(builderFactory.newBuilder()).thenAnswer(invocation -> HttpClientBuilder.create());
        Field field = GraphqlClientImpl.class.getDeclaredField("clientBuilderFactory");
        field.setAccessible(true);
        field.set(graphqlClient, builderFactory);
        graphqlClient.activate(config, mock(BundleContext.class));
        clients.add(graphqlClient);
        return graphqlClient;
    }

    private void openSocketTimeoutBreaker() {
        mode = Mode.SLOW;
        for (int i = 0; i < 2; i++) {
            try {
                execute();
                fail("Expected a socket timeout");
            } catch (SocketTimeoutException e) {
                // expected
            }
        }
        expectOpen();
    }

    private void assertLogged(Level level, String expectedMessagePart) {
        boolean found = logs.list.stream()
            .anyMatch(event -> event.getLevel() == level && event.getFormattedMessage().contains(expectedMessagePart));
        assertTrue("Missing " + level + " log containing: " + expectedMessagePart, found);
    }

    private String serverUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/graphql";
    }

    private GraphqlResponse<Data, Error> execute() {
        return execute(client);
    }

    private static GraphqlResponse<Data, Error> execute(GraphqlClientImpl graphqlClient) {
        return graphqlClient.execute(new GraphqlRequest("{dummy}"), Data.class, Error.class);
    }

    private long expectOpen() {
        return expectOpen(client);
    }

    private static long expectOpen(GraphqlClientImpl graphqlClient) {
        long start = System.nanoTime();
        try {
            execute(graphqlClient);
            fail("Expected the circuit breaker to be open");
        } catch (GraphqlRequestException e) {
            assertTrue("Unexpected cause: " + e.getCause(), e.getCause() instanceof CircuitBreakerOpenException);
        }
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    /**
     * Main scenario: the backend becomes slow, the breaker opens, the backend recovers and the breaker closes.
     * <ol>
     * <li>A first request succeeds.</li>
     * <li>The backend becomes slow. 2 requests each wait for the socket timeout and fail with {@link SocketTimeoutException}. This
     * reaches the failure threshold of 2 and opens the socket timeout breaker.</li>
     * <li>5 more requests fail immediately (faster than {@value #FAST_FAIL_MS} ms) with {@link CircuitBreakerOpenException}. The
     * server receives none of them.</li>
     * <li>The backend recovers. After the open delay the breaker is half-open, the probe request succeeds and closes the breaker,
     * and the next request is sent normally. The server receives exactly these 2 requests.</li>
     * </ol>
     * Why: proves that AEM threads stop waiting for a slow backend and that traffic resumes on its own when it recovers.
     */
    @Test
    public void testSlowBackendOpensBreakerAndRecovers() throws Exception {
        client = activate(serverUrl(), true);
        assertEquals("ok", execute().getData().text);

        mode = Mode.SLOW;
        for (int i = 0; i < 2; i++) {
            long start = System.nanoTime();
            try {
                execute();
                fail("Expected a socket timeout");
            } catch (SocketTimeoutException e) {
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= SOCKET_TIMEOUT_MS - 50);
            }
        }
        int hitsWhenOpened = hits.get();

        // open: requests fail immediately and do not reach the backend
        for (int i = 0; i < 5; i++) {
            assertTrue("Open breaker should fail fast", expectOpen() < FAST_FAIL_MS);
        }
        assertEquals(hitsWhenOpened, hits.get());

        // backend recovers, the half-open probe succeeds and the breaker closes
        mode = Mode.OK;
        Thread.sleep(OPEN_DELAY_MS + WAIT_MARGIN_MS);
        assertEquals("ok", execute().getData().text);
        assertEquals("ok", execute().getData().text);
        assertEquals(hitsWhenOpened + 2, hits.get());
    }

    /**
     * HTTP 503 opens the service unavailable breaker, and the open period gets longer while the backend stays down.
     * <ol>
     * <li>The backend returns 503 twice. Both requests fail with {@link ServiceUnavailableException}, the failure threshold of 2 is
     * reached and the breaker opens for the initial delay ({@value #OPEN_DELAY_MS} ms).</li>
     * <li>The next request is rejected by the open breaker without calling the backend.</li>
     * <li>After the initial delay the breaker is half-open. The probe request still gets 503, so the breaker opens again, now for
     * initial delay x multiplier (2 x {@value #OPEN_DELAY_MS} ms).</li>
     * <li>After waiting only one initial delay the breaker is still open. This proves that the delay increased.</li>
     * <li>The backend recovers. After the rest of the longer delay the probe succeeds and the breaker closes.</li>
     * </ol>
     * Why: checks the progressive delay settings (initial delay, multiplier, max delay) end to end, not only the calculation.
     */
    @Test
    public void testServiceUnavailableOpensBreakerWithProgressiveDelay() throws Exception {
        client = activate(serverUrl(), true);
        mode = Mode.UNAVAILABLE;
        for (int i = 0; i < 2; i++) {
            try {
                execute();
                fail("Expected HTTP 503");
            } catch (ServiceUnavailableException e) {
                assertEquals(503, e.getStatusCode());
            }
        }
        expectOpen();

        // the half-open probe still fails: the breaker opens again with a longer delay (2 x OPEN_DELAY_MS)
        Thread.sleep(OPEN_DELAY_MS + WAIT_MARGIN_MS);
        try {
            execute();
            fail("Expected HTTP 503 from the half-open probe");
        } catch (ServiceUnavailableException e) {
            assertEquals(503, e.getStatusCode());
        }
        Thread.sleep(OPEN_DELAY_MS + WAIT_MARGIN_MS);
        expectOpen();

        mode = Mode.OK;
        Thread.sleep(OPEN_DELAY_MS + WAIT_MARGIN_MS);
        assertEquals("ok", execute().getData().text);
    }

    /**
     * With {@code enableFaultTolerantFallback=false} (the default) there is no circuit breaker. 4 requests to a slow backend
     * all reach the server and each waits for the socket timeout. None fails with {@link CircuitBreakerOpenException}.
     * <p>
     * Why: the circuit breaker must only be active when it is turned on in the OSGi configuration.
     */
    @Test
    public void testDisabledByDefaultEveryRequestWaitsForTheBackend() throws Exception {
        client = activate(serverUrl(), false);
        mode = Mode.SLOW;
        for (int i = 0; i < 4; i++) {
            try {
                execute();
                fail("Expected a timeout");
            } catch (GraphqlRequestException e) {
                assertFalse(e.getCause() instanceof CircuitBreakerOpenException);
            }
        }
        assertEquals(4, hits.get());
    }

    /**
     * Connection failures never open a breaker. 5 requests to a port where nothing listens all fail with a connection error,
     * but the breaker never opens. A DEBUG log says that the failure "does not open a circuit breaker and counts as a success".
     * <p>
     * Why: documents a known limitation. Only socket (read) timeouts, HTTP 503 and other HTTP 5xx responses are counted. If
     * connection failures are counted in the future, change this test.
     */
    @Test
    public void testConnectionRefusedIsNotCounted() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        client = activate("http://127.0.0.1:" + closedPort + "/graphql", true);
        for (int i = 0; i < 5; i++) {
            try {
                execute();
                fail("Expected a connection failure");
            } catch (GraphqlRequestException e) {
                assertNotNull(e.getCause());
                assertFalse("Connection failures must not open the breaker", e.getCause() instanceof CircuitBreakerOpenException);
            }
        }
        assertLogged(Level.DEBUG, "does not open a circuit breaker and counts as a success for them");
    }

    /**
     * Single failures between successful requests do not open the breaker. The backend alternates between 503 and 200 four
     * times, so there are never 2 failures in a row. The breaker stays closed and all 8 requests reach the server.
     * <p>
     * Why: a backend with occasional errors must not cause the storefront to stop calling it.
     */
    @Test
    public void testIntermittentFailuresDoNotOpenBreaker() throws Exception {
        client = activate(serverUrl(), true);
        for (int i = 0; i < 4; i++) {
            mode = Mode.UNAVAILABLE;
            try {
                execute();
                fail("Expected HTTP 503");
            } catch (ServiceUnavailableException e) {
                // expected
            }
            mode = Mode.OK;
            assertEquals("ok", execute().getData().text);
        }
        // a success between failures keeps the failure count below the threshold, so every request reaches the backend
        assertEquals(8, hits.get());
    }

    /**
     * Each breaker only counts its own failure type, any other result counts as a success for it. The backend alternates
     * between a socket timeout and 503 three times, so neither breaker sees 2 of its failures in a row. Neither breaker opens and
     * all 6 requests reach the server.
     * <p>
     * Why: documents a known limitation, also described in the README. If the breakers start sharing a failure count, change
     * this test and the README.
     */
    @Test
    public void testAlternatingFailureTypesDoNotOpenAnyBreaker() throws Exception {
        client = activate(serverUrl(), true);
        for (int i = 0; i < 3; i++) {
            mode = Mode.SLOW;
            try {
                execute();
                fail("Expected a socket timeout");
            } catch (SocketTimeoutException e) {
                // expected
            }
            mode = Mode.UNAVAILABLE;
            try {
                execute();
                fail("Expected HTTP 503");
            } catch (ServiceUnavailableException e) {
                // expected
            }
        }
        assertEquals(6, hits.get());
    }

    /**
     * A failure that a half-open breaker doesn't handle closes it.
     * <ol>
     * <li>2 HTTP 503 responses open the service unavailable breaker.</li>
     * <li>After the open delay the backend is slow. The probe request reaches the server and fails with a socket timeout, which
     * the 503 breaker counts as a success: it closes and logs "CLOSED".</li>
     * <li>The next HTTP 503 reaches the server again instead of being rejected.</li>
     * </ol>
     * Why: documents a known limitation (Failsafe records every outcome a breaker doesn't handle as a success), also described in
     * the README. If unrelated failures stop resetting a breaker, change this test and the README.
     */
    @Test
    public void testUnhandledFailureClosesHalfOpenBreaker() throws Exception {
        client = activate(serverUrl(), true);
        mode = Mode.UNAVAILABLE;
        for (int i = 0; i < 2; i++) {
            try {
                execute();
                fail("Expected HTTP 503");
            } catch (ServiceUnavailableException e) {
                // expected
            }
        }
        expectOpen();

        Thread.sleep(OPEN_DELAY_MS + WAIT_MARGIN_MS);
        mode = Mode.SLOW;
        try {
            execute();
            fail("Expected a socket timeout from the half-open probe");
        } catch (SocketTimeoutException e) {
            // expected
        }
        assertLogged(Level.INFO, "503 circuit breaker CLOSED for 'it'");

        mode = Mode.UNAVAILABLE;
        try {
            execute();
            fail("Expected HTTP 503 from the closed breaker");
        } catch (ServiceUnavailableException e) {
            // expected
        }
        assertEquals(4, hits.get());
    }

    /**
     * Errors caused by the request, not by an unhealthy backend, never open a breaker: 5 HTTP 400 responses followed by 5 HTTP 200
     * responses that contain GraphQL errors. The breaker stays closed, all 10 requests reach the server and the GraphQL errors
     * are returned to the caller.
     * <p>
     * Why: a bad query or an unknown product must not block all requests to the backend.
     */
    @Test
    public void testClientErrorsAndGraphqlErrorsDoNotOpenBreaker() throws Exception {
        client = activate(serverUrl(), true);
        mode = Mode.BAD_REQUEST;
        for (int i = 0; i < 5; i++) {
            try {
                execute();
                fail("Expected HTTP 400");
            } catch (GraphqlRequestException e) {
                assertFalse("4xx must not open the breaker", e.getCause() instanceof CircuitBreakerOpenException);
                assertTrue(e.getMessage().contains("400"));
            }
        }
        mode = Mode.GRAPHQL_ERROR;
        for (int i = 0; i < 5; i++) {
            GraphqlResponse<Data, Error> response = execute();
            assertEquals("Internal server error", response.getErrors().get(0).message);
        }
        assertEquals(10, hits.get());
    }

    /**
     * In half-open state only one probe request reaches the backend, even when many requests arrive at the same time.
     * <ol>
     * <li>Open the socket timeout breaker, then wait for the open delay so that it becomes half-open. The backend is still slow.</li>
     * <li>Send 5 requests at the same time. Exactly 1 reaches the server and times out, the other 4 are rejected with
     * {@link CircuitBreakerOpenException}.</li>
     * <li>The failed probe opens the breaker again. This is logged at WARN with "previous state: HALF_OPEN".</li>
     * </ol>
     * Why: when many pages render at once, a recovering backend must not be flooded with requests.
     */
    @Test
    public void testHalfOpenBreakerLetsOnlyOneProbeThrough() throws Exception {
        client = activate(serverUrl(), true);
        openSocketTimeoutBreaker();
        int hitsWhenOpened = hits.get();

        // the backend is still slow when the breaker becomes half-open: concurrent requests must not all reach it
        Thread.sleep(OPEN_DELAY_MS + WAIT_MARGIN_MS);
        int requests = 5;
        ExecutorService callers = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Class<?>>> results = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                results.add(callers.submit((Callable<Class<?>>) () -> {
                    start.await();
                    try {
                        execute();
                        return null;
                    } catch (GraphqlRequestException e) {
                        return e instanceof SocketTimeoutException ? SocketTimeoutException.class : e.getCause().getClass();
                    }
                }));
            }
            start.countDown();
            int timeouts = 0;
            int rejected = 0;
            for (Future<Class<?>> result : results) {
                Class<?> outcome = result.get(10, TimeUnit.SECONDS);
                if (outcome == SocketTimeoutException.class) {
                    timeouts++;
                } else if (outcome == CircuitBreakerOpenException.class) {
                    rejected++;
                }
            }
            assertEquals(1, timeouts);
            assertEquals(requests - 1, rejected);
            assertEquals(hitsWhenOpened + 1, hits.get());
        } finally {
            callers.shutdownNow();
        }

        // the failed probe opens the breaker again
        expectOpen();
        assertLogged(Level.WARN, "Socket timeout circuit breaker OPENED for 'it'");
        assertLogged(Level.WARN, "previous state: HALF_OPEN");
    }

    /**
     * Each GraphQL client has its own breakers. While the breaker of client "failing" is open, client "healthy" (another
     * endpoint on the same server) still sends its requests normally.
     * <p>
     * Why: on AEM each OSGi configuration is a separate client. One failing endpoint must not block the others.
     */
    @Test
    public void testBreakersAreIsolatedPerClient() throws Exception {
        client = activate("failing", serverUrl(), true);
        GraphqlClientImpl healthyClient = activate("healthy", serverUrl().replace("/graphql", "/healthy"), true);

        openSocketTimeoutBreaker();
        assertEquals("ok", execute(healthyClient).getData().text);
        assertEquals("ok", execute(healthyClient).getData().text);
        assertEquals(2, healthyHits.get());
        expectOpen();
    }

    /**
     * Checks the log messages used to diagnose the breaker, from start-up to recovery:
     * <ul>
     * <li>INFO at start-up with the client name and URL,</li>
     * <li>WARN for the read timeout,</li>
     * <li>WARN when the breaker opens, with the previous state and the failure threshold,</li>
     * <li>INFO with the progressive delay,</li>
     * <li>DEBUG when a request is rejected, naming the breaker that rejected it,</li>
     * <li>DEBUG with the state of all breakers before each request,</li>
     * <li>INFO when the breaker becomes half-open and when it closes again.</li>
     * </ul>
     * Why: people search the logs for these messages when they troubleshoot a slow or failing backend. If you change a log
     * message, update this test.
     */
    @Test
    public void testFailurePathsAreLogged() throws Exception {
        client = activate(serverUrl(), true);
        openSocketTimeoutBreaker();

        assertLogged(Level.INFO, "Initialized CircuitBreakerService for 'it' (ranking 0, " + serverUrl() + ")");
        assertLogged(Level.WARN, "Read timeout after");
        assertLogged(Level.WARN, "Socket timeout circuit breaker OPENED for 'it'");
        assertLogged(Level.WARN, "previous state: CLOSED, failure threshold: 2");
        assertLogged(Level.INFO, "progressive delay: " + OPEN_DELAY_MS + "ms");
        assertLogged(Level.DEBUG, "rejected without calling the backend: the SocketTimeout circuit breaker of 'it'");
        assertLogged(Level.DEBUG, "circuit breaker states [ServiceUnavailable=CLOSED, ServerError=CLOSED, SocketTimeout=OPEN]");

        mode = Mode.OK;
        Thread.sleep(OPEN_DELAY_MS + WAIT_MARGIN_MS);
        assertEquals("ok", execute().getData().text);
        String clientName = "'it' (ranking 0, " + serverUrl() + ")";
        assertLogged(Level.INFO, "Socket timeout circuit breaker HALF-OPEN for " + clientName + " - allowing 1 probe request(s)");
        assertLogged(Level.INFO, "Socket timeout circuit breaker CLOSED for " + clientName + " - the probe request(s) did not time out");
    }
}
