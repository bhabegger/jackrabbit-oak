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
import org.apache.jackrabbit.oak.plugins.index.search.FieldNames;
import org.apache.jackrabbit.oak.spi.query.Cursor;
import org.apache.jackrabbit.oak.spi.query.Filter;
import org.apache.jackrabbit.oak.spi.query.QueryIndex;
import org.apache.jackrabbit.oak.spi.query.fulltext.*;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.BooleanClause.Occur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
    public String getIndexName() {
        return "luceneNg";
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
    public String getPlan(Filter filter, NodeState rootState) {
        return "lucene9:" + indexPath + " ft=" + filter.getFullTextConstraint();
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
            Analyzer analyzer = new StandardAnalyzer();
            Query ftQuery = getFullTextQuery(ft, analyzer);
            LOG.debug("Building full-text query: {}", ftQuery);
            return ftQuery;
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

    /**
     * Converts a FullTextExpression to a Lucene Query using visitor pattern.
     * Based on legacy LuceneIndex implementation.
     */
    private static Query getFullTextQuery(FullTextExpression ft, final Analyzer analyzer) {
        final AtomicReference<Query> result = new AtomicReference<>();
        ft.accept(new FullTextVisitor() {

            @Override
            public boolean visit(FullTextContains contains) {
                return contains.getBase().accept(this);
            }

            @Override
            public boolean visit(FullTextOr or) {
                BooleanQuery.Builder bq = new BooleanQuery.Builder();
                for (FullTextExpression e : or.list) {
                    Query x = getFullTextQuery(e, analyzer);
                    bq.add(x, Occur.SHOULD);
                }
                result.set(bq.build());
                return true;
            }

            @Override
            public boolean visit(FullTextAnd and) {
                BooleanQuery.Builder bq = new BooleanQuery.Builder();
                for (FullTextExpression e : and.list) {
                    Query x = getFullTextQuery(e, analyzer);
                    bq.add(x, Occur.MUST);
                }
                result.set(bq.build());
                return true;
            }

            @Override
            public boolean visit(FullTextTerm term) {
                String propertyName = term.getPropertyName();
                String text = term.getText();
                Query q = tokenToQuery(text, propertyName, analyzer);
                if (q != null) {
                    result.set(q);
                }
                return true;
            }
        });
        return result.get();
    }

    /**
     * Tokenizes text and builds appropriate Lucene query (TermQuery or PhraseQuery).
     * Based on legacy LuceneIndex implementation.
     */
    private static Query tokenToQuery(String text, String fieldName, Analyzer analyzer) {
        List<String> tokens = tokenize(text, analyzer);

        if (tokens.isEmpty()) {
            return new BooleanQuery.Builder().build();
        }

        // Use FieldNames.FULLTEXT if no specific field
        String field = (fieldName == null || "*".equals(fieldName))
            ? FieldNames.FULLTEXT
            : fieldName;

        if (tokens.size() == 1) {
            // Single token - use TermQuery
            return new TermQuery(new Term(field, tokens.get(0)));
        } else {
            // Multiple tokens - use PhraseQuery
            PhraseQuery.Builder pq = new PhraseQuery.Builder();
            for (String token : tokens) {
                pq.add(new Term(field, token));
            }
            return pq.build();
        }
    }

    /**
     * Tokenizes text using the analyzer.
     * Based on legacy LuceneIndex implementation.
     */
    private static List<String> tokenize(String text, Analyzer analyzer) {
        List<String> tokens = new ArrayList<>();
        try (TokenStream stream = analyzer.tokenStream(FieldNames.FULLTEXT, new StringReader(text))) {
            CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                tokens.add(termAtt.toString());
            }
            stream.end();
        } catch (IOException e) {
            LOG.error("Failed to tokenize text: " + text, e);
        }
        return tokens;
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
