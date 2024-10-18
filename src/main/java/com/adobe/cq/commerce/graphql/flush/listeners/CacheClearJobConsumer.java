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

import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    service = JobConsumer.class,
    property = JobConsumer.PROPERTY_TOPICS + "=com/myproject/cache/clear")
public class CacheClearJobConsumer implements JobConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheClearJobConsumer.class);

    @Override
    public JobResult process(Job job) {
        // Cache clearing logic
        clearCacheOnNode();
        return JobResult.OK;
    }

    private void clearCacheOnNode() {
        // Cache clearing logic (e.g., clearing in-memory cache)
        LOGGER.error("Cache cleared on node");
    }
}
