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

import java.lang.annotation.Annotation;

public class MockGraphqlClientConfiguration implements Annotation, GraphqlClientConfiguration {

    public static final String URL = "https://hostname/graphql";

    @Override
    public String identifier() {
        return GraphqlClientConfiguration.DEFAULT_IDENTIFIER;
    }

    @Override
    public String url() {
        return URL;
    }

    @Override
    public boolean acceptSelfSignedCertificates() {
        return GraphqlClientConfiguration.ACCEPT_SELF_SIGNED_CERTIFICATES;
    }

    @Override
    public int maxHttpConnections() {
        return GraphqlClientConfiguration.MAX_HTTP_CONNECTIONS_DEFAULT;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return GraphqlClientConfiguration.class;
    }
}
