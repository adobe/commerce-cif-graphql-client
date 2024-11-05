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

class CacheInvalidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheInvalidator.class);
    private Map<String, Cache<CacheKey, GraphqlResponse<?, ?>>> caches;
    private Gson gson;

    CacheInvalidator(Map<String, Cache<CacheKey, GraphqlResponse<?, ?>>> caches) {
        this.caches = caches;
        this.gson = new Gson();
    }

    void invalidateCache(String storeView, String[] cacheNames, String[] patterns) {
        if (StringUtils.isBlank(storeView) && (cacheNames == null || cacheNames.length == 0) &&
            (patterns == null || patterns.length == 0)) {
            invalidateAll();
        } else if (!StringUtils.isBlank(storeView) && (cacheNames == null || cacheNames.length == 0) &&
            (patterns == null || patterns.length == 0)) {
            invalidateStoreView(storeView);
        } else if (StringUtils.isBlank(storeView) && (cacheNames != null && cacheNames.length > 0) &&
            (patterns == null || patterns.length == 0)) {
            invalidateSpecificCaches(cacheNames);
        } else {
            for (String pattern : patterns) {
                invalidateCacheBasedOnPattern(pattern, storeView, cacheNames);
            }
        }
    }

    private void invalidateStoreView(String storeView) {
        caches.forEach((cacheName, cache) -> {
            cache.asMap().entrySet().stream()
                .filter(entry -> checkIfStorePresent(storeView, entry.getKey()))
                .forEach(entry -> {
                    LOGGER.info("Invalidating key: {} in cache: {}", entry.getKey(), cacheName);
                    cache.invalidate(entry.getKey());
                });
        });
    }

    private void invalidateAll() {
        LOGGER.info("Invalidating all caches...");
        caches.values().forEach(Cache::invalidateAll);
    }

    private void invalidateSpecificCaches(String[] cacheEntries) {
        for (String cacheName : cacheEntries) {
            Cache<CacheKey, GraphqlResponse<?, ?>> cache = caches.get(cacheName);
            if (cache != null) {
                LOGGER.info("Invalidating cache: {}", cacheName);
                cache.invalidateAll();
            } else {
                LOGGER.warn("Cache not found: {}", cacheName);
            }
        }
    }

    private void invalidateCacheBasedOnPattern(String pattern, String storeView, String[] listOfCacheToSearch) {
        Pattern regex = Pattern.compile(pattern);
        caches.forEach((cacheName, cache) -> {
            if (listOfCacheToSearch != null
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
                    // Replace \\u003d with = in the JSON string
                    jsonResponse = jsonResponse.replace("\\u003d", "=");
                    Matcher matcher = regex.matcher(jsonResponse);
                    return matcher.find();
                })
                .forEach(entry -> {
                    LOGGER.info("Invalidating key: {} in cache: {}", entry.getKey(), cacheName);
                    cache.invalidate(entry.getKey());
                });
        });
    }

    private boolean checkIfStorePresent(String storeView, CacheKey cacheKey) {
        List<Header> headers = cacheKey.getRequestOptions().getHeaders();
        return headers.stream()
            .anyMatch(
                header -> "Store".equalsIgnoreCase(header.getName())
                    && storeView.equalsIgnoreCase(header.getValue()));
    }
}
