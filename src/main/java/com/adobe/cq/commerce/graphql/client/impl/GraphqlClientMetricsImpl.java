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

import java.io.Closeable;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import com.adobe.cq.commerce.graphql.client.GraphqlClientConfiguration;
import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

class GraphqlClientMetricsImpl implements GraphqlClientMetrics, Closeable {

    private static final String METRIC_LABEL_IDENTIFIER = "gql_client_identifier";
    private static final String METRIC_LABEL_ENDPOINT = "gql_client_endpoint";
    private static final String METRIC_LABEL_STATUS_CODE = "gql_response_status";
    private static final String METRIC_LABEL_CACHE_NAME = "gql_cache_name";

    private final MetricRegistry metrics;
    private final GraphqlClientConfiguration configuration;
    private final Timer requestDurationTimer;
    private final ConcurrentMap<Integer, Counter> requestErrorCounters;
    private final List<Runnable> disposables = new LinkedList<>();

    GraphqlClientMetricsImpl(MetricRegistry metrics, GraphqlClientConfiguration configuration) {
        this.metrics = metrics;
        this.configuration = configuration;
        this.requestDurationTimer = metrics.timer(REQUEST_DURATION_METRIC
            + ';' + METRIC_LABEL_ENDPOINT + '=' + configuration.url());
        this.requestErrorCounters = new ConcurrentHashMap<>();
    }

    @Override
    public void close() {
        disposables.forEach(Runnable::run);
        disposables.clear();
    }

    @Override
    public void addConnectionPoolMetric(String metricName, Supplier<? extends Number> valueSupplier) {
        String metricNameAndLabels = metricName
            + ';' + METRIC_LABEL_IDENTIFIER + '=' + configuration.identifier();
        metrics.gauge(metricNameAndLabels, () -> valueSupplier::get);
        disposables.add(() -> metrics.remove(metricNameAndLabels));
    }

    @Override
    public void addCacheMetric(String metricName, String cacheName, Supplier<? extends Number> valueSupplier) {
        String metricNameAndLabels = metricName
            + ';' + METRIC_LABEL_IDENTIFIER + '=' + configuration.identifier()
            + ';' + METRIC_LABEL_CACHE_NAME + '=' + cacheName;
        metrics.gauge(metricNameAndLabels, () -> valueSupplier::get);
        disposables.add(() -> metrics.remove(metricNameAndLabels));
    }

    @Override
    public Runnable startRequestDurationTimer() {
        return requestDurationTimer.time()::close;
    }

    @Override
    public void incrementRequestErrors() {
        incrementRequestErrors(0);
    }

    @Override
    public void incrementRequestErrors(int status) {
        requestErrorCounters.computeIfAbsent(status, k -> {
            StringBuilder name = new StringBuilder();
            name.append(REQUEST_ERROR_COUNT_METRIC);
            name.append(';').append(METRIC_LABEL_ENDPOINT).append('=').append(configuration.url());
            if (status > 0) {
                name.append(';').append(METRIC_LABEL_STATUS_CODE).append('=').append(status);
            }
            return metrics.counter(name.toString());
        }).inc();
    }
}
