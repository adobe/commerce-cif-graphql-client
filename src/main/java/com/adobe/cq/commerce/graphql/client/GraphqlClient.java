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

import java.lang.reflect.Type;

public interface GraphqlClient {

    /**
     * Returns the identifier of this GraphQL client and backing OSGi service.
     * This can be set on JCR resources with the <code>cq:graphqlClient</code> property.
     * 
     * @return The identifier value of this client.
     */
    public String getIdentifier();

    /**
     * Returns the URL of the used GraphQL server endpoint.
     * 
     * @return The identifier value of this client.
     */
    public String getGraphQLEndpoint();

    /**
     * Executes the given GraphQL request and deserializes the response data based on the types T and U.
     * The type T is used to deserialize the 'data' object of the GraphQL response, and the type U is used
     * to deserialize the 'errors' array of the GraphQL response.
     * Each generic type can be a simple class or a generic class. To specify a simple class, just do:
     * 
     * <pre>
     * GraphqlResponse&lt;MyData, MyError&gt; response = graphqlClient.execute(request, MyData.class, MyError.class);
     * MyData data = response.getData();
     * List&lt;MyError&gt; errors = response.getErrors();
     * </pre>
     * 
     * To specify a generic type (usually for the type T), one can use
     * the {@link com.google.gson.reflect.TypeToken} class. For example:
     * 
     * <pre>
     * Type typeOfT = new TypeToken&lt;List&lt;String&gt;&gt;() {}.getType();
     * GraphqlResponse&lt;List&lt;String&gt;, MyError&gt; response = graphqlClient.execute(request, typeOfT, MyError.class);
     * List&lt;String&gt; data = response.getData();
     * </pre>
     * 
     * @param request The GraphQL request.
     * @param typeOfT The type of the expected GraphQL response 'data' field.
     * @param typeOfU The type of the elements of the expected GraphQL response 'errors' field.
     * 
     * @param <T> The generic type of the 'data' object in the JSON GraphQL response.
     * @param <U> The generic type of the elements of the 'errors' array in the JSON GraphQL response.
     * 
     * @return A GraphQL response.
     * 
     * @exception RuntimeException if the GraphQL HTTP request does not return 200 or if the JSON response cannot be parsed or deserialized.
     */
    public <T, U> GraphqlResponse<T, U> execute(GraphqlRequest request, Type typeOfT, Type typeOfU);

    /**
     * Executes the given GraphQL request and deserializes the response data based on the types T and U.
     * The type T is used to deserialize the 'data' object of the GraphQL response, and the type U is used
     * to deserialize the 'errors' array of the GraphQL response.
     * Each generic type can be a simple class or a generic class. To specify a simple class, just do:
     * 
     * <pre>
     * GraphqlResponse&lt;MyData, MyError&gt; response = graphqlClient.execute(request, MyData.class, MyError.class);
     * MyData data = response.getData();
     * List&lt;MyError&gt; errors = response.getErrors();
     * </pre>
     * 
     * To specify a generic type (usually for the type T), one can use
     * the {@link com.google.gson.reflect.TypeToken} class. For example:
     * 
     * <pre>
     * Type typeOfT = new TypeToken&lt;List&lt;String&gt;&gt;() {}.getType();
     * GraphqlResponse&lt;List&lt;String&gt;, MyError&gt; response = graphqlClient.execute(request, typeOfT, MyError.class);
     * List&lt;String&gt; data = response.getData();
     * </pre>
     * 
     * @param request The GraphQL request.
     * @param typeOfT The type of the expected GraphQL response 'data' field.
     * @param typeOfU The type of the elements of the expected GraphQL response 'errors' field.
     * @param options An object holding options that can be set when executing the request.
     * 
     * @param <T> The generic type of the 'data' object in the JSON GraphQL response.
     * @param <U> The generic type of the elements of the 'errors' array in the JSON GraphQL response.
     * 
     * @return A GraphQL response.
     * 
     * @exception RuntimeException if the GraphQL HTTP request does not return 200 or if the JSON response cannot be parsed or deserialized.
     */
    public <T, U> GraphqlResponse<T, U> execute(GraphqlRequest request, Type typeOfT, Type typeOfU, RequestOptions options);

}
