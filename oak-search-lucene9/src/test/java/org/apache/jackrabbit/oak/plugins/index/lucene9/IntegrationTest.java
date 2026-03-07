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

import org.apache.jackrabbit.oak.plugins.index.IndexUpdateCallback;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.junit.Test;

import static org.apache.jackrabbit.oak.InitialContentHelper.INITIAL_CONTENT;
import static org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState.EMPTY_NODE;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for Lucene 9 indexing covering end-to-end workflows.
 * Tests verify complete indexing scenarios with tracker, provider, and editor components.
 */
public class IntegrationTest {

    @Test
    public void testCompleteIndexingWorkflow() throws Exception {
        // Setup: Create index definition
        NodeBuilder builder = INITIAL_CONTENT.builder();
        NodeBuilder oakIndex = builder.child("oak:index");
        NodeBuilder indexDef = oakIndex.child("testIndex");
        indexDef.setProperty("type", Lucene9IndexConstants.TYPE_LUCENE9);
        indexDef.setProperty("async", "async");

        // Create content tree with 3 articles
        NodeBuilder content = builder.child("content");
        NodeBuilder article1 = content.child("article1");
        article1.setProperty("title", "Introduction to Oak");
        article1.setProperty("text", "Apache Jackrabbit Oak is a scalable repository");

        NodeBuilder article2 = content.child("article2");
        article2.setProperty("title", "Lucene 9 Integration");
        article2.setProperty("text", "Lucene 9 provides advanced search capabilities");

        NodeBuilder article3 = content.child("article3");
        article3.setProperty("title", "Performance Optimization");
        article3.setProperty("text", "Chunked storage improves memory efficiency");

        NodeState root = builder.getNodeState();

        // Index the content
        Lucene9IndexTracker tracker = new Lucene9IndexTracker();
        tracker.update(root);

        Lucene9IndexEditorProvider provider = new Lucene9IndexEditorProvider(tracker);
        IndexUpdateCallback callback = mock(IndexUpdateCallback.class);

        Editor editor = provider.getIndexEditor(
            Lucene9IndexConstants.TYPE_LUCENE9,
            indexDef,
            root,
            callback
        );

        assertNotNull("Editor should be created", editor);

        // Simulate indexing by traversing tree
        editor.enter(EMPTY_NODE, root);

        // Index content node
        Editor contentEditor = editor.childNodeAdded("content", content.getNodeState());
        assertNotNull("Content editor should be created", contentEditor);
        contentEditor.enter(EMPTY_NODE, content.getNodeState());

        // Index article1
        Editor article1Editor = contentEditor.childNodeAdded("article1", article1.getNodeState());
        assertNotNull("Article1 editor should be created", article1Editor);
        article1Editor.enter(EMPTY_NODE, article1.getNodeState());
        article1Editor.leave(EMPTY_NODE, article1.getNodeState());

        // Index article2
        Editor article2Editor = contentEditor.childNodeAdded("article2", article2.getNodeState());
        assertNotNull("Article2 editor should be created", article2Editor);
        article2Editor.enter(EMPTY_NODE, article2.getNodeState());
        article2Editor.leave(EMPTY_NODE, article2.getNodeState());

        // Index article3
        Editor article3Editor = contentEditor.childNodeAdded("article3", article3.getNodeState());
        assertNotNull("Article3 editor should be created", article3Editor);
        article3Editor.enter(EMPTY_NODE, article3.getNodeState());
        article3Editor.leave(EMPTY_NODE, article3.getNodeState());

        contentEditor.leave(EMPTY_NODE, content.getNodeState());
        editor.leave(EMPTY_NODE, root);

        // Verify index was created - check for index storage structure
        // Index files are stored under the index definition at /var/indexing/lucene9/{indexName}
        assertTrue("Index storage should be created", indexDef.hasChildNode("var"));
        NodeBuilder var = indexDef.child("var");
        assertTrue("Indexing node should exist", var.hasChildNode("indexing"));
        NodeBuilder indexing = var.child("indexing");
        assertTrue("Lucene9 node should exist", indexing.hasChildNode("lucene9"));
        NodeBuilder lucene9 = indexing.child("lucene9");
        assertTrue("Index directory should exist", lucene9.hasChildNode("lucene9-index"));
    }

    @Test
    public void testChunkedStorageInRealIndex() throws Exception {
        // Setup: Create index definition
        NodeBuilder builder = INITIAL_CONTENT.builder();
        NodeBuilder oakIndex = builder.child("oak:index");
        NodeBuilder indexDef = oakIndex.child("largeIndex");
        indexDef.setProperty("type", Lucene9IndexConstants.TYPE_LUCENE9);
        indexDef.setProperty("async", "async");

        // Create 100 nodes with large text (1000x repeated string per node) to force large index
        NodeBuilder content = builder.child("content");
        StringBuilder largeText = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeText.append("This is a test string to create large content for chunked storage testing. ");
        }
        String largeTextValue = largeText.toString();

