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
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
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
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.apache.http.pool.PoolStats;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.apache.http.util.VersionInfo;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
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
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.CircuitBreakerService;
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.ServerErrorException;
import com.codahale.metrics.MetricRegistry;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.failsafe.CircuitBreaker;
import dev.failsafe.CircuitBreakerOpenException;
import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeException;

@Component(
    service = GraphqlClient.class,
    immediate = true)
@Designate(ocd = GraphqlClientConfiguration.class, factory = true)
public class GraphqlClientImpl implements GraphqlClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphqlClientImpl.class);
    private static final String USER_AGENT_NAME = "Adobe-CifGraphqlClient";
    static final String PROP_IDENTIFIER = "identifier";

    protected HttpClient client;

    @Reference(target = "(name=cif)", cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    private MetricRegistry metricsRegistry;
    @Reference
    private HttpClientBuilderFactory clientBuilderFactory = HttpClientBuilder::create;
    @Reference
    private CircuitBreakerService circuitBreakerService;

    private Gson gson;
    private Map<String, Cache<CacheKey, GraphqlResponse<?, ?>>> caches;
    private GraphqlClientMetrics metrics;
    private GraphqlClientConfigurationImpl configuration;
    private ServiceRegistration<?> registration;

    private CacheInvalidator cacheInvalidator;

    @Activate
    public void activate(GraphqlClientConfiguration configuration, BundleContext bundleContext)
        throws Exception {
        this.configuration = new GraphqlClientConfigurationImpl(configuration);
        this.gson = new Gson();

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

        if (StringUtils.isBlank(this.configuration.url())) {
            LOGGER.info("No endpoint url configured for '{}'", configuration.identifier());
            return;
        } else {
            try {
                // validate url syntax
                new URL(this.configuration.url());
            } catch (MalformedURLException ex) {
                LOGGER.error("Invalid endpoint url configured for: {}", configuration.identifier());
                return;
            }
        }

        if (StringUtils.startsWith(this.configuration.url(), "http://")) {
            if (this.configuration.allowHttpProtocol()) {
                LOGGER.warn("Insecure HTTP communication is allowed. This should NOT be done on production systems!");
            } else {
                LOGGER.error("Insecure HTTP communication for GraphQL origin is not allowed for '{}'", configuration.identifier());
                return;
            }
        }

        if (this.configuration.httpHeaders().length > 0) {
            String[] newHeaders = Arrays.stream(this.configuration.httpHeaders())
                .filter(header -> {
                    String[] parts = StringUtils.split(header, ":", 2);
                    return parts.length == 2 && StringUtils.isNoneBlank(parts[0], parts[1]);
                })
                .toArray(String[]::new);

            if (newHeaders.length != this.configuration.httpHeaders().length) {
                LOGGER.warn("Configuration contains invalid HTTP headers, please review the configuration.");
                this.configuration.setHttpHeaders(newHeaders);
            }
        }

        this.metrics = metricsRegistry != null
            ? new GraphqlClientMetricsImpl(metricsRegistry, configuration)
            : GraphqlClientMetrics.NOOP;

        configureCaches(configuration);
        if (caches != null) {
            cacheInvalidator = new CacheInvalidator(caches);
        }
        client = configureHttpClientBuilder().build();

        Hashtable<String, Object> serviceProps = new Hashtable<>();
        serviceProps.put(PROP_IDENTIFIER, configuration.identifier());
        serviceProps.put(Constants.SERVICE_RANKING, configuration.service_ranking());
        registration = bundleContext.registerService(GraphqlClient.class, this, serviceProps);
    }

    @Deactivate
    protected void deactivate() {
        if (metrics instanceof GraphqlClientMetricsImpl) {
            ((GraphqlClientMetricsImpl) metrics).close();
        }
        if (registration != null) {
            registration.unregister();
        }
        if (client instanceof CloseableHttpClient) {
            try {
                ((CloseableHttpClient) client).close();
            } catch (IOException ex) {
                LOGGER.warn("Failed to close http client: {}", ex.getMessage(), ex);
            }
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

    @Override
    public void invalidateCache(String storeView, String[] cacheNames, String[] patterns) {
        if (cacheInvalidator != null) {
            cacheInvalidator.invalidateCache(storeView, cacheNames, patterns);
        }
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
        LOGGER.debug("Executing GraphQL query on endpoint '{}': {}", configuration.url(), request.getQuery());
        Supplier<Long> stopTimer = metrics.startRequestDurationTimer();

        try {
            // Check if fault tolerant fallback is enabled
            if (configuration.enableFaultTolerantFallback()) {
                // Use fault tolerant mechanism with circuit breaker
                CircuitBreaker<Object> circuitBreaker = circuitBreakerService.getCircuitBreaker(configuration.url());

                // We execute the request with the circuit breaker (fault tolerant mode)
                return Failsafe.with(circuitBreaker).get(() -> executeHttpRequest(request, typeOfT, typeofU, options, stopTimer, true));
            } else {
                // Execute without fault tolerant mechanisms (existing flow)
                return executeHttpRequest(request, typeOfT, typeofU, options, stopTimer, false);
            }
        } catch (CircuitBreakerOpenException e) {
            // Circuit breaker open state (only happens when fault tolerant is enabled)
            metrics.incrementRequestErrors();
            throw new RuntimeException("GraphQL service temporarily unavailable (circuit breaker open). Please try again later.", e);
        } catch (FailsafeException e) {
            // All other Failsafe exceptions (only happens when fault tolerant is enabled)
            metrics.incrementRequestErrors();
            throw new RuntimeException("Failed to execute GraphQL request: " + e.getMessage(), e);
        } catch (IOException e) {
            // Common IOException handling for both fault tolerant and non-fault tolerant modes
            metrics.incrementRequestErrors();
            String message = e.getMessage();
            if (configuration.enableFaultTolerantFallback() && message != null && CircuitBreakerService.isNetworkError(message)) {
                LOGGER.warn("Network error connecting to endpoint {}: {}", configuration.url(), message);
                throw new RuntimeException("Network error: " + message, e);
            }
            throw new RuntimeException("Failed to send GraphQL request", e);
        }
    }

    private <T, U> GraphqlResponse<T, U> executeHttpRequest(GraphqlRequest request, Type typeOfT, Type typeofU, RequestOptions options,
        Supplier<Long> stopTimer, boolean isFaultTolerant) throws IOException {
        return client.execute(buildRequest(request, options), httpResponse -> {
            StatusLine statusLine = httpResponse.getStatusLine();
            int statusCode = statusLine.getStatusCode();

            // Handle server errors (5xx) - only for fault tolerant mode
            if (isFaultTolerant && statusCode >= 500 && statusCode < 600) {
                metrics.incrementRequestErrors(statusCode);
                String responseBody = EntityUtils.toString(httpResponse.getEntity(), StandardCharsets.UTF_8);
                String errorMessage = String.format("Server error %d: %s", statusCode, statusLine.getReasonPhrase());
                LOGGER.warn("Received {} from endpoint {}", errorMessage, configuration.url());
                throw new ServerErrorException(errorMessage, statusCode, responseBody);
            }

            // Handle successful responses
            if (HttpStatus.SC_OK == statusCode) {
                return handleSuccessResponse(httpResponse, typeOfT, typeofU, options, stopTimer, request);
            }

            // Handle other status codes
            metrics.incrementRequestErrors(statusCode);
            throw new RuntimeException("GraphQL query failed with response code " + statusCode);
        });
    }

    private <T, U> GraphqlResponse<T, U> handleSuccessResponse(HttpResponse httpResponse, Type typeOfT, Type typeofU,
        RequestOptions options, Supplier<Long> stopTimer, GraphqlRequest request) throws IOException {
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

        Gson requestGson = (options != null && options.getGson() != null) ? options.getGson() : this.gson;
        Type type = TypeToken.getParameterized(GraphqlResponse.class, typeOfT, typeofU).getType();
        GraphqlResponse<T, U> response = requestGson.fromJson(json, type);

        // We log GraphQL errors because they might otherwise get "silently" unnoticed
        if (response.getErrors() != null) {
            Type listErrorsType = TypeToken.getParameterized(List.class, typeofU).getType();
            String errors = requestGson.toJson(response.getErrors(), listErrorsType);
            LOGGER.warn("GraphQL request {} returned some errors {}", request.getQuery(), errors);
        }

        return response;
    }

    HttpClientBuilder configureHttpClientBuilder() throws Exception {
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
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(
            registryBuilder.build(), null, null, null, configuration.connectionTtl(), TimeUnit.SECONDS);
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

        HttpClientBuilder httpClientBuilder = clientBuilderFactory.newBuilder();
        httpClientBuilder.setDefaultRequestConfig(requestConfig)
            .setConnectionManager(cm)
            .disableCookieManagement()
            .setUserAgent(VersionInfo.getUserAgent(USER_AGENT_NAME, "com.adobe.cq.commerce.graphql.client", this.getClass()));

        if (configuration.connectionKeepAlive() == 0 || configuration.connectionTtl() == 0) {
            // never reuse connections
            httpClientBuilder.setConnectionReuseStrategy((httpResponse, httpContext) -> false);
        } else if (configuration.connectionKeepAlive() > 0) {
            // limit the keep alive to the configured maximum
            httpClientBuilder.setKeepAliveStrategy(new ConfigurableConnectionKeepAliveStrategy(configuration.connectionKeepAlive()));
        } // else reuse connections

        return httpClientBuilder;
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

    static class ConfigurableConnectionKeepAliveStrategy implements ConnectionKeepAliveStrategy {
        private int defaultConnectionKeepAlive;

        public ConfigurableConnectionKeepAliveStrategy(int defaultConnectionKeepAlive) {
            this.defaultConnectionKeepAlive = defaultConnectionKeepAlive;
        }

        @Override
        public long getKeepAliveDuration(HttpResponse httpResponse, HttpContext httpContext) {
            long keepAliveSeconds = defaultConnectionKeepAlive;

            HeaderElementIterator it = new BasicHeaderElementIterator(httpResponse.headerIterator(HTTP.CONN_KEEP_ALIVE));
            while (it.hasNext()) {
                HeaderElement he = it.nextElement();
                String param = he.getName();
                String value = he.getValue();
                if (value != null && param.equalsIgnoreCase("timeout")) {
                    try {
                        long headerConnectionKeepAlive = Long.parseLong(value);
                        if (headerConnectionKeepAlive > 0) {
                            keepAliveSeconds = Math.min(keepAliveSeconds, headerConnectionKeepAlive);
                            break;
                        }
                    } catch (NumberFormatException x) {
                        LOGGER.debug("Invalid connection keep alive timeout: {}", value);
                    }
                }
            }

            return keepAliveSeconds * 1000L;
        }
    }
}
