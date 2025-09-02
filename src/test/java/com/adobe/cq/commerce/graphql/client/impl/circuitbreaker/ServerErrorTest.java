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

public class ServerErrorTest {

    // Test constants
    private static final String ERROR_MESSAGE = "Server error occurred";
    private static final int STATUS_CODE_500 = 500;
    private static final int STATUS_CODE_503 = 503;
    private static final String RESPONSE_BODY = "Internal server error";
    private static final long DURATION_MS = 1200L;

    // Helper method for basic assertions
    private void assertBasicProperties(ServerErrorException exception, String expectedMessage,
        int expectedStatusCode, String expectedResponseBody) {
        assertEquals(expectedMessage, exception.getMessage());
        assertEquals(expectedStatusCode, exception.getStatusCode());
        assertEquals(expectedResponseBody, exception.getResponseBody());
    }

    @Test
    public void testConstructorWithDuration() {
        ServerErrorException exception = new ServerErrorException(ERROR_MESSAGE, STATUS_CODE_500, RESPONSE_BODY, DURATION_MS);

        assertBasicProperties(exception, ERROR_MESSAGE, STATUS_CODE_500, RESPONSE_BODY);
        assertEquals(DURATION_MS, exception.getDuration());
        assertEquals(ERROR_MESSAGE, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    public void testConstructorWithCauseAndDuration() {
        IOException cause = new IOException("Connection timeout");
        ServerErrorException exception = new ServerErrorException(ERROR_MESSAGE, STATUS_CODE_503, RESPONSE_BODY, cause, DURATION_MS);

        assertBasicProperties(exception, ERROR_MESSAGE, STATUS_CODE_503, RESPONSE_BODY);
        assertEquals(DURATION_MS, exception.getDuration());
        assertEquals(ERROR_MESSAGE, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    public void testInheritanceFromGraphqlRequestException() {
        ServerErrorException exception = new ServerErrorException(ERROR_MESSAGE, STATUS_CODE_500, RESPONSE_BODY);
        assertTrue("ServerErrorException should extend GraphqlRequestException",
            exception instanceof com.adobe.cq.commerce.graphql.client.GraphqlRequestException);
    }
}
