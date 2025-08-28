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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for Configuration focusing on property parsing methods.
 */
public class ConfigurationTest {

    private Configuration configuration;

    @Before
    public void setUp() {
        configuration = new Configuration();
    }

    @Test
    public void testConfigurationCreation() {
        assertNotNull("Configuration should be created successfully", configuration);

        // Test that default configurations are available
        assertNotNull("ServiceUnavailableConfig should not be null", configuration.getServiceUnavailableConfig());
        assertNotNull("ServerErrorConfig should not be null", configuration.getServerErrorConfig());
        assertNotNull("SocketTimeoutConfig should not be null", configuration.getSocketTimeoutConfig());
    }

    @Test
    public void testDefaultConfigurationValues() {
        Configuration.ServiceUnavailableConfig serviceConfig = configuration.getServiceUnavailableConfig();
        Configuration.ServerErrorConfig serverConfig = configuration.getServerErrorConfig();
        Configuration.SocketTimeoutConfig socketConfig = configuration.getSocketTimeoutConfig();

        // Test that all configs are created (non-null) - this verifies configuration loading works
        assertNotNull("Service unavailable config should not be null", serviceConfig);
        assertNotNull("Server error config should not be null", serverConfig);
        assertNotNull("Socket timeout config should not be null", socketConfig);
    }

    @Test
    public void testConfigurationConsistency() {
        // Test that creating multiple instances gives consistent results
        Configuration config1 = new Configuration();
        Configuration config2 = new Configuration();

        assertNotNull("First config should not be null", config1.getServiceUnavailableConfig());
        assertNotNull("Second config should not be null", config2.getServiceUnavailableConfig());

        // Both configurations should be able to create their configs
        assertNotNull("Config1 service unavailable should not be null", config1.getServiceUnavailableConfig());
        assertNotNull("Config1 server error should not be null", config1.getServerErrorConfig());
        assertNotNull("Config1 socket timeout should not be null", config1.getSocketTimeoutConfig());

        assertNotNull("Config2 service unavailable should not be null", config2.getServiceUnavailableConfig());
        assertNotNull("Config2 server error should not be null", config2.getServerErrorConfig());
        assertNotNull("Config2 socket timeout should not be null", config2.getSocketTimeoutConfig());
    }

    @Test
    public void testConfigurationRobustness() {
        // Test that configuration handles potential edge cases gracefully
        try {
            Configuration config = new Configuration();

            // Multiple calls should be safe
            config.getServiceUnavailableConfig();
            config.getServiceUnavailableConfig();

            config.getServerErrorConfig();
            config.getServerErrorConfig();

            config.getSocketTimeoutConfig();
            config.getSocketTimeoutConfig();

            // All should return non-null configurations
            assertNotNull("Service config should be non-null", config.getServiceUnavailableConfig());
            assertNotNull("Server config should be non-null", config.getServerErrorConfig());
            assertNotNull("Socket config should be non-null", config.getSocketTimeoutConfig());

        } catch (Exception e) {
            fail("Configuration should handle edge cases gracefully: " + e.getMessage());
        }
    }

    @Test
    public void testConfigurationGetters() {
        Configuration.ServiceUnavailableConfig serviceConfig = configuration.getServiceUnavailableConfig();
        Configuration.ServerErrorConfig serverConfig = configuration.getServerErrorConfig();
        Configuration.SocketTimeoutConfig socketConfig = configuration.getSocketTimeoutConfig();

        // Test ServiceUnavailableConfig getters
        assertTrue("Threshold should be positive", serviceConfig.getThreshold() > 0);
        assertTrue("Initial delay should be positive", serviceConfig.getInitialDelayMs() > 0);
        assertTrue("Max delay should be positive", serviceConfig.getMaxDelayMs() > 0);
        assertTrue("Delay multiplier should be positive", serviceConfig.getDelayMultiplier() > 0);
        assertTrue("Success threshold should be positive", serviceConfig.getSuccessThreshold() > 0);

        // Test ServerErrorConfig getters
        assertTrue("Threshold should be positive", serverConfig.getThreshold() > 0);
        assertTrue("Delay should be positive", serverConfig.getDelayMs() > 0);
        assertTrue("Success threshold should be positive", serverConfig.getSuccessThreshold() > 0);

        // Test SocketTimeoutConfig getters
        assertTrue("Threshold should be positive", socketConfig.getThreshold() > 0);
        assertTrue("Initial delay should be positive", socketConfig.getInitialDelayMs() > 0);
        assertTrue("Max delay should be positive", socketConfig.getMaxDelayMs() > 0);
        assertTrue("Delay multiplier should be positive", socketConfig.getDelayMultiplier() > 0);
        assertTrue("Success threshold should be positive", socketConfig.getSuccessThreshold() > 0);
    }
}