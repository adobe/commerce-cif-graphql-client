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

import org.junit.Test;

import static org.junit.Assert.*;

public class SocketTimeoutTest {

    // Test constants
    private static final String ERROR_MESSAGE = "Socket timeout occurred";
    private static final String TIMEOUT_DETAILS = "Connection timed out after 5000ms";
    private static final long DURATION_MS = 5000L;

    // Helper method for common assertions
    private void assertSocketTimeout(SocketTimeoutException exception, String expectedMessage,
        String expectedDetails, long expectedDuration, Throwable expectedCause) {
        assertEquals(expectedMessage, exception.getOriginalMessage());
        assertEquals(expectedDetails, exception.getDetails());
        assertEquals(expectedDuration, exception.getDurationMs());
        assertEquals(expectedCause, exception.getCause());
    }

    @Test
    public void testConstructorWithDuration() {
        SocketTimeoutException exception = new SocketTimeoutException(ERROR_MESSAGE, TIMEOUT_DETAILS, DURATION_MS);

        assertSocketTimeout(exception, ERROR_MESSAGE, TIMEOUT_DETAILS, DURATION_MS, null);
        assertEquals(ERROR_MESSAGE + " after " + DURATION_MS + "ms", exception.getMessage());
    }

    @Test
    public void testConstructorWithCause() {
        IOException cause = new IOException("Network error");
        SocketTimeoutException exception = new SocketTimeoutException(ERROR_MESSAGE, TIMEOUT_DETAILS, cause);

        assertSocketTimeout(exception, ERROR_MESSAGE, TIMEOUT_DETAILS, 0L, cause);
        assertEquals(ERROR_MESSAGE, exception.getMessage());
    }

    @Test
    public void testConstructorWithDurationAndCause() {
        IOException cause = new IOException("Network timeout");
        SocketTimeoutException exception = new SocketTimeoutException(ERROR_MESSAGE, TIMEOUT_DETAILS, cause, DURATION_MS);

        assertSocketTimeout(exception, ERROR_MESSAGE, TIMEOUT_DETAILS, DURATION_MS, cause);
        assertEquals(ERROR_MESSAGE + " after " + DURATION_MS + "ms", exception.getMessage());
    }

    @Test
    public void testBasicConstructorWithMessageAndDetails() {
        // Test the basic constructor with just message and details (missing coverage)
        SocketTimeoutException exception = new SocketTimeoutException(ERROR_MESSAGE, TIMEOUT_DETAILS);

        assertSocketTimeout(exception, ERROR_MESSAGE, TIMEOUT_DETAILS, 0L, null);
        assertEquals(ERROR_MESSAGE, exception.getMessage());
    }

    @Test
    public void testAllConstructorVariants() {
        // Test all constructor combinations to ensure full coverage

        // Constructor 1: message + details
        SocketTimeoutException ex1 = new SocketTimeoutException("Test message 1", "Test details 1");
        assertEquals("Test message 1", ex1.getOriginalMessage());
        assertEquals("Test details 1", ex1.getDetails());
        assertEquals(0L, ex1.getDurationMs());
        assertNull(ex1.getCause());

        // Constructor 2: message + details + duration
        SocketTimeoutException ex2 = new SocketTimeoutException("Test message 2", "Test details 2", 1500L);
        assertEquals("Test message 2", ex2.getOriginalMessage());
        assertEquals("Test details 2", ex2.getDetails());
        assertEquals(1500L, ex2.getDurationMs());
        assertNull(ex2.getCause());

        // Constructor 3: message + details + cause (no duration)
        Exception testCause = new Exception("Test cause");
        SocketTimeoutException ex3 = new SocketTimeoutException("Test message 3", "Test details 3", testCause);
        assertEquals("Test message 3", ex3.getOriginalMessage());
        assertEquals("Test details 3", ex3.getDetails());
        assertEquals(0L, ex3.getDurationMs()); // Should default to 0
        assertEquals(testCause, ex3.getCause());

        // Constructor 4: message + details + cause + duration (all parameters)
        SocketTimeoutException ex4 = new SocketTimeoutException("Test message 4", "Test details 4", testCause, 2500L);
        assertEquals("Test message 4", ex4.getOriginalMessage());
        assertEquals("Test details 4", ex4.getDetails());
        assertEquals(2500L, ex4.getDurationMs());
        assertEquals(testCause, ex4.getCause());
    }

    @Test
    public void testGetDetailsMethod() {
        // Explicitly test the getDetails() method for coverage
        String customDetails = "Custom timeout details for coverage test";
        SocketTimeoutException exception = new SocketTimeoutException("Test message", customDetails);

        assertEquals(customDetails, exception.getDetails());
        assertNotNull("Details should not be null", exception.getDetails());
    }
}
