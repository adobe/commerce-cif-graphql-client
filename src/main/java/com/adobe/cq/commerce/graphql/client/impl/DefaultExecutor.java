/*******************************************************************************
 *
 *    Copyright 2025 Adobe. All rights reserved.
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
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.client.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class DefaultExecutor implements RequestExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExecutor.class);
    protected final HttpClient client;
    protected final GraphqlClientMetrics metrics;
    protected final GraphqlClientConfiguration configuration;
    protected final Gson gson;
    protected Supplier<Long> stopTimer;

    public DefaultExecutor(HttpClient client, GraphqlClientMetrics metrics, GraphqlClientConfiguration configuration) {
        this.client = client;
        this.metrics = metrics;
        this.configuration = configuration;
        this.gson = new Gson();
    }

    @Override
    public <T, U> GraphqlResponse<T, U> execute(GraphqlRequest request, Type typeOfT, Type typeofU, RequestOptions options) {
        stopTimer = metrics.startRequestDurationTimer();
        try {
            return client.execute(buildRequest(request, options), httpResponse -> {
                StatusLine statusLine = httpResponse.getStatusLine();
                if (HttpStatus.SC_OK == statusLine.getStatusCode()) {
                    return handleValidResponse(request, typeOfT, typeofU, options, httpResponse);
                } else {
                    throw handleErrorResponse(statusLine);
                }
            });
        } catch (IOException e) {
            metrics.incrementRequestErrors();
            throw new RuntimeException("Failed to send GraphQL request", e);
        }
    }

    private RuntimeException handleErrorResponse(StatusLine statusLine) {
        metrics.incrementRequestErrors(statusLine.getStatusCode());
        throw new RuntimeException("GraphQL query failed with response code " + statusLine.getStatusCode());
    }

    @Override
    public void close() {
        if (client instanceof CloseableHttpClient) {
            try {
                ((CloseableHttpClient) client).close();
            } catch (IOException ex) {
                LOGGER.warn("Failed to close http client: {}", ex.getMessage(), ex);
            }
        }
    }

    protected <T, U> GraphqlResponse<T, U> handleValidResponse(GraphqlRequest request, Type typeOfT, Type typeofU, RequestOptions options,
        HttpResponse httpResponse) {
        HttpEntity entity = httpResponse.getEntity();
        String json;
        try {
            json = EntityUtils.toString(entity, StandardCharsets.UTF_8);
            Long executionTime = stopTimer.get();
            if (executionTime != null) {
                LOGGER.debug("Executed in {}ms", Math.floor(executionTime / 1e6));
            }
        } catch (Exception e) {
            metrics.incrementRequestErrors();
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
    }

    protected HttpUriRequest buildRequest(GraphqlRequest request, RequestOptions options) throws UnsupportedEncodingException {
        HttpMethod httpMethod = this.configuration.httpMethod();
        if (options != null && options.getHttpMethod() != null) {
            httpMethod = options.getHttpMethod();
        }

        RequestBuilder rb = RequestBuilder.create(httpMethod.toString()).setUri(configuration.url());
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
            rb.setEntity(new StringEntity(gson.toJson(request), StandardCharsets.UTF_8.name()));
        }

        for (String httpHeader : configuration.httpHeaders()) {
            String[] parts = StringUtils.split(httpHeader, ":", 2);
            if (parts.length == 2 && StringUtils.isNoneBlank(parts[0], parts[1])) {
                rb.addHeader(parts[0].trim(), parts[1].trim());
            }
        }

        if (options != null && options.getHeaders() != null) {
            for (Header header : options.getHeaders()) {
                rb.addHeader(header);
            }
        }

        return rb.build();
    }
}
