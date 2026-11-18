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

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.client.GraphqlClientConfiguration;

/**
 * Circuit breaker settings read from the OSGi {@link GraphqlClientConfiguration} of a GraphQL client, falling back to the
 * built-in defaults.
 */
class Configuration {
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

    // Formats of the OSGi values, see GraphqlClientConfiguration
    private static final String PROGRESSIVE_FORMAT = "FAILURE_THRESHOLD:INITIAL_DELAY_MS:MAX_DELAY_MS:DELAY_MULTIPLIER:SUCCESS_THRESHOLD";
    private static final String FIXED_FORMAT = "FAILURE_THRESHOLD:DELAY_MS:SUCCESS_THRESHOLD";

    // Upper bounds for OSGi values
    static final int MAX_THRESHOLD = 1000;
    // A half-open breaker lets this many trial requests through at the same time
    static final int MAX_SUCCESS_THRESHOLD = 10;
    static final long MAX_DELAY_MS = TimeUnit.DAYS.toMillis(1);

    private final GraphqlClientConfiguration osgiConfiguration;

    /**
     * Creates a configuration backed by the OSGi configuration of a GraphQL client. Each circuit breaker is configured by one
     * colon-separated value. Empty parts silently use their defaults. Invalid parts are logged and use their defaults, and parts
     * that are too large are logged and capped. A value with a different number of parts than its format is ignored completely.
     */
    Configuration(GraphqlClientConfiguration osgiConfiguration) {
        this.osgiConfiguration = osgiConfiguration;
    }

    /**
     * Gets configuration for 503 Service Unavailable circuit breaker.
     */
    public ServiceUnavailableConfig getServiceUnavailableConfig() {
        Values values = new Values("circuitBreakerServiceUnavailable", osgiConfiguration.circuitBreakerServiceUnavailable(),
            PROGRESSIVE_FORMAT);
        long initialDelay = values.delay(1, DEFAULT_503_INITIAL_DELAY_MS);
        return new ServiceUnavailableConfig(
            values.threshold(0, DEFAULT_503_THRESHOLD),
            initialDelay,
            values.maxDelay(2, initialDelay, DEFAULT_503_MAX_DELAY_MS),
            values.multiplier(3, DEFAULT_503_DELAY_MULTIPLIER),
            values.successThreshold(4, DEFAULT_503_SUCCESS_THRESHOLD));
    }

    /**
     * Gets configuration for 5xx Server Error circuit breaker.
     */
    public ServerErrorConfig getServerErrorConfig() {
        Values values = new Values("circuitBreakerServerError", osgiConfiguration.circuitBreakerServerError(), FIXED_FORMAT);
        return new ServerErrorConfig(
            values.threshold(0, DEFAULT_5XX_THRESHOLD),
            values.delay(1, DEFAULT_5XX_DELAY_MS),
            values.successThreshold(2, DEFAULT_5XX_SUCCESS_THRESHOLD));
    }

    /**
     * Gets configuration for Socket Timeout circuit breaker.
     */
    public SocketTimeoutConfig getSocketTimeoutConfig() {
        Values values = new Values("circuitBreakerSocketTimeout", osgiConfiguration.circuitBreakerSocketTimeout(), PROGRESSIVE_FORMAT);
        long initialDelay = values.delay(1, DEFAULT_TIMEOUT_INITIAL_DELAY_MS);
        return new SocketTimeoutConfig(
            values.threshold(0, DEFAULT_TIMEOUT_THRESHOLD),
            initialDelay,
            values.maxDelay(2, initialDelay, DEFAULT_TIMEOUT_MAX_DELAY_MS),
            values.multiplier(3, DEFAULT_TIMEOUT_DELAY_MULTIPLIER),
            values.successThreshold(4, DEFAULT_TIMEOUT_SUCCESS_THRESHOLD));
    }

    private static int positive(String name, int value, int defaultValue, int max) {
        if (value < 1) {
            LOGGER.warn("Invalid value {} for {}, it must be at least 1. Using default: {}", value, name, defaultValue);
            return defaultValue;
        }
        if (value > max) {
            // Failsafe allocates state proportional to the threshold
            LOGGER.warn("Invalid value {} for {}, it must be at most {}. Using: {}", value, name, max, max);
            return max;
        }
        return value;
    }

