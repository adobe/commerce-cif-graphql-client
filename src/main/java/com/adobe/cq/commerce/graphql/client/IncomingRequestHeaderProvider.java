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

package com.adobe.cq.commerce.graphql.client;

import javax.servlet.http.HttpServletRequest;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Provides access to the current incoming HTTP request, for example to read request headers
 * that should be forwarded to the GraphQL backend.
 */
@ProviderType
public interface IncomingRequestHeaderProvider {

    /**
     * Returns the current incoming {@link HttpServletRequest}, or {@code null} if no request
     * is associated with the current thread (for example during background jobs or cache warm-up).
     *
     * @return the current incoming request, or {@code null}
     */
    HttpServletRequest getCurrentRequest();
}
