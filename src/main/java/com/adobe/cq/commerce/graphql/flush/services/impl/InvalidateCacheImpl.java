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
import java.util.stream.StreamSupport;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
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
import com.adobe.cq.commerce.graphql.flush.services.InvalidateCacheService;
import com.adobe.cq.commerce.graphql.flush.services.ServiceUserService;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.Replicator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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

    private static final long MINUTES_LIMIT_IN_MILLIS = 5 * 60 * 1000;
    private static final int NODE_CREATION_LIMIT = 10;

    @Override
    public void invalidateCache(String path) {
        try (ResourceResolver resourceResolver = serviceUserService.getServiceUserResourceResolver(SERVICE_USER)) {
            Resource resource = resourceResolver.getResource(path);
            if (resource != null) {
                String graphqlClientId = resource.getValueMap().get(InvalidateCacheSupport.PARAMETER_GRAPHQL_CLIENT_ID, String.class);
                String[] invalidCacheEntries = resource.getValueMap().get(InvalidateCacheSupport.PARAMETER_INVALID_CACHE_ENTRIES,
                    String[].class);
                String[] listOfCacheToSearch = resource.getValueMap().get(InvalidateCacheSupport.PARAMETER_LIST_OF_CACHE_TO_SEARCH,
                    String[].class);
                String storeView = resource.getValueMap().get(InvalidateCacheSupport.PARAMETER_STORE_VIEW, String.class);
                String type = resource.getValueMap().get(InvalidateCacheSupport.PARAMETER_TYPE, String.class);
                String attribute = resource.getValueMap().get(InvalidateCacheSupport.PARAMETER_ATTRIBUTE, String.class);
                String[] cachePatterns;

                GraphqlClient client = getClient(graphqlClientId);

                switch (Objects.requireNonNull(type)) {
                    case InvalidateCacheSupport.TYPE_SKU:
                        cachePatterns = InvalidateCacheSupport.getProductAttributePatterns(invalidCacheEntries, "sku");
                        client.invalidateCache(storeView, listOfCacheToSearch, cachePatterns);
                        break;
                    case InvalidateCacheSupport.TYPE_UUIDS:
                        cachePatterns = InvalidateCacheSupport.getProductAttributePatterns(invalidCacheEntries, "uuid");
                        client.invalidateCache(storeView, listOfCacheToSearch, cachePatterns);
                        break;
                    case InvalidateCacheSupport.TYPE_ATTRIBUTE:
                        cachePatterns = InvalidateCacheSupport.getProductAttributePatterns(invalidCacheEntries, attribute);
                        client.invalidateCache(storeView, listOfCacheToSearch, cachePatterns);
                    case InvalidateCacheSupport.TYPE_ClEAR_SPECIFIC_CACHE:
                        client.invalidateCache(storeView, invalidCacheEntries, null);
                        break;
                    case InvalidateCacheSupport.TYPE_CLEAR_ALL:
                        client.invalidateCache(storeView, null, null);
                        break;
                    default:
                        LOGGER.warn("Unknown cache type: {}", type);
                        throw new IllegalStateException("Unknown cache type" + type);
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
        throw new IllegalStateException("GraphqlClient with ID '" + graphqlClientId + "' not found");
    }

    private void checksMandatoryFieldsPresent(JsonObject jsonObject) throws Exception {

        // Check the required fields are present
        InvalidateCacheSupport.checksMandatoryFields(jsonObject, null, InvalidateCacheSupport.REQUIRED_ATTRIBUTES);
        String type = jsonObject.get(InvalidateCacheSupport.PARAMETER_TYPE).getAsString();
        // Check the required fields based on type
        InvalidateCacheSupport.checksMandatoryFields(jsonObject, type, null);
    }

    @Override
    public void triggerCacheInvalidation(JsonObject jsonObject) {
        try (ResourceResolver resourceResolver = serviceUserService.getServiceUserResourceResolver(SERVICE_USER)) {
            if (configService.isAuthor()) {

                checksMandatoryFieldsPresent(jsonObject);

                // checks the graphql client id exists
                String graphqlClientId = jsonObject.get(InvalidateCacheSupport.PARAMETER_GRAPHQL_CLIENT_ID).getAsString();
                getClient(graphqlClientId);

                createInvalidateWorkingAreaIfNotExists(resourceResolver);

                Resource invalidateEntryResource = createInvalidateEntry(resourceResolver, jsonObject);

                Session session = resourceResolver.adaptTo(Session.class);

                this.replicateToPublishInstances(session, invalidateEntryResource.getPath());

            } else {
                throw new Exception("Operation is only supported for author");
            }

        } catch (PersistenceException e) {
            LOGGER.error("Error during node creation: {}", e.getMessage(), e);
            throw new RuntimeException("Error during node creation");
        } catch (ReplicationException e) {
            LOGGER.error("Error during node replication: {}", e.getMessage(), e);
            throw new RuntimeException("Error during node replication");
        } catch (LoginException e) {
            LOGGER.error("Error getting service user: {}", e.getMessage(), e);
            throw new RuntimeException("Error getting service user");
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    private Map<String, Object> getNodeProperties(JsonObject jsonObject) {
        Map<String, Object> nodeProperties = new HashMap<>();
        nodeProperties.put("jcr:primaryType", "nt:unstructured");
        nodeProperties.put(PROPERTY_NAME, Calendar.getInstance());
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            JsonElement value = entry.getValue();
            if (value != null) {
                if (value.isJsonArray()) {
                    JsonArray jsonArray = value.getAsJsonArray();
                    String[] values = StreamSupport.stream(jsonArray.spliterator(), false)
                        .map(JsonElement::getAsString)
                        .toArray(String[]::new);
                    nodeProperties.put(entry.getKey(), values);
                } else {
                    nodeProperties.put(entry.getKey(), value.getAsString());
                }
            }
        }
        return nodeProperties;
    }

    private Resource createInvalidateEntry(ResourceResolver resourceResolver, JsonObject jsonObject)
        throws PersistenceException, RepositoryException {

        // Retrieve the parent resource where the invalidate_entry node will be created
        Resource invalidateWorkingArea = resourceResolver.getResource(INVALIDATE_WORKING_AREA);

        if (invalidateWorkingArea == null) {
            throw new IllegalStateException("Invalidate working area does not exist: " + INVALIDATE_WORKING_AREA);
        }

        // Generate a unique node name
        String nodeName = getUniqueNodeName(resourceResolver, invalidateWorkingArea);

        if (nodeName == null) {
            throw new IllegalStateException("Number of request been reached. Please try again later.");
        }

        // Prepare the properties for the new node
        Map<String, Object> nodeProperties = getNodeProperties(jsonObject);

        // Create the new node
        Resource invalidateEntryResource = resourceResolver.create(invalidateWorkingArea, nodeName, nodeProperties);

        // Commit changes to persist the new node
        resourceResolver.commit();

        LOGGER.info("Node {} created successfully under " + INVALIDATE_WORKING_AREA, NODE_NAME_BASE);

        return invalidateEntryResource;
    }

    // Method to generate a unique node name by appending an incremental index if necessary
    private String getUniqueNodeName(ResourceResolver resourceResolver, Resource parentResource) throws RepositoryException,
        PersistenceException {
        int index = 0;
        String nodeName = NODE_NAME_BASE;
        boolean doLoopFlag = true;

        while (doLoopFlag) {
            Resource invalidateEntryResource = parentResource.getChild(nodeName);
            if (invalidateEntryResource == null) {
                return nodeName;
            } else {
                Node node = invalidateEntryResource.adaptTo(Node.class);
                if (node != null) {
                    Calendar created = node.getProperty("invalidateDate").getDate();
                    long nodeAge = Calendar.getInstance().getTimeInMillis() - created.getTimeInMillis();
                    if (nodeAge > MINUTES_LIMIT_IN_MILLIS) {
                        node.remove();
                        resourceResolver.commit();
                        return nodeName;
                    }
                }
                if (index >= NODE_CREATION_LIMIT) {
                    doLoopFlag = false;
                }
                index++;
                nodeName = NODE_NAME_BASE + "_" + index;
            }
        }
        return null;
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
