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
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.osgi.Order;
import org.apache.sling.commons.osgi.ServiceUtil;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.client.GraphqlClient;
import com.adobe.cq.commerce.graphql.client.GraphqlClientConfiguration;
import com.day.cq.commons.inherit.ComponentInheritanceValueMap;
import com.day.cq.commons.inherit.HierarchyNodeInheritanceValueMap;
import com.day.cq.commons.inherit.InheritanceValueMap;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;

@Component(
    service = { AdapterFactory.class },
    property = {
        AdapterFactory.ADAPTABLE_CLASSES + "=" + GraphqlClientAdapterFactory.RESOURCE_CLASS_NAME,
        AdapterFactory.ADAPTER_CLASSES + "=" + GraphqlClientAdapterFactory.GRAPHQLCLIENT_CLASS_NAME })
public class GraphqlClientAdapterFactory implements AdapterFactory {

    protected static final String RESOURCE_CLASS_NAME = "org.apache.sling.api.resource.Resource";
    protected static final String GRAPHQLCLIENT_CLASS_NAME = "com.adobe.cq.commerce.graphql.client.GraphqlClient";
    protected static final String CONFIG_NAME = "cloudconfigs/commerce";

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphqlClientAdapterFactory.class);

    protected NavigableSet<Holder> clients = new ConcurrentSkipListSet<>();
    protected Map<String, GraphqlClient> clientsByIdentifier = new ConcurrentHashMap<>();
    protected BundleContext bundleContext;

    @Reference(
        service = GraphqlClient.class,
        bind = "bindGraphqlClient",
        unbind = "unbindGraphqlClient",
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policy = ReferencePolicy.DYNAMIC)
    protected void bindGraphqlClient(GraphqlClient graphqlClient, Map<String, Object> properties) {
        String identifier = graphqlClient.getIdentifier();
        LOGGER.info("Registering GraphqlClient '{}'", identifier);
        clients.add(new Holder(graphqlClient, properties));
        clientsByIdentifier.remove(identifier);
    }

    protected void unbindGraphqlClient(GraphqlClient graphqlClient) {
        String identifier = graphqlClient.getIdentifier();
        LOGGER.info("De-registering GraphqlClient '{}'", identifier);
        clients.removeIf(holder -> holder.graphqlClient.equals(graphqlClient));
        clientsByIdentifier.remove(identifier);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <AdapterType> AdapterType getAdapter(Object adaptable, Class<AdapterType> type) {
        if (!(adaptable instanceof Resource) || type != GraphqlClient.class) {
            return null;
        }

        Resource res = (Resource) adaptable;
        ValueMap properties = res.getValueMap();

        String identifier = properties.get(GraphqlClientConfiguration.CQ_GRAPHQL_CLIENT, String.class);
        if (identifier == null) {
            identifier = readFallbackConfiguration(res);
        }

        if (StringUtils.isEmpty(identifier)) {
            LOGGER.error("Could not find {} property for given resource at {}", GraphqlClientConfiguration.CQ_GRAPHQL_CLIENT, res
                .getPath());
            return null;
        }

        GraphqlClient client = clientsByIdentifier.computeIfAbsent(identifier, key -> {
            for (Holder holder : clients) {
                if (holder.graphqlClient.getIdentifier().equals(key)) {
                    return holder.graphqlClient;
                }
            }
            return null;
        });

        if (client == null) {
            LOGGER.error("No GraphqlClient instance available for catalog identifier '{}'", identifier);
            return null;
        }

        return (AdapterType) client;
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

    private static class Holder implements Comparable<Holder> {

        private final GraphqlClient graphqlClient;
        private final Comparable<Object> comparable;

        public Holder(GraphqlClient graphqlClient, Map<String, Object> properties) {
            this.graphqlClient = graphqlClient;
            this.comparable = ServiceUtil.getComparableForServiceRanking(properties, Order.DESCENDING);
        }

        @Override
        public int compareTo(Holder o) {
            return comparable.compareTo(o.comparable);
        }
    }
}
