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

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Encapsulates a single index write operation to one target type.
 *
 * <p>Iterates available providers to find one that handles the given target type,
 * then returns the editor. For secondary targets, wraps the editor in an
 * {@link ErrorTolerantEditor} so failures do not block the commit.</p>
 *
 * <p>This class is package-private; it is an implementation detail of
 * {@link MultiTargetIndexEditorProvider}.</p>
 */
class IndexWriteOperation {

    private static final Logger LOG = LoggerFactory.getLogger(IndexWriteOperation.class);

    private final String targetType;
    private final boolean isPrimary;
    private final NodeBuilder definition;
    private final NodeState root;
    private final IndexUpdateCallback callback;
    private final List<IndexEditorProvider> providers;
    private final MultiTargetIndexMetrics metrics;

    IndexWriteOperation(@NotNull String targetType,
                        boolean isPrimary,
                        @NotNull NodeBuilder definition,
                        @NotNull NodeState root,
                        @NotNull IndexUpdateCallback callback,
                        @NotNull List<IndexEditorProvider> providers,
                        @NotNull MultiTargetIndexMetrics metrics) {
        this.targetType = targetType;
        this.isPrimary = isPrimary;
        this.definition = definition;
        this.root = root;
        this.callback = callback;
        this.providers = providers;
        this.metrics = metrics;
    }

    /**
     * Execute the write operation.
     *
     * @return editor for this target, or null if no provider handles the target
     *         (only possible for secondary targets)
     * @throws CommitFailedException if the primary target has no provider or the
     *                               primary provider throws
     */
    @Nullable
    Editor execute() throws CommitFailedException {
        for (IndexEditorProvider provider : providers) {
            Editor editor = provider.getIndexEditor(targetType, definition, root, callback);
            if (editor != null) {
                if (!isPrimary) {
                    editor = new ErrorTolerantEditor(editor, targetType, metrics);
                }
                return editor;
            }
        }
        // No provider found for this target
        if (isPrimary) {
            throw new CommitFailedException(CommitFailedException.OAK, 1,
                    "No provider found for primary index target: " + targetType);
        }
        LOG.warn("No provider found for secondary index target: {}, skipping write", targetType);
        return null;
    }
}
