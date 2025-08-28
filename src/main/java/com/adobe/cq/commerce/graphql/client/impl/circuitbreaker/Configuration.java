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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration class for circuit breaker settings.
 * Handles loading and parsing of circuit breaker configuration from properties files.
 * Follows Single Responsibility Principle by focusing only on configuration management.
 */
public class Configuration {
    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    // 503 Service Unavailable defaults
    private static final int DEFAULT_503_THRESHOLD = 3;
    private static final long DEFAULT_503_INITIAL_DELAY_MS = 20000L;
    private static final long DEFAULT_503_MAX_DELAY_MS = 180000L;
    private static final double DEFAULT_503_DELAY_MULTIPLIER = 1.5;
    private static final int DEFAULT_503_SUCCESS_THRESHOLD = 1;

    // 5xx Server Error defaults
    private static final int DEFAULT_5XX_THRESHOLD = 3;
    private static final long DEFAULT_5XX_DELAY_MS = 10000L;
    private static final int DEFAULT_5XX_SUCCESS_THRESHOLD = 1;

    // Socket Timeout defaults - same values as 503 for consistency
    private static final int DEFAULT_TIMEOUT_THRESHOLD = 3;
    private static final long DEFAULT_TIMEOUT_INITIAL_DELAY_MS = 20000L;
    private static final long DEFAULT_TIMEOUT_MAX_DELAY_MS = 180000L;
    private static final double DEFAULT_TIMEOUT_DELAY_MULTIPLIER = 1.5;
    private static final int DEFAULT_TIMEOUT_SUCCESS_THRESHOLD = 1;

    private final Properties properties;

    public Configuration() {
        this.properties = loadProperties();
    }

    /**
     * Gets configuration for 503 Service Unavailable circuit breaker.
     */
    public ServiceUnavailableConfig getServiceUnavailableConfig() {
        return new ServiceUnavailableConfig(
            getIntProperty("circuit.breaker.503.threshold", DEFAULT_503_THRESHOLD),
            getLongProperty("circuit.breaker.503.initial.delay.ms", DEFAULT_503_INITIAL_DELAY_MS),
            getLongProperty("circuit.breaker.503.max.delay.ms", DEFAULT_503_MAX_DELAY_MS),
            getDoubleProperty("circuit.breaker.503.delay.multiplier", DEFAULT_503_DELAY_MULTIPLIER),
            getIntProperty("circuit.breaker.503.success.threshold", DEFAULT_503_SUCCESS_THRESHOLD));
    }

    /**
     * Gets configuration for 5xx Server Error circuit breaker.
     */
    public ServerErrorConfig getServerErrorConfig() {
        return new ServerErrorConfig(
            getIntProperty("circuit.breaker.5xx.threshold", DEFAULT_5XX_THRESHOLD),
            getLongProperty("circuit.breaker.5xx.delay.ms", DEFAULT_5XX_DELAY_MS),
            getIntProperty("circuit.breaker.5xx.success.threshold", DEFAULT_5XX_SUCCESS_THRESHOLD));
    }

    /**
     * Gets configuration for Socket Timeout circuit breaker.
     */
    public SocketTimeoutConfig getSocketTimeoutConfig() {
        return new SocketTimeoutConfig(
            getIntProperty("circuit.breaker.timeout.threshold", DEFAULT_TIMEOUT_THRESHOLD),
            getLongProperty("circuit.breaker.timeout.initial.delay.ms", DEFAULT_TIMEOUT_INITIAL_DELAY_MS),
            getLongProperty("circuit.breaker.timeout.max.delay.ms", DEFAULT_TIMEOUT_MAX_DELAY_MS),
            getDoubleProperty("circuit.breaker.timeout.delay.multiplier", DEFAULT_TIMEOUT_DELAY_MULTIPLIER),
            getIntProperty("circuit.breaker.timeout.success.threshold", DEFAULT_TIMEOUT_SUCCESS_THRESHOLD));
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(
            "com/adobe/cq/commerce/graphql/client/impl/circuitbreaker/circuit-breaker.properties")) {
            if (in != null) {
                props.load(in);
                LOGGER.debug("Loaded circuit breaker configuration from properties file");
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to load circuit breaker properties, using default values", e);
        }
        return props;
    }

    private int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid integer value for property {}: {}. Using default: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    private long getLongProperty(String key, long defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid long value for property {}: {}. Using default: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    private double getDoubleProperty(String key, double defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid double value for property {}: {}. Using default: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    /**
     * Configuration for Service Unavailable (503) circuit breaker.
     */
    public static class ServiceUnavailableConfig {
        private final int threshold;
        private final long initialDelayMs;
        private final long maxDelayMs;
        private final double delayMultiplier;
        private final int successThreshold;

        public ServiceUnavailableConfig(int threshold, long initialDelayMs, long maxDelayMs,
                                        double delayMultiplier, int successThreshold) {
            this.threshold = threshold;
            this.initialDelayMs = initialDelayMs;
            this.maxDelayMs = maxDelayMs;
            this.delayMultiplier = delayMultiplier;
            this.successThreshold = successThreshold;
        }

        public int getThreshold() {
            return threshold;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public long getMaxDelayMs() {
            return maxDelayMs;
        }

        public double getDelayMultiplier() {
            return delayMultiplier;
        }

        public int getSuccessThreshold() {
            return successThreshold;
        }
    }

    /**
     * Configuration for Server Error (5xx) circuit breaker.
     */
    public static class ServerErrorConfig {
        private final int threshold;
        private final long delayMs;
        private final int successThreshold;

        public ServerErrorConfig(int threshold, long delayMs, int successThreshold) {
            this.threshold = threshold;
            this.delayMs = delayMs;
            this.successThreshold = successThreshold;
        }

        public int getThreshold() {
            return threshold;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public int getSuccessThreshold() {
            return successThreshold;
        }
    }

    /**
     * Configuration for Socket Timeout circuit breaker.
     */
    public static class SocketTimeoutConfig {
        private final int threshold;
        private final long initialDelayMs;
        private final long maxDelayMs;
        private final double delayMultiplier;
        private final int successThreshold;

        public SocketTimeoutConfig(int threshold, long initialDelayMs, long maxDelayMs,
                                   double delayMultiplier, int successThreshold) {
            this.threshold = threshold;
            this.initialDelayMs = initialDelayMs;
            this.maxDelayMs = maxDelayMs;
            this.delayMultiplier = delayMultiplier;
            this.successThreshold = successThreshold;
        }

        public int getThreshold() {
            return threshold;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public long getMaxDelayMs() {
            return maxDelayMs;
        }

        public double getDelayMultiplier() {
            return delayMultiplier;
        }

        public int getSuccessThreshold() {
            return successThreshold;
        }
    }
}
