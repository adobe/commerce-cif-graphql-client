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

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.BundleContext;

import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
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

    private static final int SERVICE_UNAVAILABLE_THRESHOLD = 3;
    private static final int SERVICE_UNAVAILABLE_SUCCESS_THRESHOLD = 1;

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
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_SERVICE_UNAVAILABLE);

        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected ServiceUnavailableException");
        } catch (ServiceUnavailableException e) {
            assertEquals(503, e.getStatusCode());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testServerErrorsWithFaultTolerance() throws Exception {
        // Test various server error status codes
        int[] serverErrorCodes = { HttpStatus.SC_INTERNAL_SERVER_ERROR, HttpStatus.SC_BAD_GATEWAY, HttpStatus.SC_GATEWAY_TIMEOUT };

        for (int i = 0; i < serverErrorCodes.length; i++) {
            TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, serverErrorCodes[i]);

            try {
                graphqlClient.execute(dummy, Data.class, Error.class);
                fail("Expected ServerErrorException for status code " + serverErrorCodes[i]);
            } catch (ServerErrorException e) {
                assertEquals(serverErrorCodes[i], e.getStatusCode());
            }

            verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
            setUp();
        }
    }

    @Test
    public void testNonServerErrorWithFaultTolerance() throws Exception {
        // Test various server error status codes
        int[] serverErrorCodes = { HttpStatus.SC_BAD_REQUEST, HttpStatus.SC_REQUEST_TOO_LONG, HttpStatus.SC_NOT_FOUND,
            HttpStatus.SC_UNAUTHORIZED, HttpStatus.SC_FORBIDDEN, HttpStatus.SC_CONFLICT, HttpStatus.SC_UNPROCESSABLE_ENTITY };

        for (int i = 0; i < serverErrorCodes.length; i++) {
            TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, serverErrorCodes[i]);

            try {
                graphqlClient.execute(dummy, Data.class, Error.class);
                fail("Expected ServerErrorException for status code " + serverErrorCodes[i]);
            } catch (RuntimeException e) {
                assertEquals("GraphQL query failed with response code " + serverErrorCodes[i], e.getMessage());
            }

            verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
            setUp();
        }
    }

    @Test
    public void testCircuitBreakerOpenExceptionWithFaultTolerance() throws Exception {
        test503WithFaultTolerance();
    }

    @Test
    public void testCircuitBreakerClosedWithFaultTolerance() throws Exception {
        test503WithFaultTolerance();
        Thread.sleep(10);
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);
        graphqlClient.execute(dummy, Data.class, Error.class);
        verify(httpClient, times(SERVICE_UNAVAILABLE_THRESHOLD + 1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    @Test
    public void testCircuitBreakerContinuousOpenStateWithFaultTolerance() throws Exception {
        // First, trigger circuit breaker to open state by exceeding threshold
        test503WithFaultTolerance();

        // Wait for circuit breaker to transition to half-open state
        Thread.sleep(10);

        // Simulate another 503 response to test circuit breaker behavior in half-open state
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_SERVICE_UNAVAILABLE);

        // Attempt execution - should throw ServiceUnavailableException and re-open circuit breaker
        // The circuit breaker will return to open state due to the 503 response
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
        } catch (ServiceUnavailableException e) {
            assertEquals(503, e.getStatusCode());
        }

        // Verify the HTTP client was called (circuit breaker was in half-open state)
        verify(httpClient, times(SERVICE_UNAVAILABLE_THRESHOLD + 1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));

        // Wait for circuit breaker timeout to transition back to half-open state
        Thread.sleep(12);

        // Simulate successful response to test circuit breaker closing
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);

        // Execute again - should succeed and close the circuit breaker
        graphqlClient.execute(dummy, Data.class, Error.class);

        // Verify the HTTP client was called again (circuit breaker is now closed)
        verify(httpClient, times(SERVICE_UNAVAILABLE_THRESHOLD + 2)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
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
    public void testMultipleConsecutive5xxFailuresWithFaultTolerance() throws Exception {
        test5XXWithFaultTolerance();
    }

    @Test
    public void test5xxFailureClosedWithFaultTolerance() throws Exception {
        test5XXWithFaultTolerance();
        Thread.sleep(1000); // Sleep for 12 seconds to allow circuit breaker to close (with 10-second delay)
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);
        graphqlClient.execute(dummy, Data.class, Error.class);
        verify(httpClient, times(SERVICE_UNAVAILABLE_THRESHOLD + 1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
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

    @Test
    public void test4xxErrorHandling() throws Exception {
        // Test various 4xx errors
        int[] clientErrorCodes = { 400, 401, 403, 404, 409, 422, 600 };

        for (int i = 0; i < clientErrorCodes.length; i++) {
            TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, clientErrorCodes[i]);

            try {
                graphqlClient.execute(dummy, Data.class, Error.class);
                fail("Expected RuntimeException for status code " + clientErrorCodes[i]);
            } catch (RuntimeException e) {
                assertEquals("GraphQL query failed with response code " + clientErrorCodes[i], e.getMessage());
            }
        }
    }

    @Test
    public void testServerErrorExceptionWithoutCause() {
        // Test ServerErrorException constructor without cause
        ServerErrorException exception = new ServerErrorException("Test error", 500, "Error body");

        assertEquals("Test error", exception.getMessage());
        assertEquals(500, exception.getStatusCode());
        assertEquals("Error body", exception.getResponseBody());
        assertNull(exception.getCause());
    }

    @Test
    public void testServerErrorExceptionWithCause() {
        // Test ServerErrorException constructor with cause
        Throwable cause = new RuntimeException("Original cause");
        ServerErrorException exception = new ServerErrorException("Server error", 500, "Error body", cause);

        assertEquals("Server error", exception.getMessage());
        assertEquals(500, exception.getStatusCode());
        assertEquals("Error body", exception.getResponseBody());
        assertEquals(cause, exception.getCause());
    }

    @Test
    public void testServiceUnavailableExceptionWithoutCause() {
        // Test ServiceUnavailableException constructor without cause
        ServiceUnavailableException exception = new ServiceUnavailableException("Service unavailable", "Service down");

        assertEquals("Service unavailable", exception.getMessage());
        assertEquals(503, exception.getStatusCode());
        assertEquals("Service down", exception.getResponseBody());
        assertNull(exception.getCause());
    }

    @Test
    public void testServiceUnavailableExceptionWithCause() {
        // Test ServiceUnavailableException constructor with cause
        Throwable cause = new RuntimeException("Original cause");
        ServiceUnavailableException exception = new ServiceUnavailableException("Service unavailable", "Service down", cause);

        assertEquals("Service unavailable", exception.getMessage());
        assertEquals(503, exception.getStatusCode());
        assertEquals("Service down", exception.getResponseBody());
        assertEquals(cause, exception.getCause());
    }

    @Test
    public void testHandleErrorResponseMethod() throws Exception {
        // Test the handleErrorResponse method that is called for non-5xx errors
        // This tests the line: throw handleErrorResponse(statusLine);
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_BAD_REQUEST);

        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail("Expected RuntimeException from handleErrorResponse");
        } catch (RuntimeException e) {
            assertEquals("GraphQL query failed with response code 400", e.getMessage());
        }

        verify(httpClient, times(1)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    // helpers
    private void test503WithFaultTolerance() throws Exception {
        for (int i = 0; i <= SERVICE_UNAVAILABLE_THRESHOLD; i++) {
            TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_SERVICE_UNAVAILABLE);
            try {
                graphqlClient.execute(dummy, Data.class, Error.class);
                if (i < SERVICE_UNAVAILABLE_THRESHOLD) {
                    fail("Expected Exception for status code " + HttpStatus.SC_SERVICE_UNAVAILABLE);
                } else {
                    fail("Expected GraphqlRequestException for status code " + HttpStatus.SC_SERVICE_UNAVAILABLE);
                }
            } catch (ServiceUnavailableException e) {
                if (i < SERVICE_UNAVAILABLE_THRESHOLD) {
                    assertEquals(503, e.getStatusCode());
                } else {
                    fail("Excepted GraphqlRequestException error");
                }
            } catch (GraphqlRequestException e) {
                assertTrue(e.getCause() instanceof CircuitBreakerOpenException);
            }
        }
        verify(httpClient, times(3)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }

    private void test5XXWithFaultTolerance() throws Exception {
        // Test various server error status codes
        int[] serverErrorCodes = { HttpStatus.SC_INTERNAL_SERVER_ERROR, HttpStatus.SC_BAD_GATEWAY, HttpStatus.SC_GATEWAY_TIMEOUT,
            HttpStatus.SC_INTERNAL_SERVER_ERROR };

        for (int i = 0; i < serverErrorCodes.length; i++) {
            TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, serverErrorCodes[i]);

            try {
                graphqlClient.execute(dummy, Data.class, Error.class);
                if (i < SERVICE_UNAVAILABLE_THRESHOLD) {
                    fail("Expected Exception for status code " + serverErrorCodes[i]);
                } else {
                    fail("Expected GraphqlRequestException for status code " + serverErrorCodes[i]);
                }
            } catch (ServerErrorException e) {
                if (i < SERVICE_UNAVAILABLE_THRESHOLD) {
                    assertEquals(serverErrorCodes[i], e.getStatusCode());
                } else {
                    fail("Excepted GraphqlRequestException error");
                }
            } catch (GraphqlRequestException e) {
                assertTrue(e.getCause() instanceof CircuitBreakerOpenException);
            }
        }

        verify(httpClient, times(3)).execute(any(HttpUriRequest.class), any(ResponseHandler.class));
    }
}
