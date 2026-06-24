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

import org.apache.commons.lang3.StringUtils;

/**
 * Resolves the client IP from a comma-separated header value such as {@code X-Forwarded-For}.
 */
final class ClientIpResolver {

    private ClientIpResolver() {}

    /**
     * Returns the first IP address from a comma-separated header value.
     * For example, {@code "203.0.113.99, 10.0.0.1"} returns {@code "203.0.113.99"}.
     *
     * @param headerValue the raw header value
     * @return the first IP, or {@code null} if the value is blank
     */
    static String resolveFirstIp(String headerValue) {
        if (StringUtils.isBlank(headerValue)) {
            return null;
        }
        String firstIp = StringUtils.substringBefore(headerValue, ",");
        firstIp = StringUtils.trim(firstIp);
        return StringUtils.isNotBlank(firstIp) ? firstIp : null;
    }
}
