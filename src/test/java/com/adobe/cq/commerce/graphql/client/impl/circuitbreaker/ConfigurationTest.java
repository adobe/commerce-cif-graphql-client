/*******************************************************************************
 *
 *    Copyright 2026 Adobe. All rights reserved.
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

import org.junit.Test;

import com.adobe.cq.commerce.graphql.client.GraphqlClientConfiguration;
import dev.failsafe.CircuitBreaker;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the circuit breaker settings read from the OSGi {@link GraphqlClientConfiguration}.
 */
public class ConfigurationTest {

    private static final double DELTA = 0.0001;
    private static final String DEFAULT_PROGRESSIVE = "3:20000:180000:1.5:1";
    private static final String DEFAULT_FIXED = "3:10000:1";

    /**
     * The defaults of the 3 circuit breaker OSGi attributes are the values that were hard-coded before they became configurable.
     * <p>
     * Why: upgrading must not change the behaviour for customers who do not set the new properties.
     */
    @Test
    public void testAnnotationDefaultsMatchPreviousBehaviour() throws Exception {
        assertDefault(DEFAULT_PROGRESSIVE, "circuitBreakerSocketTimeout");
        assertDefault(DEFAULT_PROGRESSIVE, "circuitBreakerServiceUnavailable");
        assertDefault(DEFAULT_FIXED, "circuitBreakerServerError");
    }

    /**
     * Passing the default values through OSGi gives the default configuration for all three breakers.
     */
    @Test
    public void testDefaultOsgiValuesProduceDefaultConfiguration() {
        Configuration configuration = new Configuration(mockConfiguration(DEFAULT_PROGRESSIVE, DEFAULT_PROGRESSIVE, DEFAULT_FIXED));

        assertDefaults(configuration);
    }

    /**
     * Valid custom values are used, each by the right breaker (socket timeout, HTTP 503 and other HTTP 5xx settings are not
     * mixed up) and each part in the right field.
     */
    @Test
    public void testCustomOsgiValuesAreUsed() {
        Configuration configuration = new Configuration(mockConfiguration("5:1000:60000:2.0:2", "7:3000:90000:1.25:3", "4:15000:2"));

        assertSocketTimeout(configuration.getSocketTimeoutConfig(), 5, 1000L, 60000L, 2.0, 2);
        assertServiceUnavailable(configuration.getServiceUnavailableConfig(), 7, 3000L, 90000L, 1.25, 3);
        assertServerError(configuration.getServerErrorConfig(), 4, 15000L, 2);
    }

    /**
     * Parts can be left out at the end or left empty in between, and spaces around parts are ignored. Missing parts use their
     * default.
     * <p>
     * Why: a customer who only wants a different failure threshold can set just {@code "5"}.
     */
    @Test
    public void testMissingAndEmptyPartsUseDefaults() {
        Configuration configuration = new Configuration(mockConfiguration("5", "::60000", " 4 : 2000 : 2 "));

        assertSocketTimeout(configuration.getSocketTimeoutConfig(), 5, 20000L, 180000L, 1.5, 1);
        assertServiceUnavailable(configuration.getServiceUnavailableConfig(), 3, 20000L, 60000L, 1.5, 1);
        assertServerError(configuration.getServerErrorConfig(), 4, 2000L, 2);
    }

    /**
     * Invalid numbers (0, negative numbers, a multiplier below 1 and NaN) are replaced by the defaults instead of failing the
     * activation.
     * <p>
     * Why: a typo in the configuration must not break the GraphQL client.
     */
    @Test
    public void testInvalidOsgiValuesFallBackToDefaults() {
        Configuration configuration = new Configuration(mockConfiguration("0:-1:0:0.5:0", "-5:0:-1:NaN:-1", "0:0:0"));

        assertDefaults(configuration);
    }

    /**
     * A part that is not a number is replaced by its default, and the valid parts of the same value are still used.
     * <p>
     * Why: one typo, e.g. {@code 10s} instead of {@code 10000}, must not reset the other settings of the breaker.
     */
    @Test
    public void testNonNumericPartsFallBackToDefaultsIndividually() {
        Configuration configuration = new Configuration(mockConfiguration("abc:1000:x:2.0:y", "5:20s:90000:fast:2", "1.5:10s:2"));

        assertSocketTimeout(configuration.getSocketTimeoutConfig(), 3, 1000L, 180000L, 2.0, 1);
        assertServiceUnavailable(configuration.getServiceUnavailableConfig(), 5, 20000L, 90000L, 1.5, 2);
        assertServerError(configuration.getServerErrorConfig(), 3, 10000L, 2);
    }

