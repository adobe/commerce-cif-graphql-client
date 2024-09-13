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

import org.apache.sling.settings.SlingSettingsService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.adobe.cq.commerce.graphql.flush.services.ConfigService;

@Component(service = ConfigService.class, immediate = true)
public class ConfigServiceImpl implements ConfigService {

    @Reference
    private SlingSettingsService slingSettingsService;

    private final String AUTHOR_RUNMODE = "author";
    private final String PUBLISH_RUNMODE = "publish";

    @Override
    public boolean isAuthor() {
        return slingSettingsService.getRunModes().contains(AUTHOR_RUNMODE);
    }

    @Override
    public boolean isPublish() {
        return slingSettingsService.getRunModes().contains(PUBLISH_RUNMODE);
    }

}
