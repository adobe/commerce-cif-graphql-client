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

import com.codahale.metrics.Timer;

/**
 * This interface provides a facade of the metrics tracked by the {@link GraphqlClientImpl}. With {@link GraphqlClientMetrics#NOOP} it provides a
 * no-operation implementation for environments that don't have cif metrics enabled.
 */
interface GraphqlClientMetrics {

    GraphqlClientMetrics NOOP = new GraphqlClientMetrics() {
        @Override public Runnable startRequestDurationTimer() {
            return () -> {
                // do nothing
            };
        }

        @Override public void incrementRequestErrorCount() {
            // do nothing
        }

        @Override public void incrementRequestErrorCount(int status) {
            // do nothing
        }
    };

    /**
     * Starts a request duration timer. The returned {@link Runnable} wraps the {@link Timer.Context#close()} and must be called in
     * order to add the measurement to the metric.
     *
     * @return
     */
    Runnable startRequestDurationTimer();

    /**
     * Increments the generic request error count.
     */
    void incrementRequestErrorCount();

    /**
     * Increments the specific request error count for the given status.
     *
     * @param
     */
    void incrementRequestErrorCount(int status);

}
