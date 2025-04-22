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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.graphql.client.RequestOptions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;

public class CacheInvalidatorTest {

    private CacheInvalidator cacheInvalidator;

    private Map<String, Cache<CacheKey, GraphqlResponse<?, ?>>> caches;

    private Map<String, Integer> initialCounts;

    private Method checkIfStorePresentMethod;

    @Mock
    private Logger logger;

    private static class Data {
        String text;
    }

    private static class Error {
        String message;
    }

    @Before
    public void setUp() throws NoSuchMethodException {
        MockitoAnnotations.initMocks(this);

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
        List<Header> headersWithDifferentHeaders = Collections.singletonList(new BasicHeader("test", "default"));
        RequestOptions optionsWithDifferentHeaders = new RequestOptions().withHeaders(headersWithDifferentHeaders);
        RequestOptions optionsWithNoHeader = new RequestOptions();
        CacheKey cacheKey1 = new CacheKey(request1, options1);
        CacheKey cacheKey2 = new CacheKey(request2, options2);
        CacheKey cacheKey3 = new CacheKey(request3, options1);
        CacheKey cacheKeyWithDifferentHeader = new CacheKey(request1, optionsWithDifferentHeaders);
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
        cache1.put(cacheKeyWithDifferentHeader, response1);
        cache1.put(cacheKey3, response2);
        cache2.put(cacheKey2, response2);
        cache2.put(cacheKey1, response1);

        caches.put("cache1", cache1);
        caches.put("cache2", cache2);
        cacheInvalidator = new CacheInvalidator(caches);

        // Get the private method using reflection
        checkIfStorePresentMethod = CacheInvalidator.class.getDeclaredMethod("checkIfStorePresent", String.class, CacheKey.class);
        checkIfStorePresentMethod.setAccessible(true);

        // Set the logger field to the mock logger
        setLoggerField();

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
        assertCachesInvalidated();
    }

    @Test
    public void testInvalidateAllWithEmptyStoreView() {
        cacheInvalidator.invalidateCache("", null, null);
        assertCachesInvalidated();
    }

    @Test
    public void testInvalidateAllWithEmptyArray() {
        // Call the invalidateCache method with parameters that trigger invalidateAll
        cacheInvalidator.invalidateCache(null, new String[] {}, new String[] {});

        // Verify the caches were invalidated
        assertCachesInvalidated();
    }

