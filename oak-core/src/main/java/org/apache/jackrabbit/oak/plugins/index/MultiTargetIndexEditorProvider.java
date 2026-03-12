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
import org.apache.jackrabbit.oak.spi.commit.CompositeEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * IndexEditorProvider that orchestrates writes to multiple storage targets.
 *
 * <p>This provider normalizes the index definition to determine which targets
 * to write to (via {@code storeTargets}). For single-target definitions
 * (type-only or single activeTarget), it behaves identically to a regular
 * composite provider. For multi-target definitions, it writes to all targets
 * with the first target treated as primary (failures propagate) and remaining
 * targets treated as secondary (failures are logged but do not block the commit).</p>
 *
 * <p>This provider should be registered as the sole top-level
 * {@link IndexEditorProvider}. The leaf providers (e.g.,
 * {@code LuceneIndexEditorProvider47}, {@code LuceneIndexEditorProvider9})
 * are passed to the constructor and used internally.</p>
 */
public class MultiTargetIndexEditorProvider implements IndexEditorProvider {

    private static final Logger LOG = LoggerFactory.getLogger(MultiTargetIndexEditorProvider.class);

    /**
     * The index type value this provider handles exclusively.
     * Any call with a different {@code type} returns null, preventing duplicate writes
     * when leaf providers are also registered in the composite.
     * Defaults to {@code null} (handle all types with multi-target storeTargets).
     */
    private final String handledType;
    private final List<IndexEditorProvider> providers;
    private final MultiTargetIndexMetrics metrics;

    /**
     * Creates a provider that handles ALL types (when storeTargets > 1).
     * Use only when no single-target leaf providers are registered alongside this one.
     */
    public MultiTargetIndexEditorProvider(@NotNull List<IndexEditorProvider> providers) {
        this(null, providers);
    }

    public MultiTargetIndexEditorProvider(@NotNull IndexEditorProvider... providers) {
        this(null, Arrays.asList(providers));
    }

    /**
     * Creates a provider that only activates for the given {@code handledType}.
     * This prevents duplicate writes when leaf providers for individual types
     * are also present in the whiteboard composite.
     *
     * @param handledType the {@code type} property value this provider handles
     *                    (e.g. {@code "lucene-multi"}), or {@code null} to handle all types
     * @param providers   leaf providers that handle individual target types
     */
    public MultiTargetIndexEditorProvider(@org.jetbrains.annotations.Nullable String handledType,
                                          @NotNull List<IndexEditorProvider> providers) {
        this.handledType = handledType;
        this.providers = providers;
        this.metrics = new MultiTargetIndexMetrics();
    }

    @Override
    @Nullable
    public Editor getIndexEditor(@NotNull String type,
                                 @NotNull NodeBuilder definition,
                                 @NotNull NodeState root,
                                 @NotNull IndexUpdateCallback callback) throws CommitFailedException {
        // If configured with a specific handled type, ignore all other types
        if (handledType != null && !handledType.equals(type)) {
            return null;
        }

        NormalizedIndexProperties normalized;
        try {
            normalized = IndexDefinitionHelper.normalize(definition.getNodeState());
        } catch (IllegalArgumentException e) {
            // Definition is not valid (e.g. no type, storeTargets, or activeTarget)
            // Fall through silently - IndexUpdate already logs a warning for unknown types
            return null;
        }

        List<String> storeTargets = normalized.getStoreTargets();
        List<Editor> editors = new ArrayList<>(storeTargets.size());

        for (int i = 0; i < storeTargets.size(); i++) {
            String targetType = storeTargets.get(i);
            boolean isPrimary = (i == 0);

            IndexWriteOperation operation = new IndexWriteOperation(
                    targetType, isPrimary, definition, root, callback, providers, metrics);

            Editor editor = operation.execute();
            if (editor != null) {
                editors.add(editor);
            }
        }

        return CompositeEditor.compose(editors);
    }

    /**
     * Returns the metrics collected for all write operations.
     */
    @NotNull
    public MultiTargetIndexMetrics getMetrics() {
        return metrics;
    }

    @Override
    public void close() throws IOException {
        for (IndexEditorProvider provider : providers) {
            provider.close();
        }
    }
}
