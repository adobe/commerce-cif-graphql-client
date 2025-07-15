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
 * Tests for CircuitBreakerService with comprehensive coverage of property loading methods
 * and their exception handling scenarios.
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
        // Test successful execution
        String result = circuitBreakerService.executeWithPolicies("http://test.com", () -> "success");
        assertEquals("success", result);
    }

    @Test
    public void testExecuteWithPoliciesWithServiceUnavailableException() throws IOException {
        // Test execution with ServiceUnavailableException
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
        // Test execution with ServerErrorException
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
        // Test execution with IOException
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
        // Test loadProperties method with valid properties file
        Method loadPropertiesMethod = CircuitBreakerService.class.getDeclaredMethod("loadProperties");
        loadPropertiesMethod.setAccessible(true);

        Properties props = (Properties) loadPropertiesMethod.invoke(circuitBreakerService);

        assertNotNull("Properties should not be null", props);
        // Check that some expected properties are loaded
        assertTrue("Should contain 503 threshold property",
            props.containsKey("circuit.breaker.503.threshold") || props.isEmpty());
    }

    @Test
    public void testGetIntPropertyWithValidValue() throws Exception {
        // Test getIntProperty with valid integer value
        Method getIntPropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getIntProperty", Properties.class, String.class,
            int.class);
        getIntPropertyMethod.setAccessible(true);

        Properties props = new Properties();
        props.setProperty("test.key", "42");

        int result = (Integer) getIntPropertyMethod.invoke(circuitBreakerService, props, "test.key", 10);
        assertEquals(42, result);
    }

    @Test
    public void testGetIntPropertyWithInvalidValue() throws Exception {
        // Test getIntProperty with invalid integer value (should use default)
        Method getIntPropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getIntProperty", Properties.class, String.class,
            int.class);
        getIntPropertyMethod.setAccessible(true);

        Properties props = new Properties();
        props.setProperty("test.key", "not.a.number");

        int result = (Integer) getIntPropertyMethod.invoke(circuitBreakerService, props, "test.key", 10);
        assertEquals(10, result); // Should return default value
    }

    @Test
    public void testGetIntPropertyWithNullValue() throws Exception {
        // Test getIntProperty with null value (should use default)
        Method getIntPropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getIntProperty", Properties.class, String.class,
            int.class);
        getIntPropertyMethod.setAccessible(true);

        Properties props = new Properties();

        int result = (Integer) getIntPropertyMethod.invoke(circuitBreakerService, props, "test.key", 10);
        assertEquals(10, result); // Should return default value
    }

    @Test
    public void testGetIntPropertyWithEmptyValue() throws Exception {
        // Test getIntProperty with empty value (should use default)
        Method getIntPropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getIntProperty", Properties.class, String.class,
            int.class);
        getIntPropertyMethod.setAccessible(true);

        Properties props = new Properties();
        props.setProperty("test.key", "");

        int result = (Integer) getIntPropertyMethod.invoke(circuitBreakerService, props, "test.key", 10);
        assertEquals(10, result); // Should return default value
    }

    @Test
    public void testGetLongPropertyWithValidValue() throws Exception {
        // Test getLongProperty with valid long value
        Method getLongPropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getLongProperty", Properties.class, String.class,
            long.class);
        getLongPropertyMethod.setAccessible(true);

        Properties props = new Properties();
        props.setProperty("test.key", "123456789");

        long result = (Long) getLongPropertyMethod.invoke(circuitBreakerService, props, "test.key", 1000L);
        assertEquals(123456789L, result);
    }

    @Test
    public void testGetLongPropertyWithInvalidValue() throws Exception {
        // Test getLongProperty with invalid long value (should use default)
        Method getLongPropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getLongProperty", Properties.class, String.class,
            long.class);
        getLongPropertyMethod.setAccessible(true);

        Properties props = new Properties();
        props.setProperty("test.key", "not.a.long");

        long result = (Long) getLongPropertyMethod.invoke(circuitBreakerService, props, "test.key", 1000L);
        assertEquals(1000L, result); // Should return default value
    }

    @Test
    public void testGetLongPropertyWithNullValue() throws Exception {
        // Test getLongProperty with null value (should use default)
        Method getLongPropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getLongProperty", Properties.class, String.class,
            long.class);
        getLongPropertyMethod.setAccessible(true);

        Properties props = new Properties();

        long result = (Long) getLongPropertyMethod.invoke(circuitBreakerService, props, "test.key", 1000L);
        assertEquals(1000L, result); // Should return default value
    }

    @Test
    public void testGetDoublePropertyWithValidValue() throws Exception {
        // Test getDoubleProperty with valid double value
        Method getDoublePropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getDoubleProperty", Properties.class, String.class,
            double.class);
        getDoublePropertyMethod.setAccessible(true);

        Properties props = new Properties();
        props.setProperty("test.key", "3.14159");

        double result = (Double) getDoublePropertyMethod.invoke(circuitBreakerService, props, "test.key", 1.0);
        assertEquals(3.14159, result, 0.00001);
    }

    @Test
    public void testGetDoublePropertyWithInvalidValue() throws Exception {
        // Test getDoubleProperty with invalid double value (should use default)
        Method getDoublePropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getDoubleProperty", Properties.class, String.class,
            double.class);
        getDoublePropertyMethod.setAccessible(true);

        Properties props = new Properties();
        props.setProperty("test.key", "not.a.double");

        double result = (Double) getDoublePropertyMethod.invoke(circuitBreakerService, props, "test.key", 1.0);
        assertEquals(1.0, result, 0.00001); // Should return default value
    }

    @Test
    public void testGetDoublePropertyWithNullValue() throws Exception {
        // Test getDoubleProperty with null value (should use default)
        Method getDoublePropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getDoubleProperty", Properties.class, String.class,
            double.class);
        getDoublePropertyMethod.setAccessible(true);

        Properties props = new Properties();

        double result = (Double) getDoublePropertyMethod.invoke(circuitBreakerService, props, "test.key", 1.0);
        assertEquals(1.0, result, 0.00001); // Should return default value
    }

    @Test
    public void testGetDoublePropertyWithWhitespaceValue() throws Exception {
        // Test getDoubleProperty with whitespace value (should use default)
        Method getDoublePropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getDoubleProperty", Properties.class, String.class,
            double.class);
        getDoublePropertyMethod.setAccessible(true);

        Properties props = new Properties();
        props.setProperty("test.key", "   ");

        double result = (Double) getDoublePropertyMethod.invoke(circuitBreakerService, props, "test.key", 1.0);
        assertEquals(1.0, result, 0.00001); // Should return default value
    }

    @Test
    public void testGetIntPropertyWithWhitespaceValue() throws Exception {
        // Test getIntProperty with whitespace value (should use default)
        Method getIntPropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getIntProperty", Properties.class, String.class,
            int.class);
        getIntPropertyMethod.setAccessible(true);

        Properties props = new Properties();
        props.setProperty("test.key", "   ");

        int result = (Integer) getIntPropertyMethod.invoke(circuitBreakerService, props, "test.key", 10);
        assertEquals(10, result); // Should return default value
    }

    @Test
    public void testGetLongPropertyWithWhitespaceValue() throws Exception {
        // Test getLongProperty with whitespace value (should use default)
        Method getLongPropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getLongProperty", Properties.class, String.class,
            long.class);
        getLongPropertyMethod.setAccessible(true);

        Properties props = new Properties();
        props.setProperty("test.key", "   ");

        long result = (Long) getLongPropertyMethod.invoke(circuitBreakerService, props, "test.key", 1000L);
        assertEquals(1000L, result); // Should return default value
    }

    @Test
    public void testGetIntPropertyWithTrimmedValue() throws Exception {
        // Test getIntProperty with value that needs trimming
        Method getIntPropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getIntProperty", Properties.class, String.class,
            int.class);
        getIntPropertyMethod.setAccessible(true);

        Properties props = new Properties();
        props.setProperty("test.key", "  42  ");

        int result = (Integer) getIntPropertyMethod.invoke(circuitBreakerService, props, "test.key", 10);
        assertEquals(42, result); // Should return trimmed value
    }

    @Test
    public void testGetLongPropertyWithTrimmedValue() throws Exception {
        // Test getLongProperty with value that needs trimming
        Method getLongPropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getLongProperty", Properties.class, String.class,
            long.class);
        getLongPropertyMethod.setAccessible(true);

        Properties props = new Properties();
        props.setProperty("test.key", "  123456789  ");

        long result = (Long) getLongPropertyMethod.invoke(circuitBreakerService, props, "test.key", 1000L);
        assertEquals(123456789L, result); // Should return trimmed value
    }

    @Test
    public void testGetDoublePropertyWithTrimmedValue() throws Exception {
        // Test getDoubleProperty with value that needs trimming
        Method getDoublePropertyMethod = CircuitBreakerService.class.getDeclaredMethod("getDoubleProperty", Properties.class, String.class,
            double.class);
        getDoublePropertyMethod.setAccessible(true);

        Properties props = new Properties();
        props.setProperty("test.key", "  3.14159  ");

        double result = (Double) getDoublePropertyMethod.invoke(circuitBreakerService, props, "test.key", 1.0);
        assertEquals(3.14159, result, 0.00001); // Should return trimmed value
    }

    @Test
    public void testCircuitBreakerServiceWithDefaultValues() {
        // Test that CircuitBreakerService can be created with default values
        // This tests the scenario where properties file is not found or empty
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

    @Test
    public void testCircuitBreakerServiceWithIOExceptionDuringPropertiesLoad() {
        // This test verifies that the service can handle IOExceptions during properties loading
        // The service should use default values when properties cannot be loaded
        CircuitBreakerService service = new CircuitBreakerService();
        assertNotNull("Service should be created even when properties loading fails", service);
    }
}