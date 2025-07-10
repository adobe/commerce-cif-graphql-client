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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.BundleContext;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.graphql.client.RequestOptions;
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.ServerErrorException;
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.ServiceUnavailableException;
import dev.failsafe.CircuitBreakerOpenException;
import dev.failsafe.FailsafeException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FaultTolerantExecutorTest {

    private static class Data {
        String text;
        Integer count;
    }

    private static class Error {
        String message;
    }

    private GraphqlClientImpl graphqlClient;
    private GraphqlRequest dummy = new GraphqlRequest("{dummy}");
    private MockGraphqlClientConfiguration mockConfig;
    private CloseableHttpClient httpClient;
    private HttpResponse httpResponse;
    private StatusLine statusLine;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        graphqlClient = new GraphqlClientImpl();

        httpClient = Mockito.mock(CloseableHttpClient.class);
        httpResponse = Mockito.mock(HttpResponse.class);
        statusLine = Mockito.mock(StatusLine.class);

        // Mock HttpClientBuilderFactory to return our mocked HttpClient
        HttpClientBuilderFactory mockBuilderFactory = mock(HttpClientBuilderFactory.class);
        HttpClientBuilder mockBuilder = mock(HttpClientBuilder.class);
        when(mockBuilderFactory.newBuilder()).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn((CloseableHttpClient) httpClient);

        // Use reflection to set the private clientBuilderFactory field
        Field clientBuilderFactory = GraphqlClientImpl.class.getDeclaredField("clientBuilderFactory");
        clientBuilderFactory.setAccessible(true);
        clientBuilderFactory.set(graphqlClient, mockBuilderFactory);

        mockConfig = new MockGraphqlClientConfiguration();
        mockConfig.setIdentifier("mockIdentifier");
        mockConfig.setEnableFaultTolerantFallback(true);

        graphqlClient.activate(mockConfig, mock(BundleContext.class));
    }

    @Test
    public void testSuccessfulRequestWithFaultToleranceEnabled() throws Exception {
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);

        GraphqlResponse<Data, Error> response = graphqlClient.execute(dummy, Data.class, Error.class);

        assertNotNull(response);
        assertNotNull(response.getData());
        assertEquals("Some text", response.getData().text);
        assertEquals(42, response.getData().count.intValue());
        assertEquals(1, response.getErrors().size());
        assertEquals("Error message", response.getErrors().get(0).message);

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testServiceUnavailableErrorWithFaultTolerance() throws Exception {
        setupErrorResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable", "Service temporarily unavailable");

        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected ServiceUnavailableException");
        } catch (ServiceUnavailableException e) {
            assertEquals("Server error 503: Service Unavailable", e.getMessage());
            assertEquals(503, e.getStatusCode());
            assertEquals("Service temporarily unavailable", e.getResponseBody());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testServerErrorsWithFaultTolerance() throws Exception {
        // Test various server error status codes
        int[] serverErrorCodes = { 500, 502, 504, 599 };
        String[] reasonPhrases = { "Internal Server Error", "Bad Gateway", "Gateway Timeout", "Custom Server Error" };
        String[] responseBodies = { "Internal server error occurred", "Bad gateway error", "Gateway timeout error", "Custom server error" };

        for (int i = 0; i < serverErrorCodes.length; i++) {
            setupErrorResponse(serverErrorCodes[i], reasonPhrases[i], responseBodies[i]);

            try {
                graphqlClient.execute(dummy, Data.class, Error.class);
                fail("Expected ServerErrorException for status code " + serverErrorCodes[i]);
            } catch (ServerErrorException e) {
                assertEquals("Server error " + serverErrorCodes[i] + ": " + reasonPhrases[i], e.getMessage());
                assertEquals(serverErrorCodes[i], e.getStatusCode());
                assertEquals(responseBodies[i], e.getResponseBody());
            }

            verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
            reset(httpClient, httpResponse);
            setUp();
        }
    }

    @Test
    public void testNonServerErrorWithFaultTolerance() throws Exception {
        setupErrorResponse(HttpStatus.SC_BAD_REQUEST, "Bad Request", "Bad request error");

        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("GraphQL query failed with response code 400", e.getMessage());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testCircuitBreakerOpenExceptionWithFaultTolerance() throws Exception {
        CircuitBreakerOpenException cbException = mock(CircuitBreakerOpenException.class);
        when(httpClient.execute(any(HttpUriRequest.class), any(ResponseHandler.class)))
            .thenThrow(cbException);

        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected GraphqlRequestException");
        } catch (GraphqlRequestException e) {
            assertEquals("GraphQL service temporarily unavailable (circuit breaker open). Please try again later.", e.getMessage());
            assertTrue(e.getCause() instanceof CircuitBreakerOpenException);
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testFailsafeExceptionWithFaultTolerance() throws Exception {
        FailsafeException failsafeException = mock(FailsafeException.class);
        when(failsafeException.getMessage()).thenReturn("Failsafe error occurred");
        when(httpClient.execute(any(HttpUriRequest.class), any(ResponseHandler.class)))
            .thenThrow(failsafeException);

        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected GraphqlRequestException");
        } catch (GraphqlRequestException e) {
            assertEquals("Failed to execute GraphQL request: Failsafe error occurred", e.getMessage());
            assertTrue(e.getCause() instanceof FailsafeException);
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testHttpClientIOExceptionWithFaultTolerance() throws Exception {
        when(httpClient.execute(any(HttpUriRequest.class), any(ResponseHandler.class)))
            .thenThrow(new IOException("Connection failed"));

        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected exception for IO error");
        } catch (Exception e) {
            assertTrue(e instanceof GraphqlRequestException || e instanceof RuntimeException);
            assertTrue(e.getMessage().contains("Failed to send GraphQL request") || e.getMessage().contains("Connection failed"));
            assertTrue(e.getCause() instanceof IOException);
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testRequestWithOptionsAndFaultTolerance() throws Exception {
        // Test successful request with options
        setupSuccessfulResponse();
        RequestOptions options = new RequestOptions();
        GraphqlResponse<Data, Error> response = graphqlClient.execute(dummy, Data.class, Error.class, options);

        assertNotNull(response);
        assertNotNull(response.getData());
        assertEquals("Some text", response.getData().text);
        assertEquals(42, response.getData().count.intValue());

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));

        // Test service unavailable with options
        reset(httpClient, httpResponse);
        setupErrorResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable", "Service temporarily unavailable");

        try {
            graphqlClient.execute(dummy, Data.class, Error.class, options);
            fail("Expected ServiceUnavailableException");
        } catch (ServiceUnavailableException e) {
            assertEquals("Server error 503: Service Unavailable", e.getMessage());
            assertEquals(503, e.getStatusCode());
            assertEquals("Service temporarily unavailable", e.getResponseBody());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testLoggingForServerErrorsWithFaultTolerance() throws Exception {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger(FaultTolerantExecutor.class);
        logger.setLevel(Level.WARN);
        Appender<ILoggingEvent> appender = mock(Appender.class);

        try {
            logger.addAppender(appender);

            setupErrorResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable", "Service temporarily unavailable");

            try {
                graphqlClient.execute(dummy, Data.class, Error.class);
            } catch (ServiceUnavailableException e) {
                // Expected
            }

            verify(appender).doAppend(any(ILoggingEvent.class));

        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    public void testMultipleConsecutive503FailuresWithFaultTolerance() throws Exception {
        setupErrorResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable", "Service temporarily unavailable");

        // First attempt should throw ServiceUnavailableException
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected ServiceUnavailableException on first attempt");
        } catch (ServiceUnavailableException e) {
            assertEquals(503, e.getStatusCode());
        }

        // Second attempt should also throw ServiceUnavailableException
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected ServiceUnavailableException on second attempt");
        } catch (ServiceUnavailableException e) {
            assertEquals(503, e.getStatusCode());
        }

        // Third attempt should also throw ServiceUnavailableException
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected ServiceUnavailableException on third attempt");
        } catch (ServiceUnavailableException e) {
            assertEquals(503, e.getStatusCode());
        }

        // Fourth attempt should throw CircuitBreakerOpenException (no HTTP call)
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected GraphqlRequestException due to circuit breaker");
        } catch (GraphqlRequestException e) {
            assertEquals("GraphQL service temporarily unavailable (circuit breaker open). Please try again later.", e.getMessage());
            assertTrue(e.getCause() instanceof CircuitBreakerOpenException);
        }

        verify(httpClient, times(3)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testMultipleConsecutive5xxFailuresWithFaultTolerance() throws Exception {
        // Re-initialize the client and mocks to reset circuit breaker state
        setUp();
        reset(httpClient, httpResponse);
        setupErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal Server Error", "Server error");

        // First attempt should throw ServerErrorException
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected ServerErrorException on first attempt");
        } catch (ServerErrorException e) {
            assertEquals(500, e.getStatusCode());
        }

        // Second attempt should also throw ServerErrorException
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected ServerErrorException on second attempt");
        } catch (ServerErrorException e) {
            assertEquals(500, e.getStatusCode());
        }

        // Third attempt should also throw ServerErrorException
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected ServerErrorException on third attempt");
        } catch (ServerErrorException e) {
            assertEquals(500, e.getStatusCode());
        }

        // Fourth attempt should throw CircuitBreakerOpenException (no HTTP call)
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected GraphqlRequestException due to circuit breaker");
        } catch (GraphqlRequestException e) {
            assertEquals("GraphQL service temporarily unavailable (circuit breaker open). Please try again later.", e.getMessage());
            assertTrue(e.getCause() instanceof CircuitBreakerOpenException);
        }

        verify(httpClient, times(3)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testSuccessAfterFailureWithFaultTolerance() throws Exception {
        // Test success after 503 failure
        AtomicInteger callCount = new AtomicInteger(0);

        when(httpClient.execute(any(HttpUriRequest.class), any(ResponseHandler.class)))
            .thenAnswer(invocation -> {
                int count = callCount.incrementAndGet();
                ResponseHandler<?> handler = invocation.getArgumentAt(1, ResponseHandler.class);
                if (count == 1) {
                    // First call returns 503
                    StringEntity entity = new StringEntity("Service temporarily unavailable", StandardCharsets.UTF_8);
                    when(httpResponse.getStatusLine()).thenReturn(new BasicStatusLine(org.apache.http.HttpVersion.HTTP_1_1,
                        HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable"));
                    when(httpResponse.getEntity()).thenReturn(entity);
                    return handler.handleResponse(httpResponse);
                } else {
                    // Second call returns success
                    String responseJson = "{\"data\":{\"text\":\"Some text\",\"count\":42},\"errors\":[{\"message\":\"Error message\"}]}";
                    StringEntity entity = new StringEntity(responseJson, StandardCharsets.UTF_8);
                    when(httpResponse.getStatusLine()).thenReturn(new BasicStatusLine(org.apache.http.HttpVersion.HTTP_1_1,
                        HttpStatus.SC_OK, "OK"));
                    when(httpResponse.getEntity()).thenReturn(entity);
                    return handler.handleResponse(httpResponse);
                }
            });

        // First call should fail
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected ServiceUnavailableException");
        } catch (ServiceUnavailableException e) {
            assertEquals(503, e.getStatusCode());
        }

        // Second call should succeed
        GraphqlResponse<Data, Error> response = graphqlClient.execute(dummy, Data.class, Error.class);
        assertNotNull(response);
        assertNotNull(response.getData());

        verify(httpClient, times(2)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));

        // Test success after 5xx failure
        reset(httpClient, httpResponse);
        callCount.set(0);

        when(httpClient.execute(any(HttpUriRequest.class), any(ResponseHandler.class)))
            .thenAnswer(invocation -> {
                int count = callCount.incrementAndGet();
                ResponseHandler<?> handler = invocation.getArgumentAt(1, ResponseHandler.class);
                if (count == 1) {
                    // First call returns 500
                    StringEntity entity = new StringEntity("Server error", StandardCharsets.UTF_8);
                    when(httpResponse.getStatusLine()).thenReturn(new BasicStatusLine(org.apache.http.HttpVersion.HTTP_1_1,
                        HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal Server Error"));
                    when(httpResponse.getEntity()).thenReturn(entity);
                    return handler.handleResponse(httpResponse);
                } else {
                    // Second call returns success
                    String responseJson = "{\"data\":{\"text\":\"Some text\",\"count\":42},\"errors\":[{\"message\":\"Error message\"}]}";
                    StringEntity entity = new StringEntity(responseJson, StandardCharsets.UTF_8);
                    when(httpResponse.getStatusLine()).thenReturn(new BasicStatusLine(org.apache.http.HttpVersion.HTTP_1_1,
                        HttpStatus.SC_OK, "OK"));
                    when(httpResponse.getEntity()).thenReturn(entity);
                    return handler.handleResponse(httpResponse);
                }
            });

        // First call should fail
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected ServerErrorException");
        } catch (ServerErrorException e) {
            assertEquals(500, e.getStatusCode());
        }

        // Second call should succeed
        response = graphqlClient.execute(dummy, Data.class, Error.class);
        assertNotNull(response);
        assertNotNull(response.getData());

        verify(httpClient, times(2)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testResponseBodyEdgeCasesWithFaultTolerance() throws Exception {
        // Test empty response body
        setupErrorResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable", "");

        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected ServiceUnavailableException");
        } catch (ServiceUnavailableException e) {
            assertEquals(503, e.getStatusCode());
            assertEquals("", e.getResponseBody());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));

        // Test null response body
        reset(httpClient, httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(new BasicStatusLine(org.apache.http.HttpVersion.HTTP_1_1,
            HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable"));
        when(httpResponse.getEntity()).thenReturn(null);

        when(httpClient.execute(any(HttpUriRequest.class), any(ResponseHandler.class)))
            .thenAnswer(invocation -> {
                ResponseHandler<?> handler = invocation.getArgumentAt(1, ResponseHandler.class);
                return handler.handleResponse(httpResponse);
            });

        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("Entity may not be null", e.getMessage());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testFaultToleranceDisabled() throws Exception {
        // Create new client with fault tolerance disabled
        GraphqlClientImpl clientWithoutFaultTolerance = new GraphqlClientImpl();

        HttpClientBuilderFactory mockBuilderFactory = mock(HttpClientBuilderFactory.class);
        HttpClientBuilder mockBuilder = mock(HttpClientBuilder.class);
        when(mockBuilderFactory.newBuilder()).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn((CloseableHttpClient) httpClient);

        Field clientBuilderFactory = GraphqlClientImpl.class.getDeclaredField("clientBuilderFactory");
        clientBuilderFactory.setAccessible(true);
        clientBuilderFactory.set(clientWithoutFaultTolerance, mockBuilderFactory);

        MockGraphqlClientConfiguration configWithoutFaultTolerance = new MockGraphqlClientConfiguration();
        configWithoutFaultTolerance.setIdentifier("mockIdentifier");
        configWithoutFaultTolerance.setEnableFaultTolerantFallback(false);

        clientWithoutFaultTolerance.activate(configWithoutFaultTolerance, mock(BundleContext.class));

        // Test that 503 errors are handled normally without fault tolerance
        setupErrorResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable", "Service temporarily unavailable");

        try {
            clientWithoutFaultTolerance.execute(dummy, Data.class, Error.class);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("GraphQL query failed with response code 503", e.getMessage());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testGraphqlClientConfigurationImplFaultTolerantFallback() {
        // Test default value
        GraphqlClientConfigurationImpl config = new GraphqlClientConfigurationImpl("http://test.com");
        assertFalse(config.enableFaultTolerantFallback());

        // Test setting the value
        config.setEnableFaultTolerantFallback(true);
        assertTrue(config.enableFaultTolerantFallback());

        // Test setting back to false
        config.setEnableFaultTolerantFallback(false);
        assertFalse(config.enableFaultTolerantFallback());
    }

    // Helper methods
    private void setupSuccessfulResponse() throws Exception {
        String responseJson = "{\"data\":{\"text\":\"Some text\",\"count\":42},\"errors\":[{\"message\":\"Error message\"}]}";
        StringEntity entity = new StringEntity(responseJson, StandardCharsets.UTF_8);

        when(httpResponse.getStatusLine()).thenReturn(new BasicStatusLine(org.apache.http.HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK"));
        when(httpResponse.getEntity()).thenReturn(entity);

        when(httpClient.execute(any(HttpUriRequest.class), any(ResponseHandler.class)))
            .thenAnswer(invocation -> {
                ResponseHandler<?> handler = invocation.getArgumentAt(1, ResponseHandler.class);
                return handler.handleResponse(httpResponse);
            });
    }

    private void setupErrorResponse(int statusCode, String reasonPhrase, String responseBody) throws Exception {
        StringEntity entity = new StringEntity(responseBody, StandardCharsets.UTF_8);

        when(httpResponse.getStatusLine()).thenReturn(new BasicStatusLine(org.apache.http.HttpVersion.HTTP_1_1, statusCode, reasonPhrase));
        when(httpResponse.getEntity()).thenReturn(entity);

        when(httpClient.execute(any(HttpUriRequest.class), any(ResponseHandler.class)))
            .thenAnswer(invocation -> {
                ResponseHandler<?> handler = invocation.getArgumentAt(1, ResponseHandler.class);
                return handler.handleResponse(httpResponse);
            });
    }
}
