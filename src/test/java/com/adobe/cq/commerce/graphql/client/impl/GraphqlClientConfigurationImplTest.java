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
}
