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

import org.junit.Assert;
import org.junit.Test;

import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.HttpMethod;
import com.adobe.cq.commerce.graphql.client.RequestOptions;

public class CacheKeyTest {

    @Test
    public void testEquals() {
        GraphqlRequest req1 = new GraphqlRequest("{dummy}");
        RequestOptions opt1 = new RequestOptions();
        CacheKey key1 = new CacheKey(req1, opt1);

        GraphqlRequest req2 = new GraphqlRequest("{dummy}");
        RequestOptions opt2 = new RequestOptions();
        CacheKey key2 = new CacheKey(req2, opt2);

        Assert.assertEquals(key1.hashCode(), key2.hashCode());
        Assert.assertTrue(key1.equals(key2));

        Assert.assertEquals(key1.hashCode(), key1.hashCode());
        Assert.assertTrue(key1.equals(key1));
        Assert.assertFalse(key1.equals("wrongclass"));
        Assert.assertFalse(key1.equals(null));
    }

    @Test
    public void testNotEqualsDifferentRequest() {
        GraphqlRequest req1 = new GraphqlRequest("{dummy}");
        RequestOptions opt1 = new RequestOptions();
        CacheKey key1 = new CacheKey(req1, opt1);

        GraphqlRequest req2 = new GraphqlRequest("{somethingelse}");
        RequestOptions opt2 = new RequestOptions();
        CacheKey key2 = new CacheKey(req2, opt2);

        Assert.assertNotEquals(key1.hashCode(), key2.hashCode());
        Assert.assertFalse(key1.equals(key2));
    }

    @Test
    public void testNotEqualsDifferentOptions() {
        GraphqlRequest req1 = new GraphqlRequest("{dummy}");
        RequestOptions opt1 = new RequestOptions().withHttpMethod(HttpMethod.GET);
        CacheKey key1 = new CacheKey(req1, opt1);

        GraphqlRequest req2 = new GraphqlRequest("{dummy}");
        RequestOptions opt2 = new RequestOptions().withHttpMethod(HttpMethod.POST);
        CacheKey key2 = new CacheKey(req2, opt2);

        Assert.assertNotEquals(key1.hashCode(), key2.hashCode());
        Assert.assertFalse(key1.equals(key2));
    }

    @Test
    public void testNotEqualsNoOption() {
        GraphqlRequest req1 = new GraphqlRequest("{dummy}");
        RequestOptions opt1 = new RequestOptions();
        CacheKey key1 = new CacheKey(req1, opt1);

        GraphqlRequest req2 = new GraphqlRequest("{dummy}");
        CacheKey key2 = new CacheKey(req2, null);

        Assert.assertNotEquals(key1.hashCode(), key2.hashCode());
        Assert.assertFalse(key1.equals(key2));
    }
}
