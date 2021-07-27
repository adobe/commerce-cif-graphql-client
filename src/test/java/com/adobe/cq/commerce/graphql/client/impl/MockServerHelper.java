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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.mockserver.MockServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.client.GraphqlClient;
import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;

import static org.junit.Assert.assertEquals;

public class MockServerHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockServerHelper.class);

    static {
        ConfigurationProperties.logLevel("ERROR");
    }

    private final ClientAndServer mockServer;
    private final GraphqlRequest dummy = new GraphqlRequest("{dummy}");

    public MockServerHelper() {
        mockServer = ClientAndServer.startClientAndServer();
        LOGGER.info("Started HTTP mock server on port {}", mockServer.getLocalPort());
    }

    public GraphqlResponse<Data, Error> executeGraphqlClientDummyRequest(GraphqlClient graphqlClient)
        throws IOException {
        return graphqlClient.execute(dummy, Data.class, Error.class);
    }

    public void resetWithSampleResponse() {
        String json = getResource("sample-graphql-response.json");
        mockServer.reset().when(HttpRequest.request().withPath("/graphql"))
            .respond(HttpResponse.response().withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .withHeader(HttpHeaders.CONNECTION, "keep-alive").withBody(json)
                .withDelay(TimeUnit.MILLISECONDS, 50));
    }

    public void validateSampleResponse(GraphqlResponse<Data, Error> response) throws IOException {
        assertEquals("Some text", response.getData().text);
        assertEquals(42, response.getData().count.intValue());

        // Check the response errors
        assertEquals(1, response.getErrors().size());
        Error error = response.getErrors().get(0);
        assertEquals("Error message", error.message);
    }

    private static String getResource(String filename) {
        try {
            return IOUtils.toString(MockServerHelper.class.getClassLoader().getResourceAsStream(filename),
                StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public MockServerClient reset() {
        return mockServer.reset();
    }

    public void stop() {
        mockServer.stop();
    }

    public Integer getLocalPort() {
        return mockServer.getLocalPort();
    }

    public static class Data {
        String text;
        Integer count;
    }

    public static class Error {
        String message;
    }

}
