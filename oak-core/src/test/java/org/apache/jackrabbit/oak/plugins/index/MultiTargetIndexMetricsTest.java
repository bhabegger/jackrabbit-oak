/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.plugins.index;

import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class MultiTargetIndexMetricsTest {

    private MultiTargetIndexMetrics metrics;

    @Before
    public void setUp() {
        metrics = new MultiTargetIndexMetrics();
    }

    @Test
    public void testIncrementSuccess() {
        metrics.incrementSuccess("lucene47");
        metrics.incrementSuccess("lucene47");
        metrics.incrementSuccess("lucene9");

        assertEquals(2, metrics.getSuccessCount("lucene47"));
        assertEquals(1, metrics.getSuccessCount("lucene9"));
        assertEquals(0, metrics.getSuccessCount("unknown"));
    }

    @Test
    public void testIncrementFailure() {
        metrics.incrementFailure("lucene47");
        metrics.incrementFailure("lucene9");
        metrics.incrementFailure("lucene9");

        assertEquals(1, metrics.getFailureCount("lucene47"));
        assertEquals(2, metrics.getFailureCount("lucene9"));
        assertEquals(0, metrics.getFailureCount("unknown"));
    }

    @Test
    public void testGetAllSuccesses() {
        metrics.incrementSuccess("lucene47");
        metrics.incrementSuccess("lucene47");
        metrics.incrementSuccess("lucene9");

        Map<String, Long> successes = metrics.getAllSuccesses();

        assertEquals(2, successes.size());
        assertEquals(Long.valueOf(2), successes.get("lucene47"));
        assertEquals(Long.valueOf(1), successes.get("lucene9"));
    }

    @Test
    public void testGetAllFailures() {
        metrics.incrementFailure("lucene47");
        metrics.incrementFailure("lucene9");
        metrics.incrementFailure("lucene9");

        Map<String, Long> failures = metrics.getAllFailures();

        assertEquals(2, failures.size());
        assertEquals(Long.valueOf(1), failures.get("lucene47"));
        assertEquals(Long.valueOf(2), failures.get("lucene9"));
    }

    @Test
    public void testReset() {
        metrics.incrementSuccess("lucene47");
        metrics.incrementFailure("lucene9");

        metrics.reset();

        assertEquals(0, metrics.getSuccessCount("lucene47"));
        assertEquals(0, metrics.getFailureCount("lucene9"));
        assertTrue(metrics.getAllSuccesses().isEmpty());
        assertTrue(metrics.getAllFailures().isEmpty());
    }

    @Test
    public void testConcurrentIncrements() throws InterruptedException {
        int threadCount = 10;
        int incrementsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        metrics.incrementSuccess("lucene47");
                        metrics.incrementFailure("lucene9");
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // All increments should be counted correctly
        assertEquals(threadCount * incrementsPerThread, metrics.getSuccessCount("lucene47"));
        assertEquals(threadCount * incrementsPerThread, metrics.getFailureCount("lucene9"));
    }

    @Test
    public void testToString() {
        metrics.incrementSuccess("lucene47");
        metrics.incrementFailure("lucene9");

        String str = metrics.toString();

        assertTrue(str.contains("successes"));
        assertTrue(str.contains("failures"));
        assertTrue(str.contains("lucene47"));
        assertTrue(str.contains("lucene9"));
    }

    @Test
    public void testMixedOperations() {
        metrics.incrementSuccess("lucene47");
        metrics.incrementSuccess("lucene47");
        metrics.incrementFailure("lucene47");
        metrics.incrementSuccess("lucene9");
        metrics.incrementFailure("lucene9");
        metrics.incrementFailure("lucene9");

        assertEquals(2, metrics.getSuccessCount("lucene47"));
        assertEquals(1, metrics.getFailureCount("lucene47"));
        assertEquals(1, metrics.getSuccessCount("lucene9"));
        assertEquals(2, metrics.getFailureCount("lucene9"));
    }
}
