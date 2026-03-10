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

import org.apache.jackrabbit.oak.plugins.index.cursor.Cursors;
import org.apache.jackrabbit.oak.spi.query.Cursor;
import org.apache.jackrabbit.oak.spi.query.Filter;
import org.apache.jackrabbit.oak.spi.query.QueryIndex;
import org.apache.jackrabbit.oak.spi.query.fulltext.FullTextExpression;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;

/**
 * Lucene 9 query index implementation.
 * Executes queries against Lucene 9 indexes.
 */
public class LuceneNgIndex implements QueryIndex {

    private static final Logger LOG = LoggerFactory.getLogger(LuceneNgIndex.class);

    private final LuceneNgIndexTracker tracker;
    private final String indexPath;

    public LuceneNgIndex(LuceneNgIndexTracker tracker, String indexPath) {
        this.tracker = tracker;
        this.indexPath = indexPath;
    }

    @Override
    public double getMinimumCost() {
        return 2.0; // Better than traversal (1000+) but not as good as unique lookup (1.0)
    }

    @Override
    public double getCost(Filter filter, NodeState rootState) {
        // Check if we can handle this query
        FullTextExpression ft = filter.getFullTextConstraint();

        // Check for property restrictions we can handle
        boolean hasPropertyRestriction = false;
        for (Filter.PropertyRestriction pr : filter.getPropertyRestrictions()) {
            // We can handle equality constraints on indexed properties
            if (pr.first != null && pr.first.equals(pr.last)) {
                hasPropertyRestriction = true;
                break;
            }
        }

        // Return low cost if we can handle this query, otherwise infinity
        if (ft != null || hasPropertyRestriction) {
            return 2.0; // Lower than traversal cost
        }

        return Double.POSITIVE_INFINITY;
    }

    @Override
    public Cursor query(Filter filter, NodeState rootState) {
        try {
            LuceneNgIndexNode indexNode = tracker.acquireIndexNode(indexPath);
            if (indexNode == null) {
                LOG.warn("Index node not found: {}", indexPath);
                return Cursors.newPathCursor(Collections.emptyList(), filter.getQueryLimits());
            }

            // Get definition builder from rootState for reading index data
            // Navigate to the index definition node (e.g., /oak:index/luceneNgTestIndex)
            NodeBuilder definitionBuilder = getDefinitionBuilder(rootState, indexPath);

            // Get searcher - pass definition builder so OakDirectory can access :data child node
            IndexSearcherHolder holder = new IndexSearcherHolder(
                definitionBuilder,
                indexNode.getDefinition().getIndexName()
            );
            IndexSearcher searcher = holder.getSearcher();

            // Build Lucene query from filter
            Query query = buildQuery(filter);
            LOG.debug("Executing query: {}", query);

            // Execute query
            TopDocs docs = searcher.search(query, 100); // Limit to 100 for now
            LOG.debug("Found {} hits", docs.totalHits);

            // Return cursor
            return new LuceneNgCursor(docs, searcher, holder);

        } catch (IOException e) {
            LOG.error("Error executing query on index: " + indexPath, e);
            return Cursors.newPathCursor(Collections.emptyList(), filter.getQueryLimits());
        }
    }

    private Query buildQuery(Filter filter) {
        FullTextExpression ft = filter.getFullTextConstraint();

        // Handle full-text queries
        if (ft != null) {
            String queryText = extractSearchTerm(ft);
            LOG.debug("Building full-text query for term: {}", queryText);
            return new TermQuery(new Term(":fulltext", queryText.toLowerCase()));
        }

        // Handle property restriction queries
        for (Filter.PropertyRestriction pr : filter.getPropertyRestrictions()) {
            // Handle equality constraints
            if (pr.first != null && pr.first.equals(pr.last)) {
                String value = pr.first.getValue(org.apache.jackrabbit.oak.api.Type.STRING);
                LOG.debug("Building property query for {}={}", pr.propertyName, value);
                // Don't lowercase - StringField stores exact values
                return new TermQuery(new Term(pr.propertyName, value));
            }
        }

        throw new IllegalArgumentException("No supported constraint found");
    }

    private String extractSearchTerm(FullTextExpression ft) {
        // For simple case, get the string representation and extract the term
        // Format from FullTextParser is "term" (quoted) - remove quotes
        String ftString = ft.toString();
        // Remove surrounding quotes if present
        if (ftString.startsWith("\"") && ftString.endsWith("\"") && ftString.length() > 2) {
            ftString = ftString.substring(1, ftString.length() - 1);
        }
        return ftString;
    }

    @Override
    public String getPlan(Filter filter, NodeState rootState) {
        return "lucene9:" + indexPath + " ft=" + filter.getFullTextConstraint();
    }

    @Override
    public String getIndexName() {
        return "luceneNg";
    }

    /**
     * Navigates to the index definition node from the root state.
     * Example: indexPath="/oak:index/myIndex" returns builder for that node.
     */
    private NodeBuilder getDefinitionBuilder(NodeState rootState, String indexPath) {
        NodeBuilder builder = rootState.builder();

        // Remove leading slash if present
        String path = indexPath.startsWith("/") ? indexPath.substring(1) : indexPath;

        // Navigate through path segments
        String[] segments = path.split("/");
        for (String segment : segments) {
            builder = builder.child(segment);
        }

        return builder;
    }
}
