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

package com.adobe.cq.commerce.graphql.client;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "CIF GraphQL Client Configuration Factory")
public @interface GraphqlClientConfiguration {

    String CQ_GRAPHQL_CLIENT = "cq:graphqlClient";
    String DEFAULT_IDENTIFIER = "default";

    int MAX_HTTP_CONNECTIONS_DEFAULT = 20;
    boolean ACCEPT_SELF_SIGNED_CERTIFICATES = false;
    boolean ALLOW_HTTP_PROTOCOL = false;

    int DEFAULT_CONNECTION_TIMEOUT = 5000;
    int DEFAULT_SOCKET_TIMEOUT = 5000;
    int DEFAULT_REQUESTPOOL_TIMEOUT = 2000;
    int DEFAULT_CONNECTION_KEEP_ALIVE = -1;
    int DEFAULT_CONNECTION_TTL = -1;

    @AttributeDefinition(
        name = "GraphQL Service Identifier",
        description = "A unique identifier for this GraphQL client, used by the JCR resource property " + CQ_GRAPHQL_CLIENT
            + " to identify the service.",
        type = AttributeType.STRING,
        required = true)
    String identifier() default DEFAULT_IDENTIFIER;

    @AttributeDefinition(
        name = "GraphQL Service URL",
        description = "The URL of the GraphQL server endpoint, this must be a secure host using HTTPS.",
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
        name = "Allow HTTP communication",
        description = "Enable insecure/developer mode to allow communication via HTTP. Do NOT activate on production systems!",
        type = AttributeType.BOOLEAN)
    boolean allowHttpProtocol() default ALLOW_HTTP_PROTOCOL;

    @AttributeDefinition(
        name = "Max HTTP connections",
        description = "The maximum number of concurrent HTTP connections the connector can make",
        type = AttributeType.INTEGER)
    int maxHttpConnections() default MAX_HTTP_CONNECTIONS_DEFAULT;

    @AttributeDefinition(
        name = "Http connection timeout",
        description = "The timeout in milliseconds until a connection is established. Is the timeout longer than the default timeout a "
            + "warning will be logged. Is it 0 or smaller it will fallback to the default timeout and a warning will be logged. Defaults "
            + "to " + DEFAULT_CONNECTION_TIMEOUT,
        type = AttributeType.INTEGER)
    int connectionTimeout() default DEFAULT_CONNECTION_TIMEOUT;

    @AttributeDefinition(
        name = "Http socket timeout",
        description = "The socket timeout in milliseconds, which is the timeout for waiting for data or, put differently, a maximum period "
            + "inactivity between two consecutive data packets. Is the timeout longer than the default timeout a warning will be logged. "
            + "Is it 0 or smaller it will fallback to the default timeout and a warning will be logged. Defaults to "
            + DEFAULT_SOCKET_TIMEOUT,
        type = AttributeType.INTEGER)
    int socketTimeout() default DEFAULT_SOCKET_TIMEOUT;

    @AttributeDefinition(
        name = "Request pool timeout",
        description = "The timeout in milliseconds used when requesting a connection from the connection manager. Is the timeout longer "
            + "than the default timeout a warning will be logged. Is it 0 or smaller it will fallback to the default timeout and a "
            + "warning will be logged. Defaults to " + DEFAULT_REQUESTPOOL_TIMEOUT,
        type = AttributeType.INTEGER)
    int requestPoolTimeout() default DEFAULT_REQUESTPOOL_TIMEOUT;

    @AttributeDefinition(
        name = "Connection keep alive timeout",
        description = "The maximum number of seconds an unused connections is kept alive in the connection pool after the last response. "
            + "If the value is < 0 then connections are kept alive indefinitely. If the value is 0 then connections will never be reused. "
            + "Defaults to " + DEFAULT_CONNECTION_KEEP_ALIVE,
        type = AttributeType.INTEGER)
    int connectionKeepAlive() default DEFAULT_CONNECTION_KEEP_ALIVE;

    @AttributeDefinition(
        name = "Connection time to live",
        description = "The maximum number of seconds a connections is reused for. If the value is < 0 then the connection may be reused "
            + "indefinitely, depending on the configured connection keep alive timeout. If the value is 0 then connections will never be "
            + "reused. Defaults to " + DEFAULT_CONNECTION_TTL,
        type = AttributeType.INTEGER)
    int connectionTtl() default DEFAULT_CONNECTION_TTL;

    @AttributeDefinition(
        name = "Default HTTP Headers",
        description = "HTTP Headers which shall be sent with each request. Might be used for authentication. The format of each header is "
            + "name:value",
        type = AttributeType.STRING)
    String[] httpHeaders();

    @AttributeDefinition(
        name = "GraphQL cache configurations",
        description = "Each entry must follow the format NAME:ENABLE:MAXSIZE:TIMEOUT like for example product:true:1000:5 - "
            + "NAME (String) : the name of the cache - "
            + "ENABLE (true|false) : enables or disables the cache with that NAME - "
            + "MAXSIZE (Integer) : the maximum size of the cache in number of entries - "
            + "TIMEOUT (Integer) : the timeout for each cache entry, in seconds.",
        type = AttributeType.STRING)
    String[] cacheConfigurations();

    @AttributeDefinition(
        name = "Enable Fault Tolerant Fallback",
        description = "Enable fault tolerant fallback mechanism when encountering 503 (Service Unavailable) and other service errors. "
            + "When enabled, the client will use resilient error handling with fallback strategies. When disabled, the existing error handling flow will be used.",
        type = AttributeType.BOOLEAN)
    boolean enableFaultTolerantFallback() default false;

    @AttributeDefinition(
        name = "Ranking",
        description = "Integer value defining the ranking of this queue configuration. If more than one GraphQL Client use the same "
            + "identifier the one with the higher ranking will be used. Defaults to 0")
    int service_ranking() default 0;
}
