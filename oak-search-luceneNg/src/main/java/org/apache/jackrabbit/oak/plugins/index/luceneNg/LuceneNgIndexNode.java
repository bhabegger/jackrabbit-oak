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
package org.apache.jackrabbit.oak.plugins.index.luceneNg;

import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.lucene.search.IndexSearcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Represents a Lucene 9 index with its definition and a cached searcher.
 *
 * <p>The {@link IndexSearcher} is opened once at construction time from the
 * immutable {@code indexState} snapshot and reused for all queries against
 * this version of the index. When the index data changes the tracker closes
 * this node and creates a new one with a fresh reader.</p>
 */
public class LuceneNgIndexNode {

    private static final Logger LOG = LoggerFactory.getLogger(LuceneNgIndexNode.class);

    private final String indexPath;
    /** Immutable snapshot used for change detection in the tracker. */
    private final NodeState indexState;
    private final LuceneNgIndexDefinition definition;
    /** Cached searcher; null when index has not been populated yet. */
    private final IndexSearcherHolder searcherHolder;

    /**
     * Creates a new index node, opening a cached {@link IndexSearcher} from the
     * supplied {@code indexState}. If the index has not been populated yet
     * (no {@code :data} node / segment files) the searcher is left null and
     * {@link #getSearcher()} returns null.
     *
     * @param indexPath  path to the index definition (e.g. "/oak:index/myIndex")
     * @param root       repository root state
     * @param indexState index definition node state (immutable snapshot)
     */
    public LuceneNgIndexNode(@NotNull String indexPath,
                             @NotNull NodeState root,
                             @NotNull NodeState indexState) {
        this.indexPath = indexPath;
        this.indexState = indexState;
        this.definition = new LuceneNgIndexDefinition(root, indexState, indexPath);

        IndexSearcherHolder holder = null;
        try {
            holder = new IndexSearcherHolder(indexState.builder(), definition.getIndexName());
        } catch (IOException e) {
            LOG.debug("No index data for {} yet, searcher not opened: {}", indexPath, e.getMessage());
        }
        this.searcherHolder = holder;
    }

    /** Returns the index path (e.g. "/oak:index/myIndex"). */
    public String getIndexPath() {
        return indexPath;
    }

    /** Returns the immutable node state this node was built from. */
    public NodeState getIndexState() {
        return indexState;
    }

    /** Returns the index definition. */
    public LuceneNgIndexDefinition getDefinition() {
        return definition;
    }

    /**
     * Returns the cached {@link IndexSearcher}, or {@code null} if the index
     * has not yet been populated.
     */
    @Nullable
    public IndexSearcher getSearcher() {
        return searcherHolder != null ? searcherHolder.getSearcher() : null;
    }

    /**
     * Closes the cached searcher. Called by the tracker when this node is
     * evicted (index removed, definition changed, or activeTarget flipped away).
     */
    public void close() {
        if (searcherHolder != null) {
            try {
                searcherHolder.close();
            } catch (IOException e) {
                LOG.warn("Error closing searcher for {}", indexPath, e);
            }
        }
    }
}
