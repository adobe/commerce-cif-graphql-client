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
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.client.GraphqlClientConfiguration;
import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.graphql.client.RequestOptions;
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.CircuitBreakerService;
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.ServerErrorException;
import com.adobe.cq.commerce.graphql.client.impl.circuitbreaker.ServiceUnavailableException;
import dev.failsafe.CircuitBreakerOpenException;
import dev.failsafe.FailsafeException;

public class FaultTolerantExecutor extends DefaultExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FaultTolerantExecutor.class);

    private CircuitBreakerService circuitBreakerService;

    public FaultTolerantExecutor(HttpClient client, GraphqlClientMetrics metrics, GraphqlClientConfiguration configuration) {
        super(client, metrics, configuration);
        this.circuitBreakerService = new CircuitBreakerService();
    }

    @Override
    public <T, U> GraphqlResponse<T, U> execute(GraphqlRequest request, Type typeOfT, Type typeofU, RequestOptions options) {
        stopTimer = metrics.startRequestDurationTimer();
        try {
            // Execute with fault-tolerant mechanism using circuit breaker policies
            return circuitBreakerService.executeWithPolicies(
                configuration.url(),
                () -> executeHttpRequest(request, typeOfT, typeofU, options));
        } catch (CircuitBreakerOpenException e) {
            metrics.incrementRequestErrors();
            throw new GraphqlRequestException("GraphQL service temporarily unavailable (circuit breaker open). Please try again later.", e);
        } catch (FailsafeException e) {
            metrics.incrementRequestErrors();
            throw new GraphqlRequestException("Failed to execute GraphQL request: " + e.getMessage(), e);
        }
    }

    private <T, U> GraphqlResponse<T, U> executeHttpRequest(GraphqlRequest request, Type typeOfT, Type typeofU, RequestOptions options) {
        stopTimer = metrics.startRequestDurationTimer();
        try {
            return client.execute(buildRequest(request, options), httpResponse -> {
                StatusLine statusLine = httpResponse.getStatusLine();
                int statusCode = statusLine.getStatusCode();
                // Handle server errors (5xx) - only for fault tolerant mode
                if (statusCode >= 500 && statusCode < 600) {
                    metrics.incrementRequestErrors(statusCode);
                    String responseBody = EntityUtils.toString(httpResponse.getEntity(), StandardCharsets.UTF_8);
                    String errorMessage = String.format("Server error %d: %s", statusCode, statusLine.getReasonPhrase());
                    LOGGER.warn("Received {} from endpoint {}", errorMessage, configuration.url());

                    // Special handling for 503 Service Unavailable
                    if (statusCode == HttpStatus.SC_SERVICE_UNAVAILABLE) {
                        throw new ServiceUnavailableException(errorMessage, responseBody);
                    }
                    throw new ServerErrorException(errorMessage, statusCode, responseBody);
                }
                if (HttpStatus.SC_OK == statusLine.getStatusCode()) {
                    return handleValidResponse(request, typeOfT, typeofU, options, httpResponse);
                }
                throw handleErrorResponse(statusLine);
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
}
