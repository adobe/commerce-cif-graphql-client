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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.HttpStatusCode.INTERNAL_SERVER_ERROR_500;

public class ConcurrencyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrencyTest.class);
    private static final int THREAD_COUNT = GraphqlClientConfiguration.MAX_HTTP_CONNECTIONS_DEFAULT * 2;

    private static ClientAndServer mockServer;

    private static class Data {
        String text;
        Integer count;
    }

    private static class Error {
        String message;
    }

    private GraphqlClientImpl graphqlClient;
    private GraphqlRequest dummy = new GraphqlRequest("{dummy}");

    @BeforeClass
    public static void initServer() throws IOException {
        mockServer = ClientAndServer.startClientAndServer();
        LOGGER.info("Started HTTP mock server on port {}", mockServer.getLocalPort());
    }

    @AfterClass
    public static void stopServer() {
        mockServer.stop();
    }

    @Before
    public void setUp() throws Exception {
        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setUrl("https://localhost:" + mockServer.getLocalPort() + "/graphql");
        config.setAcceptSelfSignedCertificates(true);

        graphqlClient = new GraphqlClientImpl();
        graphqlClient.activate(config);
    }

    @Test(timeout = 15000)
    public void testConcurrencyWithResponses() throws Exception {
        String json = getResource("sample-graphql-response.json");
        mockServer.reset()
            .when(HttpRequest.request().withPath("/graphql"))
            .respond(HttpResponse
                .response()
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .withHeader(HttpHeaders.CONNECTION, "keep-alive")
                .withBody(json)
                .withDelay(TimeUnit.MILLISECONDS, 50));

        ExecutorService service = Executors.newFixedThreadPool(THREAD_COUNT);
        Collection<Future<GraphqlResponse<Data, Error>>> futures = new ArrayList<>(THREAD_COUNT);
        for (int t = 0; t < THREAD_COUNT; t++) {
            futures.add(service.submit(() -> graphqlClient.execute(dummy, Data.class, Error.class)));
        }

        Collection<GraphqlResponse<Data, Error>> responses = new ArrayList<>();
        for (Future<GraphqlResponse<Data, Error>> f : futures) {
            responses.add(f.get());
        }

        assertEquals(THREAD_COUNT, responses.size());
        for (GraphqlResponse<Data, Error> response : responses) {
            assertEquals("Some text", response.getData().text);
            assertEquals(42, response.getData().count.intValue());

            // Check the response errors
            assertEquals(1, response.getErrors().size());
            Error error = response.getErrors().get(0);
            assertEquals("Error message", error.message);
        }
    }

    @Test(timeout = 15000)
    public void testConcurrencyWithErrors() throws Exception {

        // If the error messages are not consumed by the client, this test will time out
        // because all threads from the connection pool will stay stuck

        HttpResponse error = new HttpResponse()
            .withStatusCode(INTERNAL_SERVER_ERROR_500.code())
            .withReasonPhrase(INTERNAL_SERVER_ERROR_500.reasonPhrase())
            .withHeader(HttpHeaders.CONNECTION, "keep-alive")
            .withBody("Dummy content that MUST be consumed by the client")
            .withDelay(TimeUnit.MILLISECONDS, 50);

        mockServer.reset()
            .when(HttpRequest.request().withPath("/graphql"))
            .respond(error);

        ExecutorService service = Executors.newFixedThreadPool(THREAD_COUNT);
        Collection<Future<GraphqlResponse<Data, Error>>> futures = new ArrayList<>(THREAD_COUNT);
        for (int t = 0; t < THREAD_COUNT; t++) {
            futures.add(service.submit(() -> {
                try {
                    return graphqlClient.execute(dummy, Data.class, Error.class);
                } catch (Exception e) {
                    return null;
                }
            }));
        }

        Collection<GraphqlResponse<Data, Error>> responses = new ArrayList<>();
        for (Future<GraphqlResponse<Data, Error>> f : futures) {
            responses.add(f.get());
        }
        assertEquals(THREAD_COUNT, responses.size());
    }

    @Test(timeout = 15000)
    public void testConcurrencyWithTimeout() throws Exception {

        // The timeout of the HTTP client is much smaller than the timeout we define in the mock server
        // so we expect that all connection attemps will properly time out before the test itself times out

        HttpResponse error = new HttpResponse()
            .withStatusCode(INTERNAL_SERVER_ERROR_500.code())
            .withReasonPhrase(INTERNAL_SERVER_ERROR_500.reasonPhrase())
            .withHeader(HttpHeaders.CONNECTION, "keep-alive")
            .withDelay(TimeUnit.SECONDS, 60); // Must be higher than test timeout

        mockServer.reset()
            .when(HttpRequest.request().withPath("/graphql"))
            .respond(error);

        ExecutorService service = Executors.newFixedThreadPool(THREAD_COUNT);
        Collection<Future<GraphqlResponse<Data, Error>>> futures = new ArrayList<>(THREAD_COUNT);
        for (int t = 0; t < THREAD_COUNT; t++) {
            futures.add(service.submit(() -> {
                try {
                    return graphqlClient.execute(dummy, Data.class, Error.class);
                } catch (Exception e) {
                    return null;
                }
            }));
        }

        Collection<GraphqlResponse<Data, Error>> responses = new ArrayList<>();
        for (Future<GraphqlResponse<Data, Error>> f : futures) {
            responses.add(f.get());
        }
        assertEquals(THREAD_COUNT, responses.size());
    }

    private static String getResource(String filename) throws IOException {
        return IOUtils.toString(ConcurrencyTest.class.getClassLoader().getResourceAsStream(filename), StandardCharsets.UTF_8);
    }
}
