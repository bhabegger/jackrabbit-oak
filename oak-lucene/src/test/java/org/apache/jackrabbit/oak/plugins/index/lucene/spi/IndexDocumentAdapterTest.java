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
import org.apache.jackrabbit.oak.plugins.index.search.spi.Document;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Tests demonstrating IndexDocumentAdapter usage in oak-lucene runtime.
 * Shows how SPI-created documents work with actual Lucene indexing.
 */
public class IndexDocumentAdapterTest {

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
    public void testCreatePathDocument() {
        Document doc = IndexDocumentAdapter.createPathDocument("/content/page1");
        assertNotNull(doc);

        // Verify it's a valid Lucene document
        org.apache.lucene.document.Document luceneDoc =
            IndexDocumentAdapter.toLuceneDocument(doc);
        assertEquals("/content/page1", luceneDoc.get(FieldNames.PATH));
    }

    @Test
    public void testCreateFulltextDocument() {
        Document doc = IndexDocumentAdapter.createFulltextDocument(
            "/content/article",
            "Oak is a content repository"
        );
        assertNotNull(doc);

        org.apache.lucene.document.Document luceneDoc =
            IndexDocumentAdapter.toLuceneDocument(doc);
        assertEquals("/content/article", luceneDoc.get(FieldNames.PATH));
        assertEquals("Oak is a content repository", luceneDoc.get(FieldNames.FULLTEXT));
    }

    @Test
    public void testCreatePropertyDocument() {
        Document doc = IndexDocumentAdapter.createPropertyDocument(
            "/content/page",
            "title",
            "Introduction to Oak",
            true // analyzed
        );
        assertNotNull(doc);

        org.apache.lucene.document.Document luceneDoc =
            IndexDocumentAdapter.toLuceneDocument(doc);
        assertEquals("/content/page", luceneDoc.get(FieldNames.PATH));
        assertEquals("Introduction to Oak", luceneDoc.get("title"));
    }

    @Test
    public void testCreateNumericPropertyDocument() {
        long timestamp = System.currentTimeMillis();
        Document doc = IndexDocumentAdapter.createNumericPropertyDocument(
            "/content/page",
            "created",
            timestamp
        );
        assertNotNull(doc);

        org.apache.lucene.document.Document luceneDoc =
            IndexDocumentAdapter.toLuceneDocument(doc);
        assertEquals("/content/page", luceneDoc.get(FieldNames.PATH));
        assertNotNull("Should have created field", luceneDoc.getField("created"));
    }

    @Test
    public void testRuntimeIntegrationWithActualIndex() throws IOException {
        // Create documents via SPI adapter (simulating oak-lucene usage)
        Document doc1 = IndexDocumentAdapter.createFulltextDocument(
            "/content/page1",
            "Apache Jackrabbit Oak"
        );

        Document doc2 = IndexDocumentAdapter.createFulltextDocument(
            "/content/page2",
            "Lucene indexing and search"
        );

        Document doc3 = IndexDocumentAdapter.createNumericPropertyDocument(
            "/content/page3",
            "score",
            95L
        );

        // Index them using actual Lucene (simulating LuceneDocumentMaker integration)
        IndexWriterConfig config = new IndexWriterConfig(
            Version.LUCENE_47,
            new StandardAnalyzer(Version.LUCENE_47)
        );

        try (IndexWriter writer = new IndexWriter(directory, config)) {
            // Convert SPI documents to Lucene documents (bridge pattern)
            writer.addDocument(IndexDocumentAdapter.toLuceneDocument(doc1));
            writer.addDocument(IndexDocumentAdapter.toLuceneDocument(doc2));
            writer.addDocument(IndexDocumentAdapter.toLuceneDocument(doc3));
            writer.commit();
        }

        // Verify documents were indexed correctly
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs results = searcher.search(new MatchAllDocsQuery(), 10);

            assertEquals("Should have indexed 3 documents", 3, results.totalHits);

            // Verify we can read back the documents
            org.apache.lucene.document.Document readDoc1 =
                searcher.doc(results.scoreDocs[0].doc);
            assertNotNull(readDoc1.get(FieldNames.PATH));
        }
    }

    @Test
    public void testBridgePattern() {
        // Create via SPI
        Document spiDoc = IndexDocumentAdapter.createPathDocument("/test");

        // Convert to Lucene (for existing oak-lucene code)
        org.apache.lucene.document.Document luceneDoc =
            IndexDocumentAdapter.toLuceneDocument(spiDoc);

        // Verify round-trip
        assertNotNull(luceneDoc);
        assertEquals("/test", luceneDoc.get(FieldNames.PATH));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBridgeRejectsNonLucene47Documents() {
        // Create a mock Document that's not Lucene47Document
        Document invalidDoc = new Document() {};

        // Should throw because it's not compatible
        IndexDocumentAdapter.toLuceneDocument(invalidDoc);
    }
}
