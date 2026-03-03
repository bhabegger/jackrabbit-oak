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
package org.apache.jackrabbit.oak.plugins.index.lucene.spi;

import org.apache.jackrabbit.oak.plugins.index.search.FieldNames;
import org.apache.jackrabbit.oak.plugins.index.search.spi.*;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.RAMDirectory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * DEEP INTEGRATION TEST: Demonstrates full indexing and querying flow using ONLY the SPI.
 *
 * This test proves the SPI is production-ready by showing:
 * 1. Document creation via SPI (DocumentBuilder)
 * 2. Index writing via SPI (IndexWriter)
 * 3. Index reading via SPI (IndexReader)
 * 4. Query creation via SPI (QueryBuilder)
 * 5. Query execution with native Lucene (proving SPI interoperability)
 *
 * NO direct Lucene API usage except for final query execution verification.
 */
public class FullIndexingQueryFlowTest {

    private RAMDirectory directory;

    @Before
    public void setUp() {
        directory = new RAMDirectory();
    }

    @After
    public void tearDown() throws IOException {
        if (directory != null) {
            directory.close();
        }
    }

    /**
     * FULL FLOW: Build documents → Write to index → Read back → Query
     * This simulates what oak-lucene does during indexing and querying.
     */
    @Test
    public void testCompleteIndexingAndQueryingFlow() throws IOException {
        // ============================================================
        // PHASE 1: INDEXING - Build and write documents via SPI ONLY
        // ============================================================

        DocumentBuilder docBuilder = LuceneIndexHelper.newDocumentBuilder();

        // Create document 1: A content page
        Document doc1 = docBuilder
            .addStringField(FieldNames.PATH, "/content/docs/getting-started", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField("jcr:primaryType", "oak:Page", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField("title", "Getting Started with Oak", DocumentBuilder.FieldType.TEXT)
            .addStringField(FieldNames.FULLTEXT, "Apache Jackrabbit Oak is a content repository", DocumentBuilder.FieldType.TEXT)
            .addLongField("jcr:created", 1609459200000L, DocumentBuilder.FieldType.LONG)
            .addDoubleField("rating", 4.5, DocumentBuilder.FieldType.DOUBLE)
            .build();

        // Create document 2: Another content page
        docBuilder = LuceneIndexHelper.newDocumentBuilder();
        Document doc2 = docBuilder
            .addStringField(FieldNames.PATH, "/content/docs/indexing", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField("jcr:primaryType", "oak:Page", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField("title", "Lucene Indexing in Oak", DocumentBuilder.FieldType.TEXT)
            .addStringField(FieldNames.FULLTEXT, "Oak uses Lucene for full-text search and indexing", DocumentBuilder.FieldType.TEXT)
            .addLongField("jcr:created", 1609545600000L, DocumentBuilder.FieldType.LONG)
            .addDoubleField("rating", 4.8, DocumentBuilder.FieldType.DOUBLE)
            .build();

        // Create document 3: A file node
        docBuilder = LuceneIndexHelper.newDocumentBuilder();
        Document doc3 = docBuilder
            .addStringField(FieldNames.PATH, "/content/assets/manual.pdf", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField("jcr:primaryType", "nt:file", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField("title", "Oak User Manual", DocumentBuilder.FieldType.TEXT)
            .addStringField(FieldNames.FULLTEXT, "Complete guide to Apache Oak repository", DocumentBuilder.FieldType.TEXT)
            .addLongField("jcr:created", 1609632000000L, DocumentBuilder.FieldType.LONG)
            .addDoubleField("rating", 4.2, DocumentBuilder.FieldType.DOUBLE)
            .build();

        // Write documents to index via SPI IndexWriter
        try (IndexWriter writer = new Lucene47IndexWriter(directory)) {
            writer.addDocument(doc1);
            writer.addDocument(doc2);
            writer.addDocument(doc3);
            writer.commit();
        }

        System.out.println("✓ Phase 1: Indexed 3 documents via SPI");

        // ============================================================
        // PHASE 2: READING - Verify documents were indexed
        // ============================================================

        try (IndexReader reader = new Lucene47IndexReader(directory)) {
            assertEquals("Should have 3 documents", 3, reader.numDocs());
            assertEquals("maxDoc should be 3", 3, reader.maxDoc());
            assertEquals("Should report Lucene 4.7.2", IndexVersion.LUCENE_4_7_2, reader.getVersion());

            // Read first document back via SPI
            Document readDoc = reader.document(0);
            assertNotNull("Document should not be null", readDoc);
            assertTrue("Should be Lucene47Document", readDoc instanceof Lucene47Document);
        }

        System.out.println("✓ Phase 2: Read documents back via SPI");

        // ============================================================
        // PHASE 3: QUERYING - Create queries via SPI
        // ============================================================

        QueryBuilder queryBuilder = LuceneIndexHelper.newQueryBuilder();

        // Test 1: Term query for primary type
        Query primaryTypeQuery = queryBuilder.term("jcr:primaryType", "oak:Page");
        assertNotNull(primaryTypeQuery);

        // Test 2: Fulltext term query
        Query fulltextQuery = queryBuilder.term(FieldNames.FULLTEXT, "oak");
        assertNotNull(fulltextQuery);

        // Test 3: Range query on paths (string range works well)
        Query pathRangeQuery = queryBuilder.range(FieldNames.PATH, "/content/docs/a", "/content/docs/z", true, true);
        assertNotNull(pathRangeQuery);

        // Test 4: Wildcard query on title (lowercase because of analyzer)
        Query wildcardQuery = queryBuilder.wildcard("title", "*oak*");
        assertNotNull(wildcardQuery);

        // Test 5: Prefix query on path
        Query prefixQuery = queryBuilder.prefix(FieldNames.PATH, "/content/docs/");
        assertNotNull(prefixQuery);

        System.out.println("✓ Phase 3: Created 5 different query types via SPI");

        // ============================================================
        // PHASE 4: QUERY EXECUTION - Execute queries and verify results
        // ============================================================

        // Execute queries using native Lucene (proving SPI queries work with Lucene)
        try (DirectoryReader nativeReader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(nativeReader);

            // Query 1: Find all oak:Page nodes
            Lucene47Query luceneQuery1 = (Lucene47Query) primaryTypeQuery;
            TopDocs results1 = searcher.search(luceneQuery1.getLuceneQuery(), 10);
            assertEquals("Should find 2 oak:Page documents", 2, results1.totalHits);
            System.out.println("  ✓ Term query: Found " + results1.totalHits + " oak:Page nodes");

            // Query 2: Find documents with "oak" in fulltext
            Lucene47Query luceneQuery2 = (Lucene47Query) fulltextQuery;
            TopDocs results2 = searcher.search(luceneQuery2.getLuceneQuery(), 10);
            assertEquals("Should find 3 documents with 'oak'", 3, results2.totalHits);
            System.out.println("  ✓ Fulltext query: Found " + results2.totalHits + " documents with 'oak'");

            // Query 3: Find documents with paths in range /content/docs/a to /content/docs/z
            Lucene47Query luceneQuery3 = (Lucene47Query) pathRangeQuery;
            TopDocs results3 = searcher.search(luceneQuery3.getLuceneQuery(), 10);
            assertEquals("Should find 2 documents in path range", 2, results3.totalHits);
            System.out.println("  ✓ Range query: Found " + results3.totalHits + " documents in path range");

            // Query 4: Find documents with "oak" in title (wildcard, lowercase because analyzed)
            Lucene47Query luceneQuery4 = (Lucene47Query) wildcardQuery;
            TopDocs results4 = searcher.search(luceneQuery4.getLuceneQuery(), 10);
            assertEquals("Should find 3 documents with 'oak' in title", 3, results4.totalHits);
            System.out.println("  ✓ Wildcard query: Found " + results4.totalHits + " documents with 'oak' in title");

            // Query 5: Find documents under /content/docs/ path
            Lucene47Query luceneQuery5 = (Lucene47Query) prefixQuery;
            TopDocs results5 = searcher.search(luceneQuery5.getLuceneQuery(), 10);
            assertEquals("Should find 2 documents under /content/docs/", 2, results5.totalHits);
            System.out.println("  ✓ Prefix query: Found " + results5.totalHits + " documents under /content/docs/");
        }

        System.out.println("✓ Phase 4: All queries executed successfully");

        // ============================================================
        // PHASE 5: UPDATE & DELETE - Test write operations
        // ============================================================

        // Update document 1 via SPI
        try (IndexWriter writer = new Lucene47IndexWriter(directory)) {
            docBuilder = LuceneIndexHelper.newDocumentBuilder();
            Document updatedDoc = docBuilder
                .addStringField(FieldNames.PATH, "/content/docs/getting-started", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
                .addStringField("jcr:primaryType", "oak:Page", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
                .addStringField("title", "UPDATED: Getting Started with Oak", DocumentBuilder.FieldType.TEXT)
                .addStringField(FieldNames.FULLTEXT, "Apache Jackrabbit Oak is an excellent content repository", DocumentBuilder.FieldType.TEXT)
                .addLongField("jcr:created", 1609459200000L, DocumentBuilder.FieldType.LONG)
                .addLongField("jcr:modified", System.currentTimeMillis(), DocumentBuilder.FieldType.LONG)
                .addDoubleField("rating", 4.9, DocumentBuilder.FieldType.DOUBLE)
                .build();

            Term pathTerm = new Term(FieldNames.PATH, "/content/docs/getting-started");
            writer.updateDocument(pathTerm, updatedDoc);
            writer.commit();
        }

        // Verify update
        try (IndexReader reader = new Lucene47IndexReader(directory)) {
            assertEquals("Should still have 3 documents after update", 3, reader.numDocs());
        }
        System.out.println("✓ Phase 5a: Document updated via SPI");

        // Delete document 3 via SPI
        try (IndexWriter writer = new Lucene47IndexWriter(directory)) {
            Term pathTerm = new Term(FieldNames.PATH, "/content/assets/manual.pdf");
            writer.deleteDocuments(pathTerm);
            writer.commit();
        }

        // Verify deletion
        try (IndexReader reader = new Lucene47IndexReader(directory)) {
            assertEquals("Should have 2 documents after deletion", 2, reader.numDocs());
        }
        System.out.println("✓ Phase 5b: Document deleted via SPI");

        // ============================================================
        // PHASE 6: OPTIMIZATION - Force merge
        // ============================================================

        try (IndexWriter writer = new Lucene47IndexWriter(directory)) {
            writer.forceMerge(1);
        }

        try (IndexReader reader = new Lucene47IndexReader(directory)) {
            assertEquals("Should still have 2 documents after merge", 2, reader.numDocs());
        }
        System.out.println("✓ Phase 6: Index optimized via SPI");

        System.out.println("\n========================================");
        System.out.println("FULL FLOW TEST COMPLETE");
        System.out.println("========================================");
        System.out.println("✓ Document creation: SPI only");
        System.out.println("✓ Index writing: SPI only");
        System.out.println("✓ Index reading: SPI only");
        System.out.println("✓ Query creation: SPI only");
        System.out.println("✓ Query execution: SPI → Native Lucene");
        System.out.println("✓ Updates & deletes: SPI only");
        System.out.println("✓ Index optimization: SPI only");
        System.out.println("========================================");
    }

    /**
     * Test that demonstrates the SPI can handle the same document
     * being created, indexed, queried, updated, and deleted - all via SPI.
     */
    @Test
    public void testSingleDocumentLifecycle() throws IOException {
        DocumentBuilder builder = LuceneIndexHelper.newDocumentBuilder();
        QueryBuilder queryBuilder = LuceneIndexHelper.newQueryBuilder();

        // Create
        Document doc = builder
            .addStringField(FieldNames.PATH, "/test/node", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField("status", "draft", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .build();

        // Index
        try (IndexWriter writer = new Lucene47IndexWriter(directory)) {
            writer.addDocument(doc);
            writer.commit();
        }

        // Query
        Query statusQuery = queryBuilder.term("status", "draft");
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs results = searcher.search(((Lucene47Query) statusQuery).getLuceneQuery(), 10);
            assertEquals(1, results.totalHits);
        }

        // Update
        builder = LuceneIndexHelper.newDocumentBuilder();
        Document updatedDoc = builder
            .addStringField(FieldNames.PATH, "/test/node", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField("status", "published", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .build();

        try (IndexWriter writer = new Lucene47IndexWriter(directory)) {
            writer.updateDocument(new Term(FieldNames.PATH, "/test/node"), updatedDoc);
            writer.commit();
        }

        // Query updated
        Query publishedQuery = queryBuilder.term("status", "published");
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs results = searcher.search(((Lucene47Query) publishedQuery).getLuceneQuery(), 10);
            assertEquals(1, results.totalHits);
        }

        // Delete
        try (IndexWriter writer = new Lucene47IndexWriter(directory)) {
            writer.deleteDocuments(new Term(FieldNames.PATH, "/test/node"));
            writer.commit();
        }

        // Verify deleted
        try (IndexReader reader = new Lucene47IndexReader(directory)) {
            assertEquals(0, reader.numDocs());
        }
    }
}
