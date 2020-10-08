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

import java.lang.annotation.Annotation;

import com.adobe.cq.commerce.graphql.client.GraphqlClientConfiguration;
import com.adobe.cq.commerce.graphql.client.HttpMethod;

public class MockGraphqlClientConfiguration implements Annotation, GraphqlClientConfiguration {

    public static final String URL = "https://hostname/graphql";
    private String url;
    private Boolean acceptSelfSignedCertificates;
    private Boolean allowHttpProtocol;
    private String[] httpHeaders;
    private String[] cacheConfigurations;

    @Override
    public String identifier() {
        return GraphqlClientConfiguration.DEFAULT_IDENTIFIER;
    }

    @Override
    public String url() {
        return url != null ? url : URL;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public boolean acceptSelfSignedCertificates() {
        return acceptSelfSignedCertificates != null ? acceptSelfSignedCertificates
            : GraphqlClientConfiguration.ACCEPT_SELF_SIGNED_CERTIFICATES;
    }

    @Override
    public boolean allowHttpProtocol() {
        return allowHttpProtocol != null ? allowHttpProtocol
            : GraphqlClientConfiguration.ALLOW_HTTP_PROTOCOL;
    }

    @Override
    public int maxHttpConnections() {
        return GraphqlClientConfiguration.MAX_HTTP_CONNECTIONS_DEFAULT;
    }

    @Override
    public int connectionTimeout() {
        return GraphqlClientConfiguration.DEFAULT_CONNECTION_TIMEOUT;
    }

    @Override
    public int socketTimeout() {
        return GraphqlClientConfiguration.DEFAULT_SOCKET_TIMEOUT;
    }

    @Override
    public int requestPoolTimeout() {
        return GraphqlClientConfiguration.DEFAULT_REQUESTPOOL_TIMEOUT;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return GraphqlClientConfiguration.class;
    }

    @Override
    public String[] httpHeaders() {
        return httpHeaders;
    }

    @Override
    public String[] cacheConfigurations() {
        return cacheConfigurations;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setAcceptSelfSignedCertificates(boolean acceptSelfSignedCertificates) {
        this.acceptSelfSignedCertificates = acceptSelfSignedCertificates;
    }

    public void setAllowHttpProtocol(Boolean allowHttpProtocol) {
        this.allowHttpProtocol = allowHttpProtocol;
    }

    public void setHttpHeaders(String... httpHeaders) {
        this.httpHeaders = httpHeaders;
    }

    public void setCacheConfigurations(String... cacheConfigurations) {
        this.cacheConfigurations = cacheConfigurations;
    }
}
