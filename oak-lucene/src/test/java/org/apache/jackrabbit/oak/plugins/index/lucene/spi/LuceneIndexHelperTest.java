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

import org.apache.jackrabbit.oak.plugins.index.search.spi.DocumentBuilder;
import org.apache.jackrabbit.oak.plugins.index.search.spi.IndexVersion;
import org.apache.jackrabbit.oak.plugins.index.search.spi.QueryBuilder;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for LuceneIndexHelper demonstrating runtime SPI usage.
 */
public class LuceneIndexHelperTest {

    @Test
    public void testGetIndexVersion() {
        IndexVersion version = LuceneIndexHelper.getIndexVersion();
        assertEquals(IndexVersion.LUCENE_4_7_2, version);
        assertTrue(version.isLegacy());
        assertFalse(version.isModern());
    }

    @Test
    public void testNewDocumentBuilder() {
        DocumentBuilder builder = LuceneIndexHelper.newDocumentBuilder();
        assertNotNull(builder);
        assertTrue("Should be Lucene47DocumentBuilder",
                  builder instanceof Lucene47DocumentBuilder);
    }

    @Test
    public void testNewQueryBuilder() {
        QueryBuilder builder = LuceneIndexHelper.newQueryBuilder();
        assertNotNull(builder);
        assertTrue("Should be Lucene47QueryBuilder",
                  builder instanceof Lucene47QueryBuilder);
    }

    @Test
    public void testSupportsVersion() {
        assertTrue(LuceneIndexHelper.supportsVersion(IndexVersion.LUCENE_4_7_2));
        assertFalse(LuceneIndexHelper.supportsVersion(IndexVersion.LUCENE_9_X));
    }

    @Test
    public void testGetImplementationInfo() {
        String info = LuceneIndexHelper.getImplementationInfo();
        assertNotNull(info);
        assertTrue("Info should mention Lucene 4.7", info.contains("4.7"));
        assertTrue("Info should mention SPI", info.contains("SPI"));
    }

    @Test
    public void testRuntimeUsagePattern() {
        // Demonstrates how oak-lucene code can use the SPI at runtime
        DocumentBuilder docBuilder = LuceneIndexHelper.newDocumentBuilder();
        QueryBuilder queryBuilder = LuceneIndexHelper.newQueryBuilder();

        // Create a document via SPI
        var doc = docBuilder
            .addStringField("path", "/content/page", DocumentBuilder.FieldType.TEXT)
            .addLongField("created", System.currentTimeMillis(), DocumentBuilder.FieldType.LONG)
            .build();

        // Create a query via SPI
        var query = queryBuilder.term("path", "/content");

        // Verify they work with Lucene 4.7
        assertNotNull(doc);
        assertNotNull(query);
        assertTrue("Document should be Lucene 4.7 compatible", doc instanceof Lucene47Document);
        assertTrue("Query should be Lucene 4.7 compatible", query instanceof Lucene47Query);
    }
}
