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

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.UUID;
import java.util.stream.StreamSupport;

import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import javax.jcr.query.RowIterator;

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
    private final Collection<ClientHolder> clients = new ArrayList<>();

    public static final String PARAMETER_GRAPHQL_CLIENT_ID = "graphqlClientId";
    public static final String PARAMETER_STORE_VIEW = "storeView";
    public static final String PARAMETER_TYPE = "type";
    public static final String PARAMETER_STORE_PATH = "storePath";
    public static final String PARAMETER_INVALID_CACHE_ENTRIES = "invalidCacheEntries";
    public static final String PARAMETER_ATTRIBUTE = "attribute";
    public static final String PARAMETER_LIST_OF_CACHE_TO_SEARCH = "listOfCacheToSearch";
    public static final String TYPE_SKU = "skus";
    public static final String TYPE_CATEGORY = "categories";
    public static final String TYPE_UUIDS = "uuids";
    public static final String TYPE_ClEAR_SPECIFIC_CACHE = "clearSpecificCache";
    public static final String TYPE_ATTRIBUTE = "attribute";
    public static final String TYPE_CLEAR_ALL = "clearAll";
    private static final int MINUTES_LIMIT = -5;
    private static final int NODE_CREATION_LIMIT = 100;

    // Define the JSON-like structure as a constant
    public static final Map<String, Map<String, List<String>>> PROPERTIES = createJsonData();

    // Define the JSON-like structure as a constant
    public static final List<String> REQUIRED_ATTRIBUTES = getInitialRequiredAttributes();

    @Override
    public void invalidateCache(String path) {
        try (ResourceResolver resourceResolver = serviceUserService.getServiceUserResourceResolver(SERVICE_USER)) {
            Resource resource = resourceResolver.getResource(path);
            if (resource != null) {
                String graphqlClientId = resource.getValueMap().get(PARAMETER_GRAPHQL_CLIENT_ID, String.class);
                String[] invalidCacheEntries = resource.getValueMap().get(PARAMETER_INVALID_CACHE_ENTRIES,
                    String[].class);
                String[] listOfCacheToSearch = resource.getValueMap().get(PARAMETER_LIST_OF_CACHE_TO_SEARCH,
                    String[].class);
                String storeView = resource.getValueMap().get(PARAMETER_STORE_VIEW, String.class);
                String type = resource.getValueMap().get(PARAMETER_TYPE, String.class);
                String attribute = resource.getValueMap().get(PARAMETER_ATTRIBUTE, String.class);
                String[] cachePatterns;

                GraphqlClient client = getClient(graphqlClientId);

                switch (Objects.requireNonNull(type)) {
                    case TYPE_SKU:
                        cachePatterns = getAttributePatterns(invalidCacheEntries, "sku");
                        client.invalidateCache(storeView, listOfCacheToSearch, cachePatterns);
                        break;
                    case TYPE_CATEGORY:
                    case TYPE_UUIDS:
                        cachePatterns = getAttributePatterns(invalidCacheEntries, "uuid");
                        client.invalidateCache(storeView, listOfCacheToSearch, cachePatterns);
                        break;
                    case TYPE_ATTRIBUTE:
                        cachePatterns = getAttributePatterns(invalidCacheEntries, attribute);
                        client.invalidateCache(storeView, listOfCacheToSearch, cachePatterns);
                    case TYPE_ClEAR_SPECIFIC_CACHE:
                        client.invalidateCache(storeView, invalidCacheEntries, null);
                        break;
                    case TYPE_CLEAR_ALL:
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

    public static void checksMandatoryFields(JsonObject jsonObject, String type, List<String> requiredFields) {
        Map<String, Map<String, List<String>>> properties = PROPERTIES;
        if (requiredFields == null) {
            requiredFields = properties.containsKey(type) && properties.get(type).containsKey("requiredFields")
                ? properties.get(type).get("requiredFields")
                : new ArrayList<>();
        }
        boolean flag = false;
        for (String field : requiredFields) {
            if (!jsonObject.has(field)) {
                throw new MissingArgumentException("Missing required parameter : " + field);
            } else {
                if (jsonObject.get(field).isJsonArray()) {
                    JsonArray jsonArray = jsonObject.getAsJsonArray(field);
                    flag = jsonArray.size() == 0;
                } else if (jsonObject.get(field).getAsString() == null || jsonObject.get(field).getAsString().isEmpty()) {
                    flag = true;
                }
            }
            if (flag) {
                throw new MissingArgumentException("Empty required parameter : " + field);
            }
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
        checksMandatoryFields(jsonObject, null, REQUIRED_ATTRIBUTES);
        String type = jsonObject.get(PARAMETER_TYPE).getAsString();
        // Check the required fields based on type
        checksMandatoryFields(jsonObject, type, null);
    }

    @Override
    public void triggerCacheInvalidation(JsonObject jsonObject) {
        try (ResourceResolver resourceResolver = serviceUserService.getServiceUserResourceResolver(SERVICE_USER)) {
            Session session = resourceResolver.adaptTo(Session.class);
            if (configService.isAuthor() && session != null) {

                checksMandatoryFieldsPresent(jsonObject);

                // checks the graphql client id exists
                String graphqlClientId = jsonObject.get(PARAMETER_GRAPHQL_CLIENT_ID).getAsString();
                getClient(graphqlClientId);

                createInvalidateWorkingAreaIfNotExists(resourceResolver);

                Resource invalidateEntryResource = createInvalidateEntry(session, resourceResolver, jsonObject);

                replicateToPublishInstances(session, invalidateEntryResource.getPath(), ReplicationActionType.ACTIVATE);

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

    private void replicateToPublishInstances(Session session, String path, ReplicationActionType replicationType)
        throws ReplicationException {
        LOGGER.error("Replicate to publish instances");
        replicator.replicate(session, replicationType, path);
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
        nodeProperties.put(PROPERTY_INVALID_DATE, Calendar.getInstance());
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

    private Resource createInvalidateEntry(Session session, ResourceResolver resourceResolver, JsonObject jsonObject)
        throws Exception {

        // Retrieve the parent resource where the invalidate_entry node will be created
        Resource invalidateWorkingArea = resourceResolver.getResource(INVALIDATE_WORKING_AREA);

        if (invalidateWorkingArea == null) {
            throw new IllegalStateException("Invalidate working area does not exist: " + INVALIDATE_WORKING_AREA);
        }

        // Generate a unique node name
        String nodeName = getUniqueNodeName(session);

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

    private String getNodesWithInvalidDatePassed(Session session, String dynamicPath) throws Exception {
        // Calculate the date and time 5 minutes ago
        Calendar fiveMinutesAgo = Calendar.getInstance();
        fiveMinutesAgo.add(Calendar.MINUTE, MINUTES_LIMIT);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        String fiveMinutesAgoStr = dateFormat.format(fiveMinutesAgo.getTime());

        // Construct the query
        String queryStr = "SELECT [jcr:path] FROM [nt:unstructured] AS node WHERE ISDESCENDANTNODE([" + dynamicPath + "])"
            + " AND [invalidateDate] < CAST('" + fiveMinutesAgoStr + "' AS DATE)";
        Query query = session.getWorkspace().getQueryManager().createQuery(queryStr, Query.JCR_SQL2);
        // Set the limit on the number of nodes to return
        query.setLimit(1);
        QueryResult result = query.execute();
        RowIterator rowIterator = result.getRows();
        if (rowIterator.hasNext()) {
            return rowIterator.nextRow().getPath();
        } else {
            return null; // No rows found
        }
    }

    private int getNodeCount(Session session, String dynamicPath) throws Exception {
        String countQuery = "SELECT [jcr:path] FROM [nt:unstructured] AS node WHERE ISDESCENDANTNODE([" + dynamicPath + "])";
        Query query = session.getWorkspace().getQueryManager().createQuery(countQuery, Query.JCR_SQL2);
        QueryResult result = query.execute();

        // Directly get the count of nodes
        return (int) result.getRows().getSize();
    }

    // Method to generate a unique node name by appending an UUID to the base name
    private String getUniqueNodeName(Session session) throws Exception {

        int nodeSize = getNodeCount(session, INVALIDATE_WORKING_AREA);
        String nodeName = NODE_NAME_BASE + "_" + UUID.randomUUID().toString();
        if (nodeSize <= NODE_CREATION_LIMIT) {
            String path = getNodesWithInvalidDatePassed(session, INVALIDATE_WORKING_AREA);
            if (path != null) {
                Node node = session.getNode(path);
                node.remove();
                session.save();
                // Replicate the removal to the publish instance
                replicateToPublishInstances(session, path, ReplicationActionType.DELETE);
            }
            if (nodeSize == NODE_CREATION_LIMIT && path == null) {
                throw new IllegalStateException("Number of request been reached. Please try again later.");
            }
        } else {
            throw new IllegalStateException("Number of request been reached. Please try again later.");
        }
        return nodeName;
    }

    private static String getRegexBasedOnAttribute(String attribute) {
        String regex;
        switch (attribute) {
            case "uuid":
                regex = "\"uid\"\\s*:\\s*\\{\"id\"\\s*:\\s*\"";
                break;
            default:
                regex = "\"" + attribute + "\":\\s*\"";
        }
        return regex;
    }

    private static String[] getAttributePatterns(String[] patterns, String attribute) {
        String attributeString = String.join("|", patterns);
        return new String[] { getRegexBasedOnAttribute(attribute) + "(" + attributeString + ")\"" };
    }

    private static Map<String, Map<String, List<String>>> createJsonData() {
        Map<String, Map<String, List<String>>> jsonData = new HashMap<>();

        // Add property for type "sku"
        Map<String, List<String>> skuProperties = new HashMap<>();
        skuProperties.put("requiredFields", new ArrayList<>(
            List.of(
                PARAMETER_INVALID_CACHE_ENTRIES)));
        jsonData.put(TYPE_SKU, skuProperties);

        // Add property for type "uuid"
        Map<String, List<String>> uuidProperties = new HashMap<>();
        uuidProperties.put("requiredFields", new ArrayList<>(
            List.of(
                PARAMETER_INVALID_CACHE_ENTRIES)));
        jsonData.put(TYPE_UUIDS, uuidProperties);

        // Add property for type "category"
        Map<String, List<String>> categoryProperties = new HashMap<>();
        categoryProperties.put("requiredFields", new ArrayList<>(
            List.of(
                PARAMETER_INVALID_CACHE_ENTRIES)));
        jsonData.put(TYPE_CATEGORY, categoryProperties);

        // Add property for type "attribute"
        Map<String, List<String>> attributeProperties = new HashMap<>();
        attributeProperties.put("requiredFields", new ArrayList<>(
            List.of(
                PARAMETER_INVALID_CACHE_ENTRIES,
                PARAMETER_ATTRIBUTE)));
        jsonData.put(TYPE_ATTRIBUTE, attributeProperties);

        // Add property for type "clearSpecificCache"
        Map<String, List<String>> clearSpecificCacheProperties = new HashMap<>();
        clearSpecificCacheProperties.put("requiredFields", new ArrayList<>(
            List.of(
                PARAMETER_INVALID_CACHE_ENTRIES)));
        jsonData.put(TYPE_ClEAR_SPECIFIC_CACHE, clearSpecificCacheProperties);

        return jsonData;
    }

    private static List<String> getInitialRequiredAttributes() {
        List<String> requiredFields = new ArrayList<>();
        requiredFields.add(PARAMETER_GRAPHQL_CLIENT_ID);
        requiredFields.add(PARAMETER_TYPE);
        return requiredFields;
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
