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
package com.adobe.cq.commerce.graphql.flush.listeners;

import java.util.List;

import org.apache.sling.api.resource.observation.ExternalResourceChangeListener;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.graphql.flush.services.ConfigService;
import com.adobe.cq.commerce.graphql.flush.services.InvalidateCacheService;

@Component(
    service = ResourceChangeListener.class,
    immediate = true,
    property = {
        ResourceChangeListener.PATHS + "=" + InvalidateCacheService.INVALIDATE_WORKING_AREA,
        ResourceChangeListener.CHANGES + "=ADDED",
        ResourceChangeListener.CHANGES + "=CHANGED"
    })
public class InvalidateCacheEventListener implements ResourceChangeListener, ExternalResourceChangeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvalidateCacheEventListener.class);

    @Reference
    private InvalidateCacheService invalidateCacheService;

    @Reference
    private ConfigService configService;

    @Override
    public void onChange(List<ResourceChange> changes) {

        for (ResourceChange change : changes) {

            ResourceChange.ChangeType changeType = change.getType();
            String userId = change.getUserId();

            if (canDoCacheInvalidation(userId, changeType)) {
                String path = change.getPath();

                LOGGER.info("Invalid Cache Listener triggering CIF Cache Invalidation");
                invalidateCacheService.invalidateCache(path);
            }
        }
    }

    /**
     * Check if the cache can be invalidated.
     *
     * @param userId the user id
     * @param changeType the change type
     * @return true if the cache can be invalidated
     */
    private boolean canDoCacheInvalidation(String userId, ResourceChange.ChangeType changeType) {
        if (configService.isPublish()) {
            return true;
        }
        return (userId != null && userId.equals(InvalidateCacheService.SERVICE_USER))
            && (changeType.equals(ResourceChange.ChangeType.ADDED)
                || changeType.equals(ResourceChange.ChangeType.CHANGED));
    }
}
