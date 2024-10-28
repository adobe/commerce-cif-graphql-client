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

package com.adobe.cq.commerce.graphql.flush.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TypeProperties {

    public static final String PARAMETER_GRAPHQL_CLIENT_ID = "graphqlClientId";
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

    // Initialize the structure manually
    private static Map<String, Map<String, List<String>>> createJsonData() {
        Map<String, Map<String, List<String>>> jsonData = new HashMap<>();

        // Add property for type "sku"
        Map<String, List<String>> skuProperties = new HashMap<>();
        skuProperties.put("requiredFields", new ArrayList<>(List.of(PARAMETER_INVALID_CACHE_ENTRIES)));
        skuProperties.put("process", new ArrayList<>(
            List.of(
                "venia/components/commerce/product",
                "venia/components/commerce/breadcrumb")));
        jsonData.put(TYPE_SKU, skuProperties);

        // Add property for type "uuid"
        Map<String, List<String>> uuidProperties = new HashMap<>();
        uuidProperties.put("requiredFields", new ArrayList<>(List.of(PARAMETER_INVALID_CACHE_ENTRIES)));
        jsonData.put(TYPE_UUIDS, uuidProperties);

        // Add property for type "clearSpecificCache"
        Map<String, List<String>> clearSpecificCacheProperties = new HashMap<>();
        clearSpecificCacheProperties.put("requiredFields", new ArrayList<>(List.of(PARAMETER_INVALID_CACHE_ENTRIES)));
        jsonData.put(TYPE_ClEAR_SPECIFIC_CACHE, clearSpecificCacheProperties);

        // Add property for type "attribute"
        Map<String, List<String>> attributeProperties = new HashMap<>();
        attributeProperties.put("requiredFields", new ArrayList<>(List.of(PARAMETER_INVALID_CACHE_ENTRIES, PARAMETER_ATTRIBUTE)));
        jsonData.put(TYPE_ATTRIBUTE, attributeProperties);

        return jsonData;
    }

    private static List<String> getInitialRequiredAttributes() {
        List<String> requiredFields = new ArrayList<>();
        requiredFields.add(TypeProperties.PARAMETER_GRAPHQL_CLIENT_ID);
        requiredFields.add(TypeProperties.PARAMETER_TYPE);
        return requiredFields;
    }
}
