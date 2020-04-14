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

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Assert;
import org.junit.Test;

public class GraphqlRequestTest {

    @Test
    public void testEquals() {
        GraphqlRequest r1 = new GraphqlRequest("{something}");
        r1.setOperationName("operation");
        Map<String, String> var1 = new HashMap<>();
        var1.put("key1", "value1");
        var1.put("key2", "value2");
        r1.setVariables(var1);

        GraphqlRequest r2 = new GraphqlRequest("{something}");
        r2.setOperationName("operation");
        Map<String, String> var2 = new TreeMap<>();
        var2.put("key2", "value2");
        var2.put("key1", "value1");
        r2.setVariables(var2);

        Assert.assertEquals(r1.hashCode(), r2.hashCode());
        Assert.assertTrue(r1.equals(r2));

        Assert.assertEquals(r1.hashCode(), r1.hashCode());
        Assert.assertTrue(r1.equals(r1));
        Assert.assertFalse(r1.equals("wrongclass"));
    }

    @Test
    public void testNotEqualsDifferentQueries() {
        GraphqlRequest r1 = new GraphqlRequest("{something}");
        GraphqlRequest r2 = new GraphqlRequest("{else}");

        Assert.assertNotEquals(r1.hashCode(), r2.hashCode());
        Assert.assertFalse(r1.equals(r2));
    }

    @Test
    public void testNotEqualsDifferentOperationNames() {
        GraphqlRequest r1 = new GraphqlRequest("{something}");
        r1.setOperationName("operation1");

        GraphqlRequest r2 = new GraphqlRequest("{something}");
        r2.setOperationName("operation2");

        Assert.assertNotEquals(r1.hashCode(), r2.hashCode());
        Assert.assertFalse(r1.equals(r2));
    }

    @Test
    public void testNotEqualsDifferentVariables() {
        GraphqlRequest r1 = new GraphqlRequest("{something}");
        r1.setOperationName("operation");
        Map<String, String> var1 = new HashMap<>();
        var1.put("key1", "value1");
        var1.put("key2", "value2");
        r1.setVariables(var1);

        GraphqlRequest r2 = new GraphqlRequest("{something}");
        r2.setOperationName("operation");
        Map<String, String> var2 = new TreeMap<>();
        var2.put("key2", "value2");
        var2.put("key3", "value3");
        r2.setVariables(var2);

        Assert.assertNotEquals(r1.hashCode(), r2.hashCode());
        Assert.assertFalse(r1.equals(r2));
    }
}
