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

package com.adobe.cq.commerce.graphql.client.impl.circuitbreaker;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import com.adobe.cq.commerce.graphql.client.GraphqlClientConfiguration;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for CircuitBreakerService with optimized coverage focusing on core functionality.
 */
public class CircuitBreakerServiceTest {

    private CircuitBreakerService circuitBreakerService;

    @Before
    public void setUp() {
        // Unset circuit breaker settings, so the defaults are used
        circuitBreakerService = new CircuitBreakerService(mock(GraphqlClientConfiguration.class));
    }

    @Test
    public void testServiceCreation() {
        assertNotNull("Service should be created successfully", circuitBreakerService);
    }

    @Test
    public void testExecuteWithPoliciesSuccess() throws Exception {
        String result = circuitBreakerService.executeWithPolicies("http://test.com", () -> "success");
        assertEquals("success", result);
    }

    @Test
    public void testExecuteWithPoliciesWithServiceUnavailable() throws IOException {
        try {
            circuitBreakerService.executeWithPolicies("http://test.com", () -> {
                throw new ServiceUnavailableException("Service unavailable", "Service down");
            });
            fail("Expected ServiceUnavailableException to be thrown");
        } catch (ServiceUnavailableException e) {
            assertEquals("Service unavailable", e.getMessage());
            assertEquals("Service down", e.getResponseBody());
        }
    }

    @Test
    public void testExecuteWithPoliciesWithServerError() throws IOException {
        try {
            circuitBreakerService.executeWithPolicies("http://test.com", () -> {
                throw new ServerErrorException("Server error", 500, "Internal server error");
            });
            fail("Expected ServerErrorException to be thrown");
        } catch (ServerErrorException e) {
            assertEquals("Server error", e.getMessage());
            assertEquals(500, e.getStatusCode());
            assertEquals("Internal server error", e.getResponseBody());
        }
    }

    @Test
    public void testExecuteWithPoliciesWithSocketTimeout() throws IOException {
        try {
            circuitBreakerService.executeWithPolicies("http://test.com", () -> {
                throw new SocketTimeoutException("Socket timeout", "Connection timed out", 5000L);
            });
            fail("Expected SocketTimeoutException to be thrown");
        } catch (SocketTimeoutException e) {
            assertEquals("Socket timeout", e.getMessage());
            assertEquals("Connection timed out", e.getDetails());
            assertEquals(5000L, e.getDuration());
        }
    }

    @Test
    public void testAllThreeCircuitBreakerPolicies() {
        // Test that all three circuit breaker policies are integrated and handle their respective exceptions

        // Test ServiceUnavailable handling
        try {
            circuitBreakerService.executeWithPolicies("http://test.com", () -> {
                throw new ServiceUnavailableException("Service down", "Maintenance mode");
            });
            fail("Expected ServiceUnavailableException");
        } catch (ServiceUnavailableException e) {
            assertEquals("Service down", e.getMessage());
        }

        // Test ServerError handling
        try {
            circuitBreakerService.executeWithPolicies("http://test.com", () -> {
                throw new ServerErrorException("Internal error", 500, "Database unavailable");
            });
            fail("Expected ServerErrorException");
        } catch (ServerErrorException e) {
            assertEquals("Internal error", e.getMessage());
        }

        // Test SocketTimeout handling
        try {
            circuitBreakerService.executeWithPolicies("http://test.com", () -> {
                throw new SocketTimeoutException("Timeout", "Connection timeout", 10000L);
            });
            fail("Expected SocketTimeoutException");
        } catch (SocketTimeoutException e) {
            assertEquals("Timeout", e.getMessage());
        }
    }
}
