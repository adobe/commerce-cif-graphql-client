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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.Test;

public class RequestOptionsTest {

    @Test
    public void testEqualsEmptyRequestOptions() {
        RequestOptions opt1 = new RequestOptions();
        RequestOptions opt2 = new RequestOptions();

        Assert.assertEquals(opt1.hashCode(), opt2.hashCode());
        Assert.assertTrue(opt1.equals(opt2));

        Assert.assertEquals(opt1.hashCode(), opt1.hashCode());
        Assert.assertTrue(opt1.equals(opt1));
        Assert.assertFalse(opt1.equals("wrongclass"));
    }

    @Test
    public void testEqualsEmptyHeadersList() {
        RequestOptions opt1 = new RequestOptions();
        opt1.withHeaders(new ArrayList<>());
        RequestOptions opt2 = new RequestOptions();
        opt2.withHeaders(new LinkedList<>());

        Assert.assertEquals(opt1.hashCode(), opt2.hashCode());
        Assert.assertTrue(opt1.equals(opt2));
    }

    @Test
    public void testEquals() {
        RequestOptions opt1 = new RequestOptions();
        opt1.withHttpMethod(HttpMethod.GET);
        List<Header> headers1 = new ArrayList<>();
        headers1.add(new BasicHeader("Store", "default"));
        headers1.add(new BasicHeader("Authentication", "Bearer 1234"));
        opt1.withHeaders(headers1);

        RequestOptions opt2 = new RequestOptions();
        opt2.withHttpMethod(HttpMethod.GET);
        List<Header> headers2 = new LinkedList<>();
        headers2.add(new BasicHeader("Authentication", "Bearer 1234")); // Same headers but different order
        headers2.add(new BasicHeader("Store", "default"));
        opt2.withHeaders(headers2);

        Assert.assertEquals(opt1.hashCode(), opt2.hashCode());
        Assert.assertTrue(opt1.equals(opt2));

        Assert.assertEquals(opt1.hashCode(), opt1.hashCode());
        Assert.assertTrue(opt1.equals(opt1));
    }

    @Test
    public void testNotEqualsDifferentHttpMethods() {
        RequestOptions opt1 = new RequestOptions();
        opt1.withHttpMethod(HttpMethod.GET);

        RequestOptions opt2 = new RequestOptions();
        opt2.withHttpMethod(HttpMethod.POST);

        Assert.assertNotEquals(opt1.hashCode(), opt2.hashCode());
        Assert.assertFalse(opt1.equals(opt2));
    }

    @Test
    public void testNotEqualsNoHttpMethodInOneRequestOptions() {
        RequestOptions opt1 = new RequestOptions();
        opt1.withHttpMethod(HttpMethod.GET);

        RequestOptions opt2 = new RequestOptions(); // No httpMethod

        Assert.assertNotEquals(opt1.hashCode(), opt2.hashCode());
        Assert.assertFalse(opt1.equals(opt2));
    }

    @Test
    public void testNotEqualsNoHeadersInOneRequestOptions() {
        RequestOptions opt1 = new RequestOptions();
        opt1.withHttpMethod(HttpMethod.GET);
        List<Header> headers1 = new ArrayList<>();
        headers1.add(new BasicHeader("Store", "default"));
        headers1.add(new BasicHeader("Authentication", "Bearer 1234"));
        opt1.withHeaders(headers1);

        RequestOptions opt2 = new RequestOptions();  // No headers
        opt2.withHttpMethod(HttpMethod.GET);

        Assert.assertNotEquals(opt1.hashCode(), opt2.hashCode());
        Assert.assertFalse(opt1.equals(opt2));
    }

    @Test
    public void testNotEqualsDifferentHeadersListSize() {
        RequestOptions opt1 = new RequestOptions();
        opt1.withHttpMethod(HttpMethod.GET);
        List<Header> headers1 = new ArrayList<>();
        headers1.add(new BasicHeader("Store", "default"));
        headers1.add(new BasicHeader("Authentication", "Bearer 1234"));
        opt1.withHeaders(headers1);

        RequestOptions opt2 = new RequestOptions();
        opt2.withHttpMethod(HttpMethod.GET);
        List<Header> headers2 = new LinkedList<>();
        headers2.add(new BasicHeader("Store", "default"));
        opt2.withHeaders(headers2);

        Assert.assertNotEquals(opt1.hashCode(), opt2.hashCode());
        Assert.assertFalse(opt1.equals(opt2));
    }

    @Test
    public void testNotEqualsDifferentHeaderValues() {
        RequestOptions opt1 = new RequestOptions();
        opt1.withHttpMethod(HttpMethod.GET);
        List<Header> headers1 = new ArrayList<>();
        headers1.add(new BasicHeader("Store", "default"));
        headers1.add(new BasicHeader("Authentication", "Bearer 1234"));
        opt1.withHeaders(headers1);

        RequestOptions opt2 = new RequestOptions();
        opt2.withHttpMethod(HttpMethod.GET);
        List<Header> headers2 = new LinkedList<>();
        headers2.add(new BasicHeader("Store", "default"));
        headers2.add(new BasicHeader("Authentication", "Bearer 5678"));
        opt2.withHeaders(headers2);

        Assert.assertNotEquals(opt1.hashCode(), opt2.hashCode());
        Assert.assertFalse(opt1.equals(opt2));
    }
}
