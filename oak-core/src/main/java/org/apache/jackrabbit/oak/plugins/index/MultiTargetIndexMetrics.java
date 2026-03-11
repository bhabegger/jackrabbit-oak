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

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Thread-safe metrics collection for multi-target index writes.
 * Tracks success and failure counts per target type (e.g., "lucene47", "lucene9").
 */
public class MultiTargetIndexMetrics {

    private final ConcurrentHashMap<String, AtomicLong> successCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> failureCounts = new ConcurrentHashMap<>();

    /**
     * Increment success counter for a target type.
     *
     * @param targetType the storage type (e.g., "lucene47", "lucene9")
     */
    public void incrementSuccess(@NotNull String targetType) {
        successCounts.computeIfAbsent(targetType, k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Increment failure counter for a target type.
     *
     * @param targetType the storage type (e.g., "lucene47", "lucene9")
     */
    public void incrementFailure(@NotNull String targetType) {
        failureCounts.computeIfAbsent(targetType, k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Get success count for a specific target type.
     *
     * @param targetType the storage type
     * @return success count (0 if no operations recorded)
     */
    public long getSuccessCount(@NotNull String targetType) {
        AtomicLong count = successCounts.get(targetType);
        return count != null ? count.get() : 0;
    }

    /**
     * Get failure count for a specific target type.
     *
     * @param targetType the storage type
     * @return failure count (0 if no failures recorded)
     */
    public long getFailureCount(@NotNull String targetType) {
        AtomicLong count = failureCounts.get(targetType);
        return count != null ? count.get() : 0;
    }

    /**
     * Get all success counts as an immutable snapshot.
     *
     * @return map of targetType to success count
     */
    @NotNull
    public Map<String, Long> getAllSuccesses() {
        return successCounts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }

    /**
     * Get all failure counts as an immutable snapshot.
     *
     * @return map of targetType to failure count
     */
    @NotNull
    public Map<String, Long> getAllFailures() {
        return failureCounts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }

    /**
     * Reset all metrics (primarily for testing).
     */
    public void reset() {
        successCounts.clear();
        failureCounts.clear();
    }

    @Override
    public String toString() {
        return "MultiTargetIndexMetrics{" +
                "successes=" + getAllSuccesses() +
                ", failures=" + getAllFailures() +
                '}';
    }
}
