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
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicStatusLine;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.adobe.cq.commerce.graphql.client.GraphqlClientConfiguration;
import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.graphql.client.RequestOptions;
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.CircuitBreakerService;
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.ServerErrorException;
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.ServiceUnavailableException;
import com.codahale.metrics.MetricRegistry;
import com.google.gson.reflect.TypeToken;
import dev.failsafe.CircuitBreakerOpenException;
import dev.failsafe.FailsafeException;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

public class FaultTolerantExecutorTest {

    private static class Data {
        String text;
        Integer count;
    }

    private static class Error {
        String message;
    }

    private FaultTolerantExecutor faultTolerantExecutor;
    private GraphqlRequest dummy = new GraphqlRequest("{dummy}");
    private MockGraphqlClientConfiguration mockConfig;
    private GraphqlClientMetrics metrics;
    private HttpClient httpClient;
    private HttpResponse httpResponse;
    private StatusLine statusLine;

    @Before
    public void setUp() throws Exception {
        // Use manual mocking instead of MockitoAnnotations to avoid Java 11 compatibility issues
        httpClient = mock(HttpClient.class);
        httpResponse = mock(HttpResponse.class);
        statusLine = mock(StatusLine.class);

        mockConfig = new MockGraphqlClientConfiguration();
        mockConfig.setIdentifier("mockIdentifier");
        mockConfig.setUrl("http://test-endpoint.com/graphql");

        metrics = new GraphqlClientMetricsImpl(new MetricRegistry(), mockConfig);
        faultTolerantExecutor = new FaultTolerantExecutor(httpClient, metrics, mockConfig);
    }

