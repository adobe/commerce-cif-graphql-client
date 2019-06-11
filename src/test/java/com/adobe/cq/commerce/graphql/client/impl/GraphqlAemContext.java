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

import org.apache.sling.testing.mock.sling.ResourceResolverType;

import com.adobe.cq.commerce.graphql.client.GraphqlClient;
import io.wcm.testing.mock.aem.junit.AemContext;
import io.wcm.testing.mock.aem.junit.AemContextCallback;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class GraphqlAemContext {

    public final static String CATALOG_IDENTIFIER = "my-catalog";

    public static GraphqlClientAdapterFactory adapterFactory;

    private GraphqlAemContext() {}

    public static AemContext createContext(String contentPath) {
        GraphqlAemContext.adapterFactory = new GraphqlClientAdapterFactory();
        return new AemContext(
            (AemContextCallback) context -> {
                // Add mock GraphqlClient
                GraphqlClient mockClient = mock(GraphqlClient.class);
                when(mockClient.getIdentifier()).thenReturn(CATALOG_IDENTIFIER);
                context.registerService(GraphqlClient.class, mockClient);

                // Add AdapterFactory
                context.registerInjectActivateService(GraphqlAemContext.adapterFactory);

                // Load page structure
                context.load().json(contentPath, "/content");
            },
            ResourceResolverType.JCR_MOCK);
    }

}
