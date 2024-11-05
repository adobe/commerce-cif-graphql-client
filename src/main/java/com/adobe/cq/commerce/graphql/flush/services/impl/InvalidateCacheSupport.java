/*******************************************************************************
 *
 *    Copyright 2024 Adobe. All rights reserved.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class InvalidateCacheSupport {

    public static final String PARAMETER_GRAPHQL_CLIENT_ID = "graphqlClientId";
    public static final String PARAMETER_STORE_VIEW = "storeView";
    public static final String PARAMETER_TYPE = "type";
    public static final String PARAMETER_INVALID_CACHE_ENTRIES = "invalidCacheEntries";
    public static final String PARAMETER_ATTRIBUTE = "attribute";
    public static final String PARAMETER_LIST_OF_CACHE_TO_SEARCH = "listOfCacheToSearch";
    public static final String TYPE_SKU = "skus";
    public static final String TYPE_UUIDS = "uuids";
    public static final String TYPE_ClEAR_SPECIFIC_CACHE = "clearSpecificCache";
    public static final String TYPE_ATTRIBUTE = "attribute";
    public static final String TYPE_CLEAR_ALL = "clearAll";

    // Define the JSON-like structure as a constant
    public static final Map<String, Map<String, List<String>>> PROPERTIES = createJsonData();

    // Define the JSON-like structure as a constant
    public static final List<String> REQUIRED_ATTRIBUTES = getInitialRequiredAttributes();

    private static Map<String, Map<String, List<String>>> createJsonData() {
        Map<String, Map<String, List<String>>> jsonData = new HashMap<>();

        // Add property for type "sku"
        Map<String, List<String>> skuProperties = new HashMap<>();
        skuProperties.put("requiredFields", new ArrayList<>(
            List.of(
                PARAMETER_INVALID_CACHE_ENTRIES)));
        // To-do list
        // skuProperties.put("process", new ArrayList<>(
        // List.of(
        // "venia/components/commerce/product",
        // "venia/components/commerce/breadcrumb")));

        jsonData.put(TYPE_SKU, skuProperties);

        // Add property for type "uuid"
        Map<String, List<String>> uuidProperties = new HashMap<>();
        uuidProperties.put("requiredFields", new ArrayList<>(
            List.of(
                PARAMETER_INVALID_CACHE_ENTRIES)));
        jsonData.put(TYPE_UUIDS, uuidProperties);

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

    private static List<String> getInitialRequiredAttributes() {
        List<String> requiredFields = new ArrayList<>();
        requiredFields.add(PARAMETER_GRAPHQL_CLIENT_ID);
        requiredFields.add(PARAMETER_TYPE);
        return requiredFields;
    }

    public static String[] getProductAttributePatterns(String[] patterns, String attribute) {
        String attributeString = String.join("|", patterns);
        return new String[] { getRegexBasedOnAttribute(attribute) + "(" + attributeString + ")\"" };
    }

    public static String[] convertJsonArrayToStringArray(JsonArray jsonArray) {
        return StreamSupport.stream(jsonArray.spliterator(), false)
            .map(JsonElement::getAsString)
            .toArray(String[]::new);
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
}
