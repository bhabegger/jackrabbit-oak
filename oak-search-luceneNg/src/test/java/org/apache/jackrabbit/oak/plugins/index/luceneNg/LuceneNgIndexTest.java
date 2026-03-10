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

import org.apache.jackrabbit.oak.InitialContentHelper;
import org.apache.jackrabbit.oak.api.PropertyValue;
import org.apache.jackrabbit.oak.plugins.index.search.FieldNames;
import org.apache.jackrabbit.oak.plugins.index.luceneNg.directory.BlobFactory;
import org.apache.jackrabbit.oak.plugins.index.luceneNg.directory.OakDirectory;
import org.apache.jackrabbit.oak.plugins.memory.PropertyValues;
import org.apache.jackrabbit.oak.spi.query.Cursor;
import org.apache.jackrabbit.oak.spi.query.Filter;
import org.apache.jackrabbit.oak.spi.query.Filter.PathRestriction;
import org.apache.jackrabbit.oak.spi.query.Filter.PropertyRestriction;
import org.apache.jackrabbit.oak.spi.query.QueryIndex.IndexPlan;
import org.apache.jackrabbit.oak.spi.query.fulltext.FullTextParser;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LuceneNgIndexTest {

    @Test
    public void testBasicTextQuery() throws Exception {
        // Setup: Create index with documents
        NodeBuilder builder = InitialContentHelper.INITIAL_CONTENT.builder();
        NodeBuilder indexDef = builder.child("oak:index").child("test");
        indexDef.setProperty("type", LuceneNgIndexConstants.TYPE_LUCENE9);

        // Index some documents
        OakDirectory directory = new OakDirectory(indexDef, "test", false);
        IndexWriterConfig config = new IndexWriterConfig(new org.apache.lucene.analysis.standard.StandardAnalyzer());
        IndexWriter writer = new IndexWriter(directory, config);

        Document doc1 = new Document();
        doc1.add(new StringField("path", "/content/article1", Field.Store.YES));
        doc1.add(new TextField(FieldNames.FULLTEXT, "Apache Jackrabbit Oak", Field.Store.NO));
        writer.addDocument(doc1);

        Document doc2 = new Document();
        doc2.add(new StringField("path", "/content/article2", Field.Store.YES));
        doc2.add(new TextField(FieldNames.FULLTEXT, "Lucene search engine", Field.Store.NO));
        writer.addDocument(doc2);

        writer.commit();
        writer.close();
        directory.close();

        NodeState root = builder.getNodeState();

        // Create index and tracker
        LuceneNgIndexTracker tracker = new LuceneNgIndexTracker();
        tracker.update(root);

        LuceneNgIndex index = new LuceneNgIndex(tracker, "/oak:index/test");

        // Create filter for full-text search
        Filter filter = mock(Filter.class);
        when(filter.getFullTextConstraint()).thenReturn(FullTextParser.parse("*", "Oak"));
        when(filter.getPathRestriction()).thenReturn(PathRestriction.NO_RESTRICTION);
        when(filter.getPropertyRestrictions()).thenReturn(Collections.emptyList());
        when(filter.getQueryLimits()).thenReturn(null);

        // Execute query
        Cursor cursor = index.query(filter, root);

        assertNotNull("Cursor should not be null", cursor);
        assertTrue("Should find article1", cursor.hasNext());

        String path = cursor.next().getPath();
        assertEquals("Should find /content/article1", "/content/article1", path);

        assertFalse("Should only find one document", cursor.hasNext());
    }

    @Test
    public void testGetCost() throws Exception {
        NodeState root = InitialContentHelper.INITIAL_CONTENT;

        LuceneNgIndexTracker tracker = new LuceneNgIndexTracker();
        tracker.update(root);

        LuceneNgIndex index = new LuceneNgIndex(tracker, "/oak:index/test");

        Filter filter = mock(Filter.class);
        when(filter.getFullTextConstraint()).thenReturn(FullTextParser.parse("*", "test"));

        double cost = index.getCost(filter, root);

        assertTrue("Cost should be greater than 0", cost > 0);
        assertTrue("Cost should be finite", Double.isFinite(cost));
    }

    @Test
    public void testNumericRangeQuery() throws Exception {
        // Setup: Create index with numeric property
        NodeBuilder builder = InitialContentHelper.INITIAL_CONTENT.builder();
        NodeBuilder oakIndex = builder.child("oak:index");
        NodeBuilder indexDef = oakIndex.child("test");
        indexDef.setProperty("type", LuceneNgIndexConstants.TYPE_LUCENE9);

        // Index documents with age property
        OakDirectory directory = new OakDirectory(indexDef, "test", false);
        IndexWriterConfig config = new IndexWriterConfig(new org.apache.lucene.analysis.standard.StandardAnalyzer());
        IndexWriter writer = new IndexWriter(directory, config);

        // Document 1: age = 25
        Document doc1 = new Document();
        doc1.add(new StringField("path", "/person1", Field.Store.YES));
        doc1.add(new LongPoint("age", 25L));
        doc1.add(new StoredField("age", 25L));
        writer.addDocument(doc1);

        // Document 2: age = 35
        Document doc2 = new Document();
        doc2.add(new StringField("path", "/person2", Field.Store.YES));
        doc2.add(new LongPoint("age", 35L));
        doc2.add(new StoredField("age", 35L));
        writer.addDocument(doc2);

        // Document 3: age = 45
        Document doc3 = new Document();
        doc3.add(new StringField("path", "/person3", Field.Store.YES));
        doc3.add(new LongPoint("age", 45L));
        doc3.add(new StoredField("age", 45L));
        writer.addDocument(doc3);

        writer.commit();
        writer.close();
        directory.close();

        NodeState root = builder.getNodeState();

        // Create index and tracker
        LuceneNgIndexTracker tracker = new LuceneNgIndexTracker();
        tracker.update(root);

        LuceneNgIndex index = new LuceneNgIndex(tracker, "/oak:index/test");

        // Create filter for: age > 30
        Filter filter = mock(Filter.class);
        when(filter.getFullTextConstraint()).thenReturn(null);
        PropertyValue pv30 = PropertyValues.newLong(30L);
        PropertyRestriction pr = new PropertyRestriction();
        pr.propertyName = "age";
        pr.first = pv30;
        pr.firstIncluding = false;  // exclusive: >
        when(filter.getPropertyRestrictions()).thenReturn(Collections.singletonList(pr));
        when(filter.getQueryLimits()).thenReturn(null);

        // Execute query
        Cursor cursor = index.query(filter, root);

        // Should return person2 (35) and person3 (45), not person1 (25)
        assertTrue("Should find results", cursor.hasNext());
        List<String> paths = new ArrayList<>();
        while (cursor.hasNext()) {
            paths.add(cursor.next().getPath());
        }

        assertEquals("Should find 2 results", 2, paths.size());
        assertTrue("Should contain /person2", paths.contains("/person2"));
        assertTrue("Should contain /person3", paths.contains("/person3"));
        assertFalse("Should not contain /person1", paths.contains("/person1"));
    }
}
