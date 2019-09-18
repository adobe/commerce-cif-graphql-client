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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.graphql.client.HttpMethod;
import com.adobe.cq.commerce.graphql.client.RequestOptions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class GraphqlClientImplTest {

    private static class Data {
        String text;
        Integer count;
    }

    private static class Error {
        String message;
    }

    private GraphqlClientImpl graphqlClient;
    private GraphqlRequest dummy = new GraphqlRequest("{dummy}");

    @Before
    public void setUp() throws Exception {
        graphqlClient = new GraphqlClientImpl();
        graphqlClient.activate(new MockGraphqlClientConfiguration());
        graphqlClient.client = Mockito.mock(HttpClient.class);
    }

    @Test
    public void testRequestResponse() throws Exception {
        setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);

        dummy.setOperationName("customOperation");
        dummy.setVariables(Collections.singletonMap("variableName", "variableValue"));
        GraphqlResponse<Data, Error> response = graphqlClient.execute(dummy, Data.class, Error.class);

        // Check that the query is what we expect
        String body = getResource("sample-graphql-request.json");
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
        setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_SERVICE_UNAVAILABLE);
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
        setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
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
        setupNullResponse(graphqlClient.client);
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
        setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);

        GraphqlResponse<Data, Error> response = graphqlClient.execute(dummy, Data.class, Error.class, new RequestOptions().withGson(gson));
        assertEquals("customText", response.getData().text);
        assertEquals(4242, response.getData().count.intValue());
    }

    @Test
    public void testHeaders() throws Exception {
        setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
        List<Header> headers = Collections.singletonList(new BasicHeader("customName", "customValue"));
        graphqlClient.execute(dummy, Data.class, Error.class, new RequestOptions().withHeaders(headers));

        // Check that the HTTP client is sending the custom request headers
        HeadersMatcher matcher = new HeadersMatcher(headers);
        Mockito.verify(graphqlClient.client, Mockito.times(1)).execute(Mockito.argThat(matcher));
    }

    @Test
    public void testGetHttpMethod() throws Exception {
        setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
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

        setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
        graphqlClient.execute(request, Data.class, Error.class, new RequestOptions().withHttpMethod(HttpMethod.GET));

        // Check that the GraphQL request is properly encoded in the URL
        GetQueryMatcher matcher = new GetQueryMatcher(request);
        Mockito.verify(graphqlClient.client, Mockito.times(1)).execute(Mockito.argThat(matcher));
    }

    /**
     * Matcher class used to check that the GraphQL request body is properly set.
     */
    private static class RequestBodyMatcher extends ArgumentMatcher<HttpUriRequest> {

        private String body;

        public RequestBodyMatcher(String body) {
            this.body = body;
        }

        @Override
        public boolean matches(Object obj) {
            if (!(obj instanceof HttpUriRequest) && !(obj instanceof HttpEntityEnclosingRequest)) {
                return false;
            }
            HttpEntityEnclosingRequest req = (HttpEntityEnclosingRequest) obj;
            try {
                String body = IOUtils.toString(req.getEntity().getContent(), StandardCharsets.UTF_8);
                return body.equals(this.body);
            } catch (Exception e) {
                return false;
            }
        }

    }

    /**
     * Matcher class used to check that the headers are properly passed to the HTTP client.
     */
    private static class HeadersMatcher extends ArgumentMatcher<HttpUriRequest> {

        private List<Header> headers;

        public HeadersMatcher(List<Header> headers) {
            this.headers = headers;
        }

        @Override
        public boolean matches(Object obj) {
            if (!(obj instanceof HttpUriRequest)) {
                return false;
            }
            HttpUriRequest req = (HttpUriRequest) obj;
            for (Header header : headers) {
                Header reqHeader = req.getFirstHeader(header.getName());
                if (reqHeader == null || !reqHeader.getValue().equals(header.getValue())) {
                    return false;
                }
            }
            return true;
        }
    }

    private static String encode(String str) throws UnsupportedEncodingException {
        return URLEncoder.encode(str, StandardCharsets.UTF_8.name());
    }

    /**
     * Matcher class used to check that the GraphQL query is properly set and encoded when sent with a GET request.
     */
    private static class GetQueryMatcher extends ArgumentMatcher<HttpUriRequest> {

        GraphqlRequest request;

        public GetQueryMatcher(GraphqlRequest request) {
            this.request = request;
        }

        @Override
        public boolean matches(Object obj) {
            if (!(obj instanceof HttpUriRequest)) {
                return false;
            }
            HttpUriRequest req = (HttpUriRequest) obj;
            String expectedEncodedQuery = MockGraphqlClientConfiguration.URL;
            try {
                expectedEncodedQuery += "?query=" + encode(request.getQuery());
                if (request.getOperationName() != null) {
                    expectedEncodedQuery += "&operationName=" + encode(request.getOperationName());
                }
                if (request.getVariables() != null) {
                    String json = new Gson().toJson(request.getVariables());
                    expectedEncodedQuery += "&variables=" + encode(json);
                }
            } catch (UnsupportedEncodingException e) {
                return false;
            }
            return HttpMethod.GET.toString().equals(req.getMethod()) && expectedEncodedQuery.equals(req.getURI().toString());
        }
    }

    /**
     * This method prepares the mock http response with either the content of the <code>filename</code>
     * or the provided <code>content</code> String.<br>
     * <br>
     * <b>Important</b>: because of the way the content of an HTTP response is consumed, this method MUST be called each time
     * the client is called.
     *
     * @param filename The file to use for the json response.
     * @param httpClient The HTTP client for which we want to mock responses.
     * @param httpCode The http code that the mocked response will return.
     * @param startsWith When set, the body of the GraphQL POST request must start with that String.
     * 
     * @return The JSON content of that file.
     * 
     * @throws IOException
     */
    private static String setupHttpResponse(String filename, HttpClient httpClient, int httpCode) throws IOException {
        String json = getResource(filename);

        HttpEntity mockedHttpEntity = Mockito.mock(HttpEntity.class);
        HttpResponse mockedHttpResponse = Mockito.mock(HttpResponse.class);
        StatusLine mockedStatusLine = Mockito.mock(StatusLine.class);

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        Mockito.when(mockedHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(bytes));
        Mockito.when(mockedHttpEntity.getContentLength()).thenReturn(new Long(bytes.length));

        Mockito.when(mockedHttpResponse.getEntity()).thenReturn(mockedHttpEntity);
        Mockito.when(httpClient.execute((HttpUriRequest) Mockito.any())).thenReturn(mockedHttpResponse);

        Mockito.when(mockedStatusLine.getStatusCode()).thenReturn(httpCode);
        Mockito.when(mockedHttpResponse.getStatusLine()).thenReturn(mockedStatusLine);

        return json;
    }

    private static void setupNullResponse(HttpClient httpClient) throws IOException {
        HttpResponse mockedHttpResponse = Mockito.mock(HttpResponse.class);
        StatusLine mockedStatusLine = Mockito.mock(StatusLine.class);

        Mockito.when(mockedHttpResponse.getEntity()).thenReturn(null);
        Mockito.when(httpClient.execute((HttpUriRequest) Mockito.any())).thenReturn(mockedHttpResponse);

        Mockito.when(mockedStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        Mockito.when(mockedHttpResponse.getStatusLine()).thenReturn(mockedStatusLine);
    }

    private static String getResource(String filename) throws IOException {
        return IOUtils.toString(GraphqlClientImplTest.class.getClassLoader().getResourceAsStream(filename), StandardCharsets.UTF_8);
    }
}
