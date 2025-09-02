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

package com.adobe.cq.commerce.graphql.client;

import java.io.IOException;

import org.junit.Test;

import static org.junit.Assert.*;

public class GraphqlRequestExceptionTest {

    // Test constants for reusability
    private static final String TEST_MESSAGE = "Test error message";
    private static final String ALTERNATE_MESSAGE = "Test message";
    private static final String TOP_LEVEL_MESSAGE = "Top level message";
    private static final String IO_ERROR_MESSAGE = "IO error";
    private static final String INTERMEDIATE_CAUSE_MESSAGE = "Intermediate cause";
    private static final String ROOT_CAUSE_MESSAGE = "Root cause";

    private static final long ZERO_DURATION = 0L;
    private static final long SHORT_DURATION = 500L;
    private static final long MEDIUM_DURATION = 1000L;
    private static final long LONG_DURATION = 1500L;

    // Helper method to create a standard IOException
    private IOException createTestIOException() {
        return new IOException(IO_ERROR_MESSAGE);
    }

    // Helper method to assert exception properties with zero duration
    private void assertExceptionWithZeroDuration(GraphqlRequestException exception, String expectedMessage, Throwable expectedCause) {
        assertEquals(expectedMessage, exception.getMessage());
        assertEquals(ZERO_DURATION, exception.getDuration());
        assertEquals(expectedCause, exception.getCause());
    }

    // Helper method to assert exception properties with duration
    private void assertExceptionWithDuration(GraphqlRequestException exception, String expectedMessage,
        long expectedDuration, Throwable expectedCause) {
        assertEquals(expectedMessage, exception.getMessage());
        assertEquals(expectedDuration, exception.getDuration());
        assertEquals(expectedCause, exception.getCause());
    }

    @Test
    public void testConstructorWithMessageOnly() {
        GraphqlRequestException exception = new GraphqlRequestException(TEST_MESSAGE);
        assertExceptionWithZeroDuration(exception, TEST_MESSAGE, null);
    }

    @Test
    public void testConstructorWithMessageAndCause() {
        IOException cause = createTestIOException();
        GraphqlRequestException exception = new GraphqlRequestException(TEST_MESSAGE, cause);
        assertExceptionWithZeroDuration(exception, TEST_MESSAGE, cause);
    }

    @Test
    public void testConstructorWithMessageCauseAndDuration() {
        IOException cause = createTestIOException();
        GraphqlRequestException exception = new GraphqlRequestException(TEST_MESSAGE, LONG_DURATION, cause);
        assertExceptionWithDuration(exception, TEST_MESSAGE, LONG_DURATION, cause);
    }

    @Test
    public void testConstructorWithMessageAndDuration() {
        GraphqlRequestException exception = new GraphqlRequestException(TEST_MESSAGE, LONG_DURATION);
        assertExceptionWithDuration(exception, TEST_MESSAGE, LONG_DURATION, null);
    }

    @Test
    public void testDurationFormattingBehavior() {
        // Test zero duration - no formatting
        GraphqlRequestException zeroException = new GraphqlRequestException(TEST_MESSAGE, ZERO_DURATION);
        assertExceptionWithDuration(zeroException, TEST_MESSAGE, ZERO_DURATION, null);

        // Test positive duration - no automatic formatting in message
        GraphqlRequestException positiveException = new GraphqlRequestException(TEST_MESSAGE, MEDIUM_DURATION);
        assertExceptionWithDuration(positiveException, TEST_MESSAGE, MEDIUM_DURATION, null);
    }

    @Test
    public void testNullMessageHandling() {
        // Test null message with zero duration
        GraphqlRequestException nullMessageException = new GraphqlRequestException(null);
        assertNull(nullMessageException.getMessage());
        assertEquals(ZERO_DURATION, nullMessageException.getDuration());

        // Test null message with duration
        GraphqlRequestException nullWithDurationException = new GraphqlRequestException(null, SHORT_DURATION);
        assertNull(nullWithDurationException.getMessage());
        assertEquals(SHORT_DURATION, nullWithDurationException.getDuration());
    }

    @Test
    public void testNullCauseHandling() {
        // Test null cause without duration
        GraphqlRequestException nullCauseException = new GraphqlRequestException(ALTERNATE_MESSAGE, (Throwable) null);
        assertExceptionWithZeroDuration(nullCauseException, ALTERNATE_MESSAGE, null);

        // Test null cause with duration
        GraphqlRequestException nullCauseWithDurationException = new GraphqlRequestException(ALTERNATE_MESSAGE, MEDIUM_DURATION, null);
        assertExceptionWithDuration(nullCauseWithDurationException, ALTERNATE_MESSAGE, MEDIUM_DURATION, null);
    }

    @Test
    public void testInheritanceAndType() {
        GraphqlRequestException exception = new GraphqlRequestException(ALTERNATE_MESSAGE);
        assertTrue("GraphqlRequestException should extend RuntimeException", exception instanceof RuntimeException);
    }

    @Test
    public void testNestedCauseChain() {
        RuntimeException rootCause = new RuntimeException(ROOT_CAUSE_MESSAGE);
        IOException intermediateCause = new IOException(INTERMEDIATE_CAUSE_MESSAGE, rootCause);
        GraphqlRequestException exception = new GraphqlRequestException(TOP_LEVEL_MESSAGE, SHORT_DURATION, intermediateCause);

        assertEquals(intermediateCause, exception.getCause());
        assertEquals(rootCause, exception.getCause().getCause());
        assertExceptionWithDuration(exception, TOP_LEVEL_MESSAGE, SHORT_DURATION, intermediateCause);
    }
}
