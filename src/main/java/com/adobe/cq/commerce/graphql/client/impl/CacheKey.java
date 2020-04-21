/*******************************************************************************
 *
 *    Copyright 2020 Adobe. All rights reserved.
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

import java.util.Objects;

import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.RequestOptions;

public class CacheKey {

    private GraphqlRequest request;
    private RequestOptions options;
    private Integer hash;

    CacheKey(GraphqlRequest request, RequestOptions options) {
        this.request = request;
        this.options = options;
    }

    @Override
    public int hashCode() {
        if (hash == null) {
            hash = Objects.hash(request, options);
        }
        return hash.intValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CacheKey that = (CacheKey) o;
        return Objects.equals(request, that.request) && Objects.equals(options, that.options);
    }
}
