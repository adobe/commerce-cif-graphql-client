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
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.exception.ServerError;
import dev.failsafe.CircuitBreaker;

import static org.junit.Assert.*;

public class ServerErrorPolicyTest {

    private ServerErrorPolicy policy;
    private Configuration.ServerErrorConfig config;

    @Before
    public void setUp() {
        config = new Configuration.ServerErrorConfig(3, 10000L, 1);
        policy = new ServerErrorPolicy(config);
    }

    @Test
    public void testGetPolicyName() {
        assertEquals("ServerError", policy.getPolicyName());
    }

    @Test
    public void testGetHandledException() {
        assertEquals(ServerError.class, policy.getHandledException());
    }

    @Test
    public void testCreateCircuitBreaker() {
        CircuitBreaker<Object> circuitBreaker = policy.createCircuitBreaker();
        assertNotNull(circuitBreaker);
    }

    @Test
    public void testPolicyConfiguration() {
        // Test that the policy correctly uses its configuration
        Configuration.ServerErrorConfig testConfig = new Configuration.ServerErrorConfig(5, 15000L, 2);
        ServerErrorPolicy testPolicy = new ServerErrorPolicy(testConfig);

        assertEquals("ServerError", testPolicy.getPolicyName());
        assertEquals(ServerError.class, testPolicy.getHandledException());

        CircuitBreaker<Object> circuitBreaker = testPolicy.createCircuitBreaker();
        assertNotNull(circuitBreaker);
    }
}
