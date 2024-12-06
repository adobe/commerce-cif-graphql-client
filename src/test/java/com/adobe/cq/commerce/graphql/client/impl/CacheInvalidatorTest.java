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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Test;

import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.graphql.client.RequestOptions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import static org.junit.Assert.*;

public class CacheInvalidatorTest {

    private CacheInvalidator cacheInvalidator;

    private Map<String, Cache<CacheKey, GraphqlResponse<?, ?>>> caches;

    private Map<String, Integer> initialCounts;

    private Method checkIfStorePresentMethod;

    private static class Data {
        String text;
    }

    private static class Error {
        String message;
    }

    @Before
    public void setUp() throws NoSuchMethodException {

        caches = new HashMap<>();

        // Create real caches and add them to the map
        Cache<CacheKey, GraphqlResponse<?, ?>> cache1 = CacheBuilder.newBuilder().build();
        Cache<CacheKey, GraphqlResponse<?, ?>> cache2 = CacheBuilder.newBuilder().build();

        // Add some data to the caches
        GraphqlRequest request1 = new GraphqlRequest("{dummy1}");
        GraphqlRequest request2 = new GraphqlRequest("{dummy2}");
        GraphqlRequest request3 = new GraphqlRequest("{dummy3}");
        List<Header> headers1 = Collections.singletonList(new BasicHeader("Store", "default"));
        RequestOptions options1 = new RequestOptions().withHeaders(headers1);
        List<Header> headers2 = Collections.singletonList(new BasicHeader("Store", "defaultTest"));
        RequestOptions options2 = new RequestOptions().withHeaders(headers2);
        RequestOptions optionsWithNoHeader = new RequestOptions();

        CacheKey cacheKey1 = new CacheKey(request1, options1);
        CacheKey cacheKey2 = new CacheKey(request2, options2);
        CacheKey cacheKey3 = new CacheKey(request3, options1);
        CacheKey cacheKeyWithNoHeader = new CacheKey(request1, optionsWithNoHeader);

        // Define the responses
        GraphqlResponse<Data, Error> response1 = new GraphqlResponse<>();
        response1.setData(new Data());
        response1.getData().text = "sku1";

        GraphqlResponse<Data, Error> response2 = new GraphqlResponse<>();
        response2.setData(new Data());
        response2.getData().text = "sku2";

        cache1.put(cacheKey1, response1);
        cache1.put(cacheKey2, response2);
        cache1.put(cacheKey3, response2);
        cache1.put(cacheKeyWithNoHeader, response1);
        cache1.put(cacheKey3, response2);
        cache2.put(cacheKey2, response2);
        cache2.put(cacheKey1, response1);

        caches.put("cache1", cache1);
        caches.put("cache2", cache2);
        cacheInvalidator = new CacheInvalidator(caches);

        // Get the private method using reflection
        checkIfStorePresentMethod = CacheInvalidator.class.getDeclaredMethod("checkIfStorePresent", String.class, CacheKey.class);
        checkIfStorePresentMethod.setAccessible(true);

        // Store the initial count of entries in each cache
        initialCounts = new HashMap<>();
        for (Map.Entry<String, Cache<CacheKey, GraphqlResponse<?, ?>>> entry : caches.entrySet()) {
            initialCounts.put(entry.getKey(), entry.getValue().asMap().size());
        }

    }

    @Test
    public void testInvalidateAll() {
        // Call the invalidateCache method with parameters that trigger invalidateAll
        cacheInvalidator.invalidateCache(null, null, null);

        // Verify the caches were invalidated
        for (Cache<CacheKey, GraphqlResponse<?, ?>> cache : caches.values()) {
            assertTrue(cache.asMap().isEmpty());
        }
    }

    @Test
    public void testInvalidateStoreView() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        cacheInvalidator.invalidateCache("default", null, null);

