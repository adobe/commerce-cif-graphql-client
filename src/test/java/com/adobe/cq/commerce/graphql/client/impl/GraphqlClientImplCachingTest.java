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

package com.adobe.cq.commerce.graphql.client.impl;

import java.util.Collections;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Test;

import com.adobe.cq.commerce.graphql.client.CachingStrategy;
import com.adobe.cq.commerce.graphql.client.CachingStrategy.DataFetchingPolicy;
import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.graphql.client.RequestOptions;
import com.day.cq.replication.ReplicationAction;
import com.day.cq.replication.ReplicationActionType;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class GraphqlClientImplCachingTest {

    private static class Data {
        String text;
        Integer count;
    }

    private static class Error {
        String message;
    }

    private GraphqlClientImpl graphqlClient;
    private GraphqlRequest dummy = new GraphqlRequest("{dummy}");

    private static final String MY_CACHE = "mycache";
    private static final String MY_DISABLED_CACHE = "mydisabledcache";

    @Before
    public void setUp() throws Exception {
        graphqlClient = new GraphqlClientImpl();

        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setCacheConfigurations(MY_CACHE + ":true:100:5", MY_DISABLED_CACHE + ":false:100:5", "");

        graphqlClient.activate(config);
        graphqlClient.client = mock(HttpClient.class);
    }

    @Test(expected = IllegalStateException.class)
    public void testInvalidCacheConfiguration() throws Exception {
        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setCacheConfigurations(MY_CACHE + ":true:"); // Not enough parameters
        graphqlClient.activate(config);
    }

    @Test(expected = NumberFormatException.class)
    public void testInvalidMaxSizeParameter() throws Exception {
        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setCacheConfigurations(MY_CACHE + ":true:bad:5"); // Cache max size must be an Integer
        graphqlClient.activate(config);
    }

    @Test(expected = NumberFormatException.class)
    public void testInvalidTimeoutParameter() throws Exception {
        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setCacheConfigurations(MY_CACHE + ":true:100:bad"); // Cache timeout must be an Integer
        graphqlClient.activate(config);
    }

    @Test
    public void testActiveCache() throws Exception {
        CachingStrategy cachingStrategy = new CachingStrategy()
            .withCacheName(MY_CACHE)
            .withDataFetchingPolicy(DataFetchingPolicy.CACHE_FIRST);
        RequestOptions requestOptions = new RequestOptions()
            .withCachingStrategy(cachingStrategy);

        TestUtils.setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
        GraphqlResponse<Data, Error> response = graphqlClient.execute(dummy, Data.class, Error.class, requestOptions);
        assertEquals("Some text", response.getData().text);
        assertEquals(42, response.getData().count.intValue());

        // This response is coming from the cache
        GraphqlResponse<Data, Error> response2 = graphqlClient.execute(dummy, Data.class, Error.class, requestOptions);
        assertEquals("Some text", response2.getData().text);
        assertEquals(42, response2.getData().count.intValue());

        // HTTP client was only called once
        verify(graphqlClient.client, times(1)).execute(any());
    }

    @Test
    public void testActiveCacheInvalidation() throws Exception {
        CachingStrategy cachingStrategy = new CachingStrategy()
            .withCacheName(MY_CACHE)
            .withDataFetchingPolicy(DataFetchingPolicy.CACHE_FIRST);
        RequestOptions requestOptions = new RequestOptions()
            .withCachingStrategy(cachingStrategy);

        CacheInvalidationHandler invalidationHandler = new CacheInvalidationHandler();
        invalidationHandler.graphqlClients = Collections.singletonList(graphqlClient);

        TestUtils.setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);

        // execute query
        graphqlClient.execute(dummy, Data.class, Error.class, requestOptions);

        // invalidate caches
        invalidationHandler.handleEvent(new ReplicationAction(ReplicationActionType.ACTIVATE, "/").toEvent());

        // execute query again
        graphqlClient.execute(dummy, Data.class, Error.class, requestOptions);

        // HTTP client was only called once
        verify(graphqlClient.client, times(2)).execute(any());
    }

    @Test
    public void testActiveCacheButDifferentQueries() throws Exception {
        CachingStrategy cachingStrategy = new CachingStrategy()
            .withCacheName(MY_CACHE)
            .withDataFetchingPolicy(DataFetchingPolicy.CACHE_FIRST);
        RequestOptions requestOptions = new RequestOptions()
            .withCachingStrategy(cachingStrategy);

        TestUtils.setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
        GraphqlResponse<Data, Error> response = graphqlClient.execute(dummy, Data.class, Error.class, requestOptions);
        assertEquals("Some text", response.getData().text);
        assertEquals(42, response.getData().count.intValue());

        // This reponse is NOT coming from the cache, so we have to prepare the HTTP response again
        TestUtils.setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
        GraphqlResponse<Data, Error> response2 = graphqlClient.execute(new GraphqlRequest("{dummy2}"), Data.class, Error.class,
            requestOptions);
        assertEquals("Some text", response2.getData().text);
        assertEquals(42, response2.getData().count.intValue());

        // HTTP client was called twice
        verify(graphqlClient.client, times(2)).execute(any());
    }

    @Test
    public void testNoCacheDifferentHttpHeaders() throws Exception {
        CachingStrategy cachingStrategy = new CachingStrategy()
            .withCacheName(MY_CACHE)
            .withDataFetchingPolicy(DataFetchingPolicy.CACHE_FIRST);

        RequestOptions requestOptions1 = new RequestOptions()
            .withCachingStrategy(cachingStrategy)
            .withHeaders(Collections.singletonList(new BasicHeader("Some", "value1")));

        RequestOptions requestOptions2 = new RequestOptions()
            .withCachingStrategy(cachingStrategy)
            .withHeaders(Collections.singletonList(new BasicHeader("Some", "value2")));

        TestUtils.setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
        GraphqlResponse<Data, Error> response = graphqlClient.execute(dummy, Data.class, Error.class, requestOptions1);
        assertEquals("Some text", response.getData().text);
        assertEquals(42, response.getData().count.intValue());

        // This reponse is NOT coming from the cache, so we have to prepare the HTTP response again
        TestUtils.setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
        GraphqlResponse<Data, Error> response2 = graphqlClient.execute(dummy, Data.class, Error.class, requestOptions2);
        assertEquals("Some text", response2.getData().text);
        assertEquals(42, response2.getData().count.intValue());

        // HTTP client was called twice
        verify(graphqlClient.client, times(2)).execute(any());
    }

    @Test
    public void testDisabledCache() throws Exception {
        CachingStrategy cachingStrategy = new CachingStrategy()
            .withCacheName(MY_DISABLED_CACHE)
            .withDataFetchingPolicy(DataFetchingPolicy.CACHE_FIRST);
        RequestOptions requestOptions = new RequestOptions()
            .withCachingStrategy(cachingStrategy);

        assertNoCaching(dummy, requestOptions);
    }

    @Test
    public void testCachingDisabledByPolicy() throws Exception {
        CachingStrategy cachingStrategy = new CachingStrategy()
            .withCacheName(MY_CACHE)
            .withDataFetchingPolicy(DataFetchingPolicy.NETWORK_ONLY);
        RequestOptions requestOptions = new RequestOptions()
            .withCachingStrategy(cachingStrategy);

        assertNoCaching(dummy, requestOptions);
    }

    @Test
    public void testNoRequesOptions() throws Exception {
        assertNoCaching(dummy, null);
    }

    @Test
    public void testNoCachingStrategy() throws Exception {
        RequestOptions requestOptions = new RequestOptions();
        assertNoCaching(dummy, requestOptions);
    }

    @Test
    public void testNoDataFetchingPolicy() throws Exception {
        CachingStrategy cachingStrategy = new CachingStrategy()
            .withCacheName(MY_CACHE);
        RequestOptions requestOptions = new RequestOptions()
            .withCachingStrategy(cachingStrategy);

        assertNoCaching(dummy, requestOptions);
    }

    @Test
    public void testNoCachingForMutation() throws Exception {
        CachingStrategy cachingStrategy = new CachingStrategy()
            .withCacheName(MY_CACHE)
            .withDataFetchingPolicy(DataFetchingPolicy.CACHE_FIRST);
        RequestOptions requestOptions = new RequestOptions()
            .withCachingStrategy(cachingStrategy);

        assertNoCaching(new GraphqlRequest("mutation{dummy}"), requestOptions);
    }

    @Test
    public void testNoCache() throws Exception {
        graphqlClient = new GraphqlClientImpl();
        graphqlClient.activate(new MockGraphqlClientConfiguration());
        graphqlClient.client = mock(HttpClient.class);

        CachingStrategy cachingStrategy = new CachingStrategy()
            .withCacheName(MY_CACHE)
            .withDataFetchingPolicy(DataFetchingPolicy.CACHE_FIRST);
        RequestOptions requestOptions = new RequestOptions()
            .withCachingStrategy(cachingStrategy);

        assertNoCaching(dummy, requestOptions);
    }

    @Test
    public void testEmptyCache() throws Exception {
        graphqlClient = new GraphqlClientImpl();
        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setCacheConfigurations(ArrayUtils.EMPTY_STRING_ARRAY);
        graphqlClient.activate(config);
        graphqlClient.client = mock(HttpClient.class);

        CachingStrategy cachingStrategy = new CachingStrategy()
            .withCacheName(MY_CACHE)
            .withDataFetchingPolicy(DataFetchingPolicy.CACHE_FIRST);
        RequestOptions requestOptions = new RequestOptions()
            .withCachingStrategy(cachingStrategy);

        assertNoCaching(dummy, requestOptions);
    }

    private void assertNoCaching(GraphqlRequest request, RequestOptions requestOptions) throws Exception {
        TestUtils.setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
        GraphqlResponse<Data, Error> response = graphqlClient.execute(request, Data.class, Error.class, requestOptions);
        assertEquals("Some text", response.getData().text);
        assertEquals(42, response.getData().count.intValue());

        // This reponse is NOT coming from the cache, so we have to prepare the HTTP response again
        TestUtils.setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
        GraphqlResponse<Data, Error> response2 = graphqlClient.execute(request, Data.class, Error.class, requestOptions);
        assertEquals("Some text", response2.getData().text);
        assertEquals(42, response2.getData().count.intValue());

        // HTTP client was called twice
        verify(graphqlClient.client, times(2)).execute(any());
    }
}
