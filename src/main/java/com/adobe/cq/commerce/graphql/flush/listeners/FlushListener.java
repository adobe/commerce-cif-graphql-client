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

package com.adobe.cq.commerce.graphql.flush.listeners;

import java.util.List;

import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.flush.services.FlushService;

@Component(
    service = ResourceChangeListener.class,
    immediate = true,
    property = {
        ResourceChangeListener.PATHS + "=" + FlushService.FLUSH_WORKING_AREA,
        ResourceChangeListener.CHANGES + "=ADDED"
    })
public class FlushListener implements ResourceChangeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlushListener.class);

    @Reference
    FlushService flushService;

    @Override
    public void onChange(List<ResourceChange> changes) {

        for (ResourceChange change : changes) {

            ResourceChange.ChangeType changeType = change.getType();

            if (changeType.equals(ResourceChange.ChangeType.ADDED)) {

                String path = change.getPath();
                LOGGER.info("Node created at path: {}", path);

                if (path.startsWith(FlushService.FLUSH_WORKING_AREA)) {

                    LOGGER.info("Flush Listener triggering CIF Cache Flush");
                    flushService.flush();

                } else {
                    LOGGER.error("Invalid path: {}", path);
                }
            } else {
                LOGGER.error("Change Type not supported: {}", changeType);
            }

        }

    }
}
