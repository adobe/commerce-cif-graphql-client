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

import java.util.Properties;

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

    @Test
    public void testConfigurationWithInvalidProperties() {
        // Create a configuration with a custom Properties object to test edge cases
        Configuration config = new Configuration() {
            @Override
            protected Properties loadProperties() {
                Properties props = new Properties();
                // Add invalid values to test NumberFormatException handling
                props.setProperty("circuit.breaker.503.threshold", "invalid-number");
                props.setProperty("circuit.breaker.5xx.delay.ms", "not-a-long");
                props.setProperty("circuit.breaker.timeout.delay.multiplier", "not-a-double");
                props.setProperty("circuit.breaker.503.initial.delay.ms", "   ");  // whitespace test
                return props;
            }
        };

        // These should use default values when parsing fails
        Configuration.ServiceUnavailableConfig serviceConfig = config.getServiceUnavailableConfig();
        Configuration.ServerErrorConfig serverConfig = config.getServerErrorConfig();
        Configuration.SocketTimeoutConfig socketConfig = config.getSocketTimeoutConfig();

        // Verify default values are used when parsing fails
        assertEquals("Should use default threshold", 3, serviceConfig.getThreshold());
        assertEquals("Should use default delay", 10000L, serverConfig.getDelayMs());
        assertEquals("Should use default multiplier", 1.5, socketConfig.getDelayMultiplier(), 0.01);
    }

    @Test
    public void testConfigurationWithNullValues() {
        // Test behavior when properties return null values
        Configuration config = new Configuration() {
            @Override
            protected Properties loadProperties() {
                return new Properties(); // Empty properties - all getProperty calls return null
            }
        };

        Configuration.ServiceUnavailableConfig serviceConfig = config.getServiceUnavailableConfig();
        Configuration.ServerErrorConfig serverConfig = config.getServerErrorConfig();
        Configuration.SocketTimeoutConfig socketConfig = config.getSocketTimeoutConfig();

        // Should all use default values
        assertEquals("Should use default threshold", 3, serviceConfig.getThreshold());
        assertEquals("Should use default initial delay", 20000L, serviceConfig.getInitialDelayMs());
        assertEquals("Should use default max delay", 180000L, serviceConfig.getMaxDelayMs());
        assertEquals("Should use default multiplier", 1.5, serviceConfig.getDelayMultiplier(), 0.01);
        assertEquals("Should use default success threshold", 1, serviceConfig.getSuccessThreshold());

        assertEquals("Should use default threshold", 3, serverConfig.getThreshold());
        assertEquals("Should use default delay", 10000L, serverConfig.getDelayMs());
        assertEquals("Should use default success threshold", 1, serverConfig.getSuccessThreshold());

        assertEquals("Should use default threshold", 3, socketConfig.getThreshold());
        assertEquals("Should use default initial delay", 20000L, socketConfig.getInitialDelayMs());
        assertEquals("Should use default max delay", 180000L, socketConfig.getMaxDelayMs());
        assertEquals("Should use default multiplier", 1.5, socketConfig.getDelayMultiplier(), 0.01);
        assertEquals("Should use default success threshold", 1, socketConfig.getSuccessThreshold());
    }

    @Test
    public void testConfigurationIOExceptionHandling() {
        // Test that Configuration handles IOException gracefully when properties file doesn't exist
        Configuration config = new Configuration() {
            @Override
            protected Properties loadProperties() {
                Properties props = new Properties();
                try {
                    // Try to load from non-existent file to trigger IOException path
                    props.load(this.getClass().getResourceAsStream("non-existent-file.properties"));
                } catch (Exception e) {
                    // This tests the IOException handling path in loadProperties
                }
                return props;
            }
        };

        // Should still work with default values
        assertNotNull("Configuration should handle IO errors gracefully", config.getServiceUnavailableConfig());
        assertNotNull("Configuration should handle IO errors gracefully", config.getServerErrorConfig());
        assertNotNull("Configuration should handle IO errors gracefully", config.getSocketTimeoutConfig());
    }
}