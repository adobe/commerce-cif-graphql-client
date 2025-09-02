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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.failsafe.CircuitBreaker;
import dev.failsafe.Failsafe;

/**
 * Orchestrates circuit breaker policies for fault-tolerant GraphQL request execution.
 * Uses composition to combine different circuit breaker policies for comprehensive fault tolerance.
 */
public class CircuitBreakerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitBreakerService.class);

    private final List<CircuitBreaker<Object>> circuitBreakers;

    public CircuitBreakerService() {
        this(new Configuration());
    }

    public CircuitBreakerService(Configuration configuration) {
        // Create policies with their configurations
        List<Policy> policies = Arrays.asList(
            new ServiceUnavailablePolicy(configuration.getServiceUnavailableConfig()),
            new ServerErrorPolicy(configuration.getServerErrorConfig()),
            new SocketTimeoutPolicy(configuration.getSocketTimeoutConfig()));

        // Create circuit breakers from policies
        this.circuitBreakers = policies.stream()
            .map(policy -> {
                CircuitBreaker<Object> breaker = policy.createCircuitBreaker();
                LOGGER.debug("Created circuit breaker for policy: {}", policy.getPolicyName());
                return breaker;
            })
            .collect(Collectors.toList());

        LOGGER.info("Initialized CircuitBreakerService with {} policies", policies.size());
    }

    /**
     * Executes a supplier with all configured circuit breaker policies.
     * Policies are composed together to provide comprehensive fault tolerance.
     * 
     * @param endpointUrl the endpoint URL for logging purposes
     * @param supplier the supplier to execute
     * @return the result of the supplier execution
     */
    public <T> T executeWithPolicies(String endpointUrl, java.util.function.Supplier<T> supplier) {
        LOGGER.debug("Executing request to {} with {} circuit breakers", endpointUrl, circuitBreakers.size());

        // Compose all circuit breakers together
        return Failsafe.with(circuitBreakers).get(supplier::get);
    }
}
