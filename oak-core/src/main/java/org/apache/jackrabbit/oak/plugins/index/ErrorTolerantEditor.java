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

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Editor wrapper that prevents exceptions from propagating, making it safe for
 * secondary target writes in multi-target indexing scenarios.
 *
 * <p>All exceptions are caught, logged, and recorded in metrics, but never propagated.
 * This allows primary target writes to succeed even if secondary targets fail.</p>
 *
 * <p>Child editors are also wrapped to maintain error tolerance recursively.</p>
 */
public class ErrorTolerantEditor implements Editor {

    private static final Logger LOG = LoggerFactory.getLogger(ErrorTolerantEditor.class);

    private final Editor delegate;
    private final String targetType;
    private final MultiTargetIndexMetrics metrics;

    /**
     * Creates an error-tolerant wrapper around an editor.
     *
     * @param delegate the editor to wrap
     * @param targetType the storage type (e.g., "lucene47", "lucene9") for metrics
     * @param metrics metrics collector for tracking success/failure
     */
    public ErrorTolerantEditor(@NotNull Editor delegate,
                               @NotNull String targetType,
                               @NotNull MultiTargetIndexMetrics metrics) {
        this.delegate = delegate;
        this.targetType = targetType;
        this.metrics = metrics;
    }

    @Override
    public void enter(@NotNull NodeState before, @NotNull NodeState after) {
        try {
            delegate.enter(before, after);
            metrics.incrementSuccess(targetType);
        } catch (Exception e) {
            LOG.error("Secondary target write failed (enter): {}", targetType, e);
            metrics.incrementFailure(targetType);
            // Do NOT propagate exception
        }
    }

    @Override
    public void leave(@NotNull NodeState before, @NotNull NodeState after) {
        try {
            delegate.leave(before, after);
            metrics.incrementSuccess(targetType);
        } catch (Exception e) {
            LOG.error("Secondary target write failed (leave): {}", targetType, e);
            metrics.incrementFailure(targetType);
            // Do NOT propagate exception
        }
    }

    @Override
    public void propertyAdded(@NotNull PropertyState after) {
        try {
            delegate.propertyAdded(after);
            metrics.incrementSuccess(targetType);
        } catch (Exception e) {
            LOG.error("Secondary target write failed (propertyAdded): {}", targetType, e);
            metrics.incrementFailure(targetType);
            // Do NOT propagate exception
        }
    }

    @Override
    public void propertyChanged(@NotNull PropertyState before, @NotNull PropertyState after) {
        try {
            delegate.propertyChanged(before, after);
            metrics.incrementSuccess(targetType);
        } catch (Exception e) {
            LOG.error("Secondary target write failed (propertyChanged): {}", targetType, e);
            metrics.incrementFailure(targetType);
            // Do NOT propagate exception
        }
    }

    @Override
    public void propertyDeleted(@NotNull PropertyState before) {
        try {
            delegate.propertyDeleted(before);
            metrics.incrementSuccess(targetType);
        } catch (Exception e) {
            LOG.error("Secondary target write failed (propertyDeleted): {}", targetType, e);
            metrics.incrementFailure(targetType);
            // Do NOT propagate exception
        }
    }

    @Override
    @Nullable
    public Editor childNodeAdded(@NotNull String name, @NotNull NodeState after) {
        try {
            Editor child = delegate.childNodeAdded(name, after);
            metrics.incrementSuccess(targetType);
            return child != null ? new ErrorTolerantEditor(child, targetType, metrics) : null;
        } catch (Exception e) {
            LOG.error("Secondary target write failed (childNodeAdded): {}", targetType, e);
            metrics.incrementFailure(targetType);
            return null;
        }
    }

    @Override
    @Nullable
    public Editor childNodeChanged(@NotNull String name, @NotNull NodeState before, @NotNull NodeState after) {
        try {
            Editor child = delegate.childNodeChanged(name, before, after);
            metrics.incrementSuccess(targetType);
            return child != null ? new ErrorTolerantEditor(child, targetType, metrics) : null;
        } catch (Exception e) {
            LOG.error("Secondary target write failed (childNodeChanged): {}", targetType, e);
            metrics.incrementFailure(targetType);
            return null;
        }
    }

    @Override
    @Nullable
    public Editor childNodeDeleted(@NotNull String name, @NotNull NodeState before) {
        try {
            Editor child = delegate.childNodeDeleted(name, before);
            metrics.incrementSuccess(targetType);
            return child != null ? new ErrorTolerantEditor(child, targetType, metrics) : null;
        } catch (Exception e) {
            LOG.error("Secondary target write failed (childNodeDeleted): {}", targetType, e);
            metrics.incrementFailure(targetType);
            return null;
        }
    }
}
