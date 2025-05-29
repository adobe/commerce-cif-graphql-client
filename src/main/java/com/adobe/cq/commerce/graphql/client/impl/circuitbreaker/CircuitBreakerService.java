/*******************************************************************************
 *
 *    Copyright 2019 Adobe. All rights reserved.
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

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.failsafe.CircuitBreaker;

/**
 * Service for creating and managing circuit breakers.
 * This service creates and manages circuit breakers for different endpoints,
 * reusing existing circuit breakers when possible.
 */
@Component(service = CircuitBreakerService.class)
public class CircuitBreakerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitBreakerService.class);

    // Default configuration values (placeholder for future OSGi configuration)
    private static final int DEFAULT_FAILURE_THRESHOLD = 3;
    private static final int DEFAULT_DELAY_SECONDS = 10;
    private static final int DEFAULT_SUCCESS_THRESHOLD = 1;

    // HTTP status codes that typically indicate availability issues
    private static final int SERVICE_UNAVAILABLE = 503;
    private static final int GATEWAY_TIMEOUT = 504;
    private static final int BAD_GATEWAY = 502;

    // Patterns to identify availability-related issues in response body
    private static final Pattern MAINTENANCE_PATTERN = Pattern.compile(
        "(?i)(maintenance|temporarily unavailable|service unavailable|down for maintenance|retry later)");
    private static final Pattern TIMEOUT_PATTERN = Pattern.compile(
        "(?i)(timeout|timed out|connection refused|service unavailable|too many requests|rate limit)");

    // List of network error indicators that should trigger the circuit breaker
    private static final List<String> NETWORK_ERROR_INDICATORS = Arrays.asList(
        "timeout",
        "timed out",
        "refused",
        "reset",
        "closed",
        "unavailable",
        "unreachable",
        "connection error",
        "connect error",
        "no route to host",
        "host unreachable",
        "broken pipe");

    private final Map<String, CircuitBreaker<Object>> circuitBreakers = new ConcurrentHashMap<>();

    @Activate
    protected void activate() {
        LOGGER.info(
            "Circuit breaker service activated with default configuration: failureThreshold={}, delaySeconds={}, successThreshold={}",
            DEFAULT_FAILURE_THRESHOLD, DEFAULT_DELAY_SECONDS, DEFAULT_SUCCESS_THRESHOLD);
    }

    /**
     * Gets or creates a circuit breaker for the specified endpoint.
     * 
     * @param endpointUrl The URL of the endpoint to protect
     * @return A circuit breaker for the specified endpoint
     */
    public CircuitBreaker<Object> getCircuitBreaker(String endpointUrl) {
        return circuitBreakers.computeIfAbsent(endpointUrl, url -> createCircuitBreaker(url));
    }

    /**
     * Creates a circuit breaker for the specified endpoint.
     * 
     * @param endpointUrl The URL of the endpoint being protected
     * @return Configured circuit breaker
     */
    private CircuitBreaker<Object> createCircuitBreaker(final String endpointUrl) {
        LOGGER.debug("Creating circuit breaker for endpoint: {}", endpointUrl);

        return CircuitBreaker.builder()
            .handleIf(throwable -> {
                // Only trigger for ServerErrorException instances (which are only created for actual server errors)
                if (throwable instanceof ServerErrorException) {
                    ServerErrorException serverError = (ServerErrorException) throwable;
                    int statusCode = serverError.getStatusCode();
                    String responseBody = serverError.getResponseBody();

                    // Check for specific status codes that indicate service availability issues
                    if (statusCode == SERVICE_UNAVAILABLE || statusCode == GATEWAY_TIMEOUT || statusCode == BAD_GATEWAY) {
                        LOGGER.debug("Circuit breaker triggered for availability-related status code: {} ({})",
                            statusCode, serverError.getMessage());
                        return true;
                    }

                    // For other 5xx errors, check the response body for signs of maintenance or availability issues
                    if (statusCode >= 500 && statusCode < 600) {
                        if (responseBody != null &&
                            (MAINTENANCE_PATTERN.matcher(responseBody).find() ||
                                TIMEOUT_PATTERN.matcher(responseBody).find())) {
                            LOGGER.debug("Circuit breaker triggered for 5xx error with availability indicators: {} ({})",
                                statusCode, serverError.getMessage());
                            return true;
                        }

                        LOGGER.debug("Not triggering circuit breaker for 5xx error without availability indicators: {} ({})",
                            statusCode, serverError.getMessage());
                    }

                    // For network-related errors identified by message content
                    if (statusCode == 0) {
                        String message = serverError.getMessage();
                        if (message != null) {
                            boolean isNetworkError = NETWORK_ERROR_INDICATORS.stream()
                                .anyMatch(indicator -> message.toLowerCase().contains(indicator));

                            if (isNetworkError) {
                                LOGGER.debug("Circuit breaker triggered for network error indicator in message: {}", message);
                                return true;
                            }
                        }
                    }
                }

                // For all other errors, including IOException which might indicate network issues
                if (throwable instanceof java.io.IOException) {
                    String message = throwable.getMessage();
                    if (message != null) {
                        boolean isNetworkError = NETWORK_ERROR_INDICATORS.stream()
                            .anyMatch(indicator -> message.toLowerCase().contains(indicator));

                        if (isNetworkError) {
                            LOGGER.debug("Circuit breaker triggered for network error: {}", message);
                            return true;
                        }
                    }
                }

                return false;
            })
            .withFailureThreshold(DEFAULT_FAILURE_THRESHOLD)
            .withDelay(Duration.ofSeconds(DEFAULT_DELAY_SECONDS))
            .withSuccessThreshold(DEFAULT_SUCCESS_THRESHOLD)
            .onOpen(event -> {
                LOGGER.warn("Circuit breaker OPENED for endpoint {} due to service unavailability. " +
                    "Further requests will fail fast without attempting to call the service.", endpointUrl);
            })
            .onHalfOpen(event -> {
                LOGGER.info("Circuit breaker HALF-OPENED for endpoint {}. " +
                    "Testing service availability with next request.", endpointUrl);
            })
            .onClose(event -> {
                LOGGER.info("Circuit breaker CLOSED for endpoint {}. " +
                    "Service is available again and accepting requests normally.", endpointUrl);
            })
            .build();
    }

    /**
     * Clears an existing circuit breaker for the specified endpoint.
     * This forces creation of a new circuit breaker the next time one is requested.
     * 
     * @param endpointUrl The URL of the endpoint
     * @return true if a circuit breaker was removed, false otherwise
     */
    public boolean clearCircuitBreaker(String endpointUrl) {
        CircuitBreaker<Object> removed = circuitBreakers.remove(endpointUrl);
        if (removed != null) {
            LOGGER.info("Cleared circuit breaker for endpoint: {}", endpointUrl);
            return true;
        }
        return false;
    }

    /**
     * Checks if a given error message contains indicators of network or availability issues.
     * 
     * @param message The error message to check
     * @return true if the message indicates a network or availability issue
     */
    public static boolean isNetworkError(String message) {
        if (message == null) {
            return false;
        }

        String lowerMessage = message.toLowerCase();
        return NETWORK_ERROR_INDICATORS.stream()
            .anyMatch(lowerMessage::contains);
    }
}
