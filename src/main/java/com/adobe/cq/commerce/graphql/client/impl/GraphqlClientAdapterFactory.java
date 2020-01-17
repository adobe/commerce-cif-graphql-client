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

import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.caconfig.resource.ConfigurationResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.client.GraphqlClient;
import com.adobe.xfa.ut.StringUtils;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;

@Component(
    service = { AdapterFactory.class },
    property = {
        AdapterFactory.ADAPTABLE_CLASSES + "=" + GraphqlClientAdapterFactory.RESOURCE_CLASS_NAME,
        AdapterFactory.ADAPTER_CLASSES + "=" + GraphqlClientAdapterFactory.GRAPHQLCLIENT_CLASS_NAME
    })
public class GraphqlClientAdapterFactory implements AdapterFactory {

    protected static final String RESOURCE_CLASS_NAME = "org.apache.sling.api.resource.Resource";
    protected static final String GRAPHQLCLIENT_CLASS_NAME = "com.adobe.cq.commerce.graphql.client.GraphqlClient";

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphqlClientAdapterFactory.class);

    protected Map<String, GraphqlClient> clients = new ConcurrentHashMap<>();

    @Reference
    private ConfigurationResourceResolver configurationResourceResolver;

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
        if (!(adaptable instanceof Resource) || type != GraphqlClient.class) {
            return null;
        }

        Resource res = (Resource) adaptable;

        // Get cq:graphqlClient property from ancestor pages
        Page page = res.getResourceResolver().adaptTo(PageManager.class).getContainingPage(res);
        Resource configs = configurationResourceResolver.getResource(page.getContentResource(), "settings", "commerce/default");

        ValueMap properties = configs.getValueMap();

        String identifier = properties.get(GraphqlClientConfiguration.CQ_GRAPHQL_CLIENT, "");
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
    }
}
