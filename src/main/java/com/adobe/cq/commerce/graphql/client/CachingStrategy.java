/*******************************************************************************
 *
 *    Copyright 2020 Adobe. All rights reserved.
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

package com.adobe.cq.commerce.graphql.client;

public class CachingStrategy {

    private String cacheName;
    private DataFetchingPolicy dataFetchingPolicy;

    public String getCacheName() {
        return cacheName;
    }

    public CachingStrategy withCacheName(String cacheName) {
        this.cacheName = cacheName;
        return this;
    }

    public DataFetchingPolicy getDataFetchingPolicy() {
        return dataFetchingPolicy;
    }

    public CachingStrategy withDataFetchingPolicy(DataFetchingPolicy dataFetchingPolicy) {
        this.dataFetchingPolicy = dataFetchingPolicy;
        return this;
    }

    /**
     * The data fetching policy with respect to caching.
     */
    public static enum DataFetchingPolicy {

        /**
         * Fetch data from the cache first. If the response doesn't exist or is expired, then fetch a response from the network.
         */
        CACHE_FIRST,

        /**
         * Fetch data from the network only, ignoring any cached responses.
         */
        NETWORK_ONLY
    }
}
