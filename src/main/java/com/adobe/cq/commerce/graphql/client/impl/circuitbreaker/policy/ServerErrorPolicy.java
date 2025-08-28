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
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.exception.ServerError;
import dev.failsafe.CircuitBreaker;

/**
 * Circuit breaker policy for Server Error (5xx) errors.
 * Uses constant delay strategy for general server errors (excluding 503).
 * Follows Single Responsibility Principle by focusing only on 5xx error handling.
 */
public class ServerErrorPolicy implements Policy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerErrorPolicy.class);

    private final Configuration.ServerErrorConfig config;

    public ServerErrorPolicy(Configuration.ServerErrorConfig config) {
        this.config = config;
    }

    @Override
    public CircuitBreaker<Object> createCircuitBreaker() {
        return CircuitBreaker.builder()
            .handleIf(ServerError.class::isInstance)
            .withFailureThreshold(config.getThreshold())
            .withDelay(Duration.ofMillis(config.getDelayMs()))
            .onOpen(event -> LOGGER.warn("5xx circuit breaker OPENED"))
            .onClose(event -> LOGGER.info("5xx circuit breaker CLOSED"))
            .withSuccessThreshold(config.getSuccessThreshold())
            .build();
    }

    @Override
    public String getPolicyName() {
        return "ServerError";
    }

    @Override
    public Class<? extends Exception> getHandledException() {
        return ServerError.class;
    }
}
