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

import org.junit.Before;
import org.junit.Test;

import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.Configuration;
import dev.failsafe.CircuitBreaker;

import static org.junit.Assert.*;

public class SocketTimeoutTest {

    private SocketTimeout policy;
    private Configuration.SocketTimeoutConfig config;

    @Before
    public void setUp() {
        config = new Configuration.SocketTimeoutConfig(3, 20000L, 180000L, 1.5, 1);
        policy = new SocketTimeout(config);
    }

    @Test
    public void testGetPolicyName() {
        assertEquals("SocketTimeout", policy.getPolicyName());
    }

    @Test
    public void testGetHandledException() {
        assertEquals(com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.exception.SocketTimeout.class, policy.getHandledException());
    }

    @Test
    public void testCreateCircuitBreaker() {
        CircuitBreaker<Object> circuitBreaker = policy.createCircuitBreaker();
        assertNotNull(circuitBreaker);
    }

    @Test
    public void testPolicyConfiguration() {
        // Test that the policy correctly uses its configuration
        Configuration.SocketTimeoutConfig testConfig = new Configuration.SocketTimeoutConfig(5, 30000L, 300000L, 2.0, 2);
        SocketTimeout testPolicy = new SocketTimeout(testConfig);

        assertEquals("SocketTimeout", testPolicy.getPolicyName());
        assertEquals(com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.exception.SocketTimeout.class, testPolicy
            .getHandledException());

        CircuitBreaker<Object> circuitBreaker = testPolicy.createCircuitBreaker();
        assertNotNull(circuitBreaker);
    }
}
