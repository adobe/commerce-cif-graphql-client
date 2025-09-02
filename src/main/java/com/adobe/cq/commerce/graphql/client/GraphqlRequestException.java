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

    private final long duration;

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
        this(message, 0L, cause);
    }

    /**
     * Creates a new GraphqlRequestException with a message and duration.
     * 
     * @param message the exception message
     * @param duration the execution duration in milliseconds of the failing request
     */
    public GraphqlRequestException(String message, long duration) {
        super(message);
        this.duration = duration;
    }

    /**
     * Creates a new GraphqlRequestException with a message, duration, and cause.
     * 
     * @param message the exception message
     * @param duration the execution duration in milliseconds of the failing request
     * @param cause the underlying cause
     */
    public GraphqlRequestException(String message, long duration, Throwable cause) {
        super(message, cause);
        this.duration = duration;
    }

    /**
     * Gets the execution duration in milliseconds of the failing request.
     * 
     * @return the execution duration in milliseconds
     */
    public long getDuration() {
        return duration;
    }
}
