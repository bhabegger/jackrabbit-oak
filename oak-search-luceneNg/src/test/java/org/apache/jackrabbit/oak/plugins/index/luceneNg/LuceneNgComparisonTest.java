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

import org.apache.jackrabbit.oak.InitialContent;
import org.apache.jackrabbit.oak.Oak;
import org.apache.jackrabbit.oak.api.ContentRepository;
import org.apache.jackrabbit.oak.api.ContentSession;
import org.apache.jackrabbit.oak.api.Result;
import org.apache.jackrabbit.oak.api.ResultRow;
import org.apache.jackrabbit.oak.api.Root;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.index.AsyncIndexUpdate;
import org.apache.jackrabbit.oak.plugins.index.lucene.LuceneIndexEditorProvider;
import org.apache.jackrabbit.oak.plugins.index.lucene.LuceneIndexProvider;
import org.apache.jackrabbit.oak.spi.security.OpenSecurityProvider;
import org.apache.jackrabbit.oak.spi.state.NodeStore;
import org.apache.jackrabbit.oak.plugins.memory.MemoryNodeStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Comparison test between legacy Lucene (4.7) and LuceneNg (9.12).
 * Verifies that both implementations:
 * - Are correctly selected based on tags
 * - Produce identical query results for the same content
 */
public class LuceneNgComparisonTest {

    private static final String LEGACY_TAG = "legacyLucene";
    private static final String NEW_TAG = "newLucene";

    // Shared query definitions
    private static final String FULLTEXT_QUERY =
        "SELECT [jcr:path] FROM [nt:base] WHERE " +
        "CONTAINS(*, 'search') " +
        "OPTION(index tag %s)";

    private static final String PROPERTY_QUERY =
        "SELECT [jcr:path] FROM [nt:base] WHERE " +
        "[title] = 'Oak Testing' " +
        "OPTION(index tag %s)";

    private ContentRepository repository;
    private ContentSession session;
    private Root root;
    private NodeStore nodeStore;
    private AsyncIndexUpdate async;

    @Before
    public void setup() throws Exception {
        // Create node store
        nodeStore = new MemoryNodeStore();

        // Create index tracker for LuceneNg
        LuceneNgIndexTracker ngTracker = new LuceneNgIndexTracker();

        // Create providers
        LuceneIndexProvider legacyProvider = new LuceneIndexProvider();
        LuceneIndexEditorProvider legacyEditor = new LuceneIndexEditorProvider();

        LuceneNgQueryIndexProvider ngProvider = new LuceneNgQueryIndexProvider(ngTracker);
        LuceneNgIndexEditorProvider ngEditor = new LuceneNgIndexEditorProvider(ngTracker);

        // Build repository with both providers (synchronous indexing)
        repository = new Oak(nodeStore)
            .with(new InitialContent())
            .with(new OpenSecurityProvider())
            .with((org.apache.jackrabbit.oak.spi.query.QueryIndexProvider) legacyProvider)
            .with(legacyEditor)
            .with((org.apache.jackrabbit.oak.spi.query.QueryIndexProvider) ngProvider)
            .with(ngEditor)
            .createContentRepository();

        session = repository.login(null, null);
        root = session.getLatestRoot();

        // Create both indexes first (before content)
        createLegacyLuceneIndex();
        createLuceneNgIndex();

        // Create test content (will be indexed synchronously on commit)
        createTestContent();

        root.commit();

        // Refresh root to see indexed content
        root = session.getLatestRoot();
    }

    @After
    public void teardown() throws Exception {
        if (session != null) {
            session.close();
        }
    }

    /**
     * Creates test content for both indexes to consume
     */
    private void createTestContent() {
        Tree content = root.getTree("/").addChild("content");

        // Page 1
        Tree page1 = content.addChild("page1");
        page1.setProperty("jcr:primaryType", "nt:unstructured");
        page1.setProperty("title", "Oak Testing");
        page1.setProperty("description", "Testing Oak search functionality");

        // Page 2
        Tree page2 = content.addChild("page2");
        page2.setProperty("jcr:primaryType", "nt:unstructured");
        page2.setProperty("title", "Lucene Integration");
        page2.setProperty("description", "Integration between Oak and search engines");

        // Page 3
        Tree page3 = content.addChild("page3");
        page3.setProperty("jcr:primaryType", "nt:unstructured");
        page3.setProperty("title", "Oak Testing");
        page3.setProperty("description", "More content about Oak search");
    }

