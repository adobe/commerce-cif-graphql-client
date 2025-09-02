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

public class ServiceUnavailableTest {

    // Test constants
    private static final String ERROR_MESSAGE = "Service temporarily unavailable";
    private static final String RESPONSE_BODY = "Service is down for maintenance";
    private static final long DURATION_MS = 800L;
    private static final int EXPECTED_STATUS_CODE = 503;

    // Helper method for common assertions
    private void assertServiceUnavailable(ServiceUnavailableException exception, String expectedMessage,
        String expectedResponseBody, long expectedDuration, Throwable expectedCause) {
        assertEquals(expectedMessage, exception.getMessage());
        assertEquals(EXPECTED_STATUS_CODE, exception.getStatusCode());
        assertEquals(expectedResponseBody, exception.getResponseBody());
        assertEquals(expectedDuration, exception.getDuration());
        assertEquals(expectedCause, exception.getCause());
    }

    @Test
    public void testConstructorWithDuration() {
        ServiceUnavailableException exception = new ServiceUnavailableException(ERROR_MESSAGE, RESPONSE_BODY, DURATION_MS);

        assertServiceUnavailable(exception, ERROR_MESSAGE, RESPONSE_BODY, DURATION_MS, null);
        assertEquals(ERROR_MESSAGE, exception.getMessage());
    }

    @Test
    public void testConstructorWithCauseAndDuration() {
        IOException cause = new IOException("Network timeout");
        ServiceUnavailableException exception = new ServiceUnavailableException(ERROR_MESSAGE, RESPONSE_BODY, cause, DURATION_MS);

        assertServiceUnavailable(exception, ERROR_MESSAGE, RESPONSE_BODY, DURATION_MS, cause);
        assertEquals(ERROR_MESSAGE, exception.getMessage());
    }

    @Test
    public void testStatusCodeAlwaysReturns503() {
        // Verify that getStatusCode() always returns 503, regardless of constructor used
        ServiceUnavailableException exception1 = new ServiceUnavailableException(ERROR_MESSAGE, RESPONSE_BODY);
        ServiceUnavailableException exception2 = new ServiceUnavailableException(ERROR_MESSAGE, RESPONSE_BODY, DURATION_MS);

        assertEquals(EXPECTED_STATUS_CODE, exception1.getStatusCode());
        assertEquals(EXPECTED_STATUS_CODE, exception2.getStatusCode());
    }
}
