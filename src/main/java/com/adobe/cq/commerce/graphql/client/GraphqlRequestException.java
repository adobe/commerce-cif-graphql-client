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

/**
 * Exception thrown when a GraphQL request fails.
 * This exception is used to wrap various types of failures that can occur during GraphQL request execution.
 * It includes duration information to help with performance analysis and debugging.
 */
public class GraphqlRequestException extends RuntimeException {

    private final long durationMs;

    /**
     * Creates a new GraphqlRequestException with a message only.
     * Duration is automatically set to 0.
     * 
     * @param message the exception message
     */
    public GraphqlRequestException(String message) {
        this(message, 0L);
    }

    /**
     * Creates a new GraphqlRequestException with a message and cause.
     * Duration is automatically set to 0.
     * 
     * @param message the exception message
     * @param cause the underlying cause
     */
    public GraphqlRequestException(String message, Throwable cause) {
        this(message, cause, 0L);
    }

    /**
     * Creates a new GraphqlRequestException with a message, cause, and duration.
     * 
     * @param message the exception message
     * @param cause the underlying cause
     * @param durationMs the duration in milliseconds when the exception occurred
     */
    public GraphqlRequestException(String message, Throwable cause, long durationMs) {
        super(message, cause);
        this.durationMs = durationMs;
    }

    /**
     * Creates a new GraphqlRequestException with a message and duration.
     * 
     * @param message the exception message
     * @param durationMs the duration in milliseconds when the exception occurred
     */
    public GraphqlRequestException(String message, long durationMs) {
        super(message);
        this.durationMs = durationMs;
    }

    /**
     * Gets the duration in milliseconds when the exception occurred.
     * 
     * @return the duration in milliseconds
     */
    public long getDurationMs() {
        return durationMs;
    }

    /**
     * Gets the original exception message without duration information.
     * 
     * @return the original exception message
     */
    public String getOriginalMessage() {
        return super.getMessage();
    }

    @Override
    public String getMessage() {
        String originalMessage = super.getMessage();
        if (durationMs > 0) {
            return originalMessage + " after " + durationMs + "ms";
        }
        return originalMessage;
    }
}
