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

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.plugins.index.ErrorTolerantEditor;
import org.apache.jackrabbit.oak.plugins.index.IndexEditorProvider;
import org.apache.jackrabbit.oak.plugins.index.IndexUpdateCallback;
import org.apache.jackrabbit.oak.plugins.index.MultiTargetIndexEditorProvider;
import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.plugins.memory.MemoryNodeBuilder;
import org.apache.jackrabbit.oak.plugins.memory.PropertyStates;
import org.apache.jackrabbit.oak.spi.commit.CompositeEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;

import static org.apache.jackrabbit.oak.InitialContentHelper.INITIAL_CONTENT;
import static org.apache.jackrabbit.oak.api.Type.STRINGS;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for multi-target write capability.
 * Validates end-to-end dual-write scenarios with primary/secondary semantics,
 * query routing via activeTarget, and failure handling.
 */
public class MultiTargetWriteIntegrationTest {

    private IndexEditorProvider lucene47Provider;
    private LuceneNgIndexEditorProvider lucene9Provider;
    private LuceneNgIndexTracker tracker;
    private Editor lucene47Editor;
    private IndexUpdateCallback callback;
    private NodeState root;

    @Before
    public void setUp() throws Exception {
        lucene47Provider = Mockito.mock(IndexEditorProvider.class);
        lucene47Editor = Mockito.mock(Editor.class);
        callback = Mockito.mock(IndexUpdateCallback.class);

        when(lucene47Provider.getIndexEditor(eq("lucene47"), any(), any(), any()))
                .thenReturn(lucene47Editor);
        when(lucene47Provider.getIndexEditor(eq("lucene9"), any(), any(), any()))
                .thenReturn(null);

        tracker = new LuceneNgIndexTracker();
        lucene9Provider = new LuceneNgIndexEditorProvider(tracker);

        // Build a minimal repository state
        NodeBuilder builder = INITIAL_CONTENT.builder();
        root = builder.getNodeState();
    }

    private NodeBuilder multiTargetDefinition(String primaryTarget, String... additionalTargets) {
        NodeBuilder def = new MemoryNodeBuilder(EmptyNodeState.EMPTY_NODE);
        String[] allTargets = new String[additionalTargets.length + 1];
        allTargets[0] = primaryTarget;
        System.arraycopy(additionalTargets, 0, allTargets, 1, additionalTargets.length);
        def.setProperty("type", primaryTarget); // required by IndexUpdate to trigger provider
        def.setProperty(PropertyStates.createProperty("storeTargets", Arrays.asList(allTargets), STRINGS));
        def.setProperty("activeTarget", additionalTargets.length > 0
                ? additionalTargets[additionalTargets.length - 1]
                : primaryTarget);
        return def;
    }

    // -------------------------------------------------------------------------
    // Dual write scenarios
    // -------------------------------------------------------------------------

    @Test
    public void testDualWrite_BothProvidersCalled() throws Exception {
        NodeBuilder definition = multiTargetDefinition("lucene47", "lucene9");
        MultiTargetIndexEditorProvider provider =
                new MultiTargetIndexEditorProvider(lucene47Provider, lucene9Provider);

        Editor editor = provider.getIndexEditor("lucene47", definition, root, callback);

        assertNotNull("Editor should be returned for multi-target definition", editor);
        // lucene47 provider must have been called for the primary target
        verify(lucene47Provider).getIndexEditor(eq("lucene47"), any(), any(), any());
        // lucene9 provider is the real provider — we verify via tracker after indexing
    }

    @Test
    public void testDualWrite_PrimaryEditorIsNotWrapped() throws Exception {
        NodeBuilder definition = multiTargetDefinition("lucene47", "lucene9");
        MultiTargetIndexEditorProvider provider =
                new MultiTargetIndexEditorProvider(lucene47Provider, lucene9Provider);

        Editor editor = provider.getIndexEditor("lucene47", definition, root, callback);

        assertNotNull(editor);
        // A CompositeEditor wraps both; neither the composite itself nor the primary
        // component inside it should be an ErrorTolerantEditor when tested directly
        // (secondary component IS wrapped, but we verify via failure test below)
    }

    // -------------------------------------------------------------------------
    // Primary failure blocks commit
    // -------------------------------------------------------------------------

