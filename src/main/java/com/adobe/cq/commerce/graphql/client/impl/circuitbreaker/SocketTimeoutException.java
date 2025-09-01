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

import com.adobe.cq.commerce.graphql.client.GraphqlRequestException;

/**
 * Custom exception for socket timeout errors.
 * This exception is used by the circuit breaker policies to handle socket timeout errors with a specific retry strategy.
 * The exception type itself indicates the error category, eliminating the need for specific timeout checking.
 */
public class SocketTimeoutException extends GraphqlRequestException {
    private final String details;

    /**
     * Creates a new SocketTimeoutException with the given message and details.
     * 
     * @param message The error message
     * @param details Additional details about the timeout, may contain connection information
     */
    public SocketTimeoutException(String message, String details) {
        super(message, 0);
        this.details = details;
    }

    /**
     * Creates a new SocketTimeoutException with the given message, details and duration.
     * 
     * @param message The error message
     * @param details Additional details about the timeout, may contain connection information
     * @param durationMs The duration in milliseconds
     */
    public SocketTimeoutException(String message, String details, long durationMs) {
        super(message, durationMs);
        this.details = details;
    }

    /**
     * Creates a new SocketTimeoutException with the given message, details and cause.
     * 
     * @param message The error message
     * @param details Additional details about the timeout, may contain connection information
     * @param cause The throwable that caused this exception
     */
    public SocketTimeoutException(String message, String details, Throwable cause) {
        super(message, cause, 0);
        this.details = details;
    }

    /**
     * Creates a new SocketTimeoutException with the given message, details, cause and duration.
     * 
     * @param message The error message
     * @param details Additional details about the timeout, may contain connection information
     * @param cause The throwable that caused this exception
     * @param durationMs The duration in milliseconds
     */
    public SocketTimeoutException(String message, String details, Throwable cause, long durationMs) {
        super(message, cause, durationMs);
        this.details = details;
    }

    /**
     * Gets the timeout details.
     * 
     * @return The timeout details content
     */
    public String getDetails() {
        return details;
    }
}
