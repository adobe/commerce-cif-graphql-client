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

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.client.GraphqlClient;
import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.graphql.flush.services.ConfigService;
import com.adobe.cq.commerce.graphql.flush.services.InvalidateDispatcherService;
import com.adobe.cq.commerce.graphql.flush.services.ServiceUserService;
import com.google.gson.reflect.TypeToken;

@Component(service = InvalidateDispatcherService.class, immediate = true)
public class InvalidateDispatcherCacheImpl implements InvalidateDispatcherService {

    @Reference
    private ConfigService configService;

    @Reference
    private ServiceUserService serviceUserService;

    private static final Logger LOGGER = LoggerFactory.getLogger(InvalidateDispatcherCacheImpl.class);

    private final Collection<ClientHolder> clients = new ArrayList<>();

    @Override
    public void invalidateCache(String path) {
        if (!configService.isAuthor()) {
            LOGGER.error("Operation is only supported for author");
            return;
        }
        try (ResourceResolver resourceResolver = serviceUserService.getServiceUserResourceResolver(SERVICE_USER)) {
            Resource resource = resourceResolver.getResource(path);
            Session session = resourceResolver.adaptTo(Session.class);
            if (resource != null && session != null) {
                ValueMap properties = resource.getValueMap();
                String storePath = getPropertiesValue(properties, InvalidateCacheImpl.PARAMETER_STORE_PATH, String.class);
                if (isValid(properties, resourceResolver, storePath)) {

                    String graphqlClientId = getPropertiesValue(properties, InvalidateCacheImpl.PARAMETER_GRAPHQL_CLIENT_ID, String.class);
                    String[] invalidCacheEntries = getPropertiesValue(properties, InvalidateCacheImpl.PARAMETER_INVALID_CACHE_ENTRIES,
                        String[].class);
                    String type = getPropertiesValue(properties, InvalidateCacheImpl.PARAMETER_TYPE, String.class);
                    GraphqlClient client = getClient(graphqlClientId);
                    String dataString = formatList(invalidCacheEntries, ", ", "\"%s\"");

                    String[] invalidateDispatcherPagePaths = new String[0];
                    String[] correspondingPaths = new String[0];

                    if (type.equals(InvalidateCacheImpl.TYPE_SKU)) {
                        invalidateDispatcherPagePaths = getCorrespondingProductsPageBasedOnSku(session, storePath, invalidCacheEntries);

                        String query = generateSkuQuery(dataString);
                        Map<String, Object> data = getGraphqlResponseData(client, query);
                        if (data != null && data.get("products") != null) {
                            correspondingPaths = getSkuBasedInvalidPaths(resourceResolver, data, storePath);
                        }
                    } else if (type.equals(InvalidateCacheImpl.TYPE_CATEGORY)) {
                        invalidateDispatcherPagePaths = getCorrespondingCategoryPageBasedOnUid(session, storePath, invalidCacheEntries);
                        String query = generateCategoryQuery(dataString);
                        Map<String, Object> data = getGraphqlResponseData(client, query);
                        if (data != null && data.get("categoryList") != null) {
                            correspondingPaths = getCategoryBasedInvalidPaths(resourceResolver, data, storePath);
                        }
                    }
                    String[] allPaths = Stream.concat(Arrays.stream(invalidateDispatcherPagePaths), Arrays.stream(correspondingPaths))
                        .toArray(String[]::new);

                    for (String dispatcherPath : allPaths) {
                        flushCache(dispatcherPath);
                    }
                }
            } else {
                LOGGER.error("Resource not found at path: {}", path);
            }
        } catch (LoginException e) {
            LOGGER.error("Error getting service user: {}", e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String[] getSkuBasedInvalidPaths(ResourceResolver resourceResolver, Map<String, Object> data, String storePath)
        throws RepositoryException {
        // Create Type objects for the generic types T and U
        Set<String> uniquePagePaths = new HashSet<>();
        String categoryPath = getCorrespondingPagePath(resourceResolver, storePath, "cq:cifCategoryPage");
        String productPath = getCorrespondingPagePath(resourceResolver, storePath, "cq:cifProductPage");
        String productPageUrlFormat = getPageFormatUrl(resourceResolver, storePath, "productPageUrlFormat");
        String categoryPageUrlFormat = getPageFormatUrl(resourceResolver, storePath, "categoryPageUrlFormat");

        int productPageIndex = productPageUrlFormat.lastIndexOf(".html");
        productPageUrlFormat = (productPageIndex != -1) ? productPageUrlFormat.substring(0, productPageIndex + 5) : productPageUrlFormat;

        int categoryPageIndex = categoryPageUrlFormat.lastIndexOf(".html");
        categoryPageUrlFormat = (categoryPageIndex != -1) ? categoryPageUrlFormat.substring(0, categoryPageIndex + 5)
            : categoryPageUrlFormat;

        Map<String, Object> products = (Map<String, Object>) data.get("products");
        List<Map<String, Object>> items = (List<Map<String, Object>>) products.get("items");
        for (Map<String, Object> item : items) {
            String productUrlPath = productPageUrlFormat;
            String categoryUrlPath = categoryPageUrlFormat;
            String sku = (String) item.get("sku");
            String urlKey = (String) item.get("url_key");
            productUrlPath = productUrlPath.replace("{{page}}", productPath)
                .replace("{{url_key}}", urlKey).replace("{{sku}}", sku);
            categoryUrlPath = categoryUrlPath.replace("{{page}}", categoryPath);
            List<Map<String, String>> urlRewrites = (List<Map<String, String>>) item.get("url_rewrites");
            if (urlRewrites != null) {
                for (Map<String, String> urlRewrite : urlRewrites) {
                    String actualProductUrl = productUrlPath;
                    String actualCategoryUrl = categoryUrlPath;
                    String productUrl = urlRewrite.get("url").replace(".html", "");
                    String[] parts = productUrl.split("/");
                    if (parts.length > 1) {
                        String categoryUrl = String.join("/", Arrays.copyOf(parts, parts.length - 1));
                        actualCategoryUrl = actualCategoryUrl.replace("{{url_path}}", categoryUrl);
                        uniquePagePaths.add(actualCategoryUrl);
                    }
                    actualProductUrl = actualProductUrl.replace("{{url_path}}", productUrl);
                    uniquePagePaths.add(actualProductUrl);
                }
            }
        }
        return uniquePagePaths.toArray(new String[0]);
    }

    private static String[] getCategoryBasedInvalidPaths(ResourceResolver resourceResolver, Map<String, Object> data, String storePath)
        throws RepositoryException {
        // Create Type objects for the generic types T and U
        Set<String> uniquePagePaths = new HashSet<>();

        String productPath = getCorrespondingPagePath(resourceResolver, storePath, "cq:cifProductPage");
        String productPageUrlFormat = getPageFormatUrl(resourceResolver, storePath, "productPageUrlFormat");
        String categoryPath = getCorrespondingPagePath(resourceResolver, storePath, "cq:cifCategoryPage");
        String categoryPageUrlFormat = getPageFormatUrl(resourceResolver, storePath, "categoryPageUrlFormat");

        int productPageIndex = productPageUrlFormat.lastIndexOf(".html");
        productPageUrlFormat = (productPageIndex != -1) ? productPageUrlFormat.substring(0, productPageIndex) : productPageUrlFormat;

        int categoryPageIndex = categoryPageUrlFormat.lastIndexOf(".html");
        categoryPageUrlFormat = (categoryPageIndex != -1) ? categoryPageUrlFormat.substring(0, categoryPageIndex)
            : categoryPageUrlFormat;

        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("categoryList");
        for (Map<String, Object> item : items) {
            String productUrlPath = productPageUrlFormat;
            String categoryUrlPath = categoryPageUrlFormat;
            String urlPath = (String) item.get("url_path");
            String urlKey = (String) item.get("url_key");
            productUrlPath = productUrlPath.replace("{{page}}", productPath);
            categoryUrlPath = categoryUrlPath.replace("{{page}}", categoryPath);
            uniquePagePaths.add(productUrlPath.replace("{{url_key}}", urlKey).replace("{{url_path}}", urlPath));
            uniquePagePaths.add(categoryUrlPath.replace("{{url_key}}", urlKey).replace("{{url_path}}", urlPath));
        }
        return uniquePagePaths.toArray(new String[0]);
    }

    private static Map<String, Object> getGraphqlResponseData(GraphqlClient client, String query) {
        GraphqlRequest request = new GraphqlRequest(query);

        // Create Type objects for the generic types T and U
        Type typeOfT = new TypeToken<Map<String, Object>>() {}.getType();
        Type typeOfU = new TypeToken<Map<String, Object>>() {}.getType();

        GraphqlResponse<Map<String, Object>, Map<String, Object>> response = client.execute(request, typeOfT, typeOfU);
        if (response.getErrors() != null && !response.getErrors().isEmpty()) {
            LOGGER.error("Error executing GraphQL query: {}", response.getErrors());
        } else {
            return response.getData();
        }
        return null;
    }

    private static boolean isValid(ValueMap valueMap, ResourceResolver resourceResolver, String storePath) {
        Map<String, Map<String, Object>> jsonData = createJsonData();
        for (Map.Entry<String, Map<String, Object>> entry : jsonData.entrySet()) {
            Map<String, Object> properties = entry.getValue();
            String key = entry.getKey();
            boolean isFunction = (boolean) properties.get("isFunction");
            if (isFunction) {
                String methodName = (String) properties.get("method");
                String propertyName = (String) properties.get("propertyName");
                try {
                    Method method = InvalidateDispatcherCacheImpl.class.getDeclaredMethod(methodName, ResourceResolver.class, String.class,
                        String.class);
                    Object result = method.invoke(null, resourceResolver, storePath, propertyName);
                    if (result == null) {
                        return false;
                    }
                } catch (Exception e) {
                    return false;
                }
            } else {
                boolean isFlag = true;
                Class<?> clazz = (Class<?>) properties.get("class");
                Object value = getPropertiesValue(valueMap, key, clazz);
                if (value instanceof String) {
                    isFlag = !((String) value).isEmpty();
                } else if (value instanceof Object[]) {
                    isFlag = !(((Object[]) value).length == 0);
                }
                if (!isFlag) {
                    return false;
                }
            }

        }
        return true;
    }

    private static Map<String, Map<String, Object>> createJsonData() {
        Map<String, Map<String, Object>> jsonData = new HashMap<>();

        // Add property for type "graphqlClientId"
        Map<String, Object> graphqlClientId = new HashMap<>();
        graphqlClientId.put("isFunction", false);
        graphqlClientId.put("class", String.class);
        jsonData.put(InvalidateCacheImpl.PARAMETER_GRAPHQL_CLIENT_ID, graphqlClientId);

        // Add property for type "invalidCacheEntries"
        Map<String, Object> invalidCacheEntries = new HashMap<>();
        invalidCacheEntries.put("isFunction", false);
        invalidCacheEntries.put("class", String[].class);
        jsonData.put(InvalidateCacheImpl.PARAMETER_INVALID_CACHE_ENTRIES, invalidCacheEntries);

        // Add property for type "storePath"
        Map<String, Object> storePath = new HashMap<>();
        storePath.put("isFunction", false);
        storePath.put("class", String.class);
        jsonData.put(InvalidateCacheImpl.PARAMETER_STORE_PATH, storePath);

        // Add property for type "type"
        Map<String, Object> type = new HashMap<>();
        type.put("isFunction", false);
        type.put("class", String.class);
        jsonData.put(InvalidateCacheImpl.PARAMETER_TYPE, type);

        // Add property for type "categoryPath"
        Map<String, Object> categoryPath = new HashMap<>();
        categoryPath.put("isFunction", true);
        categoryPath.put("method", "getCorrespondingPagePath");
        categoryPath.put("propertyName", "cq:cifCategoryPage");
        categoryPath.put("args",
            new ArrayList<>(
                List.of("resourceResolver", "storePath", "propertyName")));
        jsonData.put("categoryPath", categoryPath);

        // Add property for type "productPath"
        Map<String, Object> productPath = new HashMap<>();
        productPath.put("isFunction", true);
        productPath.put("method", "getCorrespondingPagePath");
        productPath.put("propertyName", "cq:cifProductPage");
        productPath.put("args",
            new ArrayList<>(
                List.of("resourceResolver", "storePath", "propertyName")));
        jsonData.put("productPath", productPath);

        // Add property for type "productPageUrlFormat"
        Map<String, Object> productPageUrlFormat = new HashMap<>();
        productPageUrlFormat.put("isFunction", true);
        productPageUrlFormat.put("method", "getPageFormatUrl");
        productPageUrlFormat.put("propertyName", "productPageUrlFormat");
        productPageUrlFormat.put("args",
            new ArrayList<>(
                List.of("resourceResolver", "storePath", "propertyName")));
        jsonData.put("productPageUrlFormat", productPageUrlFormat);

        // Add property for type "categoryPageUrlFormat"
        Map<String, Object> categoryPageUrlFormat = new HashMap<>();
        categoryPageUrlFormat.put("isFunction", true);
        categoryPageUrlFormat.put("method", "getPageFormatUrl");
        categoryPageUrlFormat.put("propertyName", "categoryPageUrlFormat");
        categoryPageUrlFormat.put("args",
            new ArrayList<>(
                List.of("resourceResolver", "storePath", "propertyName")));
        jsonData.put("categoryPageUrlFormat", categoryPageUrlFormat);

        return jsonData;
    }

    private static Object getPropertiesValue(ValueMap properties, String key, Object clazz) {
        return properties.get(key, clazz);
    }

    private static <T> T getPropertiesValue(ValueMap properties, String key, Class<T> clazz) {
        return properties.get(key, clazz);
    }

    private static String getCorrespondingPagePath(ResourceResolver resourceResolver, String storePath, String propertyName)
        throws RepositoryException {
        Resource pathResource = resourceResolver.getResource(storePath);
        if (pathResource != null && pathResource.adaptTo(Node.class) != null) {
            Node node = pathResource.adaptTo(Node.class);
            if (node != null) {
                Node contentNode = node.hasNode("jcr:content") ? node.getNode("jcr:content") : null;
                if (contentNode != null && contentNode.hasProperty(propertyName)) {
                    return contentNode.getProperty(propertyName).getString();
                }
            }
        }
        return null;
    }

    private static String generateSkuQuery(String skuString) {

        // Construct the GraphQL query
        return "{\n" +
            "  products(\n" +
            "    filter: { sku: { in: [" + skuString + "] } }\n" +
            "    pageSize: 100\n" +
            "    currentPage: 1\n" +
            "  ) {\n" +
            "    items {\n" +
            "      sku\n" +
            "      url_key\n" +
            "      url_path\n" +
            "      url_rewrites {\n" +
            "        url\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";
    }

    private static String generateCategoryQuery(String uidString) {

        // Construct the GraphQL query
        return "{\n" +
            "  categoryList(\n" +
            "    filters: { category_uid: { in: [" + uidString + "] } }\n" +
            "  ) {\n" +
            "    url_path\n" +
            "    url_key\n" +
            "    }\n" +
            "}";
    }

    private static String formatList(String[] invalidCacheEntries, String delimiter, String pattern) {
        return Arrays.stream(invalidCacheEntries)
            .map(item -> String.format(pattern, item))
            .collect(Collectors.joining(delimiter));
    }

    private static String getSkuBasedSql2Query(String storePath, String skuList) {
        return "SELECT content.[jcr:path] " +
            "FROM [cq:Page] AS page " +
            "INNER JOIN [nt:unstructured] AS content ON ISDESCENDANTNODE(content, page) " +
            "WHERE ISDESCENDANTNODE(page,'" + storePath + "' ) " +
            "AND (" +
            "(content.[product] in (" + skuList + ") AND content.[productType] in ('combinedSku')) " +
            "OR (content.[selection] in (" + skuList + ") AND content.[selectionType] in ('combinedSku', 'sku'))" +
            ")";
    }

    private static String getCategoryBasedSql2Query(String storePath, String categoryList) {
        return "SELECT content.[jcr:path] " +
            "FROM [cq:Page] AS page " +
            "INNER JOIN [nt:unstructured] AS content ON ISDESCENDANTNODE(content, page) " +
            "WHERE ISDESCENDANTNODE(page,'" + storePath + "' ) " +
            "AND (" +
            "(content.[categoryId] in (" + categoryList + ") AND content.[categoryIdType] in ('uid')) " +
            "OR (content.[category] in (" + categoryList + ") AND content.[categoryType] in ('uid'))" +
            ")";
    }

    private String[] getCorrespondingProductsPageBasedOnSku(Session session, String storePath, String[] invalidCacheEntries)
        throws Exception {

        String skuList = formatList(invalidCacheEntries, ", ", "'%s'");
        String sql2Query = getSkuBasedSql2Query(storePath, skuList);
        return getCorrespondingPageBasedOnQuery(session, sql2Query);
    }

    private String[] getCorrespondingCategoryPageBasedOnUid(Session session, String storePath, String[] invalidCacheEntries)
        throws Exception {

        String categoryList = formatList(invalidCacheEntries, ", ", "'%s'");
        String sql2Query = getCategoryBasedSql2Query(storePath, categoryList);
        return getCorrespondingPageBasedOnQuery(session, sql2Query);
    }

    private String[] getCorrespondingPageBasedOnQuery(Session session, String queryString)
        throws Exception {

        Set<String> uniquePagePaths = new HashSet<>();
        Query query = session.getWorkspace().getQueryManager().createQuery(queryString, Query.JCR_SQL2);
        QueryResult result = query.execute();
        RowIterator rows = result.getRows();
        while (rows.hasNext()) {
            Row row = rows.nextRow();
            // Retrieve the jcr:path column directly
            String fullPath = row.getPath("content");
            String pagePath = extractPagePath(fullPath) + ".html";

            // Add to the set to ensure uniqueness
            uniquePagePaths.add(pagePath);
        }

        // Convert the set to a String array and return
        return uniquePagePaths.toArray(new String[0]);
    }

    private String extractPagePath(String fullPath) {
        // Find the index of "/jcr:content" in the path
        int jcrContentIndex = fullPath.indexOf("/jcr:content");

        // If "/jcr:content" exists, truncate the path; otherwise, return the full path
        return jcrContentIndex != -1 ? fullPath.substring(0, jcrContentIndex) : fullPath;
    }

    private static String getPageFormatUrl(ResourceResolver resourceResolver, String storePath, String propertyName) {
        try {
            String commerceConfigPath = getValueFromNodeOrParent(resourceResolver, storePath, "cq:conf");
            if (commerceConfigPath != null) {
                return getCommerceDataForSpecificProperty(resourceResolver, commerceConfigPath, propertyName);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private static String getCommerceDataForSpecificProperty(ResourceResolver resourceResolver, String path, String propertyName)
        throws RepositoryException {
        String specificPath = path + "/settings/cloudconfigs/commerce/jcr:content";
        Resource pathResource = resourceResolver.getResource(specificPath);
        if (pathResource != null && pathResource.adaptTo(Node.class) != null) {
            Node specificNode = pathResource.adaptTo(Node.class);
            if (specificNode != null && specificNode.hasProperty(propertyName)) {
                return specificNode.getProperty(propertyName).getString();
            } else {
                return "default";
            }
        }
        return null;
    }

    private static String getValueFromNodeOrParent(ResourceResolver resourceResolver, String storePath, String propertyName)
        throws RepositoryException {
        Resource pathResource = resourceResolver.getResource(storePath);
        if (pathResource != null && pathResource.adaptTo(Node.class) != null) {
            Node node = pathResource.adaptTo(Node.class);
            while (node != null && !"/content".equals(node.getPath())) {
                Node contentNode = node.hasNode("jcr:content") ? node.getNode("jcr:content") : null;
                if (contentNode != null && contentNode.hasProperty(propertyName)) {
                    return contentNode.getProperty(propertyName).getString();
                }
                node = node.getParent();
            }
        }

        return null; // or throw an exception if value is not found
    }

    private static void flushCache(String handle) {
        try {
            String server = "localhost:80";
            String uri = "/dispatcher/invalidate.cache";

            HttpClient client = new HttpClient();
            PostMethod post = new PostMethod("http://" + server + uri);
            post.setRequestHeader("CQ-Action", "Delete");
            post.setRequestHeader("CQ-Handle", handle);
            post.setRequestHeader("CQ-Action-Scope", "ResourceOnly");

            client.executeMethod(post);
            System.out.println("Response: " + post.getResponseBodyAsString());
            post.releaseConnection();
            // log the results
            LOGGER.info("result: {}", post.getResponseBodyAsString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            LOGGER.error("Flushcache servlet exception: {}", e.getMessage());
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
