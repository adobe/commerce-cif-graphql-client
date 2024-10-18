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
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.settings.SlingSettingsService;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
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

    @Reference
    private EventAdmin eventAdmin;

    @Reference
    private JobManager jobManager;

    @Reference
    private SlingSettingsService slingSettingsService;

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

        postEvent(graphqlClientId, cacheEntries);
        // triggerCacheClearJob();

        LOGGER.info("jobManager activated succesfully");

        sendJsonResponse(response, SlingHttpServletResponse.SC_OK, "Invalidate cache triggered successfully");
    }

    private void postEvent(String graphqlClientId, String[] cacheEntries) {
        Dictionary<String, Object> properties = new Hashtable<>();
        properties.put("graphqlClientId", graphqlClientId);
        if (cacheEntries != null) {
            properties.put("cacheEntries", cacheEntries);
        }
        properties.put("event.distribute", true); // Ensure the event is distributed
        properties.put("event.application", getNodeIdentifier());
        Event event = new Event("com/example/event/TOPIC", properties);
        try {
            eventAdmin.postEvent(event);
            LOGGER.info("Event posted successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to post event", e);
        }
    }

    private void triggerCacheClearJob() {
        try {
            // Job properties (to send metadata if necessary)
            Map<String, Object> props = new HashMap<>();
            props.put("eventSource", "author");
            props.put("event.distribute", true);

            // Add job and check if it's successfully added
            Job result = jobManager.addJob("com/myproject/cache/clear", props);
            String jobId = result.getId();

            LOGGER.error("Job added successfully {}", jobId);

            if (jobId != null) {
                LOGGER.error("Job added successfully.");
            } else {
                LOGGER.error("Job could not be added.");
            }
            ;
        } catch (Exception e) {
            LOGGER.error("Failed to trigger cache clear job", e);
        }
    }

    private String getNodeIdentifier() {
        // Retrieve the unique Sling ID for the current node
        String slingId = slingSettingsService.getSlingId();
        return slingId;
    }

    private void sendJsonResponse(SlingHttpServletResponse response, int statusCode, String message) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(statusCode);
        response.getWriter().write("{\"message\": \"" + message + "\"}");
    }
}
