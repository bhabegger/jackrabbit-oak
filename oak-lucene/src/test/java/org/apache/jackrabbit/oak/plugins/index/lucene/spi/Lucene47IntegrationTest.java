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

import org.apache.jackrabbit.oak.plugins.index.search.spi.Document;
import org.apache.jackrabbit.oak.plugins.index.search.spi.DocumentBuilder;
import org.apache.jackrabbit.oak.plugins.index.search.spi.Query;
import org.apache.jackrabbit.oak.plugins.index.search.spi.QueryBuilder;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Integration test verifying that Lucene 4.7 SPI wrappers work correctly.
 * Demonstrates that the Oak Search SPI can be implemented against the embedded Lucene 4.7.2.
 */
public class Lucene47IntegrationTest {

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

    @Test
    public void testDocumentBuilding() {
        DocumentBuilder builder = new Lucene47DocumentBuilder();

        Document doc = builder
            .addStringField("id", "doc1", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField("title", "Introduction to Oak", DocumentBuilder.FieldType.TEXT)
            .addLongField("created", 1234567890L, DocumentBuilder.FieldType.LONG)
            .addDoubleField("score", 98.5, DocumentBuilder.FieldType.DOUBLE)
            .addBinaryField("data", new byte[]{1, 2, 3})
            .build();

        assertNotNull("Document should not be null", doc);
        assertTrue("Document should be Lucene47Document", doc instanceof Lucene47Document);

        // Verify underlying Lucene document has fields
        org.apache.lucene.document.Document luceneDoc = ((Lucene47Document) doc).getLuceneDocument();
        assertNotNull("Lucene document should not be null", luceneDoc);
        assertEquals("Should have id field", "doc1", luceneDoc.get("id"));
        assertEquals("Should have title field", "Introduction to Oak", luceneDoc.get("title"));
    }

    @Test
    public void testQueryBuilding() {
        QueryBuilder queryBuilder = new Lucene47QueryBuilder();

        Query termQuery = queryBuilder.term("field", "value");
        assertNotNull("Term query should not be null", termQuery);
        assertTrue("Query should be Lucene47Query", termQuery instanceof Lucene47Query);

        Query rangeQuery = queryBuilder.range("field", "a", "z", true, true);
        assertNotNull("Range query should not be null", rangeQuery);

        Query wildcardQuery = queryBuilder.wildcard("field", "val*");
        assertNotNull("Wildcard query should not be null", wildcardQuery);

        Query prefixQuery = queryBuilder.prefix("field", "val");
        assertNotNull("Prefix query should not be null", prefixQuery);
    }

    @Test
    public void testEndToEndWorkflow() throws IOException {
        // Build documents using SPI
        DocumentBuilder builder = new Lucene47DocumentBuilder();

        Document doc1 = builder
            .addStringField("id", "page1", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField("content", "Oak is a content repository", DocumentBuilder.FieldType.TEXT)
            .build();

        Document doc2 = new Lucene47DocumentBuilder()
            .addStringField("id", "page2", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField("content", "Learn about Oak indexing", DocumentBuilder.FieldType.TEXT)
            .build();

        // Index using native Lucene 4.7 (simulating what oak-lucene does)
        IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_47, new StandardAnalyzer(Version.LUCENE_47));
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            writer.addDocument(((Lucene47Document) doc1).getLuceneDocument());
            writer.addDocument(((Lucene47Document) doc2).getLuceneDocument());
            writer.commit();
        }

        // Query using SPI
        QueryBuilder queryBuilder = new Lucene47QueryBuilder();
        Query query = queryBuilder.term("content", "oak");

        // Search using native Lucene 4.7
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Lucene47Query luceneQuery = (Lucene47Query) query;
            TopDocs results = searcher.search(luceneQuery.getLuceneQuery(), 10);

            assertEquals("Should find 2 documents with 'oak'", 2, results.totalHits);
        }
    }

    @Test
    public void testFieldTypes() {
        DocumentBuilder builder = new Lucene47DocumentBuilder();

        Document doc = builder
            .addStringField("analyzed", "This is analyzed text", DocumentBuilder.FieldType.STRING_ANALYZED)
            .addStringField("notAnalyzed", "exact-match", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField("text", "Full text content", DocumentBuilder.FieldType.TEXT)
            .addStringField("storedOnly", "not indexed", DocumentBuilder.FieldType.STORED_ONLY)
            .build();

        assertNotNull(doc);

        org.apache.lucene.document.Document luceneDoc = ((Lucene47Document) doc).getLuceneDocument();
        assertEquals("analyzed field", "This is analyzed text", luceneDoc.get("analyzed"));
        assertEquals("notAnalyzed field", "exact-match", luceneDoc.get("notAnalyzed"));
        assertEquals("text field", "Full text content", luceneDoc.get("text"));
        assertEquals("storedOnly field", "not indexed", luceneDoc.get("storedOnly"));
    }
}
