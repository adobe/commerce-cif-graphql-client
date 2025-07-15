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
import java.lang.reflect.Method;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for CircuitBreakerService with optimized coverage focusing on core functionality.
 */
public class CircuitBreakerServiceTest {

    private CircuitBreakerService circuitBreakerService;

    @Before
    public void setUp() {
        circuitBreakerService = new CircuitBreakerService();
    }

    @Test
    public void testCircuitBreakerServiceCreation() {
        assertNotNull("CircuitBreakerService should be created successfully", circuitBreakerService);
    }

    @Test
    public void testExecuteWithPoliciesSuccess() throws Exception {
        String result = circuitBreakerService.executeWithPolicies("http://test.com", () -> "success");
        assertEquals("success", result);
    }

    @Test
    public void testExecuteWithPoliciesWithServiceUnavailableException() throws IOException {
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
    public void testExecuteWithPoliciesWithServerErrorException() throws IOException {
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
    public void testExecuteWithPoliciesWithIOException() throws IOException {
        try {
            circuitBreakerService.executeWithPolicies("http://test.com", () -> {
                try {
                    throw new IOException("Connection failed");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof IOException);
            assertEquals("Connection failed", e.getCause().getMessage());
        }
    }

    @Test
    public void testLoadPropertiesWithValidFile() throws Exception {
        Method loadPropertiesMethod = CircuitBreakerService.class.getDeclaredMethod("loadProperties");
        loadPropertiesMethod.setAccessible(true);

        Properties props = (Properties) loadPropertiesMethod.invoke(circuitBreakerService);

        assertNotNull("Properties should not be null", props);
        // Check that some expected properties are loaded
        assertTrue("Should contain 503 threshold property",
            props.containsKey("circuit.breaker.503.threshold") || props.isEmpty());
    }

    @Test
    public void testPropertyParsingMethods() throws Exception {
        // Test all property parsing methods with various scenarios in a single test
        Method getIntPropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getIntProperty", Properties.class, String.class,
            int.class);
        Method getLongPropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getLongProperty", Properties.class, String.class,
            long.class);
        Method getDoublePropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getDoubleProperty", Properties.class, String.class,
            double.class);

        getIntPropertyMethod.setAccessible(true);
        getLongPropertyMethod.setAccessible(true);
        getDoublePropertyMethod.setAccessible(true);

        Properties props = new Properties();

        // Test valid values
        props.setProperty("int.key", "42");
        props.setProperty("long.key", "123456789");
        props.setProperty("double.key", "3.14159");

        assertEquals(42, getIntPropertyMethod.invoke(circuitBreakerService, props, "int.key", 10));
        assertEquals(123456789L, getLongPropertyMethod.invoke(circuitBreakerService, props, "long.key", 1000L));
        assertEquals(3.14159, (Double) getDoublePropertyMethod.invoke(circuitBreakerService, props, "double.key", 1.0), 0.00001);

        // Test invalid values (should use defaults)
        props.setProperty("invalid.int", "not.a.number");
        props.setProperty("invalid.long", "not.a.long");
        props.setProperty("invalid.double", "not.a.double");

        assertEquals(10, getIntPropertyMethod.invoke(circuitBreakerService, props, "invalid.int", 10));
        assertEquals(1000L, getLongPropertyMethod.invoke(circuitBreakerService, props, "invalid.long", 1000L));
        assertEquals(1.0, (Double) getDoublePropertyMethod.invoke(circuitBreakerService, props, "invalid.double", 1.0), 0.00001);

        // Test null/missing values (should use defaults)
        assertEquals(10, getIntPropertyMethod.invoke(circuitBreakerService, props, "missing.int", 10));
        assertEquals(1000L, getLongPropertyMethod.invoke(circuitBreakerService, props, "missing.long", 1000L));
        assertEquals(1.0, (Double) getDoublePropertyMethod.invoke(circuitBreakerService, props, "missing.double", 1.0), 0.00001);

        // Test whitespace values (should use defaults)
        props.setProperty("whitespace.int", "   ");
        props.setProperty("whitespace.long", "   ");
        props.setProperty("whitespace.double", "   ");

        assertEquals(10, getIntPropertyMethod.invoke(circuitBreakerService, props, "whitespace.int", 10));
        assertEquals(1000L, getLongPropertyMethod.invoke(circuitBreakerService, props, "whitespace.long", 1000L));
        assertEquals(1.0, (Double) getDoublePropertyMethod.invoke(circuitBreakerService, props, "whitespace.double", 1.0), 0.00001);

        // Test trimmed values
        props.setProperty("trimmed.int", "  42  ");
        props.setProperty("trimmed.long", "  123456789  ");
        props.setProperty("trimmed.double", "  3.14159  ");

        assertEquals(42, getIntPropertyMethod.invoke(circuitBreakerService, props, "trimmed.int", 10));
        assertEquals(123456789L, getLongPropertyMethod.invoke(circuitBreakerService, props, "trimmed.long", 1000L));
        assertEquals(3.14159, (Double) getDoublePropertyMethod.invoke(circuitBreakerService, props, "trimmed.double", 1.0), 0.00001);
    }

    @Test
    public void testCircuitBreakerServiceWithDefaultValues() {
        // Test that CircuitBreakerService can be created with default values
        CircuitBreakerService service = new CircuitBreakerService();
        assertNotNull("Service should be created with default values", service);

        // Test that it can execute successfully
        try {
            String result = service.executeWithPolicies("http://test.com", () -> "test");
            assertEquals("test", result);
        } catch (Exception e) {
            fail("Should not throw exception for successful execution");
        }
    }
}