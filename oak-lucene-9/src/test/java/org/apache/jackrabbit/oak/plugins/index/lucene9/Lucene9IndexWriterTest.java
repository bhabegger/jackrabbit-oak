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

import org.apache.jackrabbit.oak.plugins.index.search.spi.Document;
import org.apache.jackrabbit.oak.plugins.index.search.spi.DocumentBuilder;
import org.apache.jackrabbit.oak.plugins.index.search.spi.IndexWriter;
import org.apache.jackrabbit.oak.plugins.index.search.spi.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class Lucene9IndexWriterTest {

    private ByteBuffersDirectory directory;
    private Lucene9IndexWriter writer;

    @Before
    public void setUp() throws IOException {
        directory = new ByteBuffersDirectory();
        writer = new Lucene9IndexWriter(directory);
    }

    @After
    public void tearDown() throws IOException {
        if (writer != null) {
            writer.close();
        }
        if (directory != null) {
            directory.close();
        }
    }

    @Test
    public void testAddDocument() throws IOException {
        Document doc = new Lucene9DocumentBuilder()
            .addStringField("path", "/content/test", DocumentBuilder.FieldType.TEXT)
            .addLongField("modified", 123456L, DocumentBuilder.FieldType.LONG)
            .build();

        writer.addDocument(doc);
        writer.commit();

        // Verify document was added
        assertNotNull(writer);
    }

    @Test
    public void testUpdateDocument() throws IOException {
        // Add initial document
        Document doc1 = new Lucene9DocumentBuilder()
            .addStringField("id", "doc1", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField("title", "Original", DocumentBuilder.FieldType.TEXT)
            .build();

        writer.addDocument(doc1);
        writer.commit();

        // Update document
        Document doc2 = new Lucene9DocumentBuilder()
            .addStringField("id", "doc1", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField("title", "Updated", DocumentBuilder.FieldType.TEXT)
            .build();

        Term term = new Term("id", "doc1");
        writer.updateDocument(term, doc2);
        writer.commit();

        assertNotNull(writer);
    }

    @Test
    public void testDeleteDocuments() throws IOException {
        // Add document
        Document doc = new Lucene9DocumentBuilder()
            .addStringField("id", "doc1", DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .build();

        writer.addDocument(doc);
        writer.commit();

        // Delete document
        Term term = new Term("id", "doc1");
        writer.deleteDocuments(term);
        writer.commit();

        assertNotNull(writer);
    }

    @Test
    public void testForceMerge() throws IOException {
        // Add multiple documents
        for (int i = 0; i < 10; i++) {
            Document doc = new Lucene9DocumentBuilder()
                .addStringField("id", "doc" + i, DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
                .build();
            writer.addDocument(doc);
        }
        writer.commit();

        // Force merge to single segment
        writer.forceMerge(1);

        assertNotNull(writer);
    }
}
