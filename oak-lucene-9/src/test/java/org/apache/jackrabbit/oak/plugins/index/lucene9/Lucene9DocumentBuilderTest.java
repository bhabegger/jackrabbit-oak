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
import org.apache.jackrabbit.oak.plugins.index.search.spi.DocumentBuilder.FieldType;
import org.junit.Test;
import static org.junit.Assert.*;

public class Lucene9DocumentBuilderTest {

    @Test
    public void testBuildEmptyDocument() {
        DocumentBuilder builder = new Lucene9DocumentBuilder();
        Document doc = builder.build();
        assertNotNull(doc);
        assertTrue(doc instanceof Lucene9Document);
    }

    @Test
    public void testAddStringField() {
        DocumentBuilder builder = new Lucene9DocumentBuilder();
        Document doc = builder
            .addStringField("path", "/content", FieldType.STRING_NOT_ANALYZED)
            .build();

        assertNotNull(doc);
        Lucene9Document lucene9Doc = (Lucene9Document) doc;
        assertEquals(1, lucene9Doc.getLuceneDocument().getFields().size());
    }

    @Test
    public void testAddLongField() {
        DocumentBuilder builder = new Lucene9DocumentBuilder();
        Document doc = builder
            .addLongField("size", 12345L, FieldType.LONG)
            .build();

        assertNotNull(doc);
        Lucene9Document lucene9Doc = (Lucene9Document) doc;
        assertEquals(2, lucene9Doc.getLuceneDocument().getFields().size()); // LongPoint + StoredField
    }

    @Test
    public void testChaining() {
        DocumentBuilder builder = new Lucene9DocumentBuilder();
        Document doc = builder
            .addStringField("path", "/content", FieldType.TEXT)
            .addStringField("title", "Hello", FieldType.TEXT)
            .addLongField("size", 100L, FieldType.LONG)
            .build();

        Lucene9Document lucene9Doc = (Lucene9Document) doc;
        assertEquals(4, lucene9Doc.getLuceneDocument().getFields().size()); // 2 text + LongPoint + StoredField
    }
}
