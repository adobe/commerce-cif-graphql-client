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
import java.util.stream.Collectors;

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

    private static final String STORE_HEADER = "Store";
    private static final String DEFAULT_STORE = "default";
    private static final String DEFAULT_TEST_STORE = "defaultTest";
    private static final String CACHE1 = "cache1";
    private static final String CACHE2 = "cache2";

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
        initializeCaches();
        initializeCacheInvalidator();
        initializeReflectionMethod();
        storeInitialCounts();
    }

    private void initializeCaches() {
        caches = new HashMap<>();

        // Create and populate caches
        Cache<CacheKey, GraphqlResponse<?, ?>> cache1 = createAndPopulateCache1();
        Cache<CacheKey, GraphqlResponse<?, ?>> cache2 = createAndPopulateCache2();

        caches.put(CACHE1, cache1);
        caches.put(CACHE2, cache2);
    }

    private Cache<CacheKey, GraphqlResponse<?, ?>> createAndPopulateCache1() {
        Cache<CacheKey, GraphqlResponse<?, ?>> cache = CacheBuilder.newBuilder().build();

        // Create requests and options
        GraphqlRequest request1 = new GraphqlRequest("{dummy1}");
        GraphqlRequest request2 = new GraphqlRequest("{dummy2}");
        GraphqlRequest request3 = new GraphqlRequest("{dummy3}");

        RequestOptions options1 = createRequestOptions(DEFAULT_STORE);
        RequestOptions options2 = createRequestOptions(DEFAULT_TEST_STORE);
        RequestOptions optionsWithDifferentHeaders = createRequestOptionsWithDifferentHeader();
        RequestOptions optionsWithNoHeader = new RequestOptions();

        // Create cache keys
        CacheKey cacheKey1 = new CacheKey(request1, options1);
        CacheKey cacheKey2 = new CacheKey(request2, options2);
        CacheKey cacheKey3 = new CacheKey(request3, options1);
        CacheKey cacheKeyWithDifferentHeader = new CacheKey(request1, optionsWithDifferentHeaders);
        CacheKey cacheKeyWithNoHeader = new CacheKey(request1, optionsWithNoHeader);

        // Create and add responses
        GraphqlResponse<Data, Error> response1 = createResponse("sku1");
        GraphqlResponse<Data, Error> response2 = createResponse("sku2");

        cache.put(cacheKey1, response1);
        cache.put(cacheKey2, response2);
        cache.put(cacheKey3, response2);
        cache.put(cacheKeyWithNoHeader, response1);
        cache.put(cacheKeyWithDifferentHeader, response1);

        return cache;
    }

    private Cache<CacheKey, GraphqlResponse<?, ?>> createAndPopulateCache2() {
        Cache<CacheKey, GraphqlResponse<?, ?>> cache = CacheBuilder.newBuilder().build();

        GraphqlRequest request1 = new GraphqlRequest("{dummy1}");
        GraphqlRequest request2 = new GraphqlRequest("{dummy2}");

        RequestOptions options1 = createRequestOptions(DEFAULT_STORE);
        RequestOptions options2 = createRequestOptions(DEFAULT_TEST_STORE);

        CacheKey cacheKey1 = new CacheKey(request1, options1);
        CacheKey cacheKey2 = new CacheKey(request2, options2);

        GraphqlResponse<Data, Error> response1 = createResponse("sku1");
        GraphqlResponse<Data, Error> response2 = createResponse("sku2");

        cache.put(cacheKey1, response1);
        cache.put(cacheKey2, response2);

        return cache;
    }

    private RequestOptions createRequestOptions(String storeValue) {
        List<Header> headers = Collections.singletonList(new BasicHeader(STORE_HEADER, storeValue));
        return new RequestOptions().withHeaders(headers);
    }

    private RequestOptions createRequestOptionsWithDifferentHeader() {
        List<Header> headers = Collections.singletonList(new BasicHeader("test", DEFAULT_STORE));
        return new RequestOptions().withHeaders(headers);
    }

    private GraphqlResponse<Data, Error> createResponse(String text) {
        GraphqlResponse<Data, Error> response = new GraphqlResponse<>();
        response.setData(new Data());
        response.getData().text = text;
        return response;
    }

    private void initializeCacheInvalidator() {
        cacheInvalidator = new CacheInvalidator(caches);
    }

    private void initializeReflectionMethod() throws NoSuchMethodException {
        checkIfStorePresentMethod = CacheInvalidator.class.getDeclaredMethod("checkIfStorePresent", String.class, CacheKey.class);
        checkIfStorePresentMethod.setAccessible(true);
    }

    private void storeInitialCounts() {
        initialCounts = caches.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().asMap().size()));
    }

    // Test methods organized by category

    @Test
    public void testInvalidateAll() {
        cacheInvalidator.invalidateCache(null, null, null);
        assertCachesInvalidated();
    }

    @Test
    public void testInvalidateAllWithEmptyStoreView() {
        cacheInvalidator.invalidateCache("", null, null);
        assertCachesInvalidated();
    }

    @Test
    public void testInvalidateAllWithEmptyArrays() {
        cacheInvalidator.invalidateCache(null, new String[] {}, new String[] {});
        assertCachesInvalidated();
    }

    @Test
    public void testInvalidateStoreView() throws InvocationTargetException, IllegalAccessException {
        cacheInvalidator.invalidateCache(DEFAULT_STORE, null, null);
        assertInvalidateStoreView(DEFAULT_STORE);
    }

    @Test
    public void testInvalidateStoreViewWithEmptyArray() throws InvocationTargetException, IllegalAccessException {
        cacheInvalidator.invalidateCache(DEFAULT_STORE, new String[] {}, new String[] {});
        assertInvalidateStoreView(DEFAULT_STORE);
    }

    @Test
    public void testInvalidateSpecificCache() {
        cacheInvalidator.invalidateCache(null, new String[] { CACHE1 }, null);
        assertInvalidateSpecificCaches(CACHE1);
    }

    @Test
    public void testInvalidateMultipleCaches() {
        cacheInvalidator.invalidateCache(null, new String[] { CACHE1, CACHE2 }, null);
        assertInvalidateSpecificCaches(CACHE1, CACHE2);
    }

    @Test
    public void testInvalidateCacheWithInvalidCacheNames() {
        cacheInvalidator.invalidateCache(null, new String[] { "cachetest1" }, null);
        assertCachesUnchanged();
    }

    @Test
    public void testInvalidateCacheWithCacheNameAndStoreView() throws InvocationTargetException, IllegalAccessException {
        assertCacheInvalidation(DEFAULT_STORE, new String[] { CACHE1 }, null, null);
    }

    @Test
    public void testInvalidateCacheWithPattern() throws InvocationTargetException, IllegalAccessException {
        assertCacheInvalidation(DEFAULT_STORE, null, new String[] { "\"text\":\\s*\"(sku2)\"" }, "sku2");
    }

    @Test
    public void testInvalidateCacheWithMultiplePatterns() throws InvocationTargetException, IllegalAccessException {
        assertCacheInvalidation(DEFAULT_STORE, null,
            new String[] { "\"text\":\\s*\"(sku2)\"", "\"text\":\\s*\"(sku1)\"" },
            "sku2", "sku1");
    }

    @Test
    public void testInvalidateCacheWithNullPattern() {
        cacheInvalidator.invalidateCache(DEFAULT_STORE, null, new String[] { null });
        assertCachesUnchanged();
    }

    @Test
    public void testInvalidateCacheWithInvalidRegexPattern() {
        try {
            cacheInvalidator.invalidateCache(DEFAULT_STORE, null, new String[] { "[invalid(regex" });
            assertCachesUnchanged();
        } catch (Exception e) {
            assertTrue("Expected PatternSyntaxException but got: " + e.getClass().getName(),
                e instanceof java.util.regex.PatternSyntaxException);
        }
    }

    @Test
    public void testInvalidateCacheWithNullStoreViewAndValidCacheNames() {
        cacheInvalidator.invalidateCache(null, new String[] { CACHE1 }, new String[] { "\"text\":\\s*\"(sku1)\"" });
        // When store view is null, no entries should be invalidated based on store view matching
        assertCachesUnchanged();
    }

    @Test
    public void testInvalidateCacheWithEmptyStoreViewAndValidCacheNames() throws InvocationTargetException, IllegalAccessException {
        cacheInvalidator.invalidateCache("", new String[] { CACHE1 }, new String[] { "\"text\":\\s*\"(sku1)\"" });
        assertCacheInvalidation("", new String[] { CACHE1 }, new String[] { "\"text\":\\s*\"(sku1)\"" }, "sku1");
    }

    @Test
    public void testInvalidateCacheWithMixedValidAndInvalidPatterns() throws InvocationTargetException, IllegalAccessException {
        cacheInvalidator.invalidateCache(DEFAULT_STORE, null,
            new String[] { "\"text\":\\s*\"(sku1)\"", null, "[[invalid(regex]]" });
        // Should only invalidate entries matching the valid pattern
        assertCacheInvalidation(DEFAULT_STORE, null, new String[] { "\"text\":\\s*\"(sku1)\"" }, "sku1");
    }

    @Test
    public void testInvalidateCacheWithStoreViewAndPatterns() throws InvocationTargetException, IllegalAccessException {
        cacheInvalidator.invalidateCache(DEFAULT_STORE, null, new String[] { "\"text\":\\s*\"(sku1)\"" });
        assertCacheInvalidation(DEFAULT_STORE, null, new String[] { "\"text\":\\s*\"(sku1)\"" }, "sku1");
    }

    // Helper assertion methods

    private void assertCacheInvalidation(String storeView, String[] cacheNames, String[] patterns, String... expectedTexts)
        throws InvocationTargetException, IllegalAccessException {
        cacheInvalidator.invalidateCache(storeView, cacheNames, patterns);

        caches.forEach((cacheName, cache) -> {
            if (cacheNames == null || Arrays.asList(cacheNames).contains(cacheName)) {
                cache.asMap().forEach((key, response) -> {
                    try {
                        boolean storeViewMatches = storeView == null || checkIfStorePresent(storeView, key);
                        boolean textMatches = expectedTexts == null || (response.getData() != null &&
                            Arrays.stream(expectedTexts).anyMatch(text -> text.equals(((Data) response.getData()).text)));

                        if (storeViewMatches && textMatches) {
                            fail("Cache entry should have been invalidated but was found: " + key);
                        }
                    } catch (InvocationTargetException | IllegalAccessException e) {
                        throw new RuntimeException("Error checking store presence", e);
                    }
                });
            }
        });
    }

    private void assertCachesInvalidated() {
        caches.values().forEach(cache -> assertTrue(cache.asMap().isEmpty()));
    }

    private void assertCachesUnchanged() {
        caches.forEach((name, cache) -> assertEquals(initialCounts.get(name).intValue(), cache.asMap().size()));
    }

    private void assertInvalidateStoreView(String storeView) throws InvocationTargetException, IllegalAccessException {
        caches.values().forEach(cache -> cache.asMap().keySet().forEach(key -> {
            try {
                assertFalse("Store view " + storeView + " found in headers",
                    checkIfStorePresent(storeView, key));
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException("Error checking store presence", e);
            }
        }));
    }

    private void assertInvalidateSpecificCaches(String... cacheNames) {
        Set<String> cacheNamesSet = new HashSet<>(Arrays.asList(cacheNames));
        caches.forEach((name, cache) -> {
            if (cacheNamesSet.contains(name)) {
                assertTrue(cache.asMap().isEmpty());
            } else {
                assertFalse(cache.asMap().isEmpty());
            }
        });
    }

    private boolean checkIfStorePresent(String storeView, CacheKey cacheKey) throws InvocationTargetException, IllegalAccessException {
        return (boolean) checkIfStorePresentMethod.invoke(cacheInvalidator, storeView, cacheKey);
    }
}