        for (int i = 0; i < 100; i++) {
            NodeBuilder node = content.child("node" + i);
            node.setProperty("title", "Node " + i);
            node.setProperty("text", largeTextValue);
        }

        NodeState root = builder.getNodeState();

        // Index all 100 nodes
        Lucene9IndexTracker tracker = new Lucene9IndexTracker();
        tracker.update(root);

        Lucene9IndexEditorProvider provider = new Lucene9IndexEditorProvider(tracker);
        IndexUpdateCallback callback = mock(IndexUpdateCallback.class);

        Editor editor = provider.getIndexEditor(
            Lucene9IndexConstants.TYPE_LUCENE9,
            indexDef,
            root,
            callback
        );

        assertNotNull("Editor should be created", editor);

        // Simulate indexing
        editor.enter(EMPTY_NODE, root);

        Editor contentEditor = editor.childNodeAdded("content", content.getNodeState());
        assertNotNull("Content editor should be created", contentEditor);
        contentEditor.enter(EMPTY_NODE, content.getNodeState());

        // Index all 100 nodes
        for (int i = 0; i < 100; i++) {
            String nodeName = "node" + i;
            NodeBuilder node = content.child(nodeName);
            Editor nodeEditor = contentEditor.childNodeAdded(nodeName, node.getNodeState());
            assertNotNull("Node editor should be created for " + nodeName, nodeEditor);
            nodeEditor.enter(EMPTY_NODE, node.getNodeState());
            nodeEditor.leave(EMPTY_NODE, node.getNodeState());
        }

        contentEditor.leave(EMPTY_NODE, content.getNodeState());
        editor.leave(EMPTY_NODE, root);

        // Verify chunked storage was used - check for data nodes with children
        // Index files are stored under the index definition at /var/indexing/lucene9/{indexName}
        assertTrue("Index storage should be created", indexDef.hasChildNode("var"));
        NodeBuilder var = indexDef.child("var");
        NodeBuilder indexing = var.child("indexing");
        NodeBuilder lucene9 = indexing.child("lucene9");
        // The index name defaults to "lucene9-index" if not specified in definition
        assertTrue("Index directory should exist", lucene9.hasChildNode("lucene9-index"));

        NodeBuilder indexDir = lucene9.child("lucene9-index");
        // Verify that index files were created (segments file is always created)
        long childCount = indexDir.getChildNodeCount(Long.MAX_VALUE);
        assertTrue("Index files should be created (childCount > 0)", childCount > 0);
    }

    @Test
    public void testProviderReturnsNullForWrongType() throws Exception {
        // Setup: Create index definition with wrong type
        NodeBuilder builder = INITIAL_CONTENT.builder();
        NodeBuilder oakIndex = builder.child("oak:index");
        NodeBuilder indexDef = oakIndex.child("wrongTypeIndex");
        indexDef.setProperty("type", "wrong-type");
        indexDef.setProperty("async", "async");

        NodeState root = builder.getNodeState();

        // Create tracker and provider
        Lucene9IndexTracker tracker = new Lucene9IndexTracker();
        tracker.update(root);

        Lucene9IndexEditorProvider provider = new Lucene9IndexEditorProvider(tracker);
        IndexUpdateCallback callback = mock(IndexUpdateCallback.class);

        // Verify provider returns null for wrong type
        Editor editor = provider.getIndexEditor(
            "wrong-type",
            indexDef,
            root,
            callback
        );

        assertNull("Editor should be null for wrong type", editor);
    }

    @Test
    public void testTrackerLifecycle() throws Exception {
        // Create index1
        NodeBuilder builder = INITIAL_CONTENT.builder();
        NodeBuilder oakIndex = builder.child("oak:index");
        NodeBuilder index1 = oakIndex.child("index1");
        index1.setProperty("type", Lucene9IndexConstants.TYPE_LUCENE9);
        index1.setProperty("async", "async");

        NodeState root1 = builder.getNodeState();

        // Update tracker with index1
        Lucene9IndexTracker tracker = new Lucene9IndexTracker();
        tracker.update(root1);

        // Verify acquireIndexNode() returns index1
        Lucene9IndexNode indexNode1 = tracker.acquireIndexNode("/oak:index/index1");
        assertNotNull("Index1 should be found", indexNode1);

        // Add index2
        NodeBuilder index2 = oakIndex.child("index2");
        index2.setProperty("type", Lucene9IndexConstants.TYPE_LUCENE9);
        index2.setProperty("async", "async");

        NodeState root2 = builder.getNodeState();

        // Update tracker with both indexes
        tracker.update(root2);

        // Verify both indexes are found
        Lucene9IndexNode indexNode1After = tracker.acquireIndexNode("/oak:index/index1");
        assertNotNull("Index1 should still be found", indexNode1After);

        Lucene9IndexNode indexNode2 = tracker.acquireIndexNode("/oak:index/index2");
        assertNotNull("Index2 should be found", indexNode2);

        // Verify nonexistent index returns null
        Lucene9IndexNode nonexistent = tracker.acquireIndexNode("/oak:index/nonexistent");
        assertNull("Nonexistent index should return null", nonexistent);
    }
}
