/*******************************************************************************
 *
 *    Copyright 2025 Adobe. All rights reserved.
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
package com.adobe.cq.commerce.graphql.client.impl.circuitbreaker;

import dev.failsafe.CircuitBreaker;

/**
 * Interface for circuit breaker policies.
 * Follows Open/Closed Principle - open for extension (new policies), closed for modification.
 * Follows Interface Segregation Principle - provides only what clients need.
 */
interface Policy {

    /**
     * Creates and configures a circuit breaker for this policy.
     * 
     * @return configured CircuitBreaker instance
     */
    CircuitBreaker<Object> createCircuitBreaker();

    /**
     * Gets the name of this policy for logging and identification.
     * 
     * @return policy name
     */
    String getPolicyName();

    /**
     * Gets the exception class that this policy handles.
     * 
     * @return exception class
     */
    Class<? extends Exception> getHandledException();
}
