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

package com.adobe.cq.commerce.graphql.flush.services.impl;

import java.util.*;
import javax.jcr.Session;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.client.GraphqlClient;
import com.adobe.cq.commerce.graphql.flush.services.ConfigService;
import com.adobe.cq.commerce.graphql.flush.services.InvalidateCacheService;
import com.adobe.cq.commerce.graphql.flush.services.ServiceUserService;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.Replicator;

@Component(service = InvalidateCacheService.class, immediate = true)
public class InvalidateCacheImpl implements InvalidateCacheService {

    @Reference
    private ConfigService configService;

    @Reference
    private Replicator replicator;

    @Reference
    private ServiceUserService serviceUserService;

    private static final Logger LOGGER = LoggerFactory.getLogger(InvalidateCacheImpl.class);
    private Collection<ClientHolder> clients = new ArrayList<>();

    @Override
    public void invalidateCache(String path) {
        try (ResourceResolver resourceResolver = serviceUserService.getServiceUserResourceResolver(SERVICE_USER)) {

            Resource resource = resourceResolver.getResource(path);
            if (resource != null) {
                String graphqlClientId = resource.getValueMap().get("graphqlClientId", String.class);
                String cacheEntriesStr = resource.getValueMap().get("cacheEntries", String.class);
                String invalidateDate = resource.getValueMap().get("invalidateDate", String.class);
                String[] cacheEntries = cacheEntriesStr != null ? cacheEntriesStr.split(",") : null;

                LOGGER.info("Invalidating graphql client cache: {}", invalidateDate);
                GraphqlClient client = this.getClient(graphqlClientId);
                if (client != null) {
                    LOGGER.info("Invalidating graphql client cache with identifier: {}", graphqlClientId);
                    client.invalidateCache(cacheEntries);
                } else {
                    LOGGER.error("GraphqlClient with ID '{}' not found", graphqlClientId);
                }
            } else {
                LOGGER.error("Resource not found at path: {}", path);
            }
        } catch (LoginException e) {
            LOGGER.error("Error getting service user: {}", e.getMessage(), e);
        }
    }

    private GraphqlClient getClient(String graphqlClientId) {
        for (ClientHolder clientHolder : clients) {
            GraphqlClient graphqlClient = clientHolder.graphqlClient;
            Map<String, Object> properties = clientHolder.properties;
            String identifier = (String) properties.get("identifier");
            if (identifier.equals(graphqlClientId)) {
                return graphqlClient;
            }
        }
        return null;
    }

    @Override
    public void triggerCacheInvalidation(String graphqlClientId, String[] cacheEntries) {

        try (ResourceResolver resourceResolver = serviceUserService.getServiceUserResourceResolver(SERVICE_USER)) {

            if (configService.isAuthor()) {

                createInvalidateWorkingAreaIfNotExists(resourceResolver);

                Resource invalidateEntryResource = createInvalidateEntry(resourceResolver, graphqlClientId, cacheEntries);

                Session session = resourceResolver.adaptTo(Session.class);

                this.replicateToPublishInstances(session, invalidateEntryResource.getPath());

            }

        } catch (PersistenceException e) {
            LOGGER.error("Error during node creation: {}", e.getMessage(), e);
        } catch (ReplicationException e) {
            LOGGER.error("Error during node replication: {}", e.getMessage(), e);
        } catch (LoginException e) {
            LOGGER.error("Error getting service user: {}", e.getMessage(), e);
        }
    }

    private void replicateToPublishInstances(Session session, String path) throws ReplicationException {
        LOGGER.error("Replicate to publish instances");
        replicator.replicate(session, ReplicationActionType.ACTIVATE, path);
    }

    private void createInvalidateWorkingAreaIfNotExists(ResourceResolver resourceResolver) throws PersistenceException {

        // Check if the resource already exists
        Resource folderResource = resourceResolver.getResource(INVALIDATE_WORKING_AREA);

        if (folderResource == null) {

            // If folder doesn't exist, create it

            // Get the parent resource where you want to create the folder
            Resource varResource = resourceResolver.getResource("/var");

            if (varResource != null) {

                // Properties to define the folder
                Map<String, Object> folderProperties = new HashMap<>();
                folderProperties.put("jcr:primaryType", "sling:Folder");

                // Create the new folder
                resourceResolver.create(varResource, "cif", folderProperties);

                // Commit the changes
                resourceResolver.commit();
                LOGGER.info("Folder /var/cif created successfully.");
            } else {
                LOGGER.error("Parent /var does not exist.");
            }

        } else {
            LOGGER.info("Folder /var/cif already exists.");
        }
    }

    private Resource createInvalidateEntry(ResourceResolver resourceResolver, String graphqlClientId, String[] cacheEntries)
        throws PersistenceException {

        // Retrieve the parent resource where the invalidate_entry node will be created
        Resource invalidateWorkingArea = resourceResolver.getResource(INVALIDATE_WORKING_AREA);

        if (invalidateWorkingArea == null) {
            throw new IllegalStateException("Invalidate working area does not exist: " + INVALIDATE_WORKING_AREA);
        }

        Resource invalidateEntryResource = invalidateWorkingArea.getChild(NODE_NAME_BASE);

        // Prepare the properties for the new node
        Map<String, Object> nodeProperties = new HashMap<>();
        nodeProperties.put("jcr:primaryType", "nt:unstructured");
        nodeProperties.put(PROPERTY_NAME, Calendar.getInstance());
        nodeProperties.put("graphqlClientId", graphqlClientId);
        if (cacheEntries != null) {
            nodeProperties.put("cacheEntries", String.join(",", cacheEntries));
        }

        if (invalidateEntryResource != null) {
            // Update the properties
            ModifiableValueMap properties = invalidateEntryResource.adaptTo(ModifiableValueMap.class);
            if (properties != null) {
                properties.putAll(nodeProperties);
            } else {
                LOGGER.error("Failed to adapt child resource to ModifiableValueMap.");
            }
        } else {
            // Create the new node
            invalidateEntryResource = resourceResolver.create(invalidateWorkingArea, NODE_NAME_BASE, nodeProperties);
            LOGGER.error("Child resource not found at path: {}/{}", INVALIDATE_WORKING_AREA, NODE_NAME_BASE);
        }

        // Commit changes to persist the new node
        resourceResolver.commit();

        LOGGER.info("Node {} created successfully under " + INVALIDATE_WORKING_AREA, NODE_NAME_BASE);

        return invalidateEntryResource;
    }

    @Reference(
        service = GraphqlClient.class,
        bind = "bindGraphqlClient",
        unbind = "unbindGraphqlClient",
        cardinality = ReferenceCardinality.MULTIPLE,
        policy = ReferencePolicy.DYNAMIC)
    void bindGraphqlClient(GraphqlClient graphqlClient, Map<String, Object> properties) {
        clients.add(new ClientHolder(graphqlClient, properties));
    }

    void unbindGraphqlClient(GraphqlClient graphqlClient, Map<?, ?> properties) {
        clients.removeIf(holder -> holder.graphqlClient.equals(graphqlClient));
    }

    private static class ClientHolder {
        private final GraphqlClient graphqlClient;
        private final Map<String, Object> properties;

        ClientHolder(GraphqlClient graphqlClient, Map<String, Object> properties) {
            this.graphqlClient = graphqlClient;
            this.properties = properties;
        }
    }
}
