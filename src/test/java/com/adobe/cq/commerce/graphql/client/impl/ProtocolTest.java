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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.osgi.framework.BundleContext;

import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.graphql.client.impl.MockServerHelper.Data;
import com.adobe.cq.commerce.graphql.client.impl.MockServerHelper.Error;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class ProtocolTest {

    private static MockServerHelper mockServer;
    private static String JAVA_VERSION = System.getProperty("java.version");

    @BeforeClass
    public static void initServer() {
        mockServer = new MockServerHelper();
        mockServer.resetWithSampleResponse();
    }

    @AfterClass
    public static void stopServer() {
        mockServer.stop();
    }

    @Test(timeout = 15000)
    public void testSimpleRequest_HTTPS() throws Exception {
        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setUrl("https://localhost:" + mockServer.getLocalPort() + "/graphql");
        config.setAcceptSelfSignedCertificates(true);

        GraphqlClientImpl graphqlClient = new GraphqlClientImpl();
        graphqlClient.activate(config, mock(BundleContext.class));

        GraphqlResponse<Data, Error> response = mockServer.executeGraphqlClientDummyRequest(graphqlClient);
        mockServer.validateSampleResponse(response);
    }

    /**
     * Ensure HTTP communication is by default not allowed.
     */
    @Test
    public void testSimpleRequest_HTTP_Disallowed() throws Exception {
        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setUrl("http://localhost:" + mockServer.getLocalPort() + "/graphql");

        BundleContext bundleContext = mock(BundleContext.class);
        GraphqlClientImpl graphqlClient = new GraphqlClientImpl();
        graphqlClient.activate(config, bundleContext);
        verify(bundleContext, never()).registerService(any(Class.class), any(GraphqlClientImpl.class), any());
    }

    /**
     * HTTP communication should work if enabled via configuration.
     */
    @Test(timeout = 15000)
    public void testSimpleRequest_HTTP_Allowed() throws Exception {
        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setUrl("http://localhost:" + mockServer.getLocalPort() + "/graphql");
        config.setAllowHttpProtocol(true);

        GraphqlClientImpl graphqlClient = new GraphqlClientImpl();
        graphqlClient.activate(config, mock(BundleContext.class));

        GraphqlResponse<Data, Error> response = mockServer.executeGraphqlClientDummyRequest(graphqlClient);
        mockServer.validateSampleResponse(response);
    }

    @Test(timeout = 15000)
    public void testUserAgent() throws Exception {
        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setUrl("https://localhost:" + mockServer.getLocalPort() + "/graphql");
        config.setAcceptSelfSignedCertificates(true);

        GraphqlClientImpl graphqlClient = new GraphqlClientImpl();
        graphqlClient.activate(config, mock(BundleContext.class));

        MockServerClient client = mockServer.resetWithSampleResponse();
        mockServer.executeGraphqlClientDummyRequest(graphqlClient);

        client.verify(HttpRequest.request().withHeader("user-agent", "Adobe-CifGraphqlClient/TEST (Java/" + JAVA_VERSION + ")"));
    }
}
