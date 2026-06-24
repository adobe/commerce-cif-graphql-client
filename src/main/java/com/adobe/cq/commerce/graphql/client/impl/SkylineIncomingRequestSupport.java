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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the current incoming HTTP request from Skyline's CIF HTTP layer.
 * <p>
 * Uses an OSGi {@link ServiceTracker} first, then falls back to reflection against Skyline's ThreadLocal
 * request holder set by {@code SlingRequestContextFilter}.
 */
final class SkylineIncomingRequestSupport {

    static final String SKYLINE_PROVIDER_FILTER = "(objectClass=com.adobe.cq.cif.common.http.IncomingRequestHeaderProvider)";

    private static final String SKYLINE_FILTER_CLASS_RESOURCE = "com/adobe/cq/cif/common/http/internal/SlingRequestContextFilter.class";

    private static final String SKYLINE_INTERNAL_PACKAGE_PATH = "com/adobe/cq/cif/common/http/internal/";

    private static final String[] THREAD_LOCAL_ACCESSOR_CLASSES = {
        "com.adobe.cq.cif.common.http.internal.IncomingRequestContext",
        "com.adobe.cq.cif.common.http.internal.SlingRequestContext",
        "com.adobe.cq.cif.common.http.internal.HttpRequestContext",
        "com.adobe.cq.cif.common.http.internal.RequestContextHolder"
    };

    private static final String[] THREAD_LOCAL_ACCESSOR_METHODS = {
        "getCurrentRequest",
        "currentRequest",
        "getRequest"
    };

    private static final Logger LOGGER = LoggerFactory.getLogger(SkylineIncomingRequestSupport.class);

    private final AtomicBoolean unboundWarningLogged = new AtomicBoolean();

    private BundleContext bundleContext;
    private volatile Object skylineProvider;
    private volatile Method threadLocalAccessor;
    private ServiceTracker<Object, Object> skylineTracker;

    void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
        threadLocalAccessor = resolveThreadLocalAccessor(bundleContext);
        if (threadLocalAccessor != null) {
            LOGGER.info(
                "Resolved Skyline ThreadLocal request accessor via {}.{}()",
                threadLocalAccessor.getDeclaringClass().getName(),
                threadLocalAccessor.getName());
        } else {
            LOGGER.warn(
                "Could not resolve Skyline ThreadLocal request accessor from bundle containing SlingRequestContextFilter");
        }

        try {
            Filter filter = bundleContext.createFilter(SKYLINE_PROVIDER_FILTER);
            skylineTracker = new ServiceTracker<>(bundleContext, filter, new SkylineProviderTracker());
            skylineTracker.open(true);
        } catch (InvalidSyntaxException e) {
            LOGGER.error("Invalid Skyline IncomingRequestHeaderProvider service filter", e);
        }

