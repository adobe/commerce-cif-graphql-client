/*******************************************************************************
 *
 *    Copyright 2026 Adobe. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.IncomingRequestHeaderProvider;
import com.adobe.cq.commerce.graphql.client.RequestOptions;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ForwardClientIpTest {

    private static final String CLIENT_IP = "1.2.3.4";
    private static final String PROXY_IP = "10.0.0.1";
    private static final String XFF_VALUE = CLIENT_IP + ", " + PROXY_IP;
    private static final String TARGET_HEADER = "X-Forwarded-By";

    private GraphqlClientImpl graphqlClient;
    private MockGraphqlClientConfiguration mockConfig;
    private CloseableHttpClient httpClient;
    private IncomingRequestHeaderProvider headerProvider;
    private HttpServletRequest servletRequest;
    private GraphqlRequest dummy = new GraphqlRequest("{dummy}");

    @Before
    public void setUp() throws Exception {
        graphqlClient = new GraphqlClientImpl();
        httpClient = Mockito.mock(CloseableHttpClient.class);

        HttpClientBuilderFactory mockBuilderFactory = mock(HttpClientBuilderFactory.class);
        HttpClientBuilder mockBuilder = mock(HttpClientBuilder.class);
        when(mockBuilderFactory.newBuilder()).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(httpClient);

        Field clientBuilderFactory = GraphqlClientImpl.class.getDeclaredField("clientBuilderFactory");
        clientBuilderFactory.setAccessible(true);
        clientBuilderFactory.set(graphqlClient, mockBuilderFactory);

        mockConfig = new MockGraphqlClientConfiguration();
        mockConfig.setIdentifier("mockIdentifier");

        headerProvider = mock(IncomingRequestHeaderProvider.class);
        servletRequest = mock(HttpServletRequest.class);

        Field providerField = GraphqlClientImpl.class.getDeclaredField("incomingRequestHeaderProvider");
        providerField.setAccessible(true);
        providerField.set(graphqlClient, headerProvider);
    }

    private void activateWithForwardingEnabled() throws Exception {
        mockConfig.setForwardClientIpEnabled(true);
        graphqlClient.activate(mockConfig, mock(org.osgi.framework.BundleContext.class));
    }

    @Test
    public void testFeatureDisabledDoesNotAddHeader() throws Exception {
        mockConfig.setForwardClientIpEnabled(false);
        graphqlClient.activate(mockConfig, mock(org.osgi.framework.BundleContext.class));
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);

        graphqlClient.execute(dummy, Data.class, Error.class);

        verify(httpClient).execute(Mockito.argThat(doesNotHaveHeader(TARGET_HEADER)), any(ResponseHandler.class));
    }

    @Test
    public void testProviderNullDoesNotAddHeader() throws Exception {
        Field providerField = GraphqlClientImpl.class.getDeclaredField("incomingRequestHeaderProvider");
        providerField.setAccessible(true);
        providerField.set(graphqlClient, null);

        activateWithForwardingEnabled();
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);

        graphqlClient.execute(dummy, Data.class, Error.class);

        verify(httpClient).execute(Mockito.argThat(doesNotHaveHeader(TARGET_HEADER)), any(ResponseHandler.class));
    }

    @Test
    public void testProviderReturnsNullRequestDoesNotAddHeader() throws Exception {
        when(headerProvider.getCurrentRequest()).thenReturn(null);

        activateWithForwardingEnabled();
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);

        graphqlClient.execute(dummy, Data.class, Error.class);

        verify(httpClient).execute(Mockito.argThat(doesNotHaveHeader(TARGET_HEADER)), any(ResponseHandler.class));
    }

    @Test
    public void testForwardsFirstIpFromXForwardedFor() throws Exception {
        when(headerProvider.getCurrentRequest()).thenReturn(servletRequest);
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(XFF_VALUE);

        activateWithForwardingEnabled();
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);

        graphqlClient.execute(dummy, Data.class, Error.class);

        List<Header> expectedHeaders = Collections.singletonList(new BasicHeader(TARGET_HEADER, CLIENT_IP));
        verify(httpClient).execute(Mockito.argThat(new TestUtils.HeadersMatcher(expectedHeaders)), any(ResponseHandler.class));
    }

    @Test
    public void testNoHeaderWhenXForwardedForMissing() throws Exception {
        when(headerProvider.getCurrentRequest()).thenReturn(servletRequest);
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);

        activateWithForwardingEnabled();
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);

        graphqlClient.execute(dummy, Data.class, Error.class);

        verify(httpClient).execute(Mockito.argThat(doesNotHaveHeader(TARGET_HEADER)), any(ResponseHandler.class));
    }

    @Test
    public void testExistingRequestOptionsHeadersPreserved() throws Exception {
        when(headerProvider.getCurrentRequest()).thenReturn(servletRequest);
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(CLIENT_IP);

        activateWithForwardingEnabled();
        TestUtils.setupHttpResponse("sample-graphql-response.json", httpClient, HttpStatus.SC_OK);

        List<Header> requestHeaders = Collections.singletonList(new BasicHeader("customName", "customValue"));
        RequestOptions options = new RequestOptions().withHeaders(requestHeaders);
        graphqlClient.execute(dummy, Data.class, Error.class, options);

        List<Header> expectedHeaders = new ArrayList<>();
        expectedHeaders.add(new BasicHeader("customName", "customValue"));
        expectedHeaders.add(new BasicHeader(TARGET_HEADER, CLIENT_IP));
        verify(httpClient).execute(Mockito.argThat(new TestUtils.HeadersMatcher(expectedHeaders)), any(ResponseHandler.class));

        assertEquals(1, options.getHeaders().size());
        assertEquals("customName", options.getHeaders().get(0).getName());
        assertEquals("customValue", options.getHeaders().get(0).getValue());
    }

    private static ArgumentMatcher<HttpUriRequest> doesNotHaveHeader(final String headerName) {
        return req -> req != null && req.getFirstHeader(headerName) == null;
    }

    private static class Data {
        String text;
    }

    private static class Error {
        String message;
    }
}
