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

package com.adobe.cq.commerce.graphql.client.impl;

import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpStatus;
import org.apache.http.client.ResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.apache.sling.testing.mock.osgi.MockOsgi;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import io.wcm.testing.mock.aem.junit.AemContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

public class GraphqlClientImplMetricsTest {

    @Rule
    public final AemContext aemContext = GraphqlAemContext.createContext();
    private final MetricRegistry metricRegistry = new MetricRegistry();
    private final GraphqlClientImpl graphqlClient = new GraphqlClientImpl();
    private final GraphqlRequest dummy = new GraphqlRequest("{dummy-é}"); // with accent to check UTF-8 character

    private CloseableHttpClient httpClient;

    private static class Data {}

    private static class Error {}

    @Before
    public void setUp() {

        httpClient = Mockito.mock(CloseableHttpClient.class);
        // Mock HttpClientBuilderFactory to return our mocked HttpClient
        HttpClientBuilderFactory mockBuilderFactory = mock(HttpClientBuilderFactory.class);
        HttpClientBuilder mockBuilder = mock(HttpClientBuilder.class);
        when(mockBuilderFactory.newBuilder()).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn((CloseableHttpClient) httpClient);

        aemContext.registerService(HttpClientBuilderFactory.class, mockBuilderFactory);
        aemContext.registerService(MetricRegistry.class, metricRegistry, "name", "cif");
        aemContext.registerInjectActivateService(graphqlClient,
            "identifier", "default",
            "url", "https://foo.bar/api");

    }

    @Test
    public void testRequestDurationTracked() throws IOException {
        // given
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);

        // when
        graphqlClient.execute(dummy, Data.class, Error.class);

        // then
        Timer timer = metricRegistry.getTimers().get("graphql-client.request.duration;gql_client_endpoint=https://foo.bar/api");
        assertNotNull(timer);
        assertEquals(1, timer.getCount());
    }

    @Test
    public void testCacheMetricsAddedAndRemovedForMultipleCaches() throws IOException {
        // given
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);
        MockOsgi.activate(graphqlClient, aemContext.bundleContext(),
            "identifier", "default",
            "url", "https://foo.bar/api",
            "cacheConfigurations", new String[] {
                "foo:true:100:100",
                "bar:true:100:100"
            });

        // when, then
        Gauge<?> fooHits = metricRegistry.getGauges().get("graphql-client.cache.hits;gql_client_identifier=default;gql_cache_name=foo");
        assertNotNull(fooHits);
        Gauge<?> fooMisses = metricRegistry.getGauges().get("graphql-client.cache.misses;gql_client_identifier=default;gql_cache_name=foo");
        assertNotNull(fooMisses);
        Gauge<?> fooEvictions = metricRegistry.getGauges().get(
            "graphql-client.cache.evictions;gql_client_identifier=default;gql_cache_name=foo");
        assertNotNull(fooEvictions);
        Gauge<?> fooUsage = metricRegistry.getGauges().get("graphql-client.cache.usage;gql_client_identifier=default;gql_cache_name=foo");
        assertNotNull(fooUsage);
        Gauge<?> barHits = metricRegistry.getGauges().get("graphql-client.cache.hits;gql_client_identifier=default;gql_cache_name=bar");
        assertNotNull(barHits);
        Gauge<?> barMisses = metricRegistry.getGauges().get("graphql-client.cache.misses;gql_client_identifier=default;gql_cache_name=bar");
        assertNotNull(barMisses);
        Gauge<?> barEvictions = metricRegistry.getGauges().get(
            "graphql-client.cache.evictions;gql_client_identifier=default;gql_cache_name=bar");
        assertNotNull(barEvictions);
        Gauge<?> barUsage = metricRegistry.getGauges().get("graphql-client.cache.usage;gql_client_identifier=default;gql_cache_name=bar");
        assertNotNull(barUsage);

        // and when, then
        MockOsgi.deactivate(graphqlClient, aemContext.bundleContext());
        assertEquals(0, metricRegistry.getGauges().size());
    }

    @Test
    public void testConnectionPoolMetricsAddedAndRemoved() throws IOException {
        // given
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);
        MockOsgi.activate(graphqlClient, aemContext.bundleContext(),
            "identifier", "default", "url", "https://example.com");

        // when, then
        Gauge<?> availableConnections = metricRegistry.getGauges().get(
            "graphql-client.connection-pool.available-connections;gql_client_identifier=default");
        assertNotNull(availableConnections);
        Gauge<?> pendingRequests = metricRegistry.getGauges().get(
            "graphql-client.connection-pool.pending-requests;gql_client_identifier=default");
        assertNotNull(pendingRequests);
        Gauge<?> usage = metricRegistry.getGauges().get("graphql-client.connection-pool.usage;gql_client_identifier=default");
        assertNotNull(usage);

        // and when, then
        MockOsgi.deactivate(graphqlClient, aemContext.bundleContext());
        assertEquals(0, metricRegistry.getGauges().size());
    }

    @Test
    public void testRequestDurationNotTrackedOnClientError() throws IOException {
        // given
        doThrow(new IOException()).when(httpClient).execute(any(), any(ResponseHandler.class));

        // when
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail();
        } catch (RuntimeException ex) {
            // expected
        }

        // then
        Timer timer = metricRegistry.getTimers().get("graphql-client.request.duration;gql_client_endpoint=https://foo.bar/api");
        assertNotNull(timer);
        assertEquals(0, timer.getCount());
        Counter counter = metricRegistry.getCounters().get("graphql-client.request.errors;gql_client_endpoint=https://foo.bar/api");
        assertNotNull(counter);
        assertEquals(1, counter.getCount());
    }

    @Test
    public void testRequestDurationNotTrackedOnEntityLoadError() throws IOException {
        // given
        InputStream is = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException();
            }
        };
        TestUtils.setupHttpResponse(is, httpClient, HttpStatus.SC_OK);

        // when
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail();
        } catch (RuntimeException ex) {
            // expected
        }

        // then
        Timer timer = metricRegistry.getTimers().get("graphql-client.request.duration;gql_client_endpoint=https://foo.bar/api");
        assertNotNull(timer);
        assertEquals(0, timer.getCount());
        Counter counter = metricRegistry.getCounters().get("graphql-client.request.errors;gql_client_endpoint=https://foo.bar/api");
        assertNotNull(counter);
        assertEquals(1, counter.getCount());
    }

    @Test
    public void testErrorCodeTrackedWithStatus() throws IOException {
        // given
        TestUtils.setupHttpResponse(mock(InputStream.class), httpClient, HttpStatus.SC_INTERNAL_SERVER_ERROR);

        // when
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail();
        } catch (RuntimeException ex) {
            // expected
        }

        // then
        Counter counter = metricRegistry.getCounters().get(
            "graphql-client.request.errors;gql_client_endpoint=https://foo.bar/api;gql_response_status=500");
        assertNotNull(counter);
        assertEquals(1, counter.getCount());
    }
}
