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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.client.GraphqlClientConfiguration;
import dev.failsafe.CircuitBreaker;
import dev.failsafe.Failsafe;

/**
 * Orchestrates circuit breaker policies for fault-tolerant GraphQL request execution.
 * Uses composition to combine different circuit breaker policies for comprehensive fault tolerance.
 */
public class CircuitBreakerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitBreakerService.class);

    private final List<CircuitBreaker<Object>> circuitBreakers;
    private final Map<CircuitBreaker<?>, String> policyNames = new IdentityHashMap<>();
    private final String clientName;
    // Builds the breaker states only when a debug message is actually logged
    private final Object states = new Object() {
        @Override
        public String toString() {
            return circuitBreakers.stream()
                .map(breaker -> policyNames.get(breaker) + "=" + breaker.getState())
                .collect(Collectors.joining(", ", "[", "]"));
        }
    };

    /**
     * Creates the service with the circuit breaker settings of the given GraphQL client configuration.
     *
     * @param graphqlClientConfiguration the OSGi configuration of the GraphQL client
     */
    public CircuitBreakerService(GraphqlClientConfiguration graphqlClientConfiguration) {
        // Several clients can share an identifier and URL (e.g. the CIF add-on and project configurations), the ranking tells them apart
        this.clientName = "'" + graphqlClientConfiguration.identifier() + "' (ranking " + graphqlClientConfiguration.service_ranking()
            + ", " + graphqlClientConfiguration.url() + ")";
        Configuration configuration = new Configuration(graphqlClientConfiguration);
        Configuration.ServiceUnavailableConfig serviceUnavailableConfig = configuration.getServiceUnavailableConfig();
        Configuration.ServerErrorConfig serverErrorConfig = configuration.getServerErrorConfig();
        Configuration.SocketTimeoutConfig socketTimeoutConfig = configuration.getSocketTimeoutConfig();

        // Create policies with their configurations
        List<Policy> policies = Arrays.asList(
            new ServiceUnavailablePolicy(serviceUnavailableConfig, clientName),
            new ServerErrorPolicy(serverErrorConfig, clientName),
            new SocketTimeoutPolicy(socketTimeoutConfig, clientName));

        // Create circuit breakers from policies
        this.circuitBreakers = policies.stream()
            .map(policy -> {
                CircuitBreaker<Object> breaker = policy.createCircuitBreaker();
                policyNames.put(breaker, policy.getPolicyName());
                LOGGER.debug("Created circuit breaker for policy: {}", policy.getPolicyName());
                return breaker;
            })
            .collect(Collectors.toList());

        LOGGER.info("Initialized CircuitBreakerService for {} with {} policies: "
            + "socket timeout [failureThreshold={}, initialDelayMs={}, maxDelayMs={}, delayMultiplier={}, successThreshold={}], "
            + "503 [failureThreshold={}, initialDelayMs={}, maxDelayMs={}, delayMultiplier={}, successThreshold={}], "
            + "5xx [failureThreshold={}, delayMs={}, successThreshold={}]",
            clientName, policies.size(),
            socketTimeoutConfig.getThreshold(), socketTimeoutConfig.getInitialDelayMs(), socketTimeoutConfig.getMaxDelayMs(),
            socketTimeoutConfig.getDelayMultiplier(), socketTimeoutConfig.getSuccessThreshold(),
            serviceUnavailableConfig.getThreshold(), serviceUnavailableConfig.getInitialDelayMs(), serviceUnavailableConfig.getMaxDelayMs(),
            serviceUnavailableConfig.getDelayMultiplier(), serviceUnavailableConfig.getSuccessThreshold(),
            serverErrorConfig.getThreshold(), serverErrorConfig.getDelayMs(), serverErrorConfig.getSuccessThreshold());
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
        LOGGER.debug("Executing request to {} with circuit breaker states {}", endpointUrl, states);

        // Compose all circuit breakers together
        return Failsafe.with(circuitBreakers).get(supplier::get);
    }

    /**
     * Returns the name of the policy that created the given circuit breaker, for example to log which breaker rejected a request.
     *
     * @param circuitBreaker a circuit breaker, typically from {@link dev.failsafe.CircuitBreakerOpenException#getCircuitBreaker()}
     * @return the policy name, or {@code "unknown"} if the breaker was not created by this service
     */
    public String getPolicyName(CircuitBreaker<?> circuitBreaker) {
        return policyNames.getOrDefault(circuitBreaker, "unknown");
    }

    /**
     * @return the name of the GraphQL client used in log messages
     */
    public String getClientName() {
        return clientName;
    }
}
