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

import org.apache.jackrabbit.oak.plugins.index.search.spi.*;
import org.apache.jackrabbit.oak.plugins.index.search.spi.DocumentBuilder.FieldType;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * End-to-end integration test demonstrating full Lucene 9 workflow:
 * document building, indexing, querying, updating, and reading.
 */
public class Lucene9IntegrationTest {

    private ByteBuffersDirectory directory;

    @Before
    public void setUp() {
        directory = new ByteBuffersDirectory();
    }

    @After
    public void tearDown() throws IOException {
        if (directory != null) {
            directory.close();
        }
    }

    @Test
    public void testCompleteWorkflow() throws IOException {
        // Step 1: Build and index documents using Oak SPI
        DocumentBuilder builder = new Lucene9DocumentBuilder();

        Document doc1 = builder
            .addStringField("id", "page1", FieldType.STRING_NOT_ANALYZED)
            .addStringField("path", "/content/page1", FieldType.TEXT)
            .addStringField("title", "Introduction to Oak", FieldType.TEXT)
            .addStringField("content", "Oak is a content repository", FieldType.TEXT)
            .addLongField("created", 1234567890L, FieldType.LONG)
            .build();

        Document doc2 = new Lucene9DocumentBuilder()
            .addStringField("id", "page2", FieldType.STRING_NOT_ANALYZED)
            .addStringField("path", "/content/page2", FieldType.TEXT)
            .addStringField("title", "Advanced Oak Features", FieldType.TEXT)
            .addStringField("content", "Learn about Oak indexing and search", FieldType.TEXT)
            .addLongField("created", 1234567900L, FieldType.LONG)
            .build();

        Document doc3 = new Lucene9DocumentBuilder()
            .addStringField("id", "page3", FieldType.STRING_NOT_ANALYZED)
            .addStringField("path", "/content/page3", FieldType.TEXT)
            .addStringField("title", "Lucene Integration", FieldType.TEXT)
            .addStringField("content", "Lucene provides full-text search", FieldType.TEXT)
            .addLongField("created", 1234567910L, FieldType.LONG)
            .build();

        // Step 2: Write documents using IndexWriter
        try (IndexWriter writer = new Lucene9IndexWriter(directory)) {
            writer.addDocument(doc1);
            writer.addDocument(doc2);
            writer.addDocument(doc3);
            writer.commit();
        }

        // Step 3: Read using IndexReader
        try (IndexReader reader = new Lucene9IndexReader(directory)) {
            assertEquals("Should have 3 documents", 3, reader.numDocs());
            assertEquals("maxDoc should be 3", 3, reader.maxDoc());
            assertEquals("Should be Lucene 9", IndexVersion.LUCENE_9_X, reader.getVersion());

            // Read specific document
            Document readDoc = reader.document(0);
            assertNotNull("Document should not be null", readDoc);
        }

        // Step 4: Query using QueryBuilder and native Lucene search
        QueryBuilder queryBuilder = new Lucene9QueryBuilder();

        // Create queries
        Query termQuery = queryBuilder.term("content", "oak");
        Query prefixQuery = queryBuilder.prefix("path", "/content/");
        Query wildcardQuery = queryBuilder.wildcard("title", "*Oak*");

        // Execute search with native Lucene
        try (DirectoryReader nativeReader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(nativeReader);

            // Search for "oak" in content
            Lucene9Query luceneQuery = (Lucene9Query) termQuery;
            TopDocs results = searcher.search(luceneQuery.getLuceneQuery(), 10);
            assertEquals("Should find 2 documents with 'oak'", 2, results.totalHits.value);

            // Verify we can access the documents
            for (ScoreDoc scoreDoc : results.scoreDocs) {
                org.apache.lucene.document.Document luceneDoc =
                    searcher.storedFields().document(scoreDoc.doc);
                assertNotNull(luceneDoc);
                assertNotNull(luceneDoc.get("title"));
            }
        }

        // Step 5: Update a document
        try (IndexWriter writer = new Lucene9IndexWriter(directory)) {
            Document updatedDoc = new Lucene9DocumentBuilder()
                .addStringField("id", "page1", FieldType.STRING_NOT_ANALYZED)
                .addStringField("path", "/content/page1", FieldType.TEXT)
                .addStringField("title", "Updated: Introduction to Oak", FieldType.TEXT)
                .addStringField("content", "Oak is an excellent content repository", FieldType.TEXT)
                .addLongField("created", 1234567890L, FieldType.LONG)
                .addLongField("modified", 1234567920L, FieldType.LONG)
                .build();

            Term idTerm = new Term("id", "page1");
            writer.updateDocument(idTerm, updatedDoc);
            writer.commit();
        }

        // Step 6: Verify update
        try (IndexReader reader = new Lucene9IndexReader(directory)) {
            assertEquals("Should still have 3 documents after update", 3, reader.numDocs());
        }

        // Step 7: Delete a document
        try (IndexWriter writer = new Lucene9IndexWriter(directory)) {
            Term idTerm = new Term("id", "page3");
            writer.deleteDocuments(idTerm);
            writer.commit();
        }

        // Step 8: Verify deletion
        try (IndexReader reader = new Lucene9IndexReader(directory)) {
            assertEquals("Should have 2 documents after deletion", 2, reader.numDocs());
        }

        // Step 9: Force merge to optimize
        try (IndexWriter writer = new Lucene9IndexWriter(directory)) {
            writer.forceMerge(1);
        }

        // Step 10: Final verification
        try (IndexReader reader = new Lucene9IndexReader(directory)) {
            assertEquals("Should still have 2 documents after merge", 2, reader.numDocs());
        }
    }

