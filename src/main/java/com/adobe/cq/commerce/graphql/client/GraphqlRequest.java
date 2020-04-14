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

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class GraphqlRequest {

    protected String query;
    protected String operationName;
    protected Object variables;

    private Integer hash;

    public GraphqlRequest(String query) {
        this.query = query;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public Object getVariables() {
        return variables;
    }

    public void setVariables(Object variables) {
        this.variables = variables;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GraphqlRequest that = (GraphqlRequest) o;
        if (!StringUtils.equals(query, that.query)) {
            return false;
        }
        if (!StringUtils.equals(operationName, that.operationName)) {
            return false;
        }
        return Objects.equals(variables, that.variables);
    }

    @Override
    public int hashCode() {
        if (hash != null) {
            return hash.intValue();
        }
        HashCodeBuilder builder = new HashCodeBuilder().append(query);
        if (operationName != null) {
            builder.append(operationName);
        }
        if (variables != null) {
            builder.append(variables.hashCode());
        }
        hash = builder.toHashCode();
        return hash.intValue();
    }
}
