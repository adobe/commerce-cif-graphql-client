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

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.client.GraphqlClient;
import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.graphql.client.HttpMethod;
import com.adobe.cq.commerce.graphql.client.RequestOptions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@Component(service = GraphqlClient.class)
@Designate(ocd = GraphqlClientConfiguration.class, factory = true)
public class GraphqlClientImpl implements GraphqlClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphqlClientImpl.class);

    protected HttpClient client;
    private Gson gson;

    private String identifier;
    private String url;
    private boolean acceptSelfSignedCertificates;
    private int maxHttpConnections;
    private HttpMethod httpMethod;

    @Activate
    public void activate(GraphqlClientConfiguration configuration) throws Exception {
        identifier = configuration.identifier();
        url = configuration.url();
        acceptSelfSignedCertificates = configuration.acceptSelfSignedCertificates();
        maxHttpConnections = configuration.maxHttpConnections();
        httpMethod = configuration.httpMethod();

        client = buildHttpClient();
        gson = new Gson();
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public <T, U> GraphqlResponse<T, U> execute(GraphqlRequest request, Type typeOfT, Type typeofU) {
        return execute(request, typeOfT, typeofU, null);
    }

    @Override
    public <T, U> GraphqlResponse<T, U> execute(GraphqlRequest request, Type typeOfT, Type typeofU, RequestOptions options) {
        LOGGER.debug("Executing GraphQL query: " + request.getQuery());
        HttpResponse httpResponse;
        try {
            httpResponse = client.execute(buildRequest(request, options));
        } catch (Exception e) {
            throw new RuntimeException("Failed to send GraphQL request", e);
        }

        StatusLine statusLine = httpResponse.getStatusLine();
        if (HttpStatus.SC_OK == statusLine.getStatusCode()) {
            HttpEntity entity = httpResponse.getEntity();
            String json;
            try {
                json = EntityUtils.toString(entity, StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException("Failed to read HTTP response content", e);
            }

            Gson gson = (options != null && options.getGson() != null) ? options.getGson() : this.gson;
            Type type = TypeToken.getParameterized(GraphqlResponse.class, typeOfT, typeofU).getType();
            GraphqlResponse<T, U> response = gson.fromJson(json, type);

            // We log GraphQL errors because they might otherwise get "silently" unnoticed
            if (response.getErrors() != null) {
                Type listErrorsType = TypeToken.getParameterized(List.class, typeofU).getType();
                String errors = gson.toJson(response.getErrors(), listErrorsType);
                LOGGER.warn("GraphQL request {} returned some errors {}", request.getQuery(), errors);
            }

            return response;
        } else {
            throw new RuntimeException("GraphQL query failed with response code " + statusLine.getStatusCode());
        }
    }

    private HttpClient buildHttpClient() throws Exception {
        SSLConnectionSocketFactory sslsf = null;
        if (acceptSelfSignedCertificates) {
            LOGGER.warn("Self-signed SSL certificates are accepted. This should NOT be done on production systems!");
            SSLContext sslContext = SSLContextBuilder.create().loadTrustMaterial(new TrustSelfSignedStrategy()).build();
            sslsf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        } else {
            sslsf = new SSLConnectionSocketFactory(SSLContexts.createDefault(), new DefaultHostnameVerifier());
        }

        // We use a pooled connection manager to support concurrent threads and connections
        Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory>create().register("https", sslsf).build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(reg);
        cm.setMaxTotal(maxHttpConnections);
        cm.setDefaultMaxPerRoute(maxHttpConnections); // we just have one route to the GraphQL endpoint

        return HttpClientBuilder.create().setConnectionManager(cm).disableCookieManagement().build();
    }

    private HttpUriRequest buildRequest(GraphqlRequest request, RequestOptions options) throws UnsupportedEncodingException {
        HttpMethod httpMethod = this.httpMethod;
        if (options != null && options.getHttpMethod() != null) {
            httpMethod = options.getHttpMethod();
        }

        RequestBuilder rb = RequestBuilder.create(httpMethod.toString()).setUri(url);
        rb.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");

        if (HttpMethod.GET.equals(httpMethod)) {
            rb.addParameter("query", request.getQuery());
            if (request.getOperationName() != null) {
                rb.addParameter("operationName", request.getOperationName());
            }
            if (request.getVariables() != null) {
                String json = gson.toJson(request.getVariables());
                rb.addParameter("variables", json);
            }
        } else {
            rb.setEntity(new StringEntity(gson.toJson(request)));
        }

        if (options != null && options.getHeaders() != null) {
            for (Header header : options.getHeaders()) {
                rb.setHeader(header.getName(), header.getValue());
            }
        }

        return rb.build();
    }
}