    /**
     * Creates legacy Lucene index with tag "legacyLucene"
     */
    private void createLegacyLuceneIndex() {
        Tree oakIndex = root.getTree("/oak:index");
        Tree indexDef = oakIndex.addChild("legacyIndex");

        // Basic index definition (synchronous for testing)
        indexDef.setProperty("jcr:primaryType", "oak:QueryIndexDefinition");
        indexDef.setProperty("type", "lucene");
        // Note: No async property = synchronous indexing
        indexDef.setProperty("tags", Collections.singleton(LEGACY_TAG), Type.STRINGS);
        indexDef.setProperty("includedPaths", Collections.singleton("/content"), Type.STRINGS);

        // Index rules
        Tree indexRules = indexDef.addChild("indexRules");
        indexRules.setProperty("jcr:primaryType", "nt:unstructured");

        Tree ntBase = indexRules.addChild("nt:base");
        ntBase.setProperty("jcr:primaryType", "nt:unstructured");

        Tree properties = ntBase.addChild("properties");
        properties.setProperty("jcr:primaryType", "nt:unstructured");

        // Title property
        Tree titleProp = properties.addChild("title");
        titleProp.setProperty("jcr:primaryType", "nt:unstructured");
        titleProp.setProperty("name", "title");
        titleProp.setProperty("propertyIndex", true);

        // Description property (analyzed for full-text)
        Tree descProp = properties.addChild("description");
        descProp.setProperty("jcr:primaryType", "nt:unstructured");
        descProp.setProperty("name", "description");
        descProp.setProperty("analyzed", true);
        descProp.setProperty("nodeScopeIndex", true);
    }

    /**
     * Creates LuceneNg index with tag "newLucene"
     */
    private void createLuceneNgIndex() {
        Tree oakIndex = root.getTree("/oak:index");
        Tree indexDef = oakIndex.addChild("luceneNgIndex");

        // Basic index definition (synchronous for testing)
        indexDef.setProperty("jcr:primaryType", "oak:QueryIndexDefinition");
        indexDef.setProperty("type", "lucene9");
        // Note: No async property = synchronous indexing
        indexDef.setProperty("tags", Collections.singleton(NEW_TAG), Type.STRINGS);
        indexDef.setProperty("includedPaths", Collections.singleton("/content"), Type.STRINGS);

        // Index rules
        Tree indexRules = indexDef.addChild("indexRules");
        indexRules.setProperty("jcr:primaryType", "nt:unstructured");

        Tree ntBase = indexRules.addChild("nt:base");
        ntBase.setProperty("jcr:primaryType", "nt:unstructured");

        Tree properties = ntBase.addChild("properties");
        properties.setProperty("jcr:primaryType", "nt:unstructured");

        // Title property
        Tree titleProp = properties.addChild("title");
        titleProp.setProperty("jcr:primaryType", "nt:unstructured");
        titleProp.setProperty("name", "title");
        titleProp.setProperty("propertyIndex", true);

        // Description property (analyzed for full-text)
        Tree descProp = properties.addChild("description");
        descProp.setProperty("jcr:primaryType", "nt:unstructured");
        descProp.setProperty("name", "description");
        descProp.setProperty("analyzed", true);
        descProp.setProperty("nodeScopeIndex", true);
    }

    @Test
    public void testLegacyLuceneIndexIsUsed() throws Exception {
        String query = String.format(FULLTEXT_QUERY, LEGACY_TAG);
        Result result = executeQuery(query);

        // Check that we got results (index is being used)
        List<String> paths = getResultPaths(result);
        assertTrue("Legacy Lucene index should return results", !paths.isEmpty());
        assertEquals("Should find 3 results with 'search' in description", 3, paths.size());
    }

    @Test
    public void testLuceneNgIndexIsUsed() throws Exception {
        String query = String.format(FULLTEXT_QUERY, NEW_TAG);
        Result result = executeQuery(query);

        // Check that we got results (index is being used)
        List<String> paths = getResultPaths(result);
        assertTrue("LuceneNg index should return results", !paths.isEmpty());
        assertEquals("Should find 3 results with 'search' in description", 3, paths.size());
    }