    /**
     * Parts after the expected number of parts are ignored, the others are used.
     */
    @Test
    public void testExtraPartsAreIgnored() {
        Configuration configuration = new Configuration(mockConfiguration("5:1000:2000:2.0:2:99", "7:3000:90000:1.25:3:x:y",
            "4:15000:2:extra"));

        assertSocketTimeout(configuration.getSocketTimeoutConfig(), 5, 1000L, 2000L, 2.0, 2);
        assertServiceUnavailable(configuration.getServiceUnavailableConfig(), 7, 3000L, 90000L, 1.25, 3);
        assertServerError(configuration.getServerErrorConfig(), 4, 15000L, 2);
    }

    /**
     * Empty and blank values use the defaults.
     */
    @Test
    public void testBlankValuesUseDefaults() {
        assertDefaults(new Configuration(mockConfiguration("", "   ", "")));
    }

    /**
     * A configuration that does not set the circuit breaker attributes returns {@code null} for them, and the defaults are used.
     * This happens with plain mocks and with custom {@link GraphqlClientConfiguration} implementations written before the
     * attributes existed.
     * <p>
     * Why: existing custom implementations must keep working.
     */
    @Test
    public void testUnsetValuesFallBackToDefaults() {
        assertDefaults(new Configuration(mock(GraphqlClientConfiguration.class)));
    }

    /**
     * An invalid max delay (0 or negative) is first replaced by the default (180000 ms). That is smaller than the configured
     * initial delay (300000 ms), so the initial delay is used as the max delay. The same happens when the max delay is left out.
     * <p>
     * Why: the max delay must never be smaller than the initial delay, also after an invalid or missing value was replaced.
     */
    @Test
    public void testInvalidMaxDelayWithLargeInitialDelayUsesInitialDelay() {
        Configuration configuration = new Configuration(mockConfiguration("3:300000:0:1.5:1", "3:300000", DEFAULT_FIXED));

        assertEquals(300000L, configuration.getSocketTimeoutConfig().getMaxDelayMs());
        assertEquals(300000L, configuration.getServiceUnavailableConfig().getMaxDelayMs());
    }

    /**
     * An infinite delay multiplier is replaced by the default (1.5).
     */
    @Test
    public void testInfiniteMultiplierFallsBackToDefault() {
        Configuration configuration = new Configuration(mockConfiguration("3:20000:180000:Infinity:1", "3:20000:180000:Infinity:1",
            DEFAULT_FIXED));

        assertEquals(1.5, configuration.getSocketTimeoutConfig().getDelayMultiplier(), DELTA);
        assertEquals(1.5, configuration.getServiceUnavailableConfig().getDelayMultiplier(), DELTA);
    }

    /**
     * A max delay smaller than the initial delay is raised to the initial delay.
     * <p>
     * Why: otherwise the delay could never reach the configured initial delay.
     */
    @Test
    public void testMaxDelaySmallerThanInitialDelayUsesInitialDelay() {
        Configuration configuration = new Configuration(mockConfiguration("3:30000:10000:1.5:1", "3:40000:5000:1.5:1", DEFAULT_FIXED));

        assertEquals(30000L, configuration.getSocketTimeoutConfig().getMaxDelayMs());
        assertEquals(40000L, configuration.getServiceUnavailableConfig().getMaxDelayMs());
    }

    /**
     * A multiplier of 1 is valid and kept. The breaker then always waits for the initial delay.
     */
    @Test
    public void testMultiplierOfOneIsAccepted() {
        Configuration configuration = new Configuration(mockConfiguration("3:20000:180000:1:1", "3:20000:180000:1.0:1", DEFAULT_FIXED));

        assertEquals(1.0, configuration.getSocketTimeoutConfig().getDelayMultiplier(), DELTA);
        assertEquals(1.0, configuration.getServiceUnavailableConfig().getDelayMultiplier(), DELTA);
    }

    /**
     * Very large values are capped: thresholds at {@link Configuration#MAX_THRESHOLD} and delays at
     * {@link Configuration#MAX_DELAY_MS}. A threshold larger than the int range is capped too. A delay larger than the long range
     * is not a valid number and uses the default.
     * <p>
     * Why: Failsafe reserves memory for each counted execution and converts delays to nanoseconds, so huge values used a lot of
     * memory or overflowed.
     */
    @Test
    public void testTooLargeOsgiValuesAreCapped() {
        String t = String.valueOf(Integer.MAX_VALUE);
        String d = String.valueOf(Long.MAX_VALUE);
        Configuration configuration = new Configuration(mockConfiguration(t + ":" + d + ":" + d + ":1.5:" + t,
            "99999999999:" + d + ":" + d + ":1.5:" + t, t + ":99999999999999999999:" + t));

        long maxDelay = Configuration.MAX_DELAY_MS;
        int maxThreshold = Configuration.MAX_THRESHOLD;
        assertSocketTimeout(configuration.getSocketTimeoutConfig(), maxThreshold, maxDelay, maxDelay, 1.5, maxThreshold);
        assertServiceUnavailable(configuration.getServiceUnavailableConfig(), maxThreshold, maxDelay, maxDelay, 1.5, maxThreshold);
        assertServerError(configuration.getServerErrorConfig(), maxThreshold, 10000L, maxThreshold);
    }

