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

import com.google.gson.JsonObject;

public interface InvalidateCacheService {

    String INVALIDATE_WORKING_AREA = "/var/cif";
    String NODE_NAME_BASE = "invalidate_entry";
    String SERVICE_USER = "cif-flush";
    String PROPERTY_INVALID_DATE = "invalidateDate";

    void invalidateCache(String path);

    void triggerCacheInvalidation(JsonObject jsonObject);

}
