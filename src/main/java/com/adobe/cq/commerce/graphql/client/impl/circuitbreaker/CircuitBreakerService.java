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
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.failsafe.CircuitBreaker;
import dev.failsafe.Failsafe;

/**
 * Managing circuit breakers with different policies.
 * This service creates and manages circuit breakers for different endpoints and error types,
 * using separate policies for 503 and general 5xx errors.
 */
public class CircuitBreakerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitBreakerService.class);

    // Configuration for 503 Service Unavailable
    private static final int SERVICE_UNAVAILABLE_THRESHOLD = 3;
    private static final long INITIAL_DELAY_MS = 20000L; // 20 seconds
    private static final long MAX_DELAY_MS = 180000L;    // 3 minutes
    private static final double DELAY_MULTIPLIER = 1.5;
    private static final String LOG_503_ATTEMPT = "503 error - Attempt {} - Progressive delay: {}ms";
    private static final String LOG_503_OPEN = "503 circuit breaker OPENED";
    private static final String LOG_503_HALF_OPEN = "503 circuit breaker HALF-OPEN - Current attempt: {}";
    private static final String LOG_503_CLOSED = "503 circuit breaker CLOSED";
    private static final int SERVICE_UNAVAILABLE_SUCCESS_THRESHOLD = 1;
    private final CircuitBreaker<Object> serviceUnavailableBreaker;

    // Configuration for general 5xx errors
    private static final int GENERAL_5XX_THRESHOLD = 3;
    private static final Duration GENERAL_5XX_DELAY = Duration.ofSeconds(10);
    private static final int GENERAL_5XX_SUCCESS_THRESHOLD = 1;
    private final CircuitBreaker<Object> general5xxBreaker;

    private int currentAttempt = 1;

    public CircuitBreakerService() {
        // Circuit Breaker for 503 errors with progressive delay
        this.serviceUnavailableBreaker = CircuitBreaker.builder()
            .handleIf(throwable -> {
                if (throwable instanceof ServerErrorException) {
                    ServerErrorException serverError = (ServerErrorException) throwable;
                    return serverError.getStatusCode() == 503;
                }
                return false;
            })
            .withFailureThreshold(SERVICE_UNAVAILABLE_THRESHOLD)
            .withDelayFn(context -> {
                long delay = (long) (INITIAL_DELAY_MS * Math.pow(DELAY_MULTIPLIER, (double) (currentAttempt - 1)));
                long finalDelay = Math.min(delay, MAX_DELAY_MS);
                LOGGER.info(LOG_503_ATTEMPT, currentAttempt, finalDelay);
                return Duration.ofMillis(finalDelay);
            })
            .onOpen(event -> LOGGER.warn(LOG_503_OPEN))
            .onHalfOpen(event -> {
                currentAttempt++;
                LOGGER.info(LOG_503_HALF_OPEN, currentAttempt);
            })
            .onClose(event -> {
                LOGGER.info(LOG_503_CLOSED);
                currentAttempt = 1;
            })
            .withSuccessThreshold(SERVICE_UNAVAILABLE_SUCCESS_THRESHOLD)
            .build();

        // Circuit Breaker for other 5xx errors with constant delay
        this.general5xxBreaker = CircuitBreaker.builder()
            .handleIf(throwable -> {
                if (throwable instanceof ServerErrorException) {
                    ServerErrorException serverError = (ServerErrorException) throwable;
                    return serverError.getStatusCode() >= 500 && serverError.getStatusCode() != 503;
                }
                return false;
            })
            .withFailureThreshold(GENERAL_5XX_THRESHOLD)
            .withDelay(GENERAL_5XX_DELAY)
            .onOpen(event -> LOGGER.warn("5xx circuit breaker OPENED"))
            .onClose(event -> LOGGER.info("5xx circuit breaker CLOSED"))
            .withSuccessThreshold(GENERAL_5XX_SUCCESS_THRESHOLD)
            .build();
    }

    public <T> T executeWithPolicies(String endpointUrl, java.util.function.Supplier<T> supplier) throws IOException {
        LOGGER.info("Executing request to {}", endpointUrl);
        return Failsafe
            .with(serviceUnavailableBreaker)
            .compose(general5xxBreaker)
            .get(supplier::get);
    }
}
