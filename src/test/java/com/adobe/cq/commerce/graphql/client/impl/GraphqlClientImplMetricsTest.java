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
import org.apache.http.client.HttpClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import io.wcm.testing.mock.aem.junit.AemContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class GraphqlClientImplMetricsTest {

    @Rule
    public final AemContext aemContext = GraphqlAemContext.createContext();
    private final MetricRegistry metricRegistry = new MetricRegistry();
    private final GraphqlClientImpl graphqlClient = new GraphqlClientImpl();
    private final GraphqlRequest dummy = new GraphqlRequest("{dummy-é}"); // with accent to check UTF-8 character
    private final MockGraphqlClientConfiguration mockConfig = new MockGraphqlClientConfiguration();

    private static class Data {}

    private static class Error {}

    @Before
    public void setUp() throws Exception {
        mockConfig.setUrl("http://foo.bar/api");
        aemContext.registerService(MetricRegistry.class, metricRegistry, "name", "cif");
        aemContext.registerInjectActivateService(graphqlClient);
        graphqlClient.activate(mockConfig);
        graphqlClient.client = mock(HttpClient.class);
    }

    @Test
    public void testRequestDurationTracked() throws IOException {
        // given
        TestUtils.setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);

        // when
        graphqlClient.execute(dummy, Data.class, Error.class);

        // then
        Timer timer = metricRegistry.getTimers().get("graphql-client.request.duration;endpoint=http://foo.bar/api");
        assertNotNull(timer);
        assertEquals(1, timer.getCount());
    }

    @Test
    public void testRequestDurationNotTrackedOnClientError() throws IOException {
        // given
        doThrow(new IOException()).when(graphqlClient.client).execute(any());

        // when
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail();
        } catch (RuntimeException ex) {
            // expected
        }

        // then
        Timer timer = metricRegistry.getTimers().get("graphql-client.request.duration;endpoint=http://foo.bar/api");
        assertNotNull(timer);
        assertEquals(0, timer.getCount());
        Counter counter = metricRegistry.getCounters().get("graphql-client.request.errors;endpoint=http://foo.bar/api");
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
        TestUtils.setupHttpResponse(is, graphqlClient.client, HttpStatus.SC_OK);

        // when
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail();
        } catch (RuntimeException ex) {
            // expected
        }

        // then
        Timer timer = metricRegistry.getTimers().get("graphql-client.request.duration;endpoint=http://foo.bar/api");
        assertNotNull(timer);
        assertEquals(0, timer.getCount());
        Counter counter = metricRegistry.getCounters().get("graphql-client.request.errors;endpoint=http://foo.bar/api");
        assertNotNull(counter);
        assertEquals(1, counter.getCount());
    }

    @Test
    public void testErrorCodeTrackedWithStatus() throws IOException {
        // given
        TestUtils.setupHttpResponse(mock(InputStream.class), graphqlClient.client, HttpStatus.SC_INTERNAL_SERVER_ERROR);

        // when
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
            fail();
        } catch (RuntimeException ex) {
            // expected
        }

        // then
        Counter counter = metricRegistry.getCounters().get("graphql-client.request.errors;endpoint=http://foo.bar/api;status=500");
        assertNotNull(counter);
        assertEquals(1, counter.getCount());
    }
}