    @Test
    public void testInvalidateStoreView() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        cacheInvalidator.invalidateCache("default", null, null);
        // Verify that the cache was invalidated for the store view, were it has been set as default
        assertInvalidateStoreView();
    }

    @Test
    public void testInvalidateStoreViewWithEmptyArray() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        cacheInvalidator.invalidateCache("default", new String[] {}, new String[] {});
        // Verify that the cache was invalidated for the store view, were it has been set as default
        assertInvalidateStoreView();
    }

    @Test
    public void testInvalidateCacheBasedOnSpecificCacheName() {
        cacheInvalidator.invalidateCache(null, new String[] { "cache1" }, null);
        assertInvalidateSpecificCaches("cache1");
    }

    @Test
    public void testInvalidateCacheBasedOnSpecificCacheNameWithEmptyArray() {
        cacheInvalidator.invalidateCache(null, new String[] { "cache1" }, new String[] {});
        assertInvalidateSpecificCaches("cache1");
    }

    @Test
    public void testInvalidateCacheWithMultipleCacheNames() {
        // This will clear cache1 and cache2 i.e all the caches
        cacheInvalidator.invalidateCache(null, new String[] { "cache1", "cache2" }, null);
        assertInvalidateSpecificCaches("cache1", "cache2");
    }

    @Test
    public void testInvalidateCacheWithInvalidCacheNames() {
        // This will not clear any cache as the cache names are invalid
        cacheInvalidator.invalidateCache(null, new String[] { "cachetest1" }, null);

        // Verify that the count of entries in each cache is the same as before
        for (Map.Entry<String, Cache<CacheKey, GraphqlResponse<?, ?>>> entry : caches.entrySet()) {
            assertEquals(initialCounts.get(entry.getKey()).intValue(), entry.getValue().asMap().size());
        }
    }

    @Test
    public void testInvalidateCacheWithCacheNameAndStoreView() throws InvocationTargetException, IllegalAccessException {
        assertCacheInvalidation("default", new String[] { "cache1" }, null, null);
    }

    @Test
    public void testInvalidateCacheInMultipleCacheListForSpecificPattern() throws InvocationTargetException, IllegalAccessException {
        assertCacheInvalidation("default", null, new String[] { "\"text\":\\s*\"(sku2)\"" }, "sku2");
    }

    @Test
    public void testInvalidateCacheForEmptySpecificPattern() {
        cacheInvalidator.invalidateCache("defaultTest", null, new String[] { "" });
        verify(logger).debug("Skipping null pattern in patterns array");

    }

    @Test
    public void testInvalidateCacheWithStoreViewDefaultTestAndCacheNameCache2AndTextSku2() throws InvocationTargetException,
        IllegalAccessException {
        assertCacheInvalidation("defaultTest", new String[] { "cache2" }, new String[] { "\"text\":\\s*\"(sku2)\"" }, "sku2");
    }

    @Test
    public void testInvalidateCacheWithStoreViewDefaultTestAndMultipleStringPatterns() throws InvocationTargetException,
        IllegalAccessException {
        assertCacheInvalidation("default", null, new String[] { "\"text\":\\s*\"(sku2)\"", "\"text\":\\s*\"(sku1)\"" }, "sku2", "sku1");
    }

    private void assertCacheInvalidation(String storeView, String[] cacheNames, String[] patterns, String... expectedTexts)
        throws InvocationTargetException, IllegalAccessException {
        cacheInvalidator.invalidateCache(storeView, cacheNames, patterns);

        for (Map.Entry<String, Cache<CacheKey, GraphqlResponse<?, ?>>> cacheEntry : caches.entrySet()) {
            Cache<CacheKey, GraphqlResponse<?, ?>> cache = cacheEntry.getValue();
            String cacheName = cacheEntry.getKey();
            for (Map.Entry<CacheKey, GraphqlResponse<?, ?>> entry : cache.asMap().entrySet()) {
                CacheKey key = entry.getKey();
                GraphqlResponse<?, ?> response = entry.getValue();

                boolean storeViewMatches = storeView == null || checkIfStorePresent(storeView, key);
                boolean cacheNameMatches = cacheNames == null || Arrays.asList(cacheNames).contains(cacheName);
                boolean textMatches = expectedTexts == null || (response.getData() != null && Arrays.stream(expectedTexts).anyMatch(
                    text -> text.equals(((Data) response.getData()).text)));

                assertFalse("Cache with specified criteria found", storeViewMatches && cacheNameMatches && textMatches);
            }
        }
    }

    @Test
    public void testInvalidateCacheWithStoreViewDefaultTestAndComplexStringPattern() throws InvocationTargetException,
        IllegalAccessException {
        assertCacheInvalidation("default", null, new String[] { "\"text\":\\s*\"(sku2|sku1)\"" }, "sku2", "sku1");
    }

    @Test
    public void testCheckIfStorePresentWithNullStoreView() throws InvocationTargetException, IllegalAccessException {
        GraphqlRequest request = new GraphqlRequest("{test}");
        List<Header> headers = Collections.singletonList(new BasicHeader("Store", "default"));
        RequestOptions options = new RequestOptions().withHeaders(headers);
        CacheKey cacheKey = new CacheKey(request, options);

        // Test with null storeView
        boolean result = checkIfStorePresent(null, cacheKey);
        assertFalse("Should return false when storeView is null", result);
    }

    @Test
    public void testCheckIfStorePresentWithNullCacheKey() throws InvocationTargetException, IllegalAccessException {
        // Test with null cacheKey
        boolean result = checkIfStorePresent("default", null);
        assertFalse("Should return false when cacheKey is null", result);
    }

    @Test
    public void testCheckIfStorePresentWithNullRequestOptions() throws InvocationTargetException, IllegalAccessException {
        GraphqlRequest request = new GraphqlRequest("{test}");
        CacheKey cacheKey = new CacheKey(request, null);

        // Test with null requestOptions
        boolean result = checkIfStorePresent("default", cacheKey);
        assertFalse("Should return false when requestOptions is null", result);
    }

    @Test
    public void testCheckIfStorePresentWithNullHeaders() throws InvocationTargetException, IllegalAccessException {
        GraphqlRequest request = new GraphqlRequest("{test}");
        RequestOptions options = new RequestOptions(); // No headers provided
        CacheKey cacheKey = new CacheKey(request, options);

        // Test with null headers
        boolean result = checkIfStorePresent("default", cacheKey);
        assertFalse("Should return false when headers are null", result);
    }

    @Test
    public void testCheckIfStorePresentWithEmptyHeaders() throws InvocationTargetException, IllegalAccessException {
        GraphqlRequest request = new GraphqlRequest("{test}");
        RequestOptions options = new RequestOptions().withHeaders(Collections.emptyList());
        CacheKey cacheKey = new CacheKey(request, options);

        // Test with empty headers list
        boolean result = checkIfStorePresent("default", cacheKey);
        assertFalse("Should return false when headers list is empty", result);
    }

    private void assertCachesInvalidated() {
        for (Cache<CacheKey, GraphqlResponse<?, ?>> cache : caches.values()) {
            assertTrue(cache.asMap().isEmpty());
        }
    }

    private void assertInvalidateStoreView() throws InvocationTargetException, IllegalAccessException {
        for (Cache<CacheKey, GraphqlResponse<?, ?>> cache : caches.values()) {
            for (CacheKey key : cache.asMap().keySet()) {
                boolean storeViewExists = checkIfStorePresent("default", key);
                assertFalse("Store view default not found in headers", storeViewExists);
            }
        }
    }

    private void assertInvalidateSpecificCaches(String... cacheNames) {
        // Verify that other caches are not empty
        Set<String> cacheNamesSet = new HashSet<>(Arrays.asList(cacheNames));
        for (Map.Entry<String, Cache<CacheKey, GraphqlResponse<?, ?>>> entry : caches.entrySet()) {
            if (cacheNamesSet.contains(entry.getKey())) {
                assertTrue(entry.getValue().asMap().isEmpty());
            } else {
                assertFalse(entry.getValue().asMap().isEmpty());
            }
        }
    }

    private boolean checkIfStorePresent(String storeView, CacheKey cacheKey) throws InvocationTargetException, IllegalAccessException {
        return (boolean) checkIfStorePresentMethod.invoke(cacheInvalidator, storeView, cacheKey);
    }

    private void setLoggerField() {
        try {
            Field loggerField = CacheInvalidator.class.getDeclaredField("LOGGER");
            loggerField.setAccessible(true);

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(loggerField, loggerField.getModifiers() & ~Modifier.FINAL);
            loggerField.set(null, logger);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set logger field", e);
        }
    }
}
