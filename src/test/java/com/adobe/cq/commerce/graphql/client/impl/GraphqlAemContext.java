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

import java.lang.reflect.Method;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.serviceusermapping.ServiceUserMapped;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import com.adobe.cq.commerce.graphql.client.GraphqlClient;
import com.google.common.collect.ImmutableMap;
import io.wcm.testing.mock.aem.junit.AemContext;
import io.wcm.testing.mock.aem.junit.AemContextBuilder;

import static org.apache.sling.testing.mock.caconfig.ContextPlugins.CACONFIG;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class GraphqlAemContext {

    public final static String CATALOG_IDENTIFIER = "my-catalog";

    public static GraphqlClientAdapterFactory adapterFactory;

    private GraphqlAemContext() {}

    public static AemContext createContext(Map<String, String> contentPaths) {
        GraphqlAemContext.adapterFactory = new GraphqlClientAdapterFactory();

        AemContext ctx = new AemContextBuilder(ResourceResolverType.JCR_MOCK).plugin(CACONFIG)
            .beforeSetUp(context -> {
                ConfigurationAdmin configurationAdmin = context.getService(ConfigurationAdmin.class);
                Configuration serviceConfiguration = configurationAdmin.getConfiguration(
                    "org.apache.sling.caconfig.resource.impl.def.DefaultContextPathStrategy");

                Dictionary<String, Object> props = new Hashtable<>();
                props.put("configRefResourceNames", new String[] { ".", "jcr:content" });
                props.put("configRefPropertyNames", "cq:conf");
                serviceConfiguration.update(props);

                serviceConfiguration = configurationAdmin.getConfiguration(
                    "org.apache.sling.caconfig.resource.impl.def.DefaultConfigurationResourceResolvingStrategy");
                props = new Hashtable<>();
                props.put("configPath", "/conf");
                serviceConfiguration.update(props);

                serviceConfiguration = configurationAdmin.getConfiguration("org.apache.sling.caconfig.impl.ConfigurationResolverImpl");
                props = new Hashtable<>();
                props.put("configBucketNames", new String[] { "settings" });
                serviceConfiguration.update(props);
            }).build();

        // Load page structure
        contentPaths.entrySet().iterator().forEachRemaining(entry -> {
            ctx.load().json(entry.getValue(), entry.getKey());
        });

        ServiceUserMapped serviceUserMapped = Mockito.mock(ServiceUserMapped.class);
        ctx.registerService(ServiceUserMapped.class, serviceUserMapped, ImmutableMap.of(ServiceUserMapped.SUBSERVICENAME,
            "cifconfig-reader-service"));

        GraphqlClient mockClient = mock(GraphqlClient.class);
        when(mockClient.getIdentifier()).thenReturn(CATALOG_IDENTIFIER);
        ctx.registerService(GraphqlClient.class, mockClient);

        ResourceResolverFactory mockResolverFactory = mockResourceResolverFactory(ctx);
        try {
            FieldUtils.writeField(GraphqlAemContext.adapterFactory, "resolverFactory", mockResolverFactory, true);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        // Add AdapterFactory
        ctx.registerInjectActivateService(GraphqlAemContext.adapterFactory);

        return ctx;
    }

    /**
     * Builds a mock {@link ResourceResolverFactory}. Since the AEM Mocks library doesn't seem to work nice with this we need to build
     * a mock factory which returns a mock resolver.
     * Our implementation closes the resource resolver, so the factory will return a mock {@link ResourceResolver} which delegates all the
     * method calls to the one from the context, except for the {@link ResourceResolver#close()} method.
     * 
     * @param ctx an {@link AemContext object}
     * @return a mock {@link ResourceResolverFactory}
     */

    public static ResourceResolverFactory mockResourceResolverFactory(AemContext ctx) {
        try {
            ResourceResolverFactory mockResolverFactory = Mockito.mock(ResourceResolverFactory.class);
            ResourceResolver mockServiceResolver = mock(ResourceResolver.class, (Answer) invocation -> {
                Method theMethod = invocation.getMethod();
                if (!theMethod.getName().equals("close")) {
                    return theMethod.invoke(ctx.resourceResolver(), invocation.getArguments());
                }
                return null;
            });
            when(mockResolverFactory.getServiceResourceResolver(Mockito.anyMapOf(String.class, Object.class)))
                .thenReturn(mockServiceResolver);

            return mockResolverFactory;
        } catch (LoginException e) {
            e.printStackTrace();
        }

        return null;
    }

}
