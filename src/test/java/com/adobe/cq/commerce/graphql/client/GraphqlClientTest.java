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

package com.adobe.cq.commerce.graphql.client;

import org.junit.Test;

import java.lang.reflect.Type;
import static org.junit.Assert.fail;

public class GraphqlClientTest {

    @Test
    public void testInvalidateCacheDefaultImplementation() {
        GraphqlClient graphqlClient = new GraphqlClient() {
            // Provide empty implementations for the other methods
            @Override
            public String getIdentifier() {
                return null;
            }

            @Override
            public String getGraphQLEndpoint() {
                return null;
            }

            @Override
            public GraphqlClientConfiguration getConfiguration() {
                return null;
            }

            @Override
            public <T, U> GraphqlResponse<T, U> execute(GraphqlRequest request, Type typeOfT, Type typeOfU, RequestOptions options) {
                return null;
            }

            @Override
            public <T, U> GraphqlResponse<T, U> execute(GraphqlRequest request, Type typeOfT, Type typeOfU) {
                return null;
            }

        };

        // Call the default invalidateCache method and assert no exceptions are thrown
        try {
            graphqlClient.invalidateCache("default", new String[]{"cache1"}, new String[]{"pattern1"});
        } catch (Exception e) {
            fail("invalidateCache method should not throw any exception");
        }
    }
}
