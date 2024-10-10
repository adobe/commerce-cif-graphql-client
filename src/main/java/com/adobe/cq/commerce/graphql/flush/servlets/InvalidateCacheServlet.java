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

package com.adobe.cq.commerce.graphql.flush.servlets;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.flush.services.ConfigService;
import com.adobe.cq.commerce.graphql.flush.services.InvalidateCacheService;

@Component(
    service = { Servlet.class },
    immediate = true,
    property = {
        Constants.SERVICE_DESCRIPTION + "=CIF Invalidate Cache Servlet",
        "sling.servlet.methods=" + HttpConstants.METHOD_POST,
        "sling.servlet.paths=" + "/bin/cif/invalidate-cache"
    })
public class InvalidateCacheServlet extends SlingAllMethodsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvalidateCacheServlet.class);

    @Reference
    private ConfigService configService;

    @Reference
    private InvalidateCacheService invalidateCacheService;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {

        if (!configService.isAuthor()) {
            LOGGER.error("Operation is only supported for author");
            sendJsonResponse(response, SlingHttpServletResponse.SC_FORBIDDEN, "Operation is only supported for author");
            return;
        }

        String graphqlClientId = request.getParameter("graphqlClientId");
        if (graphqlClientId == null || graphqlClientId.isEmpty()) {
            LOGGER.error("Missing required parameter: graphqlClientId");
            sendJsonResponse(response, SlingHttpServletResponse.SC_BAD_REQUEST, "Missing required parameter: graphqlClientId");
            return;
        }

        String cacheEntriesParam = request.getParameter("cacheEntries");
        String[] cacheEntries = cacheEntriesParam != null ? cacheEntriesParam.split(",") : null;
        invalidateCacheService.triggerCacheInvalidation(graphqlClientId, cacheEntries);

        sendJsonResponse(response, SlingHttpServletResponse.SC_OK, "Invalidate cache triggered successfully");
    }

    private void sendJsonResponse(SlingHttpServletResponse response, int statusCode, String message) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(statusCode);
        response.getWriter().write("{\"message\": \"" + message + "\"}");
    }
}
