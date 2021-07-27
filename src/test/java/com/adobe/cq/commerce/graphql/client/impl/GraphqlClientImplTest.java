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
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.message.BasicHeader;
import org.hamcrest.CustomMatcher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.adobe.cq.commerce.graphql.client.GraphqlClientConfiguration;
import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.graphql.client.HttpMethod;
import com.adobe.cq.commerce.graphql.client.RequestOptions;
import com.adobe.cq.commerce.graphql.client.impl.TestUtils.GetQueryMatcher;
import com.adobe.cq.commerce.graphql.client.impl.TestUtils.HeadersMatcher;
import com.adobe.cq.commerce.graphql.client.impl.TestUtils.RequestBodyMatcher;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class GraphqlClientImplTest {

    private static final String AUTH_HEADER_VALUE = "Basic 1234";
    private static final String CACHE_HEADER_VALUE = "max-age=300";

    private static class Data {
        String text;
        Integer count;
    }

    private static class Error {
        String message;
    }

    private GraphqlClientImpl graphqlClient;
    private GraphqlRequest dummy = new GraphqlRequest("{dummy-é}"); // with accent to check UTF-8 character
    private MockGraphqlClientConfiguration mockConfig;

    @Before
    public void setUp() throws Exception {
        graphqlClient = new GraphqlClientImpl();

        mockConfig = new MockGraphqlClientConfiguration();
        // Add three test headers, one with extra white space around " : " to make sure we properly trim spaces, and one empty header
        mockConfig.setHttpHeaders(HttpHeaders.AUTHORIZATION + ":" + AUTH_HEADER_VALUE, HttpHeaders.CACHE_CONTROL + " : "
            + CACHE_HEADER_VALUE,
            "");

        graphqlClient.activate(mockConfig);
        graphqlClient.client = Mockito.mock(HttpClient.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSocketTimeout() throws Exception {
        mockConfig.setSocketTimeout(0);
        graphqlClient.activate(mockConfig);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidConnectionTimeout() throws Exception {
        mockConfig.setConnectionTimeout(0);
        graphqlClient.activate(mockConfig);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRequestPoolTimeout() throws Exception {
        mockConfig.setRequestPoolTimeout(0);
        graphqlClient.activate(mockConfig);
    }

    @Test
    public void testWarningsAreLoggedForTimeoutsTooBig() throws Exception {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger(GraphqlClientImpl.class);
        logger.setLevel(Level.WARN);
        Appender<ILoggingEvent> appender = mock(Appender.class);
        logger.addAppender(appender);

        mockConfig.setSocketTimeout(10000);
        mockConfig.setConnectionTimeout(10000);
        mockConfig.setRequestPoolTimeout(10000);
        graphqlClient.activate(mockConfig);

        // verify the 3 warnings are logged
        verify(appender, times(3)).doAppend(argThat(new CustomMatcher<ILoggingEvent>("log event of level warn") {
            @Override
            public boolean matches(Object o) {
                return o instanceof ILoggingEvent && ((ILoggingEvent) o).getLevel() == Level.WARN;
            }
        }));
    }

    @Test
    public void testRequestResponse() throws Exception {
        TestUtils.setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);

        dummy.setOperationName("customOperation");
        dummy.setVariables(Collections.singletonMap("variableName", "variableValue"));
        GraphqlResponse<Data, Error> response = graphqlClient.execute(dummy, Data.class, Error.class);

        // Check that the query is what we expect
        String body = TestUtils.getResource("sample-graphql-request.json");
        RequestBodyMatcher matcher = new RequestBodyMatcher(body);
        Mockito.verify(graphqlClient.client, Mockito.times(1)).execute(Mockito.argThat(matcher));

        // Check the response data
        assertEquals("Some text", response.getData().text);
        assertEquals(42, response.getData().count.intValue());

        // Check the response errors
        assertEquals(1, response.getErrors().size());
        Error error = response.getErrors().get(0);
        assertEquals("Error message", error.message);

        assertEquals(GraphqlClientConfiguration.DEFAULT_IDENTIFIER, graphqlClient.getIdentifier());
    }

    @Test
    public void testHttpError() throws Exception {
        TestUtils.setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_SERVICE_UNAVAILABLE);
        Exception exception = null;
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
        } catch (Exception e) {
            exception = e;
        }
        assertEquals("GraphQL query failed with response code 503", exception.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testHttpClientException() throws Exception {
        Mockito.when(graphqlClient.client.execute(Mockito.any())).thenThrow(IOException.class);
        Exception exception = null;
        try {
            graphqlClient.execute(dummy, Data.class, Error.class);
        } catch (Exception e) {
            exception = e;
        }
        assertEquals("Failed to send GraphQL request", exception.getMessage());
    }

    @Test
    public void testInvalidResponse() throws Exception {
        TestUtils.setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
        Exception exception = null;
        try {
            graphqlClient.execute(dummy, String.class, String.class);
        } catch (Exception e) {
            exception = e;
        }
        assertNotNull(exception);
    }

    @Test
    public void testHttpResponseError() throws Exception {
        TestUtils.setupNullResponse(graphqlClient.client);
        Exception exception = null;
        try {
            graphqlClient.execute(dummy, String.class, String.class);
        } catch (Exception e) {
            exception = e;
        }
        assertEquals("Failed to read HTTP response content", exception.getMessage());
    }

    @Test
    public void testCustomGson() throws Exception {

        // A custom deserializer that returns dummy data
        class CustomDeserializer implements JsonDeserializer<Data> {
            public Data deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                Data data = new Data();
                data.text = "customText";
                data.count = 4242;
                return data;
            }
        }

        Gson gson = new GsonBuilder().registerTypeAdapter(Data.class, new CustomDeserializer()).create();

        // The response from the JSON data is overwritten by the custom deserializer
        TestUtils.setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);

        GraphqlResponse<Data, Error> response = graphqlClient.execute(dummy, Data.class, Error.class, new RequestOptions().withGson(gson));
        assertEquals("customText", response.getData().text);
        assertEquals(4242, response.getData().count.intValue());
    }

    @Test
    public void testHeaders() throws Exception {
        TestUtils.setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
        List<Header> requestHeaders = Collections.singletonList(new BasicHeader("customName", "customValue"));
        graphqlClient.execute(dummy, Data.class, Error.class, new RequestOptions().withHeaders(requestHeaders));

        List<Header> expectedHeaders = new ArrayList<>();
        expectedHeaders.addAll(requestHeaders);
        expectedHeaders.add(new BasicHeader(HttpHeaders.AUTHORIZATION, AUTH_HEADER_VALUE));
        expectedHeaders.add(new BasicHeader(HttpHeaders.CACHE_CONTROL, CACHE_HEADER_VALUE));

        // Check that the HTTP client is sending the custom request headers and the headers set in the OSGi config
        HeadersMatcher matcher = new HeadersMatcher(expectedHeaders);
        Mockito.verify(graphqlClient.client, Mockito.times(1)).execute(Mockito.argThat(matcher));
    }

    private void testConfigureHeader(String header) throws Exception {
        graphqlClient = new GraphqlClientImpl();
        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setHttpHeaders(header);
        graphqlClient.activate(config);
        graphqlClient.execute(dummy, Data.class, Error.class);
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidHeaderSeparator() throws Exception {
        testConfigureHeader(HttpHeaders.AUTHORIZATION + "=" + AUTH_HEADER_VALUE);
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidHeaderNoValue() throws Exception {
        testConfigureHeader(HttpHeaders.AUTHORIZATION + ":");
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidHeaderNoName() throws Exception {
        testConfigureHeader(":" + AUTH_HEADER_VALUE);
    }

    @Test
    public void testGetHttpMethod() throws Exception {
        TestUtils.setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
        graphqlClient.execute(dummy, Data.class, Error.class, new RequestOptions().withHttpMethod(HttpMethod.GET));

        // Check that the GraphQL request is properly encoded in the URL
        GetQueryMatcher matcher = new GetQueryMatcher(dummy);
        Mockito.verify(graphqlClient.client, Mockito.times(1)).execute(Mockito.argThat(matcher));
    }

    @Test
    public void testGetHttpMethodWithVariables() throws Exception {
        String query = "query MyQuery($arg: String) {something(arg: $arg) {field}}";
        GraphqlRequest request = new GraphqlRequest(query);
        request.setOperationName("MyQuery");
        request.setVariables(Collections.singletonMap("arg", "something"));

        TestUtils.setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
        graphqlClient.execute(request, Data.class, Error.class, new RequestOptions().withHttpMethod(HttpMethod.GET));

        // Check that the GraphQL request is properly encoded in the URL
        GetQueryMatcher matcher = new GetQueryMatcher(request);
        Mockito.verify(graphqlClient.client, Mockito.times(1)).execute(Mockito.argThat(matcher));
    }

    @Test
    public void testGetGraphQLEndpoint() throws Exception {
        String endpointURL = graphqlClient.getGraphQLEndpoint();
        assertEquals(MockGraphqlClientConfiguration.URL, endpointURL);
    }

    @Test
    public void testGetConfiguration() {
        GraphqlClientConfiguration configuration = graphqlClient.getConfiguration();
        assertEquals(mockConfig, configuration);
    }
}
