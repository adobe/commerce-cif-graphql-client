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
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import org.mockito.ArgumentMatcher;

import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.HttpMethod;
import com.google.gson.Gson;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestUtils {

    /**
     * Matcher class used to check that the GraphQL request body is properly set.
     */
    public static class RequestBodyMatcher extends ArgumentMatcher<HttpUriRequest> {

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
    public static class HeadersMatcher extends ArgumentMatcher<HttpUriRequest> {

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

    public static String encode(String str) throws UnsupportedEncodingException {
        return URLEncoder.encode(str, StandardCharsets.UTF_8.name());
    }

    /**
     * Matcher class used to check that the GraphQL query is properly set and encoded when sent with a GET request.
     */
    public static class GetQueryMatcher extends ArgumentMatcher<HttpUriRequest> {

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
    public static String setupHttpResponse(String filename, HttpClient httpClient, int httpCode) throws IOException {
        String json = getResource(filename);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        HttpResponse mockedHttpResponse = mock(HttpResponse.class);
        StatusLine mockedStatusLine = mock(StatusLine.class);

        when(mockedHttpResponse.getEntity()).then(inv -> {
            HttpEntity mockHttpEntity = mock(HttpEntity.class);
            when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(bytes));
            when(mockHttpEntity.getContentLength()).thenReturn(new Long(bytes.length));
            return mockHttpEntity;
        });
        when(httpClient.execute((HttpUriRequest) any())).thenReturn(mockedHttpResponse);

        when(mockedStatusLine.getStatusCode()).thenReturn(httpCode);
        when(mockedHttpResponse.getStatusLine()).thenReturn(mockedStatusLine);

        return json;
    }

    public static void setupHttpResponse(InputStream data, HttpClient httpClient, int httpCode) throws IOException {
        HttpEntity mockedHttpEntity = mock(HttpEntity.class);
        HttpResponse mockedHttpResponse = mock(HttpResponse.class);
        StatusLine mockedStatusLine = mock(StatusLine.class);

        when(mockedHttpEntity.getContent()).thenReturn(data);

        when(mockedHttpResponse.getEntity()).thenReturn(mockedHttpEntity);
        when(httpClient.execute((HttpUriRequest) any())).thenReturn(mockedHttpResponse);

        when(mockedStatusLine.getStatusCode()).thenReturn(httpCode);
        when(mockedHttpResponse.getStatusLine()).thenReturn(mockedStatusLine);
    }

    public static void setupNullResponse(HttpClient httpClient) throws IOException {
        HttpResponse mockedHttpResponse = mock(HttpResponse.class);
        StatusLine mockedStatusLine = mock(StatusLine.class);

        when(mockedHttpResponse.getEntity()).thenReturn(null);
        when(httpClient.execute((HttpUriRequest) any())).thenReturn(mockedHttpResponse);

        when(mockedStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(mockedHttpResponse.getStatusLine()).thenReturn(mockedStatusLine);
    }

    public static String getResource(String filename) throws IOException {
        return IOUtils.toString(GraphqlClientImplTest.class.getClassLoader().getResourceAsStream(filename), StandardCharsets.UTF_8);
    }
}