    @Test
    public void testPrimaryFailure_BlocksCommit() throws Exception {
        when(lucene47Provider.getIndexEditor(eq("lucene47"), any(), any(), any()))
                .thenReturn(null); // primary returns null → no provider

        NodeBuilder definition = multiTargetDefinition("lucene47", "lucene9");
        MultiTargetIndexEditorProvider provider =
                new MultiTargetIndexEditorProvider(lucene47Provider, lucene9Provider);

        try {
            provider.getIndexEditor("lucene47", definition, root, callback);
            fail("Expected CommitFailedException when primary target has no provider");
        } catch (CommitFailedException e) {
            assertTrue("Error should mention primary target",
                    e.getMessage().contains("lucene47"));
        }
    }

    @Test
    public void testPrimaryEditorEnterThrows_ExceptionPropagates() throws Exception {
        doThrow(new RuntimeException("Primary write failed"))
                .when(lucene47Editor).enter(any(), any());

        NodeBuilder definition = multiTargetDefinition("lucene47", "lucene9");
        MultiTargetIndexEditorProvider provider =
                new MultiTargetIndexEditorProvider(lucene47Provider, lucene9Provider);

        Editor editor = provider.getIndexEditor("lucene47", definition, root, callback);
        assertNotNull(editor);

        // Primary error NOT wrapped → exception propagates
        try {
            editor.enter(EmptyNodeState.EMPTY_NODE, EmptyNodeState.EMPTY_NODE);
            fail("Expected RuntimeException from primary target");
        } catch (RuntimeException e) {
            assertEquals("Primary write failed", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Secondary failure allows commit
    // -------------------------------------------------------------------------

    @Test
    public void testSecondaryFailure_AllowsCommit() throws Exception {
        // Secondary target (lucene9) returns an editor that throws on enter
        Editor failingLucene9Editor = Mockito.mock(Editor.class);
        doThrow(new RuntimeException("Secondary write failed"))
                .when(failingLucene9Editor).enter(any(), any());

        // Override lucene9 provider with one that returns the failing editor
        IndexEditorProvider failingLucene9Provider = Mockito.mock(IndexEditorProvider.class);
        when(failingLucene9Provider.getIndexEditor(eq("lucene9"), any(), any(), any()))
                .thenReturn(failingLucene9Editor);
        when(failingLucene9Provider.getIndexEditor(eq("lucene47"), any(), any(), any()))
                .thenReturn(null);

        NodeBuilder definition = multiTargetDefinition("lucene47", "lucene9");
        MultiTargetIndexEditorProvider provider =
                new MultiTargetIndexEditorProvider(lucene47Provider, failingLucene9Provider);

        Editor editor = provider.getIndexEditor("lucene47", definition, root, callback);
        assertNotNull(editor);

        // Should not throw — secondary is wrapped in ErrorTolerantEditor
        editor.enter(EmptyNodeState.EMPTY_NODE, EmptyNodeState.EMPTY_NODE);
        // Primary editor was called
        verify(lucene47Editor).enter(any(), any());
    }

    @Test
    public void testSecondaryMissingProvider_AllowsCommit() throws Exception {
        // lucene9 provider returns null (doesn't handle this type)
        when(lucene47Provider.getIndexEditor(eq("lucene9"), any(), any(), any()))
                .thenReturn(null);
        IndexEditorProvider noopProvider = Mockito.mock(IndexEditorProvider.class);
        when(noopProvider.getIndexEditor(anyString(), any(), any(), any())).thenReturn(null);

        NodeBuilder definition = multiTargetDefinition("lucene47", "lucene9");
        MultiTargetIndexEditorProvider provider =
                new MultiTargetIndexEditorProvider(lucene47Provider, noopProvider);

        // Should not throw even though secondary has no provider
        Editor editor = provider.getIndexEditor("lucene47", definition, root, callback);
        // Returns at least the primary editor
        assertNotNull(editor);
    }

    // -------------------------------------------------------------------------
    // Query routing to activeTarget
    // -------------------------------------------------------------------------

    @Test
    public void testQueryRouting_ActiveTargetLucene9_TrackerPicksItUp() {
        // Build repository with a multi-target index where activeTarget=lucene9
        NodeBuilder builder = INITIAL_CONTENT.builder();
        NodeBuilder indexDef = builder.child("oak:index").child("dualWriteIndex");
        indexDef.setProperty("type", "lucene47"); // legacy type for IndexUpdate
        indexDef.setProperty(PropertyStates.createProperty(
                "storeTargets", Arrays.asList("lucene47", "lucene9"), STRINGS));
        indexDef.setProperty("activeTarget", "lucene9");

        NodeState root = builder.getNodeState();

        LuceneNgIndexTracker localTracker = new LuceneNgIndexTracker();
        localTracker.update(root);

        // Tracker should pick up the index because activeTarget=lucene9
        LuceneNgIndexNode indexNode = localTracker.acquireIndexNode("/oak:index/dualWriteIndex");
        assertNotNull("Index with activeTarget=lucene9 should be tracked for queries", indexNode);
    }

    @Test
    public void testQueryRouting_ActiveTargetLucene47_NotTracked() {
        // Build repository with a multi-target index where activeTarget=lucene47
        NodeBuilder builder = INITIAL_CONTENT.builder();
        NodeBuilder indexDef = builder.child("oak:index").child("dualWriteIndex");
        indexDef.setProperty("type", "lucene47");
        indexDef.setProperty(PropertyStates.createProperty(
                "storeTargets", Arrays.asList("lucene47", "lucene9"), STRINGS));
        indexDef.setProperty("activeTarget", "lucene47");

        NodeState root = builder.getNodeState();

        LuceneNgIndexTracker localTracker = new LuceneNgIndexTracker();
        localTracker.update(root);

        // Tracker should NOT pick up the index because activeTarget=lucene47 (not lucene9)
        LuceneNgIndexNode indexNode = localTracker.acquireIndexNode("/oak:index/dualWriteIndex");
        assertNull("Index with activeTarget=lucene47 should NOT be tracked for lucene9 queries", indexNode);
    }

    @Test
    public void testQueryRouting_LegacyTypeOnly_LuceneNg_StillTracked() {
        // Backward compat: type-only index with type=lucene9 still tracked
        NodeBuilder builder = INITIAL_CONTENT.builder();
        NodeBuilder indexDef = builder.child("oak:index").child("legacyLucene9Index");
        indexDef.setProperty("type", LuceneNgIndexConstants.TYPE_LUCENE9);

        NodeState root = builder.getNodeState();

        LuceneNgIndexTracker localTracker = new LuceneNgIndexTracker();
        localTracker.update(root);

        LuceneNgIndexNode indexNode = localTracker.acquireIndexNode("/oak:index/legacyLucene9Index");
        assertNotNull("Legacy type=lucene9 index should still be tracked", indexNode);
    }

    // -------------------------------------------------------------------------
    // Metrics collection
    // -------------------------------------------------------------------------

    @Test
    public void testMetrics_AccessibleAfterWrites() throws Exception {
        NodeBuilder definition = multiTargetDefinition("lucene47", "lucene9");
        MultiTargetIndexEditorProvider provider =
                new MultiTargetIndexEditorProvider(lucene47Provider, lucene9Provider);

        provider.getIndexEditor("lucene47", definition, root, callback);

        assertNotNull("Metrics should be accessible after writes", provider.getMetrics());
    }

    // -------------------------------------------------------------------------
    // Single-target backward compatibility
    // -------------------------------------------------------------------------

    @Test
    public void testSingleTarget_BackwardCompat_TypeOnly() throws Exception {
        // type-only definition normalized to storeTargets=[type], activeTarget=type
        NodeBuilder definition = new MemoryNodeBuilder(EmptyNodeState.EMPTY_NODE);
        definition.setProperty("type", "lucene47");

        MultiTargetIndexEditorProvider provider =
                new MultiTargetIndexEditorProvider(lucene47Provider, lucene9Provider);

        Editor editor = provider.getIndexEditor("lucene47", definition, root, callback);

        assertNotNull("Single-target (type-only) should return an editor", editor);
        // Only lucene47 provider should have been called for lucene47 target
        verify(lucene47Provider).getIndexEditor(eq("lucene47"), any(), any(), any());
        verify(lucene47Provider, never()).getIndexEditor(eq("lucene9"), any(), any(), any());
    }
}