    @Test
    public void testRangeQuery() throws IOException {
        // Index documents with numeric fields
        try (IndexWriter writer = new Lucene9IndexWriter(directory)) {
            for (int i = 1; i <= 10; i++) {
                Document doc = new Lucene9DocumentBuilder()
                    .addStringField("id", "doc" + i, FieldType.STRING_NOT_ANALYZED)
                    .addLongField("score", i * 10L, FieldType.LONG)
                    .build();
                writer.addDocument(doc);
            }
            writer.commit();
        }

        // Verify documents were indexed
        try (IndexReader reader = new Lucene9IndexReader(directory)) {
            assertEquals("Should have 10 documents", 10, reader.numDocs());
        }

        // Query with range (this tests the QueryBuilder)
        QueryBuilder queryBuilder = new Lucene9QueryBuilder();
        Query rangeQuery = queryBuilder.range("id", "doc3", "doc7", true, true);
        assertNotNull("Range query should not be null", rangeQuery);
    }

    @Test
    public void testMultipleFieldTypes() throws IOException {
        // Test document with various field types
        Document doc = new Lucene9DocumentBuilder()
            .addStringField("analyzed", "This is analyzed text", FieldType.STRING_ANALYZED)
            .addStringField("notAnalyzed", "exact-match", FieldType.STRING_NOT_ANALYZED)
            .addStringField("text", "Full text content", FieldType.TEXT)
            .addLongField("longValue", 12345L, FieldType.LONG)
            .addDoubleField("doubleValue", 123.45, FieldType.DOUBLE)
            .addBinaryField("binary", new byte[]{1, 2, 3, 4})
            .addStringField("storedOnly", "not indexed", FieldType.STORED_ONLY)
            .build();

        try (IndexWriter writer = new Lucene9IndexWriter(directory)) {
            writer.addDocument(doc);
            writer.commit();
        }

        try (IndexReader reader = new Lucene9IndexReader(directory)) {
            assertEquals("Should have 1 document", 1, reader.numDocs());

            Document readDoc = reader.document(0);
            assertNotNull(readDoc);
            assertTrue(readDoc instanceof Lucene9Document);
        }
    }
}
