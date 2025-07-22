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

/**
 * Custom exception for 503 Service Unavailable errors.
 * This exception is used by the circuit breaker policies to handle 503 errors with a progressive delay strategy.
 * The exception type itself indicates the error category, eliminating the need for status code checking.
 */
public class ServiceUnavailableException extends RuntimeException {
    private final String responseBody;

    /**
     * Creates a new ServiceUnavailableException with the given message and response body.
     * 
     * @param message The error message
     * @param responseBody The response body, may contain details about the error
     */
    public ServiceUnavailableException(String message, String responseBody) {
        super(message);
        this.responseBody = responseBody;
    }

    /**
     * Creates a new ServiceUnavailableException with the given message, response body and cause.
     * 
     * @param message The error message
     * @param responseBody The response body, may contain details about the error
     * @param cause The throwable that caused this exception
     */
    public ServiceUnavailableException(String message, String responseBody, Throwable cause) {
        super(message, cause);
        this.responseBody = responseBody;
    }

    /**
     * Gets the HTTP status code. This method always returns 503 for ServiceUnavailableException.
     * 
     * @return The HTTP status code (always 503)
     */
    public int getStatusCode() {
        return 503;
    }

    /**
     * Gets the response body.
     * 
     * @return The response body content
     */
    public String getResponseBody() {
        return responseBody;
    }
}
