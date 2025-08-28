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
package com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.policy;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.Configuration;
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.Policy;
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.exception.ServiceUnavailable;
import dev.failsafe.CircuitBreaker;

/**
 * Circuit breaker policy for 503 Service Unavailable errors.
 * Uses progressive delay strategy where delays increase with each failure attempt.
 * Follows Single Responsibility Principle by focusing only on 503 error handling.
 */
public class ServiceUnavailablePolicy implements Policy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceUnavailablePolicy.class);

    private final Configuration.ServiceUnavailableConfig config;
    private int currentAttempt = 1;

    public ServiceUnavailablePolicy(Configuration.ServiceUnavailableConfig config) {
        this.config = config;
    }

    @Override
    public CircuitBreaker<Object> createCircuitBreaker() {
        return CircuitBreaker.builder()
            .handleIf(ServiceUnavailable.class::isInstance)
            .withFailureThreshold(config.getThreshold())
            .withDelayFn(context -> {
                long delay = (long) (config.getInitialDelayMs() * Math.pow(config.getDelayMultiplier(), (double) (currentAttempt - 1)));
                long finalDelay = Math.min(delay, config.getMaxDelayMs());
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
            .withSuccessThreshold(config.getSuccessThreshold())
            .build();
    }

    @Override
    public String getPolicyName() {
        return "ServiceUnavailable";
    }

    @Override
    public Class<? extends Exception> getHandledException() {
        return ServiceUnavailable.class;
    }
}