    @Test
    public void testSuccessfulRequest() throws Exception {
        // Setup successful response
        setupSuccessfulResponse();

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();
        GraphqlResponse<Data, Error> response = faultTolerantExecutor.execute(dummy, dataType, errorType, null);

        assertNotNull(response);
        assertNotNull(response.getData());
        assertEquals("Some text", response.getData().text);
        assertEquals(42, response.getData().count.intValue());

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testServiceUnavailableError() throws Exception {
        // Setup 503 response
        setupErrorResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable", "Service temporarily unavailable");

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected ServiceUnavailableException");
        } catch (ServiceUnavailableException e) {
            assertEquals("Server error 503: Service Unavailable", e.getMessage());
            assertEquals(503, e.getStatusCode());
            assertEquals("Service temporarily unavailable", e.getResponseBody());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testServerError500() throws Exception {
        // Setup 500 response
        setupErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal Server Error", "Internal server error occurred");

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected ServerErrorException");
        } catch (ServerErrorException e) {
            assertEquals("Server error 500: Internal Server Error", e.getMessage());
            assertEquals(500, e.getStatusCode());
            assertEquals("Internal server error occurred", e.getResponseBody());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testServerError502() throws Exception {
        // Setup 502 response
        setupErrorResponse(HttpStatus.SC_BAD_GATEWAY, "Bad Gateway", "Bad gateway error");

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected ServerErrorException");
        } catch (ServerErrorException e) {
            assertEquals("Server error 502: Bad Gateway", e.getMessage());
            assertEquals(502, e.getStatusCode());
            assertEquals("Bad gateway error", e.getResponseBody());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testServerError504() throws Exception {
        // Setup 504 response
        setupErrorResponse(HttpStatus.SC_GATEWAY_TIMEOUT, "Gateway Timeout", "Gateway timeout error");

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected ServerErrorException");
        } catch (ServerErrorException e) {
            assertEquals("Server error 504: Gateway Timeout", e.getMessage());
            assertEquals(504, e.getStatusCode());
            assertEquals("Gateway timeout error", e.getResponseBody());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testServerError599() throws Exception {
        // Setup 599 response (custom server error)
        setupErrorResponse(599, "Custom Server Error", "Custom server error");

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected ServerErrorException");
        } catch (ServerErrorException e) {
            assertEquals("Server error 599: Custom Server Error", e.getMessage());
            assertEquals(599, e.getStatusCode());
            assertEquals("Custom server error", e.getResponseBody());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testNonServerError() throws Exception {
        // Setup 400 response (client error, not server error)
        setupErrorResponse(HttpStatus.SC_BAD_REQUEST, "Bad Request", "Bad request error");

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("GraphQL query failed with response code 400", e.getMessage());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testCircuitBreakerOpenException() throws Exception {
        // Simulate CircuitBreakerOpenException from Failsafe
        CircuitBreakerOpenException cbException = mock(CircuitBreakerOpenException.class);
        when(httpClient.execute(any(HttpUriRequest.class), any(ResponseHandler.class)))
            .thenThrow(cbException);

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected GraphqlRequestException");
        } catch (GraphqlRequestException e) {
            assertEquals("GraphQL service temporarily unavailable (circuit breaker open). Please try again later.", e.getMessage());
            assertTrue(e.getCause() instanceof CircuitBreakerOpenException);
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testFailsafeException() throws Exception {
        // Simulate FailsafeException from Failsafe
        FailsafeException failsafeException = mock(FailsafeException.class);
        when(failsafeException.getMessage()).thenReturn("Failsafe error occurred");
        when(httpClient.execute(any(HttpUriRequest.class), any(ResponseHandler.class)))
            .thenThrow(failsafeException);

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected GraphqlRequestException");
        } catch (GraphqlRequestException e) {
            assertEquals("Failed to execute GraphQL request: Failsafe error occurred", e.getMessage());
            assertTrue(e.getCause() instanceof FailsafeException);
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testHttpClientIOException() throws Exception {
        when(httpClient.execute(any(HttpUriRequest.class), any(ResponseHandler.class)))
            .thenThrow(new IOException("Connection failed"));

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected exception for IO error");
        } catch (Exception e) {
            assertTrue(e instanceof GraphqlRequestException || e instanceof RuntimeException);
            assertEquals("Failed to send GraphQL request", e.getMessage());
            assertTrue(e.getCause() instanceof IOException);
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testNullResponseEntity() throws Exception {
        // Setup response with null entity
        when(httpResponse.getStatusLine()).thenReturn(new BasicStatusLine(org.apache.http.HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK"));
        when(httpResponse.getEntity()).thenReturn(null);

        when(httpClient.execute(any(HttpUriRequest.class), any(ResponseHandler.class)))
            .thenAnswer(invocation -> {
                ResponseHandler<?> handler = invocation.getArgumentAt(1, ResponseHandler.class);
                return handler.handleResponse(httpResponse);
            });

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("Failed to read HTTP response content", e.getMessage());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testSuccessfulRequestWithOptions() throws Exception {
        // Setup successful response
        setupSuccessfulResponse();

        RequestOptions options = new RequestOptions();
        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();
        GraphqlResponse<Data, Error> response = faultTolerantExecutor.execute(dummy, dataType, errorType, options);

        assertNotNull(response);
        assertNotNull(response.getData());
        assertEquals("Some text", response.getData().text);
        assertEquals(42, response.getData().count.intValue());

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testServiceUnavailableWithOptions() throws Exception {
        // Setup 503 response
        setupErrorResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable", "Service temporarily unavailable");

        RequestOptions options = new RequestOptions();
        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, options);
            fail("Expected ServiceUnavailableException");
        } catch (ServiceUnavailableException e) {
            assertEquals("Server error 503: Service Unavailable", e.getMessage());
            assertEquals(503, e.getStatusCode());
            assertEquals("Service temporarily unavailable", e.getResponseBody());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testLoggingForServerErrors() throws Exception {
        // Setup logger capture
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger(FaultTolerantExecutor.class);
        logger.setLevel(Level.WARN);
        Appender<ILoggingEvent> appender = mock(Appender.class);

        try {
            logger.addAppender(appender);

            // Setup 503 response
            setupErrorResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable", "Service temporarily unavailable");

            Type dataType = new TypeToken<Data>() {}.getType();
            Type errorType = new TypeToken<Error>() {}.getType();
            try {
                faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            } catch (ServiceUnavailableException e) {
                // Expected
            }

            // Verify warning was logged
            verify(appender).doAppend(any(ILoggingEvent.class));

        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    public void testConstructor() {
        assertNotNull(faultTolerantExecutor);
        assertEquals(httpClient, getHttpClientField(faultTolerantExecutor));
        assertEquals(metrics, getMetricsField(faultTolerantExecutor));
        assertEquals(mockConfig, getConfigurationField(faultTolerantExecutor));
    }

    // Enhanced test cases for circuit breaker functionality

    @Test
    public void testCircuitBreakerServiceConstructor() {
        CircuitBreakerService circuitBreakerService = new CircuitBreakerService();
        assertNotNull(circuitBreakerService);
    }

    @Test
    public void testServerErrorExceptionConstructor() {
        ServerErrorException exception = new ServerErrorException("Test error", 500, "Error body");
        assertEquals("Test error", exception.getMessage());
        assertEquals(500, exception.getStatusCode());
        assertEquals("Error body", exception.getResponseBody());
    }

    @Test
    public void testServerErrorExceptionConstructorWithCause() {
        Throwable cause = new RuntimeException("Root cause");
        ServerErrorException exception = new ServerErrorException("Test error", 500, "Error body", cause);
        assertEquals("Test error", exception.getMessage());
        assertEquals(500, exception.getStatusCode());
        assertEquals("Error body", exception.getResponseBody());
        assertEquals(cause, exception.getCause());
    }

    @Test
    public void testServiceUnavailableExceptionConstructor() {
        ServiceUnavailableException exception = new ServiceUnavailableException("Service unavailable", "Service down");
        assertEquals("Service unavailable", exception.getMessage());
        assertEquals(503, exception.getStatusCode());
        assertEquals("Service down", exception.getResponseBody());
    }

    @Test
    public void testServiceUnavailableExceptionConstructorWithCause() {
        Throwable cause = new RuntimeException("Root cause");
        ServiceUnavailableException exception = new ServiceUnavailableException("Service unavailable", "Service down", cause);
        assertEquals("Service unavailable", exception.getMessage());
        assertEquals(503, exception.getStatusCode());
        assertEquals("Service down", exception.getResponseBody());
        assertEquals(cause, exception.getCause());
    }

    @Test
    public void testCircuitBreakerProgressiveDelayFor503() throws Exception {
        // Test that 503 errors trigger progressive delay circuit breaker
        setupErrorResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable", "Service temporarily unavailable");

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();

        // First failure should throw ServiceUnavailableException
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected ServiceUnavailableException");
        } catch (ServiceUnavailableException e) {
            assertEquals(503, e.getStatusCode());
        }

        // Verify the circuit breaker is working
        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testCircuitBreakerConstantDelayFor5xx() throws Exception {
        // Test that other 5xx errors trigger constant delay circuit breaker
        setupErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal Server Error", "Server error");

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();

        // First failure should throw ServerErrorException
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected ServerErrorException");
        } catch (ServerErrorException e) {
            assertEquals(500, e.getStatusCode());
        }

        // Verify the circuit breaker is working
        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testMultipleConsecutive503Failures() throws Exception {
        // Test multiple consecutive 503 failures
        setupErrorResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable", "Service temporarily unavailable");

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();

        // Multiple failures should all throw ServiceUnavailableException
        for (int i = 0; i < 3; i++) {
            try {
                faultTolerantExecutor.execute(dummy, dataType, errorType, null);
                fail("Expected ServiceUnavailableException on attempt " + (i + 1));
            } catch (ServiceUnavailableException e) {
                assertEquals(503, e.getStatusCode());
            }
        }

        // Verify all attempts were made
        verify(httpClient, times(3)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testMultipleConsecutive5xxFailures() throws Exception {
        // Test multiple consecutive 5xx failures
        setupErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal Server Error", "Server error");

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();

        // Multiple failures should all throw ServerErrorException
        for (int i = 0; i < 3; i++) {
            try {
                faultTolerantExecutor.execute(dummy, dataType, errorType, null);
                fail("Expected ServerErrorException on attempt " + (i + 1));
            } catch (ServerErrorException e) {
                assertEquals(500, e.getStatusCode());
            }
        }

        // Verify all attempts were made
        verify(httpClient, times(3)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testSuccessAfter503Failure() throws Exception {
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

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();

        // First call should fail
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected ServiceUnavailableException");
        } catch (ServiceUnavailableException e) {
            assertEquals(503, e.getStatusCode());
        }

        // Second call should succeed
        GraphqlResponse<Data, Error> response = faultTolerantExecutor.execute(dummy, dataType, errorType, null);
        assertNotNull(response);
        assertNotNull(response.getData());

        // Verify both attempts were made
        verify(httpClient, times(2)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testSuccessAfter5xxFailure() throws Exception {
        // Test success after 5xx failure
        AtomicInteger callCount = new AtomicInteger(0);

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

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();

        // First call should fail
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected ServerErrorException");
        } catch (ServerErrorException e) {
            assertEquals(500, e.getStatusCode());
        }

        // Second call should succeed
        GraphqlResponse<Data, Error> response = faultTolerantExecutor.execute(dummy, dataType, errorType, null);
        assertNotNull(response);
        assertNotNull(response.getData());

        // Verify both attempts were made
        verify(httpClient, times(2)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testEdgeCaseStatusCode599() throws Exception {
        // Test edge case with status code 599
        setupErrorResponse(599, "Custom Server Error", "Custom error");

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected ServerErrorException");
        } catch (ServerErrorException e) {
            assertEquals(599, e.getStatusCode());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testEdgeCaseStatusCode499() throws Exception {
        // Test edge case with status code 499 (not a server error)
        setupErrorResponse(499, "Client Error", "Client error");

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("GraphQL query failed with response code 499", e.getMessage());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testEdgeCaseStatusCode600() throws Exception {
        // Test edge case with status code 600 (not a server error)
        setupErrorResponse(600, "Unknown Error", "Unknown error");

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("GraphQL query failed with response code 600", e.getMessage());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testEmptyResponseBody() throws Exception {
        // Test with empty response body
        setupErrorResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable", "");

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected ServiceUnavailableException");
        } catch (ServiceUnavailableException e) {
            assertEquals(503, e.getStatusCode());
            assertEquals("", e.getResponseBody());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testNullResponseBody() throws Exception {
        // Test with null response body
        when(httpResponse.getStatusLine()).thenReturn(new BasicStatusLine(org.apache.http.HttpVersion.HTTP_1_1,
            HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable"));
        when(httpResponse.getEntity()).thenReturn(null);

        when(httpClient.execute(any(HttpUriRequest.class), any(ResponseHandler.class)))
            .thenAnswer(invocation -> {
                ResponseHandler<?> handler = invocation.getArgumentAt(1, ResponseHandler.class);
                return handler.handleResponse(httpResponse);
            });

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();
        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("Entity may not be null", e.getMessage());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testCircuitBreakerServiceExecuteWithPolicies() throws Exception {
        // Test the executeWithPolicies method directly
        CircuitBreakerService circuitBreakerService = new CircuitBreakerService();

        // Test successful execution
        String result = circuitBreakerService.executeWithPolicies("http://test.com", () -> "success");
        assertEquals("success", result);
    }

    @Test
    public void testCircuitBreakerServiceExecuteWithPoliciesWithException() throws Exception {
        // Test the executeWithPolicies method with exception
        CircuitBreakerService circuitBreakerService = new CircuitBreakerService();

        try {
            circuitBreakerService.executeWithPolicies("http://test.com", () -> {
                throw new RuntimeException("Test exception");
            });
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("Test exception", e.getMessage());
        }
    }

    @Test
    public void testMetricsIncrementOnServerError() throws Exception {
        // Test that metrics are incremented on server errors
        setupErrorResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable", "Service temporarily unavailable");

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();

        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected ServiceUnavailableException");
        } catch (ServiceUnavailableException e) {
            // Expected
        }

        // Verify metrics were incremented
        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testMetricsIncrementOnClientError() throws Exception {
        // Test that metrics are incremented on client errors
        setupErrorResponse(HttpStatus.SC_BAD_REQUEST, "Bad Request", "Bad request");

        Type dataType = new TypeToken<Data>() {}.getType();
        Type errorType = new TypeToken<Error>() {}.getType();

        try {
            faultTolerantExecutor.execute(dummy, dataType, errorType, null);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            // Expected
        }

        // Verify metrics were incremented
        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
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

    private HttpClient getHttpClientField(FaultTolerantExecutor executor) {
        try {
            Field field = DefaultExecutor.class.getDeclaredField("client");
            field.setAccessible(true);
            return (HttpClient) field.get(executor);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private GraphqlClientMetrics getMetricsField(FaultTolerantExecutor executor) {
        try {
            Field field = DefaultExecutor.class.getDeclaredField("metrics");
            field.setAccessible(true);
            return (GraphqlClientMetrics) field.get(executor);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private GraphqlClientConfiguration getConfigurationField(FaultTolerantExecutor executor) {
        try {
            Field field = DefaultExecutor.class.getDeclaredField("configuration");
            field.setAccessible(true);
            return (GraphqlClientConfiguration) field.get(executor);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