    @Test
    public void testLegacyLuceneQueryResults() throws Exception {
        // Full-text search
        String fulltextQuery = String.format(FULLTEXT_QUERY, LEGACY_TAG);
        List<String> fulltextResults = getQueryResults(fulltextQuery);

        // Should find pages with "search" in description
        assertEquals("Should find 3 results", 3, fulltextResults.size());
        assertTrue("Should contain /content/page1", fulltextResults.contains("/content/page1"));
        assertTrue("Should contain /content/page2", fulltextResults.contains("/content/page2"));
        assertTrue("Should contain /content/page3", fulltextResults.contains("/content/page3"));

        // Property search
        String propertyQuery = String.format(PROPERTY_QUERY, LEGACY_TAG);
        List<String> propertyResults = getQueryResults(propertyQuery);

        // Should find pages with exact title match
        assertEquals("Should find 2 results", 2, propertyResults.size());
        assertTrue("Should contain /content/page1", propertyResults.contains("/content/page1"));
        assertTrue("Should contain /content/page3", propertyResults.contains("/content/page3"));
    }

    @Test
    public void testLuceneNgQueryResults() throws Exception {
        // Full-text search
        String fulltextQuery = String.format(FULLTEXT_QUERY, NEW_TAG);
        List<String> fulltextResults = getQueryResults(fulltextQuery);

        // Should find pages with "search" in description
        assertEquals("Should find 3 results", 3, fulltextResults.size());
        assertTrue("Should contain /content/page1", fulltextResults.contains("/content/page1"));
        assertTrue("Should contain /content/page2", fulltextResults.contains("/content/page2"));
        assertTrue("Should contain /content/page3", fulltextResults.contains("/content/page3"));

        // Property search
        String propertyQuery = String.format(PROPERTY_QUERY, NEW_TAG);
        List<String> propertyResults = getQueryResults(propertyQuery);

        // Should find pages with exact title match
        assertEquals("Should find 2 results", 2, propertyResults.size());
        assertTrue("Should contain /content/page1", propertyResults.contains("/content/page1"));
        assertTrue("Should contain /content/page3", propertyResults.contains("/content/page3"));
    }

    /**
     * Verifies that both implementations produce identical results
     */
    @Test
    public void testResultsAreIdentical() throws Exception {
        // Full-text search
        List<String> legacyFulltext = getQueryResults(String.format(FULLTEXT_QUERY, LEGACY_TAG));
        List<String> ngFulltext = getQueryResults(String.format(FULLTEXT_QUERY, NEW_TAG));

        Collections.sort(legacyFulltext);
        Collections.sort(ngFulltext);

        assertEquals("Full-text search results should be identical", legacyFulltext, ngFulltext);

        // Property search
        List<String> legacyProperty = getQueryResults(String.format(PROPERTY_QUERY, LEGACY_TAG));
        List<String> ngProperty = getQueryResults(String.format(PROPERTY_QUERY, NEW_TAG));

        Collections.sort(legacyProperty);
        Collections.sort(ngProperty);

        assertEquals("Property search results should be identical", legacyProperty, ngProperty);
    }

    /**
     * Executes a query and returns the result paths
     */
    private List<String> getQueryResults(String query) throws Exception {
        Result result = executeQuery(query);
        return getResultPaths(result);
    }

    /**
     * Extracts paths from query result
     */
    private List<String> getResultPaths(Result result) {
        List<String> paths = new ArrayList<>();
        for (ResultRow row : result.getRows()) {
            paths.add(row.getPath());
        }
        return paths;
    }

    /**
     * Executes a query using the current root
     */
    private Result executeQuery(String query) throws Exception {
        Map<String, org.apache.jackrabbit.oak.api.PropertyValue> bindings = Collections.emptyMap();
        Map<String, String> queryOptions = Collections.emptyMap();
        return root.getQueryEngine().executeQuery(
            query, "JCR-SQL2", Long.MAX_VALUE, 0,
            bindings, queryOptions
        );
    }
}
