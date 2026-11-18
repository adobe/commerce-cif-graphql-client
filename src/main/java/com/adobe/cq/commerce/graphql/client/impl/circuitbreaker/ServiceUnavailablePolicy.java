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
 * Circuit breaker policy for 503 Service Unavailable errors.
 * Uses progressive delay strategy where delays increase with each failure attempt.
 */
public class ServiceUnavailablePolicy implements Policy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceUnavailablePolicy.class);

    private final Configuration.ServiceUnavailableConfig config;
    // Shared by all request threads; AtomicInteger prevents lost updates. Failsafe computes the delay inside its lock but
    // calls onHalfOpen after releasing it, so in a rare race a re-opened breaker may use a delay one step too short (harmless).
    private final AtomicInteger currentAttempt = new AtomicInteger(1);

    private final String clientName;

    public ServiceUnavailablePolicy(Configuration.ServiceUnavailableConfig config, String clientName) {
        this.config = config;
        this.clientName = clientName;
    }

    @Override
    public CircuitBreaker<Object> createCircuitBreaker() {
        return CircuitBreaker.builder()
            .handleIf(ServiceUnavailableException.class::isInstance)
            .withFailureThreshold(config.getThreshold())
            .withDelayFn(context -> Duration.ofMillis(calculateDelayMs()))
            .onOpen(event -> LOGGER.warn("503 circuit breaker OPENED for {} (previous state: {}, failure threshold: {}). "
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
        LOGGER.info("503 circuit breaker for {} - attempt {} - progressive delay: {}ms", clientName, attempt, finalDelay);
        return finalDelay;
    }

    void onHalfOpen() {
        int attempt = currentAttempt.incrementAndGet();
        LOGGER.info("503 circuit breaker HALF-OPEN for {} - allowing {} probe request(s), attempt {}", clientName,
            config.getSuccessThreshold(), attempt);
    }

    void onClose() {
        LOGGER.info("503 circuit breaker CLOSED for {} - the probe request(s) did not get an HTTP 503", clientName);
        currentAttempt.set(1);
    }

    int getCurrentAttempt() {
        return currentAttempt.get();
    }

    @Override
    public String getPolicyName() {
        return "ServiceUnavailable";
    }

    @Override
    public Class<? extends Exception> getHandledException() {
        return ServiceUnavailableException.class;
    }
}