        int serviceCount = 0;
        try {
            ServiceReference<?>[] references = bundleContext.getServiceReferences(
                "com.adobe.cq.cif.common.http.IncomingRequestHeaderProvider",
                null);
            serviceCount = references != null ? references.length : 0;
        } catch (InvalidSyntaxException e) {
            LOGGER.debug("Failed to query Skyline IncomingRequestHeaderProvider services", e);
        }
        LOGGER.info(
            "Skyline IncomingRequestHeaderProvider OSGi services at bridge activation: {} (ThreadLocal fallback={})",
            serviceCount,
            threadLocalAccessor != null);
    }

    void deactivate() {
        if (skylineTracker != null) {
            skylineTracker.close();
            skylineTracker = null;
        }
        skylineProvider = null;
        bundleContext = null;
    }

    javax.servlet.http.HttpServletRequest getCurrentRequest() {
        javax.servlet.http.HttpServletRequest fromProvider = getCurrentRequestFromProvider();
        if (fromProvider != null) {
            return fromProvider;
        }
        javax.servlet.http.HttpServletRequest fromThreadLocal = getCurrentRequestFromThreadLocal();
        if (fromThreadLocal != null) {
            return fromThreadLocal;
        }
        if (unboundWarningLogged.compareAndSet(false, true)) {
            LOGGER.warn(
                "Skyline incoming request unavailable; client IP forwarding skipped until "
                    + "IncomingRequestHeaderProvider OSGi service or ThreadLocal accessor is available");
        }
        return null;
    }

    void setSkylineProviderForTesting(Object provider) {
        this.skylineProvider = provider;
        unboundWarningLogged.set(false);
    }

    void clearSkylineProviderForTesting() {
        this.skylineProvider = null;
    }

    private javax.servlet.http.HttpServletRequest getCurrentRequestFromProvider() {
        Object delegate = skylineProvider;
        if (delegate == null) {
            return null;
        }
        try {
            return (javax.servlet.http.HttpServletRequest) delegate.getClass().getMethod("getCurrentRequest").invoke(delegate);
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("Failed to read current request from Skyline IncomingRequestHeaderProvider", e);
            return null;
        }
    }

    private javax.servlet.http.HttpServletRequest getCurrentRequestFromThreadLocal() {
        Method accessor = threadLocalAccessor;
        if (accessor == null) {
            return null;
        }
        try {
            return (javax.servlet.http.HttpServletRequest) accessor.invoke(null);
        } catch (ReflectiveOperationException e) {
            LOGGER.debug(
                "Failed to read current request from Skyline ThreadLocal accessor {}",
                accessor.getDeclaringClass().getName(),
                e);
            return null;
        }
    }

    private static Method resolveThreadLocalAccessor(BundleContext bundleContext) {
        Bundle skylineBundle = findSkylineHttpBundle(bundleContext);
        if (skylineBundle == null) {
            LOGGER.warn("Skyline bundle containing SlingRequestContextFilter was not found");
            return null;
        }
        for (String className : THREAD_LOCAL_ACCESSOR_CLASSES) {
            Method accessor = resolveAccessor(skylineBundle, className);
            if (accessor != null) {
                return accessor;
            }
        }
        return discoverAccessorFromBundleEntries(skylineBundle);
    }

    private static Method discoverAccessorFromBundleEntries(Bundle bundle) {
        Enumeration<String> paths = bundle.getEntryPaths(SKYLINE_INTERNAL_PACKAGE_PATH);
        if (paths == null) {
            return null;
        }
        while (paths.hasMoreElements()) {
            String path = paths.nextElement();
            if (!path.endsWith(".class")) {
                continue;
            }
            String className = path.substring(0, path.length() - 6).replace('/', '.');
            Method accessor = resolveAccessor(bundle, className);
            if (accessor != null) {
                return accessor;
            }
        }
        return null;
    }

    private static Bundle findSkylineHttpBundle(BundleContext bundleContext) {
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getState() != Bundle.ACTIVE) {
                continue;
            }
            if (bundle.getEntry(SKYLINE_FILTER_CLASS_RESOURCE) != null) {
                return bundle;
            }
        }
        return null;
    }

    private static Method resolveAccessor(Bundle bundle, String className) {
        try {
            Class<?> clazz = bundle.loadClass(className);
            for (String methodName : THREAD_LOCAL_ACCESSOR_METHODS) {
                try {
                    Method method = clazz.getMethod(methodName);
                    if (javax.servlet.http.HttpServletRequest.class.isAssignableFrom(method.getReturnType())
                        && Modifier.isStatic(method.getModifiers())) {
                        return method;
                    }
                } catch (NoSuchMethodException ignored) {
                    // try next method name
                }
            }
        } catch (ClassNotFoundException e) {
            return null;
        }
        return null;
    }

    private final class SkylineProviderTracker implements ServiceTrackerCustomizer<Object, Object> {

        @Override
        public Object addingService(ServiceReference<Object> reference) {
            Object service = bundleContext.getService(reference);
            skylineProvider = service;
            unboundWarningLogged.set(false);
            LOGGER.info("Bound Skyline IncomingRequestHeaderProvider for graphql-client client IP forwarding");
            return service;
        }

        @Override
        public void modifiedService(ServiceReference<Object> reference, Object service) {
            skylineProvider = service;
        }

        @Override
        public void removedService(ServiceReference<Object> reference, Object service) {
            bundleContext.ungetService(reference);
            if (skylineProvider == service) {
                skylineProvider = null;
                LOGGER.info("Unbound Skyline IncomingRequestHeaderProvider from graphql-client client IP forwarding bridge");
            }
        }
    }
}
