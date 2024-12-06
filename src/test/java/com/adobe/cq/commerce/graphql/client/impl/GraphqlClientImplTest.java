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
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.*;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicListHeaderIterator;
import org.apache.http.protocol.HTTP;
import org.hamcrest.CustomMatcher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.adobe.cq.commerce.graphql.client.GraphqlClient;
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private CacheInvalidator cacheInvalidator;

    @Before
    public void setUp() throws Exception {
        graphqlClient = new GraphqlClientImpl();

        mockConfig = new MockGraphqlClientConfiguration();
        mockConfig.setIdentifier("mockIdentifier");
        // Add three test headers, one with extra white space around " : " to make sure we properly trim spaces, and one empty header
        mockConfig.setHttpHeaders(
            HttpHeaders.AUTHORIZATION + ":" + AUTH_HEADER_VALUE,
            HttpHeaders.CACHE_CONTROL + " : " + CACHE_HEADER_VALUE);

        graphqlClient.activate(mockConfig, mock(BundleContext.class));
        graphqlClient.client = Mockito.mock(HttpClient.class);

        cacheInvalidator = mock(CacheInvalidator.class);

        // Use reflection to set the private cacheInvalidator field
        Field cacheInvalidatorField = GraphqlClientImpl.class.getDeclaredField("cacheInvalidator");
        cacheInvalidatorField.setAccessible(true);
        cacheInvalidatorField.set(graphqlClient, cacheInvalidator);
    }

    @Test
    public void testRegistersAsGraphqlClientService() throws Exception {
        ArgumentCaptor<Dictionary> serviceProps = ArgumentCaptor.forClass(Dictionary.class);
        // given
        BundleContext bundleContext = mock(BundleContext.class);
        ServiceRegistration registration = mock(ServiceRegistration.class);
        when(bundleContext.registerService(eq(GraphqlClient.class), eq(graphqlClient), serviceProps.capture())).thenReturn(registration);
        mockConfig.setServiceRanking(200);

        // when
        graphqlClient.activate(mockConfig, bundleContext);

        // then
        verify(bundleContext).registerService(eq(GraphqlClient.class), eq(graphqlClient), any());
        assertEquals(200, serviceProps.getValue().get(Constants.SERVICE_RANKING));
        assertEquals("mockIdentifier", serviceProps.getValue().get("identifier"));

        // and when
        graphqlClient.deactivate();

        // then
        verify(registration).unregister();
    }

    @Test
    public void testEmptyUrlRegistersNoService() throws Exception {
        BundleContext bundleContext = mock(BundleContext.class);
        mockConfig.setUrl("");
        graphqlClient.activate(mockConfig, bundleContext);
        verify(bundleContext, never()).registerService(any(Class.class), any(GraphqlClientImpl.class), any());
        // verify that no exception is thrown
        graphqlClient.deactivate();
    }

    @Test
    public void testInvalidUrlRegistersNoService() throws Exception {
        BundleContext bundleContext = mock(BundleContext.class);
        mockConfig.setUrl("$[env:URL]");
        graphqlClient.activate(mockConfig, bundleContext);
        verify(bundleContext, never()).registerService(any(Class.class), any(GraphqlClientImpl.class), any());
        // verify that no exception is thrown
        graphqlClient.deactivate();
    }

    @Test
    public void testInvalidTimeouts() throws Exception {
        mockConfig.setSocketTimeout(0);
        mockConfig.setConnectionTimeout(0);
        mockConfig.setRequestPoolTimeout(0);
        graphqlClient.activate(mockConfig, mock(BundleContext.class));

        assertEquals(GraphqlClientConfiguration.DEFAULT_SOCKET_TIMEOUT, graphqlClient.getConfiguration().socketTimeout());
        assertEquals(GraphqlClientConfiguration.DEFAULT_CONNECTION_TIMEOUT, graphqlClient.getConfiguration().connectionTimeout());
        assertEquals(GraphqlClientConfiguration.DEFAULT_REQUESTPOOL_TIMEOUT, graphqlClient.getConfiguration().requestPoolTimeout());
    }

    @Test
    public void testWarningsAreLoggedForInvalidConfigurations() throws Exception {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger(GraphqlClientImpl.class);
        logger.setLevel(Level.WARN);
        Appender<ILoggingEvent> appender = mock(Appender.class, "testWarningsAreLoggedForTimeoutsTooBig");
        try {
            logger.addAppender(appender);

            mockConfig.setSocketTimeout(10000);
            mockConfig.setConnectionTimeout(10000);
            mockConfig.setRequestPoolTimeout(10000);
            mockConfig.setHttpHeaders("");
            graphqlClient.activate(mockConfig, mock(BundleContext.class));

            // verify the 3 warnings are logged
            verify(appender, times(4)).doAppend(argThat(new CustomMatcher<ILoggingEvent>("log event of level warn") {
                @Override
                public boolean matches(Object o) {
                    return o instanceof ILoggingEvent && ((ILoggingEvent) o).getLevel() == Level.WARN;
                }
            }));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    public void testInvalidHttpHeaders() throws Exception {
        mockConfig.setHttpHeaders("anything", "", ":Value", "Name: ", "Header: Value");
        graphqlClient.activate(mockConfig, mock(BundleContext.class));
        assertArrayEquals(new String[] { "Header: Value" }, graphqlClient.getConfiguration().httpHeaders());
    }

    @Test
    public void testInvalidHttpHeadersSkipped() throws Exception {
        // should not be possible in real world, but may happen if a regression is introduced that exposes the setHttpHeaders() of the
        // configuration
        TestUtils.setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
        GraphqlClientConfigurationImpl activeConfig = (GraphqlClientConfigurationImpl) graphqlClient.getConfiguration();
        activeConfig.setHttpHeaders("anything", "", ":Value", "Name: ", "Header: Value");
        graphqlClient.execute(dummy, Data.class, Error.class);

        List<Header> expectedHeaders = new ArrayList<>();
        expectedHeaders.add(new BasicHeader("Header", "Value"));

        // Check that the HTTP client is sending the custom request headers and the headers set in the OSGi config
        HeadersMatcher matcher = new HeadersMatcher(expectedHeaders);
        Mockito.verify(graphqlClient.client, Mockito.times(1)).execute(Mockito.argThat(matcher), Mockito.any(ResponseHandler.class));
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
        Mockito.verify(graphqlClient.client, Mockito.times(1)).execute(Mockito.argThat(matcher), Mockito.any(ResponseHandler.class));

        // Check the response data
        assertEquals("Some text", response.getData().text);
        assertEquals(42, response.getData().count.intValue());

        // Check the response errors
        assertEquals(1, response.getErrors().size());
        Error error = response.getErrors().get(0);
        assertEquals("Error message", error.message);
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
        when(graphqlClient.client.execute(any(HttpUriRequest.class), any(ResponseHandler.class))).thenThrow(IOException.class);
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
        Mockito.verify(graphqlClient.client, Mockito.times(1)).execute(Mockito.argThat(matcher), Mockito.any(ResponseHandler.class));
    }

    @Test
    public void testGetHttpMethod() throws Exception {
        TestUtils.setupHttpResponse("sample-graphql-response.json", graphqlClient.client, HttpStatus.SC_OK);
        graphqlClient.execute(dummy, Data.class, Error.class, new RequestOptions().withHttpMethod(HttpMethod.GET));

        // Check that the GraphQL request is properly encoded in the URL
        GetQueryMatcher matcher = new GetQueryMatcher(dummy);
        Mockito.verify(graphqlClient.client, Mockito.times(1)).execute(Mockito.argThat(matcher), Mockito.any(ResponseHandler.class));
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
        Mockito.verify(graphqlClient.client, Mockito.times(1)).execute(Mockito.argThat(matcher), Mockito.any(ResponseHandler.class));
    }

    @Test
    public void testGetGraphQLEndpoint() {
        String endpointURL = graphqlClient.getGraphQLEndpoint();
        assertEquals(MockGraphqlClientConfiguration.URL, endpointURL);
    }

    @Test
    public void testGetConfiguration() {
        GraphqlClientConfiguration configuration = graphqlClient.getConfiguration();
        assertEquals(mockConfig.identifier(), configuration.identifier());
        assertEquals("mockIdentifier", graphqlClient.getIdentifier());
    }

    @Test
    public void testDefaultConnectionKeepAlive() throws Exception {
        graphqlClient = new GraphqlClientImpl();
        mockConfig = new MockGraphqlClientConfiguration();
        graphqlClient.activate(mockConfig, mock(BundleContext.class));
        HttpClientBuilder builder = graphqlClient.configureHttpClientBuilder();
        assertNull(getBuilderKeepAliveStrategy(builder));
    }

    @Test
    public void testCustomConnectionKeepAlive() throws Exception {
        int customKeepAlive = 10;

        graphqlClient = new GraphqlClientImpl();
        mockConfig = new MockGraphqlClientConfiguration();
        mockConfig.setConnectionKeepAlive(customKeepAlive);
        graphqlClient.activate(mockConfig, mock(BundleContext.class));
        HttpClientBuilder builder = graphqlClient.configureHttpClientBuilder();
        ConnectionKeepAliveStrategy connectionKeepAliveStrategy = getBuilderKeepAliveStrategy(builder);

        assertTrue(connectionKeepAliveStrategy instanceof GraphqlClientImpl.ConfigurableConnectionKeepAliveStrategy);

        // with empty headers
        HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.headerIterator(anyString())).thenReturn(mock(HeaderIterator.class));
        assertEquals(customKeepAlive * 1000L, connectionKeepAliveStrategy.getKeepAliveDuration(httpResponse, null));

        // with keep alive header timeout invalid
        prepareResponse(httpResponse, "2.5");
        assertEquals(customKeepAlive * 1000L, connectionKeepAliveStrategy.getKeepAliveDuration(httpResponse, null));

        // with keep alive header timeout negative
        prepareResponse(httpResponse, "-1");
        assertEquals(customKeepAlive * 1000L, connectionKeepAliveStrategy.getKeepAliveDuration(httpResponse, null));

        // with keep alive header timeout smaller than custom
        int responseKeepAlive = 5;
        prepareResponse(httpResponse, String.valueOf(responseKeepAlive));
        assertEquals(responseKeepAlive * 1000L, connectionKeepAliveStrategy.getKeepAliveDuration(httpResponse, null));

        // with keep alive header timeout larger than custom
        prepareResponse(httpResponse, "15");
        assertEquals(customKeepAlive * 1000L, connectionKeepAliveStrategy.getKeepAliveDuration(httpResponse, null));
    }

    @Test
    public void testInvalidateCache() {
        String storeView = "default";
        String[] cacheNames = { "cache1", "cache2" };
        String[] patterns = { "pattern1", "pattern2" };

        graphqlClient.invalidateCache(storeView, cacheNames, patterns);

        verify(cacheInvalidator).invalidateCache(storeView, cacheNames, patterns);
    }

    private void prepareResponse(HttpResponse httpResponse, String responseKeepAlive) {
        Header header = mock(Header.class);
        when(header.getName()).thenReturn(HTTP.CONN_KEEP_ALIVE);
        when(header.getValue()).thenReturn("timeout=" + responseKeepAlive);
        when(httpResponse.headerIterator(anyString())).thenReturn(new BasicListHeaderIterator(Collections.singletonList(header), null));
    }

    ConnectionKeepAliveStrategy getBuilderKeepAliveStrategy(HttpClientBuilder builder) throws Exception {
        Field field = builder.getClass().getDeclaredField("keepAliveStrategy");
        field.setAccessible(true);
        return (ConnectionKeepAliveStrategy) field.get(builder);
    }
}
