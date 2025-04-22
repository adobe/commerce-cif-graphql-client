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
package com.adobe.cq.commerce.graphql.client.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.google.common.cache.Cache;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

class CacheInvalidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheInvalidator.class);
    private static final String STORE_HEADER_NAME = "Store";
    private Map<String, Cache<CacheKey, GraphqlResponse<?, ?>>> caches;
    private Gson gson;

    CacheInvalidator(Map<String, Cache<CacheKey, GraphqlResponse<?, ?>>> caches) {
        this.caches = caches;
        this.gson = new GsonBuilder()
            .disableHtmlEscaping()
            .create();
    }

    private boolean isAllParametersEmpty(String storeView, String[] cacheNames, String[] patterns) {
        return StringUtils.isBlank(storeView) &&
            (cacheNames == null || cacheNames.length == 0) &&
            (patterns == null || patterns.length == 0);
    }

    private boolean isOnlyStoreViewProvided(String storeView, String[] cacheNames, String[] patterns) {
        return !StringUtils.isBlank(storeView) &&
            (cacheNames == null || cacheNames.length == 0) &&
            (patterns == null || patterns.length == 0);
    }

    private boolean isCacheNamesProvided(String[] cacheNames, String[] patterns) {
        return (cacheNames != null && cacheNames.length > 0) &&
            (patterns == null || patterns.length == 0);
    }

    /**
     * Invalidates cache entries based on the provided criteria.
     *
     * @param storeView The store view to match against cache entries
     * @param cacheNames Array of cache names to invalidate
     * @param patterns Array of regex patterns to match against cache entries
     */
    void invalidateCache(String storeView, String[] cacheNames, String[] patterns) {
        if (isAllParametersEmpty(storeView, cacheNames, patterns)) {
            invalidateAll();
        } else if (isOnlyStoreViewProvided(storeView, cacheNames, patterns)) {
            invalidateStoreView(storeView);
        } else if (isCacheNamesProvided(cacheNames, patterns)) {
            invalidateSpecificCaches(storeView, cacheNames);
        } else {
            for (String pattern : patterns) {
                if (pattern != null && !pattern.isEmpty()) {
                    invalidateCacheBasedOnPattern(pattern, storeView, cacheNames);
                } else {
                    LOGGER.debug("Skipping null pattern in patterns array");
                }
            }
        }
    }

    private void invalidateStoreView(String storeView) {
        caches.forEach((cacheName, cache) -> {
            cache.asMap().entrySet().stream()
                .filter(entry -> checkIfStorePresent(storeView, entry.getKey()))
                .forEach(entry -> {
                    LOGGER.debug("Invalidating key based StoreView: {} in cache: {}", entry.getKey(), cacheName);
                    cache.invalidate(entry.getKey());
                });
        });
    }

    private void invalidateAll() {
        LOGGER.debug("Invalidating all caches...");
        caches.values().forEach(Cache::invalidateAll);
    }

    private void invalidateSpecificCaches(String storeView, String[] cacheNames) {
        for (String cacheName : cacheNames) {
            Cache<CacheKey, GraphqlResponse<?, ?>> cache = caches.get(cacheName);
            if (cache != null) {
                if (storeView == null) {
                    LOGGER.debug("Invalidating entire cache: {}", cacheName);
                    cache.invalidateAll();
                } else {
                    cache.asMap().entrySet().stream()
                        .filter(entry -> checkIfStorePresent(storeView, entry.getKey()))
                        .forEach(entry -> {
                            LOGGER.debug("Invalidating key based SpecificCaches & storeView: {} in cache: {}", entry.getKey(), cacheName);
                            cache.invalidate(entry.getKey());
                        });
                }
            } else {
                LOGGER.debug("Cache not found: {}", cacheName);
            }
        }
    }

    private void invalidateCacheBasedOnPattern(String pattern, String storeView, String[] listOfCacheToSearch) {
        Pattern regex = Pattern.compile(pattern);
        caches.forEach((cacheName, cache) -> {
            if (listOfCacheToSearch != null && listOfCacheToSearch.length > 0
                && !Arrays.asList(listOfCacheToSearch).contains(cacheName)) {
                return;
            }
            cache.asMap().entrySet().stream()
                .filter(entry -> {
                    if (!checkIfStorePresent(storeView, entry.getKey())) {
                        return false;
                    }
                    GraphqlResponse<?, ?> value = entry.getValue();
                    String jsonResponse = gson.toJson(value);
                    Matcher matcher = regex.matcher(jsonResponse);
                    return matcher.find();
                })
                .forEach(entry -> {
                    LOGGER.debug("Invalidating key: {} in cache based on pattern: {}", entry.getKey(), cacheName);
                    cache.invalidate(entry.getKey());
                });
        });
    }

    private boolean checkIfStorePresent(String storeView, CacheKey cacheKey) {
        if (storeView == null || cacheKey == null || cacheKey.getRequestOptions() == null) {
            return false;
        }
        List<Header> headers = cacheKey.getRequestOptions().getHeaders();
        if (headers != null && !headers.isEmpty()) {
            return headers.stream()
                .anyMatch(
                    header -> STORE_HEADER_NAME.equalsIgnoreCase(header.getName())
                        && storeView.equalsIgnoreCase(header.getValue()));
        }
        return false;
    }
}
