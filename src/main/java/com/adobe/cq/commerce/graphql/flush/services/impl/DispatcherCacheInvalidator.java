/*******************************************************************************
 *
 *    Copyright 2024 Adobe. All rights reserved.
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
package com.adobe.cq.commerce.graphql.flush.services.impl;

import java.io.IOException;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.flush.services.ServiceUserService;

class DispatcherCacheInvalidator {

    @Reference
    private ServiceUserService serviceUserService;

    private static final Logger LOGGER = LoggerFactory.getLogger(DispatcherCacheInvalidator.class);

    static void invalidateDispatcherCache(ResourceResolver resourceResolver, String path) {
        Resource resource = resourceResolver.getResource(path);
        if (resource != null) {
            String storePath = resource.getValueMap().get(InvalidateCacheImpl.PARAMETER_STORE_PATH, String.class);
            String type = resource.getValueMap().get(InvalidateCacheImpl.PARAMETER_TYPE, String.class);
            flushCache(storePath);
            if (type != null && type.equals(InvalidateCacheImpl.TYPE_CLEAR_ALL)) {
                flushCache(storePath);
            }
        }
    }

    private static void getProductFormatUrl(ResourceResolver resourceResolver, String storePath, String propertyName) {
        try {
            String commerceConfigPath = getValueFromNodeOrParent(resourceResolver, storePath, "cq:conf");
            if (commerceConfigPath != null) {
                getCommerceDataForSpecificProperty(resourceResolver, commerceConfigPath, propertyName);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String getCommerceDataForSpecificProperty(ResourceResolver resourceResolver, String path, String propertyName)
        throws RepositoryException {
        String specificPath = path + "/settings/cloudconfigs/commerce/jcr:content";
        Resource pathResource = resourceResolver.getResource(specificPath);
        if (pathResource != null && pathResource.adaptTo(Node.class) != null) {
            Node specificNode = pathResource.adaptTo(Node.class);
            if (specificNode != null && specificNode.hasProperty(propertyName)) {
                return specificNode.getProperty(propertyName).getString();
            } else {
                return "default";
            }
        }
        return null;
    }

    private static String getValueFromNodeOrParent(ResourceResolver resourceResolver, String storePath, String propertyName)
        throws RepositoryException {
        Resource pathResource = resourceResolver.getResource(storePath);
        if (pathResource != null && pathResource.adaptTo(Node.class) != null) {
            Node node = pathResource.adaptTo(Node.class);
            while (node != null && !"/content".equals(node.getPath())) {
                Node contentNode = node.hasNode("jcr:content") ? node.getNode("jcr:content") : null;
                if (contentNode != null && contentNode.hasProperty(propertyName)) {
                    return contentNode.getProperty(propertyName).getString();
                }
                node = node.getParent();
            }
        }

        return null; // or throw an exception if value is not found
    }

    private static void flushCache(String handle) {
        try {
            String server = "localhost:80";
            String uri = "/dispatcher/invalidate.cache";

            HttpClient client = new HttpClient();
            PostMethod post = new PostMethod("http://" + server + uri);
            post.setRequestHeader("CQ-Action", "Delete");
            post.setRequestHeader("CQ-Handle", handle);

            client.executeMethod(post);
            System.out.println("Response: " + post.getResponseBodyAsString());
            post.releaseConnection();
            // log the results
            LOGGER.info("result: {}", post.getResponseBodyAsString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            LOGGER.error("Flushcache servlet exception: {}", e.getMessage());
        }
    }

}
