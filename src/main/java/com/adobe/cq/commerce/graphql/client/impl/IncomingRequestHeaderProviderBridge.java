/*******************************************************************************
 *
 *    Copyright 2026 Adobe. All rights reserved.
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

import javax.servlet.http.HttpServletRequest;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

import com.adobe.cq.commerce.graphql.client.IncomingRequestHeaderProvider;

/**
 * Registers {@link IncomingRequestHeaderProvider} for {@link GraphqlClientImpl} by delegating to Skyline's
 * CIF HTTP request context (OSGi service and/or ThreadLocal set by {@code SlingRequestContextFilter}).
 */
@Component(
    service = IncomingRequestHeaderProvider.class,
    immediate = true,
    property = {
        Constants.SERVICE_RANKING + ":Integer=-100"
    })
public class IncomingRequestHeaderProviderBridge implements IncomingRequestHeaderProvider {

    private final SkylineIncomingRequestSupport skylineSupport = new SkylineIncomingRequestSupport();

    @Activate
    protected void activate(BundleContext bundleContext) {
        skylineSupport.activate(bundleContext);
    }

    @Deactivate
    protected void deactivate() {
        skylineSupport.deactivate();
    }

    @Override
    public HttpServletRequest getCurrentRequest() {
        return skylineSupport.getCurrentRequest();
    }

    void setSkylineProviderForTesting(Object provider) {
        skylineSupport.setSkylineProviderForTesting(provider);
    }

    void clearSkylineProviderForTesting() {
        skylineSupport.clearSkylineProviderForTesting();
    }
}
