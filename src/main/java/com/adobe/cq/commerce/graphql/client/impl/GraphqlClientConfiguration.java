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

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import com.adobe.cq.commerce.graphql.client.HttpMethod;

@ObjectClassDefinition(name = "CIF GraphQL Client Configuration Factory")
public @interface GraphqlClientConfiguration {

    public String CQ_GRAPHQL_CLIENT = "cq:graphqlClient";
    public String DEFAULT_IDENTIFIER = "default";

    public static final int MAX_HTTP_CONNECTIONS_DEFAULT = 20;
    public static final boolean ACCEPT_SELF_SIGNED_CERTIFICATES = false;

    @AttributeDefinition(
        name = "GraphQL Service Identifier",
        description = "A unique identifier for this GraphQL client, used by the JCR resource property " + CQ_GRAPHQL_CLIENT
            + " to identify the service.",
        type = AttributeType.STRING,
        required = true)
    String identifier() default DEFAULT_IDENTIFIER;

    @AttributeDefinition(
        name = "GraphQL Service URL",
        description = "The URL of the GraphQL server endpoint.",
        type = AttributeType.STRING,
        required = true)
    String url();

    @AttributeDefinition(
        name = "Default HTTP method",
        description = "The default HTTP method used to send GraphQL requests.",
        required = true)
    HttpMethod httpMethod() default HttpMethod.POST;

    @AttributeDefinition(
        name = "Accept self-signed SSL certificates",
        description = "Enable insecure/developer mode to accept self-signed SSL certificates. Do NOT activate on production systems!",
        type = AttributeType.BOOLEAN)
    boolean acceptSelfSignedCertificates() default ACCEPT_SELF_SIGNED_CERTIFICATES;

    @AttributeDefinition(
        name = "Max HTTP connections",
        description = "The maximum number of concurrent HTTP connections the connector can make",
        type = AttributeType.INTEGER)
    int maxHttpConnections() default MAX_HTTP_CONNECTIONS_DEFAULT;
}
