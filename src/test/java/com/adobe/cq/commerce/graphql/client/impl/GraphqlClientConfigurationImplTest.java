/*******************************************************************************
 *
 *    Copyright 2022 Adobe. All rights reserved.
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
package com.adobe.cq.commerce.graphql.client.impl;

import org.junit.Test;

import com.adobe.cq.commerce.graphql.client.GraphqlClientConfiguration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GraphqlClientConfigurationImplTest {

    @Test
    public void testReturnsAlwaysNonNullHeaders() {
        GraphqlClientConfiguration mockConfig = mock(GraphqlClientConfiguration.class);
        assertNotNull(new GraphqlClientConfigurationImpl(mockConfig).httpHeaders());
        when(mockConfig.httpHeaders()).thenReturn(new String[] { "foo: bar" });
        assertNotNull(new GraphqlClientConfigurationImpl(mockConfig).httpHeaders());
    }

    @Test
    public void testReturnsAlwaysNonNullCacheConfigurations() {
        GraphqlClientConfiguration mockConfig = mock(GraphqlClientConfiguration.class);
        assertNotNull(new GraphqlClientConfigurationImpl(mockConfig).cacheConfigurations());
        when(mockConfig.cacheConfigurations()).thenReturn(new String[] { "foo: bar" });
        assertNotNull(new GraphqlClientConfigurationImpl(mockConfig).cacheConfigurations());
    }

    /**
     * The copy constructor copies the 3 circuit breaker settings.
     * <p>
     * Why: {@link GraphqlClientImpl} works on a copy of the OSGi configuration. A setting missing in the copy would silently use
     * the default instead of the configured value.
     */
    @Test
    public void testCopiesCircuitBreakerSettings() {
        GraphqlClientConfiguration mockConfig = mock(GraphqlClientConfiguration.class);
        when(mockConfig.circuitBreakerSocketTimeout()).thenReturn("4:1000:2000:2.5:2");
        when(mockConfig.circuitBreakerServiceUnavailable()).thenReturn("5:3000:4000:1.25:3");
        when(mockConfig.circuitBreakerServerError()).thenReturn("6:5000:4");

        GraphqlClientConfigurationImpl copy = new GraphqlClientConfigurationImpl(mockConfig);

        assertEquals("4:1000:2000:2.5:2", copy.circuitBreakerSocketTimeout());
        assertEquals("5:3000:4000:1.25:3", copy.circuitBreakerServiceUnavailable());
        assertEquals("6:5000:4", copy.circuitBreakerServerError());
    }

    /**
     * A configuration created only from a URL (without OSGi) has the default circuit breaker values.
     */
    @Test
    public void testCircuitBreakerDefaults() {
        GraphqlClientConfigurationImpl config = new GraphqlClientConfigurationImpl("https://hostname/graphql");

        assertEquals("3:20000:180000:1.5:1", config.circuitBreakerSocketTimeout());
        assertEquals("3:20000:180000:1.5:1", config.circuitBreakerServiceUnavailable());
        assertEquals("3:10000:1", config.circuitBreakerServerError());
    }
}
