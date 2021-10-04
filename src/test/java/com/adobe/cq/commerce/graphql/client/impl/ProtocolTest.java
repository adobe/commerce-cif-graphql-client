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

import org.apache.http.config.Registry;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;

import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.graphql.client.impl.MockServerHelper.Data;
import com.adobe.cq.commerce.graphql.client.impl.MockServerHelper.Error;

public class ProtocolTest {

    private static MockServerHelper mockServer;

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
        graphqlClient.activate(config);

        GraphqlResponse<Data, Error> response = mockServer.executeGraphqlClientDummyRequest(graphqlClient);
        mockServer.validateSampleResponse(response);
    }

    /**
     * Ensure HTTP communication is by default not allowed.
     */
    @Test(expected = RuntimeException.class)
    public void testSimpleRequest_HTTP_Disallowed() throws Exception {
        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setUrl("http://localhost:" + mockServer.getLocalPort() + "/graphql");

        GraphqlClientImpl graphqlClient = new GraphqlClientImpl();
        graphqlClient.activate(config);

        mockServer.executeGraphqlClientDummyRequest(graphqlClient);
    }

    /**
     * HTTP communcation should work if enabled via configuration.
     */
    @Test(timeout = 15000)
    public void testSimpleRequest_HTTP_Allowed() throws Exception {
        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setUrl("http://localhost:" + mockServer.getLocalPort() + "/graphql");
        config.setAllowHttpProtocol(true);

        GraphqlClientImpl graphqlClient = new GraphqlClientImpl();
        graphqlClient.activate(config);

        GraphqlResponse<Data, Error> response = mockServer.executeGraphqlClientDummyRequest(graphqlClient);
        mockServer.validateSampleResponse(response);
    }

    /**
     * HTTP communcation should work if enabled via proxy.
     */
    @Test(timeout = 15000)
    public void testSimpleRequest_HTTP_Allowed_Proxy() throws Exception {
        MockGraphqlClientConfiguration config = new MockGraphqlClientConfiguration();
        config.setUrl("https://localhost:" + mockServer.getLocalPort() + "/graphql");
        config.setAcceptSelfSignedCertificates(true);

        System.setProperty("http.proxyHost", "localhost");
        GraphqlClientImpl graphqlClient = new GraphqlClientImpl();
        graphqlClient.activate(config);

        Object connManager = Whitebox.getInternalState(graphqlClient.client, "connManager");
        Object connectionOperator = Whitebox.getInternalState(connManager, "connectionOperator");
        Object registry = Whitebox.getInternalState(connectionOperator, "socketFactoryRegistry");

        Registry<ConnectionSocketFactory> socketFactoryRegistry = (Registry<ConnectionSocketFactory>) registry;
        Assert.assertNotNull(socketFactoryRegistry.lookup("https"));
        Assert.assertNotNull(socketFactoryRegistry.lookup("http"));

        GraphqlResponse<Data, Error> response = mockServer.executeGraphqlClientDummyRequest(graphqlClient);
        mockServer.validateSampleResponse(response);
        System.clearProperty("http.proxyHost");
    }
}