        // Verify that the cache was invalidated for the store view, were it has been set as default
        for (Cache<CacheKey, GraphqlResponse<?, ?>> cache : caches.values()) {
            for (CacheKey key : cache.asMap().keySet()) {
                List<Header> headers = key.getRequestOptions().getHeaders();
                boolean storeViewExists = checkIfStorePresent("default", key);
                assertFalse("Store view default not found in headers", storeViewExists);
            }
        }
    }

    @Test
    public void testInvalidateCacheBasedOnSpecificCacheName() {
        cacheInvalidator.invalidateCache(null, new String[] { "cache1" }, null);

        // Verify that other caches are not empty
        for (Map.Entry<String, Cache<CacheKey, GraphqlResponse<?, ?>>> entry : caches.entrySet()) {
            if (!entry.getKey().equals("cache1")) {
                assertFalse(entry.getValue().asMap().isEmpty());
            } else {
                assertTrue(entry.getValue().asMap().isEmpty());
            }
        }
    }

    @Test
    public void testNoCacheWithMultipleCacheNames() {
        // This will clear cache1 and cache2 i.e all the caches
        cacheInvalidator.invalidateCache(null, new String[] { "cache1", "cache2" }, null);

        // Verify that other caches are not empty
        for (Map.Entry<String, Cache<CacheKey, GraphqlResponse<?, ?>>> entry : caches.entrySet()) {
            if (entry.getKey().equals("cache1") || entry.getKey().equals("cache2")) {
                assertTrue(entry.getValue().asMap().isEmpty());
            } else {
                assertFalse(entry.getValue().asMap().isEmpty());
            }
        }
    }

    @Test
    public void testNoCacheWithInvalidCacheNames() {
        // This will not clear any cache as the cache names are invalid
        cacheInvalidator.invalidateCache(null, new String[] { "cachetest1" }, null);

        // Verify that the count of entries in each cache is the same as before
        for (Map.Entry<String, Cache<CacheKey, GraphqlResponse<?, ?>>> entry : caches.entrySet()) {
            assertEquals(initialCounts.get(entry.getKey()).intValue(), entry.getValue().asMap().size());
        }
    }

    @Test
    public void testNoCacheWithStoreViewDefaultAndTextSku1() throws InvocationTargetException, IllegalAccessException,
        NoSuchMethodException {
        cacheInvalidator.invalidateCache("default", null, new String[] { "\"text\":\\s*\"(sku1)\"" });

        for (Cache<CacheKey, GraphqlResponse<?, ?>> cache : caches.values()) {
            for (Map.Entry<CacheKey, GraphqlResponse<?, ?>> entry : cache.asMap().entrySet()) {
                CacheKey key = entry.getKey();
                GraphqlResponse<?, ?> response = entry.getValue();

                boolean storeViewIsDefault = checkIfStorePresent("default", key);
                boolean textIsSku1 = response.getData() != null && "sku1".equals(((Data) response.getData()).text);

                assertFalse("Cache with store view 'default' and text 'sku1' found", storeViewIsDefault && textIsSku1);
            }
        }
    }

    @Test
    public void testNoCacheInMultipleCacheListForSpecificPattern() throws InvocationTargetException, IllegalAccessException {

        // This will invalidate the cache from cache1 and cache2 with text 'sku2'
        String storeView = "default";
        cacheInvalidator.invalidateCache(storeView, null, new String[] { "\"text\":\\s*\"(sku2)\"" });

        //
        for (Cache<CacheKey, GraphqlResponse<?, ?>> cache : caches.values()) {
            for (Map.Entry<CacheKey, GraphqlResponse<?, ?>> entry : cache.asMap().entrySet()) {
                CacheKey key = entry.getKey();
                GraphqlResponse<?, ?> response = entry.getValue();

                boolean storeViewIsDefault = checkIfStorePresent(storeView, key);
                boolean textIsSku = response.getData() != null && "sku2".equals(((Data) response.getData()).text);

                assertFalse("Cache with store view 'defaultTest' & text 'sku2' found", storeViewIsDefault && textIsSku);
            }
        }
    }

    @Test
    public void testNoCacheWithStoreViewDefaultTestAndCacheNameCache2AndTextSku2() throws InvocationTargetException,
        IllegalAccessException {

        String storeView = "defaultTest";
        cacheInvalidator.invalidateCache(storeView, new String[] { "cache2" }, new String[] { "\"text\":\\s*\"(sku2)\"" });

        for (Cache<CacheKey, GraphqlResponse<?, ?>> cache : caches.values()) {
            for (Map.Entry<CacheKey, GraphqlResponse<?, ?>> entry : cache.asMap().entrySet()) {
                CacheKey key = entry.getKey();
                GraphqlResponse<?, ?> response = entry.getValue();

                boolean storeViewIsDefaultTest = checkIfStorePresent(storeView, key);
                boolean cacheNameIsCache2 = "cache2".equals(cache);
                boolean textIsSku2 = response.getData() != null && "sku2".equals(((Data) response.getData()).text);

                assertFalse("Cache with store view 'defaultTest', cache name 'cache2' and text 'sku2' found",
                    storeViewIsDefaultTest && cacheNameIsCache2 && textIsSku2);
            }
        }
    }

    @Test
    public void testNoCacheWithStoreViewDefaultTestAndMultipleStringPatterns() throws InvocationTargetException, IllegalAccessException {

        String storeView = "default";
        cacheInvalidator.invalidateCache(storeView, null, new String[] { "\"text\":\\s*\"(sku2)\"", "\"text\":\\s*\"(sku1)\"" });

        for (Cache<CacheKey, GraphqlResponse<?, ?>> cache : caches.values()) {
            for (Map.Entry<CacheKey, GraphqlResponse<?, ?>> entry : cache.asMap().entrySet()) {
                CacheKey key = entry.getKey();
                GraphqlResponse<?, ?> response = entry.getValue();

                boolean storeViewIsDefaultTest = checkIfStorePresent(storeView, key);
                boolean textIsSku = response.getData() != null
                    && ("sku2".equals(((Data) response.getData()).text) || "sku1".equals(((Data) response.getData()).text));

                assertFalse("Cache with store view 'defaultTest', and text 'sku2' or 'sku1' found",
                    storeViewIsDefaultTest && textIsSku);
            }
        }
    }

    @Test
    public void testNoCacheWithStoreViewDefaultTestAndComplexStringPattern() throws InvocationTargetException, IllegalAccessException {

        String storeView = "default";
        cacheInvalidator.invalidateCache(storeView, null, new String[] { "\"text\":\\s*\"(sku2|sku1)\"" });

        for (Cache<CacheKey, GraphqlResponse<?, ?>> cache : caches.values()) {
            for (Map.Entry<CacheKey, GraphqlResponse<?, ?>> entry : cache.asMap().entrySet()) {
                CacheKey key = entry.getKey();
                GraphqlResponse<?, ?> response = entry.getValue();

                boolean storeViewIsDefaultTest = checkIfStorePresent(storeView, key);
                boolean textIsSku = response.getData() != null
                    && ("sku2".equals(((Data) response.getData()).text) || "sku1".equals(((Data) response.getData()).text));

                assertFalse("Cache with store view 'defaultTest', and text 'sku2' or 'sku1' found",
                    storeViewIsDefaultTest && textIsSku);
            }
        }
    }

    private boolean checkIfStorePresent(String storeView, CacheKey cacheKey) throws InvocationTargetException, IllegalAccessException {
        return (boolean) checkIfStorePresentMethod.invoke(cacheInvalidator, storeView, cacheKey);
    }
}
