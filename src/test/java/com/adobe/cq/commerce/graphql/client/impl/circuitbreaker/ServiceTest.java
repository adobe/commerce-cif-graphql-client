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
import org.mockito.MockitoAnnotations;

import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.exception.ServerError;
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.exception.ServiceUnavailable;
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.exception.SocketTimeout;

import static org.junit.Assert.*;

/**
 * Tests for Service with optimized coverage focusing on core functionality.
 */
public class ServiceTest {

    private Service circuitBreakerService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        circuitBreakerService = new Service();
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
                throw new ServiceUnavailable("Service unavailable", "Service down");
            });
            fail("Expected ServiceUnavailable to be thrown");
        } catch (ServiceUnavailable e) {
            assertEquals("Service unavailable", e.getMessage());
            assertEquals("Service down", e.getResponseBody());
        }
    }

    @Test
    public void testExecuteWithPoliciesWithServerError() throws IOException {
        try {
            circuitBreakerService.executeWithPolicies("http://test.com", () -> {
                throw new ServerError("Server error", 500, "Internal server error");
            });
            fail("Expected ServerError to be thrown");
        } catch (ServerError e) {
            assertEquals("Server error", e.getMessage());
            assertEquals(500, e.getStatusCode());
            assertEquals("Internal server error", e.getResponseBody());
        }
    }

    @Test
    public void testExecuteWithPoliciesWithSocketTimeout() throws IOException {
        try {
            circuitBreakerService.executeWithPolicies("http://test.com", () -> {
                throw new SocketTimeout("Socket timeout", "Connection timed out", 5000L);
            });
            fail("Expected SocketTimeout to be thrown");
        } catch (SocketTimeout e) {
            assertEquals("Socket timeout", e.getOriginalMessage());
            assertEquals("Connection timed out", e.getDetails());
            assertEquals(5000L, e.getDurationMs());
        }
    }

    @Test
    public void testServiceWithCustomConfiguration() {
        // Test that Service can be created with custom configuration
        Configuration customConfig = new Configuration();
        Service service = new Service(customConfig);
        assertNotNull("Service should be created with custom configuration", service);

        // Test that it can execute successfully
        try {
            String result = service.executeWithPolicies("http://test.com", () -> "custom-test");
            assertEquals("custom-test", result);
        } catch (Exception e) {
            fail("Should not throw exception for successful execution with custom config");
        }
    }

    @Test
    public void testServiceWithDefaultValues() {
        // Test that Service can be created with default values
        Service service = new Service();
        assertNotNull("Service should be created with default values", service);

        // Test that it can execute successfully
        try {
            String result = service.executeWithPolicies("http://test.com", () -> "test");
            assertEquals("test", result);
        } catch (Exception e) {
            fail("Should not throw exception for successful execution");
        }
    }

    @Test
    public void testAllThreeCircuitBreakerPolicies() {
        // Test that all three circuit breaker policies are integrated and handle their respective exceptions

        // Test ServiceUnavailable handling
        try {
            circuitBreakerService.executeWithPolicies("http://test.com", () -> {
                throw new ServiceUnavailable("Service down", "Maintenance mode");
            });
            fail("Expected ServiceUnavailable");
        } catch (ServiceUnavailable e) {
            assertEquals("Service down", e.getOriginalMessage());
        }

        // Test ServerError handling
        try {
            circuitBreakerService.executeWithPolicies("http://test.com", () -> {
                throw new ServerError("Internal error", 500, "Database unavailable");
            });
            fail("Expected ServerError");
        } catch (ServerError e) {
            assertEquals("Internal error", e.getOriginalMessage());
        }

        // Test SocketTimeout handling
        try {
            circuitBreakerService.executeWithPolicies("http://test.com", () -> {
                throw new SocketTimeout("Timeout", "Connection timeout", 10000L);
            });
            fail("Expected SocketTimeout");
        } catch (SocketTimeout e) {
            assertEquals("Timeout", e.getOriginalMessage());
        }
    }
}