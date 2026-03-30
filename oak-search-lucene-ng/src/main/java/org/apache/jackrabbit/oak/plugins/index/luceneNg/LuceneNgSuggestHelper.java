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

import org.apache.jackrabbit.oak.plugins.index.luceneNg.directory.OakDirectory;
import org.apache.jackrabbit.oak.plugins.index.search.FieldNames;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.suggest.DocumentDictionary;
import org.apache.lucene.search.suggest.analyzing.AnalyzingInfixSuggester;
import org.apache.lucene.store.Directory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Builds and reads the {@link AnalyzingInfixSuggester} for Lucene 9 suggest queries.
 *
 * <p>Suggest data is stored in a {@value #SUGGEST_NODE_NAME} child node of the index
 * definition, separate from the main {@code lucene9} storage node.</p>
 */
public final class LuceneNgSuggestHelper {

    private static final Logger LOG = LoggerFactory.getLogger(LuceneNgSuggestHelper.class);

    /** Child node name under the index definition that holds suggester data. */
    public static final String SUGGEST_NODE_NAME = ":suggest";

    private LuceneNgSuggestHelper() {
    }

    /**
     * Rebuilds the suggester from the committed main index.
     *
     * <p>Called after each successful index writer commit, only when the main index
     * contains at least one {@link FieldNames#SUGGEST} field.</p>
     *
     * @param definitionBuilder builder of the index definition node (to write the suggest child)
     * @param mainStorageBuilder builder of the main {@code lucene9} storage node
     * @param indexName          short name of the index (used for directory naming)
     */
    public static void updateSuggester(NodeBuilder definitionBuilder,
                                       NodeBuilder mainStorageBuilder,
                                       String indexName) {
        try (Directory mainDir = new OakDirectory(mainStorageBuilder, indexName, true);
             IndexReader reader = DirectoryReader.open(mainDir)) {

            if (reader.getDocCount(FieldNames.SUGGEST) <= 0) {
                LOG.debug("No suggest fields in index '{}', skipping suggester build", indexName);
                return;
            }

            NodeBuilder suggestBuilder = definitionBuilder.child(SUGGEST_NODE_NAME);
            try (Directory suggestDir = new OakDirectory(suggestBuilder, SUGGEST_NODE_NAME, false);
                 AnalyzingInfixSuggester suggester = new AnalyzingInfixSuggester(suggestDir,
                         new StandardAnalyzer())) {
                suggester.build(new DocumentDictionary(reader, FieldNames.SUGGEST,
                        null, FieldNames.SUGGEST));
                LOG.debug("Built suggester for index '{}'", indexName);
            }
        } catch (Exception e) {
            LOG.warn("Could not update suggester for index '{}': {}", indexName, e.getMessage());
        }
    }

    /**
     * Opens an {@link AnalyzingInfixSuggester} from the stored suggest data.
     *
     * <p>The caller is responsible for closing the returned suggester.</p>
     *
     * @return the suggester, or {@code null} if no suggest data exists
     */
    @Nullable
    public static AnalyzingInfixSuggester getLookup(NodeState definitionState,
                                                    String indexName) {
        NodeState suggestState = definitionState.getChildNode(SUGGEST_NODE_NAME);
        if (!suggestState.exists()) {
            return null;
        }
        try {
            NodeBuilder suggestBuilder = suggestState.builder();
            Directory suggestDir = new OakDirectory(suggestBuilder, SUGGEST_NODE_NAME, true);
            return new AnalyzingInfixSuggester(suggestDir, new StandardAnalyzer());
        } catch (Exception e) {
            LOG.warn("Could not open suggest index for '{}': {}", indexName, e.getMessage());
            return null;
        }
    }
}
