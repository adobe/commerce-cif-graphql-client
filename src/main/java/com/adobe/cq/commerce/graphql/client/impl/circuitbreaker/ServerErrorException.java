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
 * Custom exception for server errors with status code and response body.
 * This exception is used by the circuit breaker policies to differentiate between different types of server errors:
 * - 503 Service Unavailable errors are handled with a progressive delay policy
 * - Other 5xx errors are handled with a constant delay policy
 * Each type of error has its own circuit breaker with specific thresholds and delay strategies.
 */
public class ServerErrorException extends RuntimeException {
    private final int statusCode;
    private final String responseBody;

    /**
     * Creates a new ServerErrorException with the given message, status code and response body.
     * 
     * @param message The error message
     * @param statusCode The HTTP status code (used by circuit breaker policies to determine handling strategy)
     * @param responseBody The response body, may contain details about the error
     */
    public ServerErrorException(String message, int statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /**
     * Creates a new ServerErrorException with the given message, status code, response body and cause.
     * 
     * @param message The error message
     * @param statusCode The HTTP status code (used by circuit breaker policies to determine handling strategy)
     * @param responseBody The response body, may contain details about the error
     * @param cause The throwable that caused this exception
     */
    public ServerErrorException(String message, int statusCode, String responseBody, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /**
     * Gets the HTTP status code. This is used by circuit breaker policies to determine
     * which handling strategy to apply (progressive delay for 503, constant delay for other 5xx).
     * 
     * @return The HTTP status code
     */
    public int getStatusCode() {
        return statusCode;
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
