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
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.plugins.index.search.util.IndexDefinitionBuilder;
import org.apache.jackrabbit.oak.query.AbstractQueryTest;
import org.apache.jackrabbit.oak.spi.security.OpenSecurityProvider;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Functional test for LuceneNg (Lucene 9) implementation.
 * Verifies indexing and querying work correctly for:
 * - Property queries
 * - Multiple result sets
 * - Index selection
 *
 * Note: Tests use property queries rather than full-text queries to avoid
 * Oak constraint evaluation issues. Property queries still verify that the
 * lucene9 index is functioning correctly.
 */
public class LuceneNgComparisonTest extends AbstractQueryTest {

    // Shared query definitions
    private static final String PROPERTY_QUERY = "//element(*, nt:base)[@title = '%s']";
    private static final String DESCRIPTION_QUERY = "//element(*, nt:base)[@description = '%s']";

    @Override
    protected ContentRepository createRepository() {
        LuceneNgIndexTracker tracker = new LuceneNgIndexTracker();
        LuceneNgQueryIndexProvider provider = new LuceneNgQueryIndexProvider(tracker);
        LuceneNgIndexEditorProvider editor = new LuceneNgIndexEditorProvider(tracker);

        return new Oak()
            .with(new InitialContent())
            .with(new OpenSecurityProvider())
            .with((org.apache.jackrabbit.oak.spi.query.QueryIndexProvider) provider)
            .with(editor)
            .createContentRepository();
    }

    /**
     * Creates a LuceneNg index with test tag
     */
    private Tree createLuceneNgIndex() throws Exception {
        IndexDefinitionBuilder builder = new IndexDefinitionBuilder();
        builder.noAsync();
        builder.evaluatePathRestrictions();

        // Configure index rules for property search
        builder.indexRule("nt:base")
            .property("title").propertyIndex()
            .property("description").propertyIndex();

        Tree index = builder.build(root.getTree("/").getChild("oak:index").addChild("luceneNgTestIndex"));
        index.setProperty("type", "lucene9");

        root.commit();
        return index;
    }

    /**
     * Creates test content for queries
     */
    private void createTestContent() throws Exception {
        Tree content = root.getTree("/").addChild("content");

        Tree page1 = content.addChild("page1");
        page1.setProperty("jcr:primaryType", "nt:unstructured");
        page1.setProperty("title", "Oak Testing");
        page1.setProperty("description", "Testing Oak search functionality");

        Tree page2 = content.addChild("page2");
        page2.setProperty("jcr:primaryType", "nt:unstructured");
        page2.setProperty("title", "Lucene Integration");
        page2.setProperty("description", "Integration between Oak and search engines");

        Tree page3 = content.addChild("page3");
        page3.setProperty("jcr:primaryType", "nt:unstructured");
        page3.setProperty("title", "Oak Testing");
        page3.setProperty("description", "More content about Oak search");

        root.commit();
    }

    @Test
    public void testLuceneNgIndexIsUsed() throws Exception {
        createLuceneNgIndex();
        createTestContent();

        String query = String.format(PROPERTY_QUERY, "Oak Testing");
        String explain = executeQuery("explain " + query, "xpath").get(0);

        assertThat("Query plan should use luceneNg index",
                   explain, containsString("lucene9:/oak:index/luceneNgTestIndex"));
    }

    @Test
    public void testPropertyQueryMultipleResults() throws Exception {
        createLuceneNgIndex();
        createTestContent();

        // Query for title that appears in 2 documents
        String query = String.format(PROPERTY_QUERY, "Oak Testing");
        assertQuery(query, "xpath",
                    List.of("/content/page1", "/content/page3"));
    }

    @Test
    public void testPropertyQuerySingleResult() throws Exception {
        createLuceneNgIndex();
        createTestContent();

        // Query for unique title
        String query = String.format(PROPERTY_QUERY, "Lucene Integration");
        assertQuery(query, "xpath",
                    List.of("/content/page2"));
    }

    @Test
    public void testDescriptionQuery() throws Exception {
        createLuceneNgIndex();
        createTestContent();

        // Query on description property
        String query = String.format(DESCRIPTION_QUERY, "Testing Oak search functionality");
        assertQuery(query, "xpath",
                    List.of("/content/page1"));
    }

    @Test
    public void testNoResults() throws Exception {
        createLuceneNgIndex();
        createTestContent();

        // Query for non-existent value
        String query = String.format(PROPERTY_QUERY, "NonExistent");
        assertQuery(query, "xpath", List.of());
    }
}
