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

import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.apache.http.pool.PoolStats;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.VersionInfo;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.client.*;
import com.adobe.cq.commerce.graphql.client.CachingStrategy.DataFetchingPolicy;
import com.codahale.metrics.MetricRegistry;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

@Component(service = { GraphqlClient.class })
@Designate(ocd = GraphqlClientConfiguration.class, factory = true)
public class GraphqlClientImpl implements GraphqlClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphqlClientImpl.class);
    private static final String USER_AGENT_NAME = "Adobe-CifGraphqlClient";
    static final String PROP_IDENTIFIER = "identifier";

    protected HttpClient client;

    protected RequestExecutor executor;

    @Reference(target = "(name=cif)", cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    private MetricRegistry metricsRegistry;
    @Reference
    private HttpClientBuilderFactory clientBuilderFactory = HttpClientBuilder::create;

    private Map<String, Cache<CacheKey, GraphqlResponse<?, ?>>> caches;
    private GraphqlClientMetrics metrics;
    private GraphqlClientConfigurationImpl configuration;
    private ServiceRegistration<?> registration;

    private CacheInvalidator cacheInvalidator;

    @Activate
    public void activate(GraphqlClientConfiguration configuration, BundleContext bundleContext)
        throws Exception {
        this.configuration = new GraphqlClientConfigurationImpl(configuration);

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

        // create the executor
        if (configuration.enableFaultTolerantFallback()) {
            executor = new FaultTolerantExecutor(client, metrics, this.configuration);
        } else {
            executor = new DefaultExecutor(client, metrics, this.configuration);
        }

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

        if (executor != null) {
            executor.close();
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
                LOGGER.debug("Cache HIT : Returning response from cache for key {}", key);
                return (GraphqlResponse<T, U>) cache.get(key, () -> executeImpl(request, typeOfT, typeofU, options));
            } catch (ExecutionException e) {
                LOGGER.error("Failed to return response from Cache", e);
                return null;
            }
        }
        LOGGER.debug("Cache MISS : Executing GraphQL call with Commerce");
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
        return executor.execute(request, typeOfT, typeofU, options);
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
