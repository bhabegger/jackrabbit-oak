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
import org.apache.jackrabbit.oak.plugins.index.search.spi.DocumentBuilder;
import org.apache.jackrabbit.oak.plugins.index.search.spi.IndexWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.RAMDirectory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Demonstrates how LuceneDocumentMaker SHOULD use the SPI for document creation.
 *
 * This test shows the pattern that production code like LuceneDocumentMaker can follow:
 * 1. Use DocumentBuilder from LuceneIndexHelper instead of direct Field creation
 * 2. Add fields via SPI methods instead of doc.add(new StringField(...))
 * 3. Build document once and pass to IndexWriter
 *
 * CURRENT STATE: LuceneDocumentMaker uses FieldFactory.newXxxField()
 * FUTURE STATE: LuceneDocumentMaker uses DocumentBuilder via SPI
 */
public class DocumentMakerSPIIntegrationTest {

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
     * Simulates what LuceneDocumentMaker.indexTypedProperty() SHOULD do.
     *
     * CURRENT: Creates Lucene fields directly
     * <pre>{@code
     * Field f = new LongField(pname, value, Field.Store.NO);
     * doc.add(f);
     * }</pre>
     *
     * PROPOSED: Use SPI DocumentBuilder
     * <pre>{@code
     * builder.addLongField(pname, value, FieldType.LONG);
     * }</pre>
     */
    @Test
    public void testTypedPropertyIndexing_SpiWay() throws IOException {
        // ==================================================
        // CURRENT WAY (what LuceneDocumentMaker does):
        // org.apache.lucene.document.Document doc = new Document();
        // doc.add(new LongField("jcr:created", 1234567890L, Field.Store.NO));
        // doc.add(new DoubleField("rating", 4.5, Field.Store.NO));
        // doc.add(new StringField("jcr:primaryType", "oak:Page", Field.Store.NO));
        // ==================================================

        // ==================================================
        // PROPOSED SPI WAY:
        // ==================================================
        DocumentBuilder builder = LuceneIndexHelper.newDocumentBuilder();

        // Verify we're using SPI implementations
        assertTrue("Builder should be Lucene47DocumentBuilder",
            builder instanceof Lucene47DocumentBuilder);

        Document doc = builder
            .addStringField(FieldNames.PATH, "/content/page", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addLongField("jcr:created", 1234567890L, DocumentBuilder.FieldType.LONG)
            .addDoubleField("rating", 4.5, DocumentBuilder.FieldType.DOUBLE)
            .addStringField("jcr:primaryType", "oak:Page", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .build();

        assertTrue("Document should be Lucene47Document",
            doc instanceof Lucene47Document);

        // Write to index
        try (IndexWriter writer = new Lucene47IndexWriter(directory)) {
            writer.addDocument(doc);
            writer.commit();
        }

        // Verify document was indexed correctly
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs results = searcher.search(new MatchAllDocsQuery(), 10);
            assertEquals(1, results.totalHits);

            org.apache.lucene.document.Document luceneDoc = searcher.doc(results.scoreDocs[0].doc);
            assertEquals("/content/page", luceneDoc.get(FieldNames.PATH));
            assertEquals("oak:Page", luceneDoc.get("jcr:primaryType"));
        }
    }

    /**
     * Simulates what LuceneDocumentMaker.indexFulltextValue() SHOULD do.
     *
     * CURRENT: Uses FieldFactory.newFulltextField()
     * <pre>{@code
     * doc.add(FieldFactory.newFulltextField(value));
     * }</pre>
     *
     * PROPOSED: Use SPI DocumentBuilder
     * <pre>{@code
     * builder.addStringField(FieldNames.FULLTEXT, value, FieldType.TEXT);
     * }</pre>
     */
    @Test
    public void testFulltextIndexing_SpiWay() throws IOException {
        DocumentBuilder builder = LuceneIndexHelper.newDocumentBuilder();

        Document doc = builder
            .addStringField(FieldNames.PATH, "/content/article", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField(FieldNames.FULLTEXT, "Apache Jackrabbit Oak content repository",
                           DocumentBuilder.FieldType.TEXT)
            .build();

        try (IndexWriter writer = new Lucene47IndexWriter(directory)) {
            writer.addDocument(doc);
            writer.commit();
        }

        // Verify fulltext was indexed
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            assertEquals(1, reader.numDocs());
        }
    }

    /**
     * Simulates what LuceneDocumentMaker.indexNotNullProperty() SHOULD do.
     *
     * CURRENT: Adds StringField directly
     * <pre>{@code
     * doc.add(new StringField(FieldNames.NOT_NULL_PROPS, pd.name, Field.Store.NO));
     * }</pre>
     *
     * PROPOSED: Use SPI DocumentBuilder
     * <pre>{@code
     * builder.addStringField(FieldNames.NOT_NULL_PROPS, propertyName, FieldType.STRING_NOT_ANALYZED);
     * }</pre>
     */
    @Test
    public void testNotNullPropertyIndexing_SpiWay() throws IOException {
        DocumentBuilder builder = LuceneIndexHelper.newDocumentBuilder();

        Document doc = builder
            .addStringField(FieldNames.PATH, "/test/node", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField(FieldNames.NOT_NULL_PROPS, "title", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField(FieldNames.NOT_NULL_PROPS, "description", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .build();

        try (IndexWriter writer = new Lucene47IndexWriter(directory)) {
            writer.addDocument(doc);
            writer.commit();
        }

        // Verify not-null markers were indexed
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            assertEquals(1, reader.numDocs());
        }
    }

    /**
     * Simulates a complete document creation flow that LuceneDocumentMaker would do,
     * but entirely through SPI.
     */
    @Test
    public void testCompleteDocumentCreation_SpiWay() throws IOException {
        // Simulate indexing a JCR node with various properties
        DocumentBuilder builder = LuceneIndexHelper.newDocumentBuilder();

        Document doc = builder
            // Core Oak fields
            .addStringField(FieldNames.PATH, "/content/blog/2024/article-1", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField(FieldNames.FULLTEXT, "Complete guide to Apache Oak repository implementation",
                           DocumentBuilder.FieldType.TEXT)

            // JCR metadata
            .addStringField("jcr:primaryType", "oak:Page", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField("jcr:mixinTypes", "mix:versionable", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addLongField("jcr:created", System.currentTimeMillis(), DocumentBuilder.FieldType.LONG)
            .addLongField("jcr:lastModified", System.currentTimeMillis(), DocumentBuilder.FieldType.LONG)

            // Custom properties
            .addStringField("title", "Oak Implementation Guide", DocumentBuilder.FieldType.TEXT)
            .addStringField("author", "John Doe", DocumentBuilder.FieldType.TEXT)
            .addStringField("category", "documentation", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addDoubleField("rating", 4.7, DocumentBuilder.FieldType.DOUBLE)
            .addLongField("viewCount", 1250L, DocumentBuilder.FieldType.LONG)

            // Not-null markers (simulating PropertyDefinition.notNullCheckEnabled)
            .addStringField(FieldNames.NOT_NULL_PROPS, "title", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField(FieldNames.NOT_NULL_PROPS, "author", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)

            .build();

        // Index the document
        try (IndexWriter writer = new Lucene47IndexWriter(directory)) {
            writer.addDocument(doc);
            writer.commit();
        }

        // Verify indexing
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            assertEquals("Should have 1 document", 1, reader.numDocs());

            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs results = searcher.search(new MatchAllDocsQuery(), 10);
            assertEquals(1, results.totalHits);

            // Verify fields
            org.apache.lucene.document.Document luceneDoc = searcher.doc(results.scoreDocs[0].doc);
            assertEquals("/content/blog/2024/article-1", luceneDoc.get(FieldNames.PATH));
            assertEquals("oak:Page", luceneDoc.get("jcr:primaryType"));
            assertEquals("John Doe", luceneDoc.get("author"));
            assertEquals("documentation", luceneDoc.get("category"));

            System.out.println("✓ Complete document indexed successfully via SPI");
            System.out.println("  - Path: " + luceneDoc.get(FieldNames.PATH));
            System.out.println("  - Primary Type: " + luceneDoc.get("jcr:primaryType"));
            System.out.println("  - Title: " + luceneDoc.get("title"));
            System.out.println("  - Author: " + luceneDoc.get("author"));
            System.out.println("  - Category: " + luceneDoc.get("category"));
        }
    }

    /**
     * Demonstrates the migration path: old code can coexist with new SPI code.
     */
    @Test
    public void testMigrationPath_BothWaysWork() throws IOException {
        // Document 1: OLD WAY (direct Lucene API)
        org.apache.lucene.document.Document oldDoc = new org.apache.lucene.document.Document();
        oldDoc.add(new org.apache.lucene.document.StringField(FieldNames.PATH, "/old/path",
                  org.apache.lucene.document.Field.Store.YES));
        oldDoc.add(new org.apache.lucene.document.TextField("title", "Old Way Document",
                  org.apache.lucene.document.Field.Store.YES));

        // Document 2: NEW WAY (SPI)
        DocumentBuilder builder = LuceneIndexHelper.newDocumentBuilder();
        Document newDoc = builder
            .addStringField(FieldNames.PATH, "/new/path", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField("title", "New SPI Way Document", DocumentBuilder.FieldType.TEXT)
            .build();

        // Both can be indexed together
        try (IndexWriter writer = new Lucene47IndexWriter(directory)) {
            // Old way: extract Lucene document, add directly
            writer.addDocument(new Lucene47Document(oldDoc));
            // New way: via SPI
            writer.addDocument(newDoc);
            writer.commit();
        }

        // Both are searchable
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            assertEquals("Should have 2 documents (old + new)", 2, reader.numDocs());
            System.out.println("✓ Migration path works: old and new code coexist");
        }
    }
}
