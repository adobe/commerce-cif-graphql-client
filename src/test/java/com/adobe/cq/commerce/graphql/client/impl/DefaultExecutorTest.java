/*******************************************************************************
 *
 *    Copyright 2025 Adobe. All rights reserved.
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlRequestException;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.graphql.client.HttpMethod;
import com.adobe.cq.commerce.graphql.client.RequestOptions;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DefaultExecutorTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private CloseableHttpClient closeableHttpClient;

    @Mock
    private GraphqlClientMetrics metrics;

    @Mock
    private GraphqlClientConfigurationImpl configuration;

    private DefaultExecutor executor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // Setup default configuration behavior
        when(configuration.url()).thenReturn("http://test.com/graphql");
        when(configuration.httpMethod()).thenReturn(HttpMethod.POST);
        when(configuration.httpHeaders()).thenReturn(new String[0]);

        // Setup metrics behavior
        when(metrics.startRequestDurationTimer()).thenReturn(() -> 100L);

        executor = new DefaultExecutor(httpClient, metrics, configuration);
    }

    @Test
    public void testRequestTimer() throws Exception {
        DefaultExecutor.RequestTimer timer = new DefaultExecutor.RequestTimer(metrics);
        Thread.sleep(5);
        assertTrue("Duration should be at least 5 ms", timer.duration() >= 5);
        assertEquals(Long.valueOf(100L), timer.stop());
    }

    /**
     * Two requests overlap on the same executor: request A is still waiting for the backend when request B starts and ends.
     * Each request reports its own duration and stops its own metrics timer.
     * <p>
     * Why: the executor is shared by all concurrent requests of a client. When the start time and the metrics timer were fields
     * of the executor, request B overwrote them, so request A reported B's duration and stopped B's timer instead of its own.
     */
    @Test
    public void testOverlappingRequestsKeepTheirOwnTiming() throws Exception {
        assertOverlappingRequestsKeepTheirOwnTiming(executor);
    }

    /**
     * Same as {@link #testOverlappingRequestsKeepTheirOwnTiming()}, for the executor used when the circuit breakers are enabled.
     */
    @Test
    public void testOverlappingRequestsKeepTheirOwnTimingWithFaultTolerantExecutor() throws Exception {
        assertOverlappingRequestsKeepTheirOwnTiming(new FaultTolerantExecutor(httpClient, metrics, new MockGraphqlClientConfiguration()));
    }

    @SuppressWarnings("unchecked")
    private void assertOverlappingRequestsKeepTheirOwnTiming(DefaultExecutor testExecutor) throws Exception {
        Supplier<Long> stopTimerA = mock(Supplier.class);
        Supplier<Long> stopTimerB = mock(Supplier.class);
        when(metrics.startRequestDurationTimer()).thenReturn(stopTimerA, stopTimerB);

        List<GraphqlResponse<Object, Object>> responsesB = new ArrayList<>();
        AtomicBoolean firstCall = new AtomicBoolean(true);
        when(httpClient.execute(any(HttpUriRequest.class), any(ResponseHandler.class))).thenAnswer(invocation -> {
            if (firstCall.getAndSet(false)) {
                // request A waits for the backend while request B runs from start to end
                Thread.sleep(200);
                responsesB.add(testExecutor.execute(new GraphqlRequest("{b}"), Object.class, Object.class, null));
            }
            ResponseHandler<?> handler = (ResponseHandler<?>) invocation.getArguments()[1];
            return handler.handleResponse(okResponse());
        });

        GraphqlResponse<Object, Object> responseA = testExecutor.execute(new GraphqlRequest("{a}"), Object.class, Object.class, null);

        assertTrue("Request A should take at least 200 ms, was " + responseA.getDuration(), responseA.getDuration() >= 200);
        assertTrue("Request B should not include the wait of A, was " + responsesB.get(0).getDuration(),
            responsesB.get(0).getDuration() < 200);
        verify(stopTimerA, times(1)).get();
        verify(stopTimerB, times(1)).get();
    }

    private static HttpResponse okResponse() {
        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK"));
        response.setEntity(new StringEntity("{\"data\":{}}", StandardCharsets.UTF_8));
        return response;
    }

    @Test
    public void testCloseWithCloseableHttpClient() throws IOException {
        DefaultExecutor testExecutor = new DefaultExecutor(closeableHttpClient, metrics, configuration);

        // Call close method
        testExecutor.close();

        // Verify that close was called on the closeable client
        verify(closeableHttpClient, times(1)).close();
    }

    @Test
    public void testCloseWithNonCloseableHttpClient() {
        DefaultExecutor testExecutor = new DefaultExecutor(httpClient, metrics, configuration);

        // Call close method - should not throw exception even if client is not closeable
        testExecutor.close();

        // No exception should be thrown
    }

    @Test
    public void testCloseWithIOException() throws IOException {
        // Setup closeable client to throw IOException on close
        doThrow(new IOException("Test close exception")).when(closeableHttpClient).close();
        DefaultExecutor testExecutor = new DefaultExecutor(closeableHttpClient, metrics, configuration);

        // Call close method - should handle IOException gracefully
        testExecutor.close();

        // Verify close was attempted
        verify(closeableHttpClient, times(1)).close();
    }

    @Test
    public void testBuildRequestWithNullOptions() throws Exception {
        GraphqlRequest request = new GraphqlRequest("{ query }");

        // Call buildRequest with null options
        executor.buildRequest(request, null);

        // Should not throw exception
    }

    @Test
    public void testBuildRequestWithOptionsAndCustomHttpMethod() throws Exception {
        GraphqlRequest request = new GraphqlRequest("{ query }");
        RequestOptions options = mock(RequestOptions.class);
        when(options.getHttpMethod()).thenReturn(HttpMethod.GET);
        when(options.getHeaders()).thenReturn(null);

        // Call buildRequest with options that override http method
        executor.buildRequest(request, options);

        // Should not throw exception
    }

    @Test
    public void testBuildRequestWithCustomHeaders() throws Exception {
        GraphqlRequest request = new GraphqlRequest("{ query }");
        RequestOptions options = mock(RequestOptions.class);
        when(options.getHttpMethod()).thenReturn(null);
        when(options.getHeaders()).thenReturn(null);

        // Call buildRequest with custom headers
        executor.buildRequest(request, options);

        // Should not throw exception
    }

    @Test
    public void testHandleErrorResponse() {
        org.apache.http.StatusLine statusLine = mock(org.apache.http.StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_BAD_REQUEST);

        try {
            // Use reflection to call private method
            java.lang.reflect.Method method = DefaultExecutor.class.getDeclaredMethod("handleErrorResponse",
                org.apache.http.StatusLine.class, DefaultExecutor.RequestTimer.class);
            method.setAccessible(true);
            method.invoke(executor, statusLine, new DefaultExecutor.RequestTimer(metrics));
            fail("Expected GraphqlRequestException");
        } catch (Exception e) {
            assertTrue("Should wrap in GraphqlRequestException", e.getCause() instanceof GraphqlRequestException);
        }

        // Verify metrics were incremented
        verify(metrics, times(1)).incrementRequestErrors(HttpStatus.SC_BAD_REQUEST);
    }
}
