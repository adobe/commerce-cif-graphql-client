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
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;

import com.adobe.cq.commerce.graphql.client.GraphqlClient;
import com.google.common.collect.ImmutableMap;
import io.wcm.testing.mock.aem.junit.AemContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GraphqlClientAdapterFactoryTest {

    @Rule
    public final AemContext context = GraphqlAemContext.createContext(ImmutableMap.of(
        "/content", "/context/graphql-client-adapter-factory-context.json"));

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
        GraphqlClient client = context.getService(GraphqlClient.class);
        GraphqlAemContext.adapterFactory.unbindGraphqlClient(client);
        Assert.assertEquals(0, GraphqlAemContext.adapterFactory.clients.size());

        // Get page which has the catalog identifier in its jcr:content node
        Resource res = context.resourceResolver().getResource("/content/pageA");

        // Adapt page to client, verify that no client can be returned
        client = res.adaptTo(GraphqlClient.class);
        Assert.assertNull(client);
    }

    @Test
    public void testMultipleClientsWithServiceRanking() {
        GraphqlClient additionalClient = mock(GraphqlClient.class);
        when(additionalClient.getIdentifier()).thenReturn(GraphqlAemContext.CATALOG_IDENTIFIER);

        // add a client with a high ranking that should not be used / never returned
        GraphqlClient unusedClient = mock(GraphqlClient.class);
        when(unusedClient.getIdentifier()).thenReturn("unused");
        context.registerService(GraphqlClient.class, unusedClient, Constants.SERVICE_RANKING, 1000);

        context.registerService(GraphqlClient.class, additionalClient, Constants.SERVICE_RANKING, -10);

        // should get the default gql client (service ranking = 0)
        GraphqlClient client = context.resourceResolver().getResource("/content/pageA").adaptTo(GraphqlClient.class);
        assertNotEquals(additionalClient, client);

        context.registerService(GraphqlClient.class, additionalClient, Constants.SERVICE_RANKING, 10);

        // should get the additional gql client (service ranking = 10)
        client = context.resourceResolver().getResource("/content/pageA").adaptTo(GraphqlClient.class);
        assertEquals(additionalClient, client);
    }

    @Test
    public void testErrorCases() throws Exception {
        GraphqlClientImpl graphqlClient = new GraphqlClientImpl();
        MockGraphqlClientConfiguration configuration = new MockGraphqlClientConfiguration();
        configuration.setIdentifier("default");
        graphqlClient.activate(configuration, mock(BundleContext.class));

        GraphqlClientAdapterFactory factory = new GraphqlClientAdapterFactory();
        factory.bindGraphqlClient(graphqlClient, ImmutableMap.of(
            Constants.SERVICE_ID, 999L,
            GraphqlClientImpl.PROP_IDENTIFIER, "default"));

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
