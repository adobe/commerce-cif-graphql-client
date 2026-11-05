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
package com.adobe.cq.commerce.graphql.client.impl.circuitbreaker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.junit.Test;

import dev.failsafe.CircuitBreaker;
import dev.failsafe.Failsafe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the progressive delay and the shared attempt counter of {@link SocketTimeoutPolicy} and {@link ServiceUnavailablePolicy}
 * under concurrent access.
 */
public class ProgressiveDelayPolicyConcurrencyTest {

    private static final int THREADS = 16;
    private static final int TRANSITIONS_PER_THREAD = 1000;

    /**
     * Delay sequence of the socket timeout breaker with initial delay 1000 ms, multiplier 2 and max delay 3000 ms: 1000, 2000,
     * 3000 (capped), 3000. After the breaker closes the delay is 1000 ms again.
     */
    @Test
    public void testSocketTimeoutProgressiveDelay() {
        SocketTimeoutPolicy policy = new SocketTimeoutPolicy(new Configuration.SocketTimeoutConfig(3, 1000L, 3000L, 2.0, 1), "test");
        assertProgressiveDelay(policy::calculateDelayMs, policy::onHalfOpen, policy::onClose);
    }

    /**
     * Same delay sequence as {@link #testSocketTimeoutProgressiveDelay()} for the HTTP 503 breaker.
     */
    @Test
    public void testServiceUnavailableProgressiveDelay() {
        ServiceUnavailablePolicy policy = new ServiceUnavailablePolicy(new Configuration.ServiceUnavailableConfig(3, 1000L, 3000L, 2.0,
            1), "test");
        assertProgressiveDelay(policy::calculateDelayMs, policy::onHalfOpen, policy::onClose);
    }

    /**
     * 16 threads each call {@code onHalfOpen()} 1000 times at the same time. The attempt counter must be exactly
     * 1 + 16 x 1000 (no lost updates), and {@code onClose()} resets it to 1.
     * <p>
     * Why: the counter decides the progressive delay and is shared by all requests of a client. Lost updates would give wrong
     * delays.
     */
    @Test
    public void testSocketTimeoutConcurrentHalfOpenTransitions() throws Exception {
        SocketTimeoutPolicy policy = new SocketTimeoutPolicy(new Configuration.SocketTimeoutConfig(3, 1000L, 3000L, 2.0, 1), "test");
        assertConcurrentHalfOpenTransitions(policy::onHalfOpen, policy::getCurrentAttempt);
        policy.onClose();
        assertEquals(1, policy.getCurrentAttempt());
    }

    /**
     * Same check as {@link #testSocketTimeoutConcurrentHalfOpenTransitions()} for the HTTP 503 breaker.
     */
    @Test
    public void testServiceUnavailableConcurrentHalfOpenTransitions() throws Exception {
        ServiceUnavailablePolicy policy = new ServiceUnavailablePolicy(new Configuration.ServiceUnavailableConfig(3, 1000L, 3000L, 2.0,
            1), "test");
        assertConcurrentHalfOpenTransitions(policy::onHalfOpen, policy::getCurrentAttempt);
        policy.onClose();
        assertEquals(1, policy.getCurrentAttempt());
    }

    /**
     * 800 failing calls from 16 threads go through a real Failsafe breaker created by the policy. The breaker must not be
     * closed afterwards. After the max delay a successful call closes it and resets the attempt counter to 1.
     * <p>
     * Why: checks that the state change listeners stay consistent under concurrent load.
     */
    @Test
    public void testSocketTimeoutBreakerUnderConcurrentLoad() throws Exception {
        SocketTimeoutPolicy policy = new SocketTimeoutPolicy(new Configuration.SocketTimeoutConfig(3, 1L, 5L, 2.0, 1), "test");
        assertBreakerRecoversAfterConcurrentFailures(policy.createCircuitBreaker(),
            () -> new SocketTimeoutException("Read timeout", "details"), policy::getCurrentAttempt);
    }

    /**
     * Same check as {@link #testSocketTimeoutBreakerUnderConcurrentLoad()} for the HTTP 503 breaker.
     */
    @Test
    public void testServiceUnavailableBreakerUnderConcurrentLoad() throws Exception {
        ServiceUnavailablePolicy policy = new ServiceUnavailablePolicy(new Configuration.ServiceUnavailableConfig(3, 1L, 5L, 2.0, 1),
            "test");
        assertBreakerRecoversAfterConcurrentFailures(policy.createCircuitBreaker(),
            () -> new ServiceUnavailableException("Server error 503", "body"), policy::getCurrentAttempt);
    }

    private static void assertProgressiveDelay(LongSupplierWithoutBoxing delay, Runnable onHalfOpen, Runnable onClose) {
        assertEquals(1000L, delay.get());
        onHalfOpen.run();
        assertEquals(2000L, delay.get());
        onHalfOpen.run();
        // capped at the max delay
        assertEquals(3000L, delay.get());
        onHalfOpen.run();
        assertEquals(3000L, delay.get());
        onClose.run();
        assertEquals(1000L, delay.get());
    }

    private static void assertConcurrentHalfOpenTransitions(Runnable onHalfOpen, IntSupplier currentAttempt) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int j = 0; j < TRANSITIONS_PER_THREAD; j++) {
                        onHalfOpen.run();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1 + THREADS * TRANSITIONS_PER_THREAD, currentAttempt.getAsInt());
    }

    private static void assertBreakerRecoversAfterConcurrentFailures(CircuitBreaker<Object> breaker,
        Supplier<RuntimeException> failure, IntSupplier currentAttempt) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS * 50; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        Failsafe.with(breaker).get(() -> {
                            throw failure.get();
                        });
                    } catch (RuntimeException expected) {
                        // either the handled failure or CircuitBreakerOpenException
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertTrue("breaker should not be closed after only failures", !breaker.isClosed());

        // wait longer than the max delay so that the breaker allows a trial request
        Thread.sleep(50);
        assertEquals("ok", Failsafe.with(breaker).get(() -> "ok"));
        assertTrue(breaker.isClosed());
        assertEquals(1, currentAttempt.getAsInt());
    }

    @FunctionalInterface
    private interface LongSupplierWithoutBoxing {
        long get();
    }
}
