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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.apache.http.pool.PoolStats;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.apache.http.util.VersionInfo;
import org.osgi.service.component.ComponentException;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.client.CachingStrategy;
import com.adobe.cq.commerce.graphql.client.CachingStrategy.DataFetchingPolicy;
import com.adobe.cq.commerce.graphql.client.GraphqlClient;
import com.adobe.cq.commerce.graphql.client.GraphqlClientConfiguration;
import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.graphql.client.HttpMethod;
import com.adobe.cq.commerce.graphql.client.RequestOptions;
import com.codahale.metrics.MetricRegistry;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@Component(service = GraphqlClient.class)
@Designate(ocd = GraphqlClientConfiguration.class, factory = true)
public class GraphqlClientImpl implements GraphqlClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphqlClientImpl.class);
    private static final String USER_AGENT_NAME = "Adobe-CifGraphqlClient";

    protected HttpClient client;

    @Reference(target = "(name=cif)", cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    private MetricRegistry metricsRegistry;
    @Reference
    private HttpClientBuilderFactory clientBuilderFactory = HttpClientBuilder::create;

    private Gson gson;
    private Map<String, Cache<CacheKey, GraphqlResponse<?, ?>>> caches;
    private GraphqlClientMetrics metrics;
    private GraphqlClientConfigurationImpl configuration;

    @Activate
    public void activate(GraphqlClientConfiguration configuration) throws Exception {
        this.configuration = new GraphqlClientConfigurationImpl(configuration);
        this.gson = new Gson();
        this.metrics = metricsRegistry != null
            ? new GraphqlClientMetricsImpl(metricsRegistry, configuration)
            : GraphqlClientMetrics.NOOP;

        if (this.configuration.socketTimeout() <= 0) {
            LOGGER.warn("Socket timeout set to infinity. This may cause Thread starvation and should be urgently reviewed. Falling back to "
                + "default configuration.");
            this.configuration.setSocketTimeout(GraphqlClientConfiguration.DEFAULT_SOCKET_TIMEOUT);
        }
        if (this.configuration.socketTimeout() > GraphqlClientConfiguration.DEFAULT_SOCKET_TIMEOUT) {
            LOGGER.warn("Socket timeout is too big: {}. This may cause Thread starvation and should be urgently reviewed.",
                configuration.socketTimeout());
        }
        if (this.configuration.connectionTimeout() <= 0) {
            LOGGER.warn(
                "Connection timeout set to infinity. This may cause Thread starvation and should be urgently reviewed. Falling back to "
                    + "default configuration.");
            this.configuration.setConnectionTimeout(GraphqlClientConfiguration.DEFAULT_CONNECTION_TIMEOUT);
        }
        if (this.configuration.connectionTimeout() > GraphqlClientConfiguration.DEFAULT_CONNECTION_TIMEOUT) {
            LOGGER.warn("Connection timeout is too big: {}. This may cause Thread starvation and should be urgently reviewed.",
                configuration.connectionTimeout());
        }
        if (this.configuration.requestPoolTimeout() <= 0) {
            LOGGER.warn(
                "Connection timeout set to infinity. This may cause Thread starvation and should be urgently reviewed. Falling back to "
                    + "default configuration.");
            this.configuration.setRequestPoolTimeout(GraphqlClientConfiguration.DEFAULT_REQUESTPOOL_TIMEOUT);
        }
        if (this.configuration.requestPoolTimeout() > GraphqlClientConfiguration.DEFAULT_CONNECTION_TIMEOUT) {
            LOGGER.warn("Request pool timeout is too big: {}. This may cause Thread starvation and should be urgently reviewed.",
                configuration.requestPoolTimeout());
        }

        if (StringUtils.startsWith(this.configuration.url(), "http://")) {
            if (this.configuration.allowHttpProtocol()) {
                LOGGER.warn("Insecure HTTP communication is allowed. This should NOT be done on production systems!");
            } else {
                throw new ComponentException("Insecure HTTP communication for GraphQL origin is not allowed.");
            }
        }

        configureCaches(configuration);
        configureHttpClient();
    }

    @Deactivate
    protected void deactivate() {
        if (metrics instanceof GraphqlClientMetricsImpl) {
            ((GraphqlClientMetricsImpl) metrics).close();
        }
    }

    private void configureCaches(GraphqlClientConfiguration configuration) {
        if (ArrayUtils.isNotEmpty(configuration.cacheConfigurations())) {
            caches = new HashMap<>();
            for (String cacheConfiguration : configuration.cacheConfigurations()) {
                // We ignore empty values, this may happen because of the way the AEM OSGi configuration editor works
                if (StringUtils.isBlank(cacheConfiguration)) {
                    continue;
                }

                String[] parts = cacheConfiguration.split(":");
                if (parts.length != 4) {
                    throw new IllegalStateException("Cache configuration entry doesn't have the right format --> " + cacheConfiguration);
                }

                if (Boolean.parseBoolean(parts[1])) {
                    String cacheName = parts[0];
                    int maxSize = Integer.parseInt(parts[2]);
                    int ttl = Integer.parseInt(parts[3]);
                    CacheBuilder<?, ?> cacheBuilder = CacheBuilder.newBuilder()
                        .maximumSize(maxSize)
                        .expireAfterWrite(ttl, TimeUnit.SECONDS);

                    // if we have metrics, record cache stats
                    if (metrics != GraphqlClientMetrics.NOOP) {
                        cacheBuilder = cacheBuilder.recordStats();
                    }

                    Cache<CacheKey, GraphqlResponse<?, ?>> cache = (Cache<CacheKey, GraphqlResponse<?, ?>>) cacheBuilder.build();
                    caches.put(cacheName, cache);
                    metrics.addCacheMetric(GraphqlClientMetrics.CACHE_HIT_METRIC, cacheName, () -> cache.stats().hitCount());
                    metrics.addCacheMetric(GraphqlClientMetrics.CACHE_MISS_METRIC, cacheName, () -> cache.stats().missCount());
                    metrics.addCacheMetric(GraphqlClientMetrics.CACHE_EVICTION_METRIC, cacheName, () -> cache.stats().evictionCount());
                    metrics.addCacheMetric(GraphqlClientMetrics.CACHE_USAGE_METRIC, cacheName, () -> (float) cache.size() / maxSize);
                }
            }
        } else {
            caches = null; // make sure it's always reset
        }
    }

    @Override
    public String getIdentifier() {
        return configuration.identifier();
    }

    @Override
    public String getGraphQLEndpoint() {
        return configuration.url();
    }

    @Override
    public GraphqlClientConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public <T, U> GraphqlResponse<T, U> execute(GraphqlRequest request, Type typeOfT, Type typeofU) {
        return execute(request, typeOfT, typeofU, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, U> GraphqlResponse<T, U> execute(GraphqlRequest request, Type typeOfT, Type typeofU, RequestOptions options) {
        Cache<CacheKey, GraphqlResponse<?, ?>> cache = toActiveCache(request, options);
        if (cache != null) {
            CacheKey key = new CacheKey(request, options);
            try {
                return (GraphqlResponse<T, U>) cache.get(key, () -> executeImpl(request, typeOfT, typeofU, options));
            } catch (ExecutionException e) {
                return null;
            }
        }
        return executeImpl(request, typeOfT, typeofU, options);
    }

    private Cache<CacheKey, GraphqlResponse<?, ?>> toActiveCache(GraphqlRequest request, RequestOptions options) {
        if (caches == null || request.getQuery().trim().startsWith("mutation")) {
            return null;
        }

        CachingStrategy cachingStrategy = options != null ? options.getCachingStrategy() : null;
        if (cachingStrategy == null) {
            return null;
        }

        DataFetchingPolicy dataFetchingPolicy = cachingStrategy.getDataFetchingPolicy();
        if (!DataFetchingPolicy.CACHE_FIRST.equals(dataFetchingPolicy)) {
            return null;
        }

        return caches.get(cachingStrategy.getCacheName());
    }

    private <T, U> GraphqlResponse<T, U> executeImpl(GraphqlRequest request, Type typeOfT, Type typeofU, RequestOptions options) {
        LOGGER.debug("Executing GraphQL query: " + request.getQuery());
        Runnable stopTimer = metrics.startRequestDurationTimer();
        HttpResponse httpResponse;
        try {
            httpResponse = client.execute(buildRequest(request, options));
        } catch (Exception e) {
            metrics.incrementRequestErrors();
            throw new RuntimeException("Failed to send GraphQL request", e);
        }

        StatusLine statusLine = httpResponse.getStatusLine();
        if (HttpStatus.SC_OK == statusLine.getStatusCode()) {
            HttpEntity entity = httpResponse.getEntity();
            String json;
            try {
                json = EntityUtils.toString(entity, StandardCharsets.UTF_8);
                stopTimer.run();
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
        } else {
            EntityUtils.consumeQuietly(httpResponse.getEntity());
            metrics.incrementRequestErrors(statusLine.getStatusCode());
            throw new RuntimeException("GraphQL query failed with response code " + statusLine.getStatusCode());
        }
    }

    private void configureHttpClient() throws Exception {
        SSLConnectionSocketFactory sslsf;
        if (configuration.acceptSelfSignedCertificates()) {
            LOGGER.warn("Self-signed SSL certificates are accepted. This should NOT be done on production systems!");
            SSLContext sslContext = SSLContextBuilder.create().loadTrustMaterial(new TrustAllStrategy()).build();
            sslsf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        } else {
            sslsf = new SSLConnectionSocketFactory(SSLContexts.createDefault(), new DefaultHostnameVerifier());
        }

        // We use a pooled connection manager to support concurrent threads and connections
        RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", PlainConnectionSocketFactory.getSocketFactory()).register("https", sslsf);
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(registryBuilder.build());
        cm.setMaxTotal(configuration.maxHttpConnections());
        cm.setDefaultMaxPerRoute(configuration.maxHttpConnections()); // we just have one route to the GraphQL endpoint

        metrics.addConnectionPoolMetric(GraphqlClientMetrics.CONNECTION_POOL_PENDING_METRIC, () -> cm.getTotalStats().getPending());
        metrics.addConnectionPoolMetric(GraphqlClientMetrics.CONNECTION_POOL_AVAILABLE_METRIC, () -> cm.getTotalStats().getAvailable());
        metrics.addConnectionPoolMetric(GraphqlClientMetrics.CONNECTION_POOL_USAGE_METRIC, () -> {
            PoolStats stats = cm.getTotalStats();
            return (float) stats.getLeased() / stats.getMax();
        });

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(configuration.connectionTimeout())
            .setSocketTimeout(configuration.socketTimeout())
            .setConnectionRequestTimeout(configuration.requestPoolTimeout())
            .build();

        client = clientBuilderFactory.newBuilder()
            .setDefaultRequestConfig(requestConfig)
            .setConnectionManager(cm)
            .disableCookieManagement()
            .setUserAgent(VersionInfo.getUserAgent(USER_AGENT_NAME, "com.adobe.cq.commerce.graphql.client", this.getClass()))
            .build();
    }

    private HttpUriRequest buildRequest(GraphqlRequest request, RequestOptions options) throws UnsupportedEncodingException {
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

        if (configuration.httpHeaders() != null) {
            for (String httpHeader : configuration.httpHeaders()) {
                // We ignore empty values, this may happen because of the way the AEM OSGi configuration editor works
                if (StringUtils.isBlank(httpHeader)) {
                    continue;
                }

                int idx = httpHeader.indexOf(":");
                if (idx < 1 || httpHeader.length() <= (idx + 1)) {
                    throw new IllegalStateException("The HTTP header is not a name:value pair --> " + httpHeader);
                }
                rb.addHeader(httpHeader.substring(0, idx).trim(), httpHeader.substring(idx + 1).trim());
            }
        }

        if (options != null && options.getHeaders() != null) {
            for (Header header : options.getHeaders()) {
                rb.addHeader(header.getName(), header.getValue());
            }
        }

        return rb.build();
    }
}
