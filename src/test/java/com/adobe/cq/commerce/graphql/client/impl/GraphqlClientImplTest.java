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
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.graphql.client.impl.GraphqlClientImpl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GraphqlClientImplTest {

    private static class Data {
        String text;
        Integer count;
    }

    private static class Error {
        String message;
    }
    
    private GraphqlClientImpl graphqlClient;

    @Before
    public void setUp() throws Exception {
        graphqlClient = new GraphqlClientImpl();
        graphqlClient.activate(new MockGraphqlClientConfiguration());
        graphqlClient.client = Mockito.mock(HttpClient.class);
    }

    @Test
    public void testResponse() throws Exception {
        setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
        GraphqlResponse<Data, Error> response = graphqlClient.execute(new GraphqlRequest("{dummy}"), Data.class, Error.class, null);
        assertEquals("Some text", response.getData().text);
        assertEquals(42, response.getData().count.intValue());

        assertEquals(1, response.getErrors().size());
        Error error = response.getErrors().get(0);
        assertEquals("Error message", error.message);
    }

    @Test
    public void testHttpError() throws Exception {
        setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_SERVICE_UNAVAILABLE);
        Exception exception = null;
        try {
            graphqlClient.execute(new GraphqlRequest("{dummy}"), Data.class, Error.class, null);
        } catch (Exception e) {
            exception = e;
        }
        assertEquals("GraphQL query failed with response code 503", exception.getMessage());
    }

    @Test
    public void testInvalidResponse() throws Exception {
        setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
        Exception exception = null;
        try {
            graphqlClient.execute(new GraphqlRequest("{dummy}"), String.class, String.class, null);
        } catch (Exception e) {
            exception = e;
        }
        assertNotNull(exception);
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
        String json = IOUtils.toString(GraphqlClientImplTest.class.getClassLoader().getResourceAsStream(filename), StandardCharsets.UTF_8);

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
}
