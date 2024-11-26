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

package com.adobe.cq.commerce.graphql.flush.services;

public interface InvalidateDispatcherService {

    String SERVICE_USER = "cif-flush";
    String PROPERTIES_GRAPHQL_CLIENT_ID = "cq:catalogIdentifier";
    String PROPERTIES_TYPE = "type";
    String PROPERTIES_STORE_PATH = "storePath";
    String PROPERTIES_INVALID_CACHE_ENTRIES = "invalidCacheEntries";
    String PROPERTIES_ATTRIBUTE = "attribute";
    String TYPE_SKU = "skus";
    String TYPE_CATEGORY = "categories";
    String TYPE_UUIDS = "uuids";
    String TYPE_ClEAR_SPECIFIC_CACHE = "clearSpecificCache";
    String TYPE_ATTRIBUTE = "attribute";
    String TYPE_CLEAR_ALL = "clearAll";

    void invalidateCache(String path);
}
