/*******************************************************************************
 *
 *    Copyright 2021 Adobe. All rights reserved.
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

import java.lang.annotation.Annotation;

import com.adobe.cq.commerce.graphql.client.GraphqlClientConfiguration;
import com.adobe.cq.commerce.graphql.client.HttpMethod;

/**
 * A mutable implementation of {@link GraphqlClientConfiguration}.
 */
class GraphqlClientConfigurationImpl implements Annotation, GraphqlClientConfiguration {

    private String url;
    private String identifier = GraphqlClientConfiguration.DEFAULT_IDENTIFIER;
    private HttpMethod httpMethod = HttpMethod.POST;
    private boolean acceptSelfSignedCertificates = GraphqlClientConfiguration.ACCEPT_SELF_SIGNED_CERTIFICATES;
    private boolean allowHttpProtocol = GraphqlClientConfiguration.ALLOW_HTTP_PROTOCOL;
    private int maxHttpConnections = GraphqlClientConfiguration.MAX_HTTP_CONNECTIONS_DEFAULT;
    private int connectionTimeout = GraphqlClientConfiguration.DEFAULT_CONNECTION_TIMEOUT;
    private int socketTimeout = GraphqlClientConfiguration.DEFAULT_SOCKET_TIMEOUT;
    private int requestPoolTimeout = GraphqlClientConfiguration.DEFAULT_REQUESTPOOL_TIMEOUT;
    private String[] httpHeaders = new String[0];
    private String[] cacheConfigurations = new String[0];
    private int connectionKeepAlive = GraphqlClientConfiguration.DEFAULT_CONNECTION_KEEP_ALIVE;
    private int connectionTtl = GraphqlClientConfiguration.DEFAULT_CONNECTION_TTL;
    private int serviceRanking = 0;
    private boolean enableFaultTolerantFallback = false;

    GraphqlClientConfigurationImpl(String url) {
        this.url = url;
    }

    GraphqlClientConfigurationImpl(GraphqlClientConfiguration configuration) {
        identifier = configuration.identifier();
        url = configuration.url();
        httpMethod = configuration.httpMethod();
        acceptSelfSignedCertificates = configuration.acceptSelfSignedCertificates();
        allowHttpProtocol = configuration.allowHttpProtocol();
        maxHttpConnections = configuration.maxHttpConnections();
        connectionTimeout = configuration.connectionTimeout();
        socketTimeout = configuration.socketTimeout();
        requestPoolTimeout = configuration.requestPoolTimeout();
        httpHeaders = configuration.httpHeaders() != null ? configuration.httpHeaders() : this.httpHeaders;
        cacheConfigurations = configuration.cacheConfigurations() != null ? configuration.cacheConfigurations() : this.cacheConfigurations;
        connectionKeepAlive = configuration.connectionKeepAlive();
        connectionTtl = configuration.connectionTtl();
        serviceRanking = configuration.service_ranking();
        enableFaultTolerantFallback = configuration.enableFaultTolerantFallback();
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return GraphqlClientConfiguration.class;
    }

    @Override
    public String identifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    @Override
    public String url() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public HttpMethod httpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
    }

    @Override
    public boolean acceptSelfSignedCertificates() {
        return acceptSelfSignedCertificates;
    }

    public void setAcceptSelfSignedCertificates(boolean acceptSelfSignedCertificates) {
        this.acceptSelfSignedCertificates = acceptSelfSignedCertificates;
    }

    @Override
    public boolean allowHttpProtocol() {
        return allowHttpProtocol;
    }

    public void setAllowHttpProtocol(boolean allowHttpProtocol) {
        this.allowHttpProtocol = allowHttpProtocol;
    }

    @Override
    public int maxHttpConnections() {
        return maxHttpConnections;
    }

    public void setMaxHttpConnections(int maxHttpConnections) {
        this.maxHttpConnections = maxHttpConnections;
    }

    @Override
    public int connectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    @Override
    public int socketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    @Override
    public int requestPoolTimeout() {
        return requestPoolTimeout;
    }

    public void setRequestPoolTimeout(int requestPoolTimeout) {
        this.requestPoolTimeout = requestPoolTimeout;
    }

    @Override
    public String[] httpHeaders() {
        return httpHeaders;
    }

    public void setHttpHeaders(String... httpHeaders) {
        this.httpHeaders = httpHeaders;
    }

    @Override
    public String[] cacheConfigurations() {
        return cacheConfigurations;
    }

    public void setCacheConfigurations(String... cacheConfigurations) {
        this.cacheConfigurations = cacheConfigurations;
    }

    @Override
    public int connectionKeepAlive() {
        return connectionKeepAlive;
    }

    public void setConnectionKeepAlive(int connectionKeepAlive) {
        this.connectionKeepAlive = connectionKeepAlive;
    }

    @Override
    public int connectionTtl() {
        return connectionTtl;
    }

    public void setConnectionTtl(int connectionTtl) {
        this.connectionTtl = connectionTtl;
    }

    @Override
    public int service_ranking() {
        return serviceRanking;
    }

    public void setServiceRanking(int serviceRanking) {
        this.serviceRanking = serviceRanking;
    }

    @Override
    public boolean enableFaultTolerantFallback() {
        return enableFaultTolerantFallback;
    }

    public void setEnableFaultTolerantFallback(boolean enableFaultTolerantFallback) {
        this.enableFaultTolerantFallback = enableFaultTolerantFallback;
    }
}
