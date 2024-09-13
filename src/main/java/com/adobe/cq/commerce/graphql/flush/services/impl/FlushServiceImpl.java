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

package com.adobe.cq.commerce.graphql.flush.services.impl;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Session;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.client.GraphqlClient;
import com.adobe.cq.commerce.graphql.flush.services.ConfigService;
import com.adobe.cq.commerce.graphql.flush.services.FlushService;
import com.adobe.cq.commerce.graphql.flush.services.ServiceUserService;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.Replicator;

@Component(service = FlushService.class, immediate = true)
public class FlushServiceImpl implements FlushService {

    @Reference
    private GraphqlClient graphqlClient;

    @Reference
    private ConfigService configService;

    @Reference
    private Replicator replicator;

    @Reference
    private ServiceUserService serviceUserService;

    private static final Logger LOGGER = LoggerFactory.getLogger(FlushServiceImpl.class);

    @Override
    public void flush() {

        LOGGER.info("Flushing graphql client");
        graphqlClient.flushCache();

    }

    @Override
    public void triggerFlush() {

        try (ResourceResolver resourceResolver = serviceUserService.getServiceUserResourceResolver(SERVICE_USER)) {

            if (configService.isAuthor()) {

                createFlushWorkingAreaIfNotExists(resourceResolver);

                Resource flushEntryResource = createFlushEntry(resourceResolver);

                Session session = resourceResolver.adaptTo(Session.class);
                replicator.replicate(session, ReplicationActionType.ACTIVATE, flushEntryResource.getPath());

            }

        } catch (PersistenceException e) {
            LOGGER.error("Error during node creation: {}", e.getMessage(), e);
        } catch (ReplicationException e) {
            LOGGER.error("Error during node replication: {}", e.getMessage(), e);
        } catch (LoginException e) {
            LOGGER.error("Error getting service user: {}", e.getMessage(), e);
        }

    }

    private void createFlushWorkingAreaIfNotExists(ResourceResolver resourceResolver) throws PersistenceException {

        // Check if the resource already exists
        Resource folderResource = resourceResolver.getResource(FLUSH_WORKING_AREA);

        if (folderResource == null) {

            // If folder doesn't exist, create it

            // Get the parent resource where you want to create the folder
            Resource varResource = resourceResolver.getResource("/var");

            if (varResource != null) {

                // Properties to define the folder
                Map<String, Object> folderProperties = new HashMap<>();
                folderProperties.put("jcr:primaryType", "sling:Folder");

                // Create the new folder
                resourceResolver.create(varResource, "cif", folderProperties);

                // Commit the changes
                resourceResolver.commit();
                LOGGER.info("Folder /var/cif created successfully.");
            } else {
                LOGGER.error("Parent /var does not exist.");
            }

        } else {
            LOGGER.info("Folder /var/cif already exists.");
        }
    }

    private Resource createFlushEntry(ResourceResolver resourceResolver) throws PersistenceException {

        // Retrieve the parent resource where the flush_entry node will be created
        Resource flushWorkingArea = resourceResolver.getResource(FLUSH_WORKING_AREA);

        if (flushWorkingArea == null) {
            throw new IllegalStateException("Flush working area does not exist: " + FLUSH_WORKING_AREA);
        }

        // Generate unique node name if flush_entry already exists
        String newNodeName = getUniqueNodeName(flushWorkingArea);

        // Prepare the properties for the new node
        Map<String, Object> nodeProperties = new HashMap<>();
        nodeProperties.put("jcr:primaryType", "nt:unstructured");
        nodeProperties.put("flushDate", Calendar.getInstance());

        // Create the new node
        Resource flushEntryResource = resourceResolver.create(flushWorkingArea, newNodeName, nodeProperties);

        // Commit changes to persist the new node
        resourceResolver.commit();

        LOGGER.info("Node {} created successfully under " + FLUSH_WORKING_AREA, newNodeName);

        return flushEntryResource;
    }

    // Method to generate a unique node name by appending an incremental index if necessary
    private String getUniqueNodeName(Resource parentResource) {
        int index = 0;
        String nodeName = NODE_NAME_BASE;

        while (parentResource.getChild(nodeName) != null) {
            index++;
            nodeName = NODE_NAME_BASE + "_" + index;
        }

        return nodeName;
    }
}
