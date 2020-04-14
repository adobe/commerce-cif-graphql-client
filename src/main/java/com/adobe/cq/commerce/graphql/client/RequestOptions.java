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

package com.adobe.cq.commerce.graphql.client;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import com.google.gson.Gson;

/**
 * This class is used to set various options when executing a GraphQL request.
 */
public class RequestOptions {

    private Gson gson;
    private List<Header> headers;
    private HttpMethod httpMethod;
    private CachingStrategy cachingStrategy;

    private Integer hash;

    /**
     * Sets the {@link Gson} instance that will be used to deserialise the JSON response. This should only be used when the JSON
     * response cannot be deserialised by a standard Gson instance, or when some custom deserialisation is needed.
     * 
     * @param gson A custom {@link Gson} instance.
     * @return This RequestOptions object.
     */
    public RequestOptions withGson(Gson gson) {
        this.gson = gson;
        return this;
    }

    /**
     * Permits to define HTTP headers that will be sent with the GraphQL request.
     * See {@link BasicHeader} for an implementation of the Header interface.
     * 
     * @param headers The HTTP headers.
     * @return This RequestOptions object.
     */
    public RequestOptions withHeaders(List<Header> headers) {
        this.headers = headers;
        return this;
    }

    /**
     * Sets the HTTP method used to send the request, only GET or POST are supported.
     * By default, the client sends a POST request. If GET is used, the underlying HTTP client
     * will automatically URL-Encode the GraphQL query, operation name, and variables.
     * 
     * @param httpMethod The HTTP method.
     * @return This RequestOptions object.
     */
    public RequestOptions withHttpMethod(HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
        return this;
    }

    public RequestOptions withCachingStrategy(CachingStrategy cachingStrategy) {
        this.cachingStrategy = cachingStrategy;
        return this;
    }

    public Gson getGson() {
        return gson;
    }

    public List<Header> getHeaders() {
        return headers;
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public CachingStrategy getCachingStrategy() {
        return cachingStrategy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RequestOptions that = (RequestOptions) o;
        if (!Objects.equals(httpMethod, that.httpMethod)) {
            return false;
        }

        if (CollectionUtils.isEmpty(headers) && CollectionUtils.isEmpty(that.headers)) {
            return true;
        }
        if ((headers == null) ^ (that.headers == null)) { // one is null but not the other
            return false;
        }
        if (headers.size() != that.headers.size()) {
            return false;
        }

        // We cannot use Objects.equals with lists because this checks object equality for all list elements
        // and elements must be in the same order.

        Map<String, String> keyValues = new HashMap<>();
        headers.stream().forEach(h -> keyValues.put(h.getName(), h.getValue()));
        for (Header header : that.headers) {
            String value = keyValues.get(header.getName());
            if (!StringUtils.equals(value, header.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        if (hash != null) {
            return hash.intValue();
        }
        if (httpMethod == null && (headers == null || headers.isEmpty())) {
            return 0;
        }
        HashCodeBuilder builder = new HashCodeBuilder();
        if (httpMethod != null) {
            builder.append(httpMethod);
        }
        if (headers != null) {
            headers.stream()
                .sorted(Comparator.comparing(Header::getName))
                .forEach(h -> builder.append(h.getName()).append(h.getValue()));
        }
        hash = builder.toHashCode();
        return hash.intValue();
    }
}
