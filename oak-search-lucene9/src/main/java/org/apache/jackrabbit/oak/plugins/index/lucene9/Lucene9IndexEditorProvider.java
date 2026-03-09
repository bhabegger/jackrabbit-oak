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
package org.apache.jackrabbit.oak.plugins.index.lucene9;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.plugins.index.IndexEditorProvider;
import org.apache.jackrabbit.oak.plugins.index.IndexUpdateCallback;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IndexEditorProvider for Lucene 9 indexes.
 * Routes index write operations to Lucene 9 editor for lucene9 type indexes.
 */
public class Lucene9IndexEditorProvider implements IndexEditorProvider {
    private static final Logger LOG = LoggerFactory.getLogger(Lucene9IndexEditorProvider.class);

    private final Lucene9IndexTracker indexTracker;

    /**
     * Creates a new Lucene9IndexEditorProvider.
     *
     * @param indexTracker the index tracker for managing index lifecycle
     */
    public Lucene9IndexEditorProvider(@NotNull Lucene9IndexTracker indexTracker) {
        this.indexTracker = indexTracker;
    }

    @Override
    @Nullable
    public Editor getIndexEditor(@NotNull String type,
                                 @NotNull NodeBuilder definition,
                                 @NotNull NodeState root,
                                 @NotNull IndexUpdateCallback callback)
            throws CommitFailedException {

        // Only handle lucene9 type indexes
        if (!Lucene9IndexConstants.TYPE_LUCENE9.equals(type)) {
            return null;
        }

        LOG.debug("Creating Lucene 9 index editor for type: {}", type);

        try {
            return new Lucene9IndexEditor("/", definition, root);
        } catch (Exception e) {
            throw new CommitFailedException("Lucene9", 1,
                    "Failed to create Lucene9IndexEditor", e);
        }
    }

    @Override
    public void close() {
        // Nothing to close
    }
}
