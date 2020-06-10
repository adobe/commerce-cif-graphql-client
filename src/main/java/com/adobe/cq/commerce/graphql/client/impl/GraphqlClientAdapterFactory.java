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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.caconfig.ConfigurationBuilder;
import org.apache.sling.serviceusermapping.ServiceUserMapped;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.client.GraphqlClient;
import com.day.cq.commons.inherit.ComponentInheritanceValueMap;
import com.day.cq.commons.inherit.HierarchyNodeInheritanceValueMap;
import com.day.cq.commons.inherit.InheritanceValueMap;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.google.common.collect.ImmutableMap;

@Component(
    service = { AdapterFactory.class },
    property = {
        AdapterFactory.ADAPTABLE_CLASSES + "=" + GraphqlClientAdapterFactory.RESOURCE_CLASS_NAME,
        AdapterFactory.ADAPTER_CLASSES + "=" + GraphqlClientAdapterFactory.GRAPHQLCLIENT_CLASS_NAME })
public class GraphqlClientAdapterFactory implements AdapterFactory {

    private static final String CONFIGURATION_SUBSERVICE = "graphql-client-configuration";
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphqlClientAdapterFactory.class);

    protected static final String RESOURCE_CLASS_NAME = "org.apache.sling.api.resource.Resource";

    protected static final String GRAPHQLCLIENT_CLASS_NAME = "com.adobe.cq.commerce.graphql.client.GraphqlClient";

    protected static final String CONFIG_NAME = "cloudconfigs/commerce";

    protected Map<String, GraphqlClient> clients = new ConcurrentHashMap<>();

    @Reference
    private ServiceUserMapped serviceUserMapped;

    @Reference
    ResourceResolverFactory resolverFactory;

    Map<String, Object> authInfo = ImmutableMap.of(ResourceResolverFactory.SUBSERVICE, CONFIGURATION_SUBSERVICE);
    private ResourceResolver serviceResolver;

    @Reference(
        service = GraphqlClient.class,
        bind = "bindGraphqlClient",
        unbind = "unbindGraphqlClient",
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policy = ReferencePolicy.DYNAMIC)
    protected void bindGraphqlClient(GraphqlClient graphqlClient, Map<?, ?> properties) {
        String identifier = graphqlClient.getIdentifier();
        LOGGER.info("Registering GraphqlClient '{}'", identifier);
        clients.put(identifier, graphqlClient);
    }

    protected void unbindGraphqlClient(GraphqlClient graphqlClient, Map<?, ?> properties) {
        String identifier = graphqlClient.getIdentifier();
        LOGGER.info("De-registering GraphqlClient '{}'", identifier);
        clients.remove(identifier);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <AdapterType> AdapterType getAdapter(Object adaptable, Class<AdapterType> type) {
        LOGGER.debug("Try to get a new graphql client from a resource");
        if (!(adaptable instanceof Resource) || type != GraphqlClient.class) {
            return null;
        }

        try (ResourceResolver serviceResolver = resolverFactory.getServiceResourceResolver(authInfo)) {
            Resource res = serviceResolver.getResource(((Resource) adaptable).getPath());
            LOGGER.debug("Looking for a context configuration for the resource at {}", res.getPath());
            ConfigurationBuilder configBuilder = res.adaptTo(ConfigurationBuilder.class);
            ValueMap properties = configBuilder.name(CONFIG_NAME).asValueMap();

            String identifier = properties.get(GraphqlClientConfiguration.CQ_GRAPHQL_CLIENT, String.class);
            if (identifier == null) {
                LOGGER.debug("No {} property found in the context configuration, falling back to reading it from the page",
                    GraphqlClientConfiguration.CQ_GRAPHQL_CLIENT);
                identifier = readFallbackConfiguration(res);
            }

            if (StringUtils.isEmpty(identifier)) {
                LOGGER.error("Could not find {} property for given resource at {}", GraphqlClientConfiguration.CQ_GRAPHQL_CLIENT, res
                    .getPath());
                return null;
            }

            GraphqlClient client = clients.get(identifier);
            if (client == null) {
                LOGGER.error("No GraphqlClient instance available for catalog identifier '{}'", identifier);
                return null;
            }

            return (AdapterType) client;
        } catch (LoginException e) {
            LOGGER.error(e.getMessage(), e);
            return null;
        }
    }

    private String readFallbackConfiguration(Resource resource) {
        InheritanceValueMap properties;
        Page page = resource.getResourceResolver()
            .adaptTo(PageManager.class)
            .getContainingPage(resource);
        if (page != null) {
            properties = new HierarchyNodeInheritanceValueMap(page.getContentResource());
        } else {
            properties = new ComponentInheritanceValueMap(resource);
        }
        return properties.getInherited(GraphqlClientConfiguration.CQ_GRAPHQL_CLIENT, String.class);
    }

}
