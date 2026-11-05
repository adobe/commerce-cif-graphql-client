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
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.failsafe.CircuitBreaker;

/**
 * Circuit breaker policy for Socket Timeout errors.
 * Uses progressive delay strategy where delays increase with each failure attempt.
 */
public class SocketTimeoutPolicy implements Policy {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocketTimeoutPolicy.class);

    private final Configuration.SocketTimeoutConfig config;
    // Shared by all request threads that trigger circuit breaker state transitions
    private final AtomicInteger currentAttempt = new AtomicInteger(1);

    private final String clientName;

    public SocketTimeoutPolicy(Configuration.SocketTimeoutConfig config, String clientName) {
        this.config = config;
        this.clientName = clientName;
    }

    @Override
    public CircuitBreaker<Object> createCircuitBreaker() {
        return CircuitBreaker.builder()
            .handleIf(SocketTimeoutException.class::isInstance)
            .withFailureThreshold(config.getThreshold())
            .withDelayFn(context -> Duration.ofMillis(calculateDelayMs()))
            .onOpen(event -> LOGGER.warn("Socket timeout circuit breaker OPENED for {} (previous state: {}, failure threshold: {}). "
                + "Requests fail fast without calling the backend until the next probe.", clientName, event.getPreviousState(),
                config.getThreshold()))
            .onHalfOpen(event -> onHalfOpen())
            .onClose(event -> onClose())
            .withSuccessThreshold(config.getSuccessThreshold())
            .build();
    }

    long calculateDelayMs() {
        int attempt = currentAttempt.get();
        long delay = (long) (config.getInitialDelayMs() * Math.pow(config.getDelayMultiplier(), (double) (attempt - 1)));
        long finalDelay = Math.min(delay, config.getMaxDelayMs());
        LOGGER.info("Socket timeout circuit breaker for {} - attempt {} - progressive delay: {}ms", clientName, attempt, finalDelay);
        return finalDelay;
    }

    void onHalfOpen() {
        int attempt = currentAttempt.incrementAndGet();
        LOGGER.info("Socket timeout circuit breaker HALF-OPEN for {} - allowing {} probe request(s), attempt {}", clientName,
            config.getSuccessThreshold(), attempt);
    }

    void onClose() {
        LOGGER.info("Socket timeout circuit breaker CLOSED for {} - backend recovered", clientName);
        currentAttempt.set(1);
    }

    int getCurrentAttempt() {
        return currentAttempt.get();
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
