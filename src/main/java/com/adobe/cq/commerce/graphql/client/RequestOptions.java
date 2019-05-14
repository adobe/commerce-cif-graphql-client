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

import java.util.List;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import com.google.gson.Gson;

/**
 * This class is used to set various options when executing a GraphQL request.
 */
public class RequestOptions {

    private Gson gson;
    private List<Header> headers;

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

    public Gson getGson() {
        return gson;
    }

    public List<Header> getHeaders() {
        return headers;
    }

}
