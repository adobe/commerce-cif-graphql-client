/*******************************************************************************
 *
 *    Copyright 2019 Adobe. All rights reserved.
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

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.adobe.cq.commerce.graphql.client.GraphqlClient;
import io.wcm.testing.mock.aem.junit.AemContext;
import io.wcm.testing.mock.aem.junit.AemContextBuilder;

import static org.apache.sling.testing.mock.caconfig.ContextPlugins.CACONFIG;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GraphqlClientAdapterFactoryTest {

    public final static String CATALOG_IDENTIFIER = "my-catalog";

    @Rule
    public final AemContext context = new AemContextBuilder(ResourceResolverType.JCR_MOCK).plugin(CACONFIG).build();

    Resource mockConfigurationResource;

    @Before
    public void setUp() {
        GraphqlAemContext.adapterFactory = new GraphqlClientAdapterFactory();

        GraphqlClient mockClient = mock(GraphqlClient.class);
        when(mockClient.getIdentifier()).thenReturn(CATALOG_IDENTIFIER);
        context.registerService(GraphqlClient.class, mockClient);

        // Add AdapterFactory
        context.registerInjectActivateService(GraphqlAemContext.adapterFactory);

        // Load page structure
        context.load()
            .json("/context/graphql-client-adapter-factory-context.json", "/content");
    }

    @Test
    public void testGetClientForPageWithIdentifier() {
        // Get page which has the catalog identifier in its jcr:content node
        Resource res = context.resourceResolver().getResource("/content/pageA");

        // Adapt page to client, verify that correct client was returned
        GraphqlClient client = res.adaptTo(GraphqlClient.class);
        Assert.assertNotNull(client);
        Assert.assertEquals(GraphqlAemContext.CATALOG_IDENTIFIER, client.getIdentifier());
    }

    @Test
    public void testGetClientForPageWithInheritedIdentifier() {
        // Get page whose parent has the catalog identifier in its jcr:content node
        Resource res = context.resourceResolver().getResource("/content/pageB/pageC");

        // Adapt page to client, verify that correct client was returned
        GraphqlClient client = res.adaptTo(GraphqlClient.class);
        Assert.assertNotNull(client);
        Assert.assertEquals(GraphqlAemContext.CATALOG_IDENTIFIER, client.getIdentifier());
    }

    @Test
    public void testReturnNullForPageWithoutIdentifier() {
        // Get page whose parent has the catalog identifier in its jcr:content node
        Resource res = context.resourceResolver().getResource("/content/pageD");

        // Adapt page to client, verify that no client can be returned
        GraphqlClient client = res.adaptTo(GraphqlClient.class);
        Assert.assertNull(client);
    }

    @Test
    public void testReturnNullForNotExistingResolver() {
        // Remove mockClient from resolver
        GraphqlClient mockClient = mock(GraphqlClient.class);
        when(mockClient.getIdentifier()).thenReturn(GraphqlAemContext.CATALOG_IDENTIFIER);
        GraphqlAemContext.adapterFactory.unbindGraphqlClient(mockClient, null);
        Assert.assertEquals(0, GraphqlAemContext.adapterFactory.clients.size());

        // Get page which has the catalog identifier in its jcr:content node
        Resource res = context.resourceResolver().getResource("/content/pageA");

        // Adapt page to client, verify that no client can be returned
        GraphqlClient client = res.adaptTo(GraphqlClient.class);
        Assert.assertNull(client);
    }

    @Test
    public void testErrorCases() throws Exception {
        GraphqlClientImpl graphqlClient = new GraphqlClientImpl();
        graphqlClient.activate(new MockGraphqlClientConfiguration());

        GraphqlClientAdapterFactory factory = new GraphqlClientAdapterFactory();
        factory.bindGraphqlClient(graphqlClient, null);

        // Ensure that adapter returns null if not adapted from a resource
        Object target = factory.getAdapter(factory, Object.class);
        Assert.assertNull(target);

        // Ensure that adapter returns null if not adapting to a GraphQL client
        Resource res = context.resourceResolver().getResource("/content/test");
        target = factory.getAdapter(res, Object.class);
        Assert.assertNull(target);

        // Ensure it works in the right case
        target = factory.getAdapter(res, GraphqlClient.class);
        Assert.assertNotNull(target);
    }
}
