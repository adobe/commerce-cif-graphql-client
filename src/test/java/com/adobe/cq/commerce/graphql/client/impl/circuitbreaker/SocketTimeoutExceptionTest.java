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

import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.exception.SocketTimeoutException;

import static org.junit.Assert.*;

public class SocketTimeoutExceptionTest {

    // Test constants
    private static final String ERROR_MESSAGE = "Socket timeout occurred";
    private static final String TIMEOUT_DETAILS = "Connection timed out after 5000ms";
    private static final long DURATION_MS = 5000L;

    // Helper method for common assertions
    private void assertSocketTimeoutException(SocketTimeoutException exception, String expectedMessage,
        String expectedDetails, long expectedDuration, Throwable expectedCause) {
        assertEquals(expectedMessage, exception.getOriginalMessage());
        assertEquals(expectedDetails, exception.getDetails());
        assertEquals(expectedDuration, exception.getDurationMs());
        assertEquals(expectedCause, exception.getCause());
    }

    @Test
    public void testConstructorWithDuration() {
        SocketTimeoutException exception = new SocketTimeoutException(ERROR_MESSAGE, TIMEOUT_DETAILS, DURATION_MS);

        assertSocketTimeoutException(exception, ERROR_MESSAGE, TIMEOUT_DETAILS, DURATION_MS, null);
        assertEquals(ERROR_MESSAGE + " after " + DURATION_MS + "ms", exception.getMessage());
    }

    @Test
    public void testConstructorWithCause() {
        IOException cause = new IOException("Network error");
        SocketTimeoutException exception = new SocketTimeoutException(ERROR_MESSAGE, TIMEOUT_DETAILS, cause);

        assertSocketTimeoutException(exception, ERROR_MESSAGE, TIMEOUT_DETAILS, 0L, cause);
        assertEquals(ERROR_MESSAGE, exception.getMessage());
    }

    @Test
    public void testConstructorWithDurationAndCause() {
        IOException cause = new IOException("Network timeout");
        SocketTimeoutException exception = new SocketTimeoutException(ERROR_MESSAGE, TIMEOUT_DETAILS, cause, DURATION_MS);

        assertSocketTimeoutException(exception, ERROR_MESSAGE, TIMEOUT_DETAILS, DURATION_MS, cause);
        assertEquals(ERROR_MESSAGE + " after " + DURATION_MS + "ms", exception.getMessage());
    }
}
