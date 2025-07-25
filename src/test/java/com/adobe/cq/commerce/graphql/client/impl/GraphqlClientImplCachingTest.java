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

import java.lang.reflect.Field;
import java.util.Collections;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.ResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.BundleContext;

import com.adobe.cq.commerce.graphql.client.CachingStrategy;
import com.adobe.cq.commerce.graphql.client.CachingStrategy.DataFetchingPolicy;
import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.graphql.client.RequestOptions;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    private CloseableHttpClient httpClient;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        httpClient = Mockito.mock(CloseableHttpClient.class);
        // Mock HttpClientBuilderFactory to return our mocked HttpClient
        HttpClientBuilderFactory mockBuilderFactory = mock(HttpClientBuilderFactory.class);
        HttpClientBuilder mockBuilder = mock(HttpClientBuilder.class);
        when(mockBuilderFactory.newBuilder()).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn((CloseableHttpClient) httpClient);

        graphqlClient = new GraphqlClientImpl();

        // Use reflection to set the private cacheInvalidator field
        Field clientBuilderFactory = GraphqlClientImpl.class.getDeclaredField("clientBuilderFactory");
        clientBuilderFactory.setAccessible(true);
        clientBuilderFactory.set(graphqlClient, mockBuilderFactory);

        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setCacheConfigurations(MY_CACHE + ":true:100:5", MY_DISABLED_CACHE + ":false:100:5", "");

        graphqlClient.activate(config, mock(BundleContext.class));

    }

    @Test(expected = IllegalStateException.class)
    public void testInvalidCacheConfiguration() throws Exception {
        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setCacheConfigurations(MY_CACHE + ":true:"); // Not enough parameters
        graphqlClient.activate(config, mock(BundleContext.class));
    }

    @Test(expected = NumberFormatException.class)
    public void testInvalidMaxSizeParameter() throws Exception {
        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setCacheConfigurations(MY_CACHE + ":true:bad:5"); // Cache max size must be an Integer
        graphqlClient.activate(config, mock(BundleContext.class));
    }

    @Test(expected = NumberFormatException.class)
    public void testInvalidTimeoutParameter() throws Exception {
        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setCacheConfigurations(MY_CACHE + ":true:100:bad"); // Cache timeout must be an Integer
        graphqlClient.activate(config, mock(BundleContext.class));
    }

    @Test
    public void testActiveCache() throws Exception {
        CachingStrategy cachingStrategy = new CachingStrategy()
            .withCacheName(MY_CACHE)
            .withDataFetchingPolicy(DataFetchingPolicy.CACHE_FIRST);
        RequestOptions requestOptions = new RequestOptions()
            .withCachingStrategy(cachingStrategy);

        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);
        GraphqlResponse<Data, Error> response = graphqlClient.execute(dummy, Data.class, Error.class, requestOptions);
        assertEquals("Some text", response.getData().text);
        assertEquals(42, response.getData().count.intValue());

        // This reponse is coming from the cache
        GraphqlResponse<Data, Error> response2 = graphqlClient.execute(dummy, Data.class, Error.class, requestOptions);
        assertEquals("Some text", response2.getData().text);
        assertEquals(42, response2.getData().count.intValue());

        // HTTP client was only called once
        Mockito.verify(httpClient).execute(Mockito.any(), Mockito.any(ResponseHandler.class));
    }

    @Test
    public void testActiveCacheButDifferentQueries() throws Exception {
        CachingStrategy cachingStrategy = new CachingStrategy()
            .withCacheName(MY_CACHE)
            .withDataFetchingPolicy(DataFetchingPolicy.CACHE_FIRST);
        RequestOptions requestOptions = new RequestOptions()
            .withCachingStrategy(cachingStrategy);

        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);
        GraphqlResponse<Data, Error> response = graphqlClient.execute(dummy, Data.class, Error.class, requestOptions);
        assertEquals("Some text", response.getData().text);
        assertEquals(42, response.getData().count.intValue());

        // This reponse is NOT coming from the cache, so we have to prepare the HTTP response again
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);
        GraphqlResponse<Data, Error> response2 = graphqlClient.execute(new GraphqlRequest("{dummy2}"), Data.class, Error.class,
            requestOptions);
        assertEquals("Some text", response2.getData().text);
        assertEquals(42, response2.getData().count.intValue());

        // HTTP client was called twice
        Mockito.verify(httpClient, Mockito.times(2)).execute(Mockito.any(), Mockito.any(ResponseHandler.class));
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

        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);
        GraphqlResponse<Data, Error> response = graphqlClient.execute(dummy, Data.class, Error.class, requestOptions1);
        assertEquals("Some text", response.getData().text);
        assertEquals(42, response.getData().count.intValue());

        // This reponse is NOT coming from the cache, so we have to prepare the HTTP response again
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);
        GraphqlResponse<Data, Error> response2 = graphqlClient.execute(dummy, Data.class, Error.class, requestOptions2);
        assertEquals("Some text", response2.getData().text);
        assertEquals(42, response2.getData().count.intValue());

        // HTTP client was called twice
        Mockito.verify(httpClient, Mockito.times(2)).execute(Mockito.any(), Mockito.any(ResponseHandler.class));
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
        graphqlClient.activate(new MockGraphqlClientConfiguration(), mock(BundleContext.class));

        CachingStrategy cachingStrategy = new CachingStrategy()
            .withCacheName(MY_CACHE)
            .withDataFetchingPolicy(DataFetchingPolicy.CACHE_FIRST);
        RequestOptions requestOptions = new RequestOptions()
            .withCachingStrategy(cachingStrategy);

        assertNoCaching(dummy, requestOptions);
    }

    @Test
    public void testEmptyCache() throws Exception {
        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setCacheConfigurations(ArrayUtils.EMPTY_STRING_ARRAY);
        graphqlClient.activate(config, mock(BundleContext.class));

        CachingStrategy cachingStrategy = new CachingStrategy()
            .withCacheName(MY_CACHE)
            .withDataFetchingPolicy(DataFetchingPolicy.CACHE_FIRST);
        RequestOptions requestOptions = new RequestOptions()
            .withCachingStrategy(cachingStrategy);

        assertNoCaching(dummy, requestOptions);
    }

    private void assertNoCaching(GraphqlRequest request, RequestOptions requestOptions) throws Exception {
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);
        GraphqlResponse<Data, Error> response = graphqlClient.execute(request, Data.class, Error.class, requestOptions);
        assertEquals("Some text", response.getData().text);
        assertEquals(42, response.getData().count.intValue());

        // This reponse is NOT coming from the cache, so we have to prepare the HTTP response again
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);
        GraphqlResponse<Data, Error> response2 = graphqlClient.execute(request, Data.class, Error.class, requestOptions);
        assertEquals("Some text", response2.getData().text);
        assertEquals(42, response2.getData().count.intValue());

        // HTTP client was called twice
        Mockito.verify(httpClient, Mockito.times(2)).execute(Mockito.any(), Mockito.any(ResponseHandler.class));
    }
}
