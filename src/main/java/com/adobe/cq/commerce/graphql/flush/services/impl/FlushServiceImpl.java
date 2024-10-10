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
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.client.GraphqlClient;
import com.adobe.cq.commerce.graphql.flush.services.ConfigService;
import com.adobe.cq.commerce.graphql.flush.services.FlushService;
import com.adobe.cq.commerce.graphql.flush.services.ServiceUserService;
import com.day.cq.replication.AgentConfig;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.ReplicationOptions;
import com.day.cq.replication.Replicator;

@Component(service = FlushService.class, immediate = true)
public class FlushServiceImpl implements FlushService {

    @Reference
    private ConfigService configService;

    @Reference
    private Replicator replicator;

    @Reference
    private ServiceUserService serviceUserService;

    private static final Logger LOGGER = LoggerFactory.getLogger(FlushServiceImpl.class);
    private Collection<ClientHolder> clients = new ArrayList<>();

    @Override
    public void flush(String path) {
        LOGGER.info("Flushing graphql client");
        try (ResourceResolver resourceResolver = serviceUserService.getServiceUserResourceResolver(SERVICE_USER)) {

            Resource resource = resourceResolver.getResource(path);
            if (resource != null) {
                String graphqlClientId = resource.getValueMap().get("graphqlClientId", String.class);
                String cacheEntriesStr = resource.getValueMap().get("cacheEntries", String.class);
                String[] cacheEntries = cacheEntriesStr != null ? cacheEntriesStr.split(",") : null;

                LOGGER.info("Flushing graphql client");
                GraphqlClient client = this.getClient(graphqlClientId);
                if (client != null) {
                    LOGGER.info("Flushing graphql client with identifier: {}", graphqlClientId);
                    client.flushCache(cacheEntries);
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
    public void triggerFlush(String graphqlClientId, String[] cacheEntries) {

        try (ResourceResolver resourceResolver = serviceUserService.getServiceUserResourceResolver(SERVICE_USER)) {

            if (configService.isAuthor()) {

                createFlushWorkingAreaIfNotExists(resourceResolver);

                Resource flushEntryResource = createFlushEntry(resourceResolver, graphqlClientId, cacheEntries);

                Session session = resourceResolver.adaptTo(Session.class);

                this.replicateToPublishInstances(session, flushEntryResource.getPath());

                this.replicateToAuthorInstances(session, flushEntryResource.getPath());

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
        replicator.replicate(session, ReplicationActionType.ACTIVATE, path);
    }

    private void replicateToAuthorInstances(Session session, String path) throws ReplicationException {
        ReplicationOptions replicationOptions = new ReplicationOptions();
        replicationOptions.setFilter(agent -> {
            AgentConfig config = agent.getConfiguration();
            return config.getTransportURI().contains("author");
        });

        replicator.replicate(session, ReplicationActionType.ACTIVATE, path, replicationOptions);
    }

    private void createFlushWorkingAreaIfNotExists(ResourceResolver resourceResolver) throws PersistenceException {

        // Check if the resource already exists
        Resource folderResource = resourceResolver.getResource(FLUSH_WORKING_AREA);

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

    private Resource createFlushEntry(ResourceResolver resourceResolver, String graphqlClientId, String[] cacheEntries)
        throws PersistenceException {

        // Retrieve the parent resource where the flush_entry node will be created
        Resource flushWorkingArea = resourceResolver.getResource(FLUSH_WORKING_AREA);

        if (flushWorkingArea == null) {
            throw new IllegalStateException("Flush working area does not exist: " + FLUSH_WORKING_AREA);
        }

        // Generate unique node name if flush_entry already exists
        String newNodeName = getUniqueNodeName(flushWorkingArea);

        // Prepare the properties for the new node
        Map<String, Object> nodeProperties = new HashMap<>();
        nodeProperties.put("jcr:primaryType", "nt:unstructured");
        nodeProperties.put("flushDate", Calendar.getInstance());
        nodeProperties.put("graphqlClientId", graphqlClientId);
        if (cacheEntries != null) {
            nodeProperties.put("cacheEntries", String.join(",", cacheEntries));
        }

        // Create the new node
        Resource flushEntryResource = resourceResolver.create(flushWorkingArea, newNodeName, nodeProperties);

        // Commit changes to persist the new node
        resourceResolver.commit();

        LOGGER.info("Node {} created successfully under " + FLUSH_WORKING_AREA, newNodeName);

        return flushEntryResource;
    }

    // Method to generate a unique node name by appending an incremental index if necessary
    private String getUniqueNodeName(Resource parentResource) {
        int index = 0;
        String nodeName = NODE_NAME_BASE;

        while (parentResource.getChild(nodeName) != null) {
            index++;
            nodeName = NODE_NAME_BASE + "_" + index;
        }

        return nodeName;
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
