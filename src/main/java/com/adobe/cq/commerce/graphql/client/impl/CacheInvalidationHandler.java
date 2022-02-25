/*******************************************************************************
 *
 *    Copyright 2022 Adobe. All rights reserved.
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

import java.util.List;

import org.apache.sling.event.dea.DEAConstants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import com.day.cq.replication.ReplicationAction;

/**
 * This {@link EventHandler} handles replication actions to invalidate all the caches of the GraphqlClientImpls.
 * <p>
 * It is meant to run on publish and handle flush replication actions. With the supported API level, it is currently not possible to get
 * the agent that sent the replication action event and so the handler invalidates all caches for each event.
 */
@Component(
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    immediate = true,
    property = {
        EventConstants.EVENT_TOPIC + "=" + ReplicationAction.EVENT_TOPIC,
        EventConstants.EVENT_FILTER + "=(!(" + DEAConstants.PROPERTY_APPLICATION + "=*))"
    },
    service = EventHandler.class)
public class CacheInvalidationHandler implements EventHandler {

    @Reference(
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policy = ReferencePolicy.STATIC,
        policyOption = ReferencePolicyOption.GREEDY)
    protected List<GraphqlClientImpl> graphqlClients;

    @Override
    public void handleEvent(Event event) {
        for (GraphqlClientImpl graphqlClient : graphqlClients) {
            graphqlClient.invalidateCaches();
        }
    }
}
