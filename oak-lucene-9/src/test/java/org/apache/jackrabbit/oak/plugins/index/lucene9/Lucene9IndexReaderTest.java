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
import org.apache.jackrabbit.oak.plugins.index.search.spi.IndexReader;
import org.apache.jackrabbit.oak.plugins.index.search.spi.IndexVersion;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class Lucene9IndexReaderTest {

    private ByteBuffersDirectory directory;
    private Lucene9IndexWriter writer;
    private Lucene9IndexReader reader;

    @Before
    public void setUp() throws IOException {
        directory = new ByteBuffersDirectory();
        writer = new Lucene9IndexWriter(directory);

        // Add some test documents
        for (int i = 0; i < 5; i++) {
            Document doc = new Lucene9DocumentBuilder()
                .addStringField("id", "doc" + i, DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
                .addStringField("title", "Title " + i, DocumentBuilder.FieldType.TEXT)
                .addLongField("index", i, DocumentBuilder.FieldType.LONG)
                .build();
            writer.addDocument(doc);
        }
        writer.commit();
        writer.close();

        // Open reader
        reader = new Lucene9IndexReader(directory);
    }

    @After
    public void tearDown() throws IOException {
        if (reader != null) {
            reader.close();
        }
        if (directory != null) {
            directory.close();
        }
    }

    @Test
    public void testNumDocs() {
        assertEquals(5, reader.numDocs());
    }

    @Test
    public void testMaxDoc() {
        assertEquals(5, reader.maxDoc());
    }

    @Test
    public void testGetVersion() {
        assertEquals(IndexVersion.LUCENE_9_X, reader.getVersion());
    }

    @Test
    public void testDocument() throws IOException {
        Document doc = reader.document(0);
        assertNotNull(doc);
        assertTrue(doc instanceof Lucene9Document);
    }
}