    /**
     * Parses a whole number. A number outside the long range is returned as {@link Long#MAX_VALUE} or {@link Long#MIN_VALUE},
     * so that it is capped like any other value that is too large or too small.
     */
    private static long parseLong(String part) {
        try {
            return Long.parseLong(part);
        } catch (NumberFormatException e) {
            if (part.matches("[+-]?\\d+")) {
                return part.startsWith("-") ? Long.MIN_VALUE : Long.MAX_VALUE;
            }
            throw e;
        }
    }

    private static long positive(String name, long value, long defaultValue) {
        if (value < 1) {
            LOGGER.warn("Invalid value {} for {}, it must be at least 1. Using default: {}", value, name, defaultValue);
            return defaultValue;
        }
        if (value > MAX_DELAY_MS) {
            LOGGER.warn("Invalid value {} for {}, it must be at most {}. Using: {}", value, name, MAX_DELAY_MS, MAX_DELAY_MS);
            return MAX_DELAY_MS;
        }
        return value;
    }

    private static long maxDelay(String name, long value, long initialDelay, long defaultValue) {
        long maxDelay = positive(name, value, defaultValue);
        if (maxDelay < initialDelay) {
            LOGGER.warn("Invalid value {} for {}, it must not be smaller than the initial delay. Using the initial delay: {}", maxDelay,
                name, initialDelay);
            return initialDelay;
        }
        return maxDelay;
    }

    private static double multiplier(String name, double value, double defaultValue) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 1.0) {
            LOGGER.warn("Invalid value {} for {}, it must be at least 1.0. Using default: {}", value, name, defaultValue);
            return defaultValue;
        }
        return value;
    }

    /**
     * The parts of one colon-separated OSGi value, for example {@code 3:20000:180000:1.5:1}. Each part is parsed and validated
     * on its own, so one invalid part does not reset the others. A value with a different number of parts than the format was
     * most likely copied from a property with another format, so it is ignored and all defaults are used.
     */
    private static final class Values {
        private final String property;
        private final String value;
        private final String[] fields;
        private final String[] parts;

        Values(String property, String value, String format) {
            this.property = property;
            this.value = value;
            this.fields = format.split(":");
            String[] split = value == null || value.trim().isEmpty() ? new String[0] : value.trim().split(":", -1);
            if (split.length > 0 && split.length != fields.length) {
                LOGGER.warn("Invalid value '{}' for {}, it has {} parts but the format {} has {}. Using the defaults for all parts.",
                    value, property, split.length, format, fields.length);
                split = new String[0];
            }
            this.parts = split;
        }

        int threshold(int index, int defaultValue) {
            return threshold(index, defaultValue, MAX_THRESHOLD);
        }

        int successThreshold(int index, int defaultValue) {
            return threshold(index, defaultValue, MAX_SUCCESS_THRESHOLD);
        }

        private int threshold(int index, int defaultValue, int max) {
            String part = part(index);
            if (part.isEmpty()) {
                return defaultValue;
            }
            try {
                long parsed = parseLong(part);
                return positive(name(index), (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, parsed)), defaultValue, max);
            } catch (NumberFormatException e) {
                return notANumber(index, part, defaultValue);
            }
        }

        long delay(int index, long defaultValue) {
            String part = part(index);
            if (part.isEmpty()) {
                return defaultValue;
            }
            try {
                return positive(name(index), parseLong(part), defaultValue);
            } catch (NumberFormatException e) {
                return notANumber(index, part, defaultValue);
            }
        }

        long maxDelay(int index, long initialDelay, long defaultValue) {
            String part = part(index);
            if (part.isEmpty()) {
                // Not set, so silently keep the default unless it is below the configured initial delay
                return Math.max(defaultValue, initialDelay);
            }
            long parsed;
            try {
                parsed = parseLong(part);
            } catch (NumberFormatException e) {
                parsed = notANumber(index, part, defaultValue);
            }
            return Configuration.maxDelay(name(index), parsed, initialDelay, defaultValue);
        }

        double multiplier(int index, double defaultValue) {
            String part = part(index);
            if (part.isEmpty()) {
                return defaultValue;
            }
            try {
                return Configuration.multiplier(name(index), Double.parseDouble(part), defaultValue);
            } catch (NumberFormatException e) {
                return notANumber(index, part, defaultValue);
            }
        }

        private String part(int index) {
            return index < parts.length ? parts[index].trim() : "";
        }

        private String name(int index) {
            return property + " " + fields[index];
        }

        private <T> T notANumber(int index, String part, T defaultValue) {
            LOGGER.warn("Invalid value '{}' for {} in '{}', it is not a valid number. Using default: {}", part, name(index), value,
                defaultValue);
            return defaultValue;
        }
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
