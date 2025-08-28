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

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlRequestException;
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
    public void testCalculateDurationWhenStartTimeNotSet() {
        // Test calculateDuration when requestStartTime is 0 (not set)
        DefaultExecutor testExecutor = new DefaultExecutor(httpClient, metrics, configuration);
        long duration = testExecutor.calculateDuration();
        assertEquals("Duration should be 0 when start time not set", 0L, duration);
    }

    @Test
    public void testCalculateDurationWhenStartTimeSet() throws Exception {
        // Test calculateDuration when requestStartTime is set
        long startTime = System.currentTimeMillis();

        // Use reflection to set the requestStartTime
        java.lang.reflect.Field field = DefaultExecutor.class.getDeclaredField("requestStartTime");
        field.setAccessible(true);
        field.setLong(executor, startTime);

        Thread.sleep(1); // Ensure some time passes
        long duration = executor.calculateDuration();
        assertTrue("Duration should be greater than 0", duration > 0);
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
                org.apache.http.StatusLine.class);
            method.setAccessible(true);
            method.invoke(executor, statusLine);
            fail("Expected GraphqlRequestException");
        } catch (Exception e) {
            assertTrue("Should wrap in GraphqlRequestException", e.getCause() instanceof GraphqlRequestException);
        }

        // Verify metrics were incremented
        verify(metrics, times(1)).incrementRequestErrors(HttpStatus.SC_BAD_REQUEST);
    }
}
