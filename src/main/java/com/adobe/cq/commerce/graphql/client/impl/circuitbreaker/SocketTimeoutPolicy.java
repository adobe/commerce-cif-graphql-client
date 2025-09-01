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

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.failsafe.CircuitBreaker;

/**
 * Circuit breaker policy for Socket Timeout errors.
 * Uses progressive delay strategy where delays increase with each failure attempt.
 * Follows Single Responsibility Principle by focusing only on socket timeout handling.
 */
public class SocketTimeoutPolicy implements Policy {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocketTimeoutPolicy.class);

    private final Configuration.SocketTimeoutConfig config;
    private int currentAttempt = 1;

    public SocketTimeoutPolicy(Configuration.SocketTimeoutConfig config) {
        this.config = config;
    }

    @Override
    public CircuitBreaker<Object> createCircuitBreaker() {
        return CircuitBreaker.builder()
            .handleIf(SocketTimeoutException.class::isInstance)
            .withFailureThreshold(config.getThreshold())
            .withDelayFn(context -> {
                long delay = (long) (config.getInitialDelayMs() * Math.pow(config.getDelayMultiplier(), (double) (currentAttempt - 1)));
                long finalDelay = Math.min(delay, config.getMaxDelayMs());
                LOGGER.info("Socket timeout error - Attempt {} - Progressive delay: {}ms", currentAttempt, finalDelay);
                return Duration.ofMillis(finalDelay);
            })
            .onOpen(event -> LOGGER.warn("Socket timeout circuit breaker OPENED"))
            .onHalfOpen(event -> {
                currentAttempt++;
                LOGGER.info("Socket timeout circuit breaker HALF-OPEN - Current attempt: {}", currentAttempt);
            })
            .onClose(event -> {
                LOGGER.info("Socket timeout circuit breaker CLOSED");
                currentAttempt = 1;
            })
            .withSuccessThreshold(config.getSuccessThreshold())
            .build();
    }

    @Override
    public String getPolicyName() {
        return "SocketTimeout";
    }

    @Override
    public Class<? extends Exception> getHandledException() {
        return SocketTimeoutException.class;
    }
}
