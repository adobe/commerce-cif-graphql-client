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
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.failsafe.CircuitBreaker;
import dev.failsafe.Failsafe;

/**
 * Managing circuit breakers with different policies.
 * This service creates and manages circuit breakers for different endpoints and error types,
 * using separate policies for 503 and general 5xx errors.
 * The circuit breakers rely on exception types rather than status code checking for cleaner,
 * more maintainable code.
 */
public class CircuitBreakerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitBreakerService.class);

    // Default values
    private static final int DEFAULT_503_THRESHOLD = 3;
    private static final long DEFAULT_503_INITIAL_DELAY_MS = 20000L;
    private static final long DEFAULT_503_MAX_DELAY_MS = 180000L;
    private static final double DEFAULT_503_DELAY_MULTIPLIER = 1.5;
    private static final int DEFAULT_503_SUCCESS_THRESHOLD = 1;

    private static final int DEFAULT_5XX_THRESHOLD = 3;
    private static final long DEFAULT_5XX_DELAY_MS = 10000L;
    private static final int DEFAULT_5XX_SUCCESS_THRESHOLD = 1;

    private final CircuitBreaker<Object> serviceUnavailableBreaker;
    private final CircuitBreaker<Object> general5xxBreaker;
    private int currentAttempt = 1;
    private final Properties props = new Properties();

    public CircuitBreakerService() {
        loadProperties();
        // Load 503 configuration
        int threshold503 = getIntProperty(props, "circuit.breaker.503.threshold", DEFAULT_503_THRESHOLD);
        long initialDelay503 = getLongProperty(props, "circuit.breaker.503.initial.delay.ms", DEFAULT_503_INITIAL_DELAY_MS);
        long maxDelay503 = getLongProperty(props, "circuit.breaker.503.max.delay.ms", DEFAULT_503_MAX_DELAY_MS);
        double multiplier503 = getDoubleProperty(props, "circuit.breaker.503.delay.multiplier", DEFAULT_503_DELAY_MULTIPLIER);
        int successThreshold503 = getIntProperty(props, "circuit.breaker.503.success.threshold", DEFAULT_503_SUCCESS_THRESHOLD);

        // Load 5xx configuration
        int threshold5xx = getIntProperty(props, "circuit.breaker.5xx.threshold", DEFAULT_5XX_THRESHOLD);
        long delay5xxMs = getLongProperty(props, "circuit.breaker.5xx.delay.ms", DEFAULT_5XX_DELAY_MS);
        int successThreshold5xx = getIntProperty(props, "circuit.breaker.5xx.success.threshold", DEFAULT_5XX_SUCCESS_THRESHOLD);

        // Circuit Breaker for 503 errors with progressive delay
        this.serviceUnavailableBreaker = CircuitBreaker.builder()
            .handleIf(ServiceUnavailableException.class::isInstance)
            .withFailureThreshold(threshold503)
            .withDelayFn(context -> {
                long delay = (long) (initialDelay503 * Math.pow(multiplier503, (double) (currentAttempt - 1)));
                long finalDelay = Math.min(delay, maxDelay503);
                LOGGER.info("503 error - Attempt {} - Progressive delay: {}ms", currentAttempt, finalDelay);
                return Duration.ofMillis(finalDelay);
            })
            .onOpen(event -> LOGGER.warn("503 circuit breaker OPENED"))
            .onHalfOpen(event -> {
                currentAttempt++;
                LOGGER.info("503 circuit breaker HALF-OPEN - Current attempt: {}", currentAttempt);
            })
            .onClose(event -> {
                LOGGER.info("503 circuit breaker CLOSED");
                currentAttempt = 1;
            })
            .withSuccessThreshold(successThreshold503)
            .build();

        // Circuit Breaker for other 5xx errors with constant delay
        this.general5xxBreaker = CircuitBreaker.builder()
            .handleIf(ServerErrorException.class::isInstance)
            .withFailureThreshold(threshold5xx)
            .withDelay(Duration.ofMillis(delay5xxMs))
            .onOpen(event -> LOGGER.warn("5xx circuit breaker OPENED"))
            .onClose(event -> LOGGER.info("5xx circuit breaker CLOSED"))
            .withSuccessThreshold(successThreshold5xx)
            .build();
    }

    public <T> T executeWithPolicies(String endpointUrl, java.util.function.Supplier<T> supplier) {
        LOGGER.info("Executing request to {}", endpointUrl);
        return Failsafe
            .with(serviceUnavailableBreaker)
            .compose(general5xxBreaker)
            .get(supplier::get);
    }

    /**
     * Loads properties from the circuit-breaker.properties file.
     */
    private void loadProperties() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(
            "com/adobe/cq/commerce/graphql/client/impl/circuitbreaker/circuit-breaker.properties")) {
            if (in != null) {
                props.load(in);
                LOGGER.debug("Loaded circuit breaker configuration from properties file");
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to load circuit breaker properties, using default values", e);
        }
    }

    private int getIntProperty(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid integer value for property {}: {}. Using default: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    private long getLongProperty(Properties props, String key, long defaultValue) {
        String value = props.getProperty(key);
        if (value != null) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid long value for property {}: {}. Using default: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    private double getDoubleProperty(Properties props, String key, double defaultValue) {
        String value = props.getProperty(key);
        if (value != null) {
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid double value for property {}: {}. Using default: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }
}