    /**
     * Regression test for an overflow: with a huge max delay and a multiplier of 1000, after 20 half-open transitions the delay
     * is capped at {@link Configuration#MAX_DELAY_MS} and can be converted to nanoseconds, as Failsafe does when the breaker
     * opens.
     */
    @Test
    public void testHugeMaxDelayWithLargeAttemptsStaysUsable() {
        Configuration configuration = new Configuration(mockConfiguration("1:1:" + Long.MAX_VALUE + ":1000:1", DEFAULT_PROGRESSIVE,
            DEFAULT_FIXED));
        SocketTimeoutPolicy policy = new SocketTimeoutPolicy(configuration.getSocketTimeoutConfig(), "test");
        for (int i = 0; i < 20; i++) {
            policy.onHalfOpen();
        }

        long delay = policy.calculateDelayMs();
        assertEquals(Configuration.MAX_DELAY_MS, delay);
        assertEquals(delay * 1_000_000L, java.time.Duration.ofMillis(delay).toNanos());
    }

    /**
     * A {@link CircuitBreakerService} can be created from an OSGi configuration. The client name used in the logs is
     * {@code 'identifier' (ranking N, url)}, and a breaker that the service did not create is reported as "unknown".
     * <p>
     * Why: several clients can have the same identifier and URL (for example the CIF add-on default client and a project
     * client with a higher ranking), the ranking tells them apart in the logs.
     */
    @Test
    public void testServiceCanBeCreatedFromOsgiConfiguration() {
        GraphqlClientConfiguration configuration = mockConfiguration("1:10:10:1:1", "1:10:10:1:1", "1:10:1");
        when(configuration.identifier()).thenReturn("default");
        when(configuration.url()).thenReturn("https://commerce.example.com/graphql");
        when(configuration.service_ranking()).thenReturn(5);

        CircuitBreakerService service = new CircuitBreakerService(configuration);

        assertEquals("'default' (ranking 5, https://commerce.example.com/graphql)", service.getClientName());
        assertEquals("unknown", service.getPolicyName(CircuitBreaker.ofDefaults()));
    }

    private static void assertDefault(Object expected, String attribute) throws Exception {
        assertEquals(attribute, expected, GraphqlClientConfiguration.class.getMethod(attribute).getDefaultValue());
    }

    private static GraphqlClientConfiguration mockConfiguration(String socketTimeout, String serviceUnavailable, String serverError) {
        GraphqlClientConfiguration configuration = mock(GraphqlClientConfiguration.class);
        when(configuration.circuitBreakerSocketTimeout()).thenReturn(socketTimeout);
        when(configuration.circuitBreakerServiceUnavailable()).thenReturn(serviceUnavailable);
        when(configuration.circuitBreakerServerError()).thenReturn(serverError);
        return configuration;
    }

    private static void assertDefaults(Configuration configuration) {
        assertSocketTimeout(configuration.getSocketTimeoutConfig(), 3, 20000L, 180000L, 1.5, 1);
        assertServiceUnavailable(configuration.getServiceUnavailableConfig(), 3, 20000L, 180000L, 1.5, 1);
        assertServerError(configuration.getServerErrorConfig(), 3, 10000L, 1);
    }

    private static void assertSocketTimeout(Configuration.SocketTimeoutConfig config, int threshold, long initialDelay, long maxDelay,
        double multiplier, int successThreshold) {
        assertEquals(threshold, config.getThreshold());
        assertEquals(initialDelay, config.getInitialDelayMs());
        assertEquals(maxDelay, config.getMaxDelayMs());
        assertEquals(multiplier, config.getDelayMultiplier(), DELTA);
        assertEquals(successThreshold, config.getSuccessThreshold());
    }

    private static void assertServiceUnavailable(Configuration.ServiceUnavailableConfig config, int threshold, long initialDelay,
        long maxDelay, double multiplier, int successThreshold) {
        assertEquals(threshold, config.getThreshold());
        assertEquals(initialDelay, config.getInitialDelayMs());
        assertEquals(maxDelay, config.getMaxDelayMs());
        assertEquals(multiplier, config.getDelayMultiplier(), DELTA);
        assertEquals(successThreshold, config.getSuccessThreshold());
    }

    private static void assertServerError(Configuration.ServerErrorConfig config, int threshold, long delay, int successThreshold) {
        assertEquals(threshold, config.getThreshold());
        assertEquals(delay, config.getDelayMs());
        assertEquals(successThreshold, config.getSuccessThreshold());
    }
}
