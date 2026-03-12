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
package org.apache.jackrabbit.oak.plugins.index;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class IndexWriteOperationTest {

    private IndexEditorProvider mockProvider;
    private Editor mockEditor;
    private NodeBuilder mockDefinition;
    private NodeState mockRoot;
    private IndexUpdateCallback mockCallback;
    private MultiTargetIndexMetrics metrics;

    @Before
    public void setUp() throws Exception {
        mockProvider = Mockito.mock(IndexEditorProvider.class);
        mockEditor = Mockito.mock(Editor.class);
        mockDefinition = Mockito.mock(NodeBuilder.class);
        mockRoot = EmptyNodeState.EMPTY_NODE;
        mockCallback = Mockito.mock(IndexUpdateCallback.class);
        metrics = new MultiTargetIndexMetrics();

        when(mockProvider.getIndexEditor(eq("lucene47"), any(), any(), any())).thenReturn(mockEditor);
    }

    @Test
    public void testPrimaryTarget_ProviderFound_ReturnsEditor() throws Exception {
        IndexWriteOperation op = new IndexWriteOperation(
                "lucene47", true, mockDefinition, mockRoot, mockCallback,
                Collections.singletonList(mockProvider), metrics);

        Editor result = op.execute();

        assertNotNull(result);
        assertSame(mockEditor, result);
        // Primary editor is NOT wrapped in ErrorTolerantEditor
        assertFalse(result instanceof ErrorTolerantEditor);
    }

    @Test
    public void testSecondaryTarget_ProviderFound_ReturnsErrorTolerantEditor() throws Exception {
        when(mockProvider.getIndexEditor(eq("lucene9"), any(), any(), any())).thenReturn(mockEditor);

        IndexWriteOperation op = new IndexWriteOperation(
                "lucene9", false, mockDefinition, mockRoot, mockCallback,
                Collections.singletonList(mockProvider), metrics);

        Editor result = op.execute();

        assertNotNull(result);
        // Secondary editor IS wrapped in ErrorTolerantEditor
        assertTrue(result instanceof ErrorTolerantEditor);
    }

    @Test
    public void testPrimaryTarget_NoProvider_ThrowsCommitFailedException() throws Exception {
        when(mockProvider.getIndexEditor(anyString(), any(), any(), any())).thenReturn(null);

        IndexWriteOperation op = new IndexWriteOperation(
                "lucene47", true, mockDefinition, mockRoot, mockCallback,
                Collections.singletonList(mockProvider), metrics);

        try {
            op.execute();
            fail("Expected CommitFailedException");
        } catch (CommitFailedException e) {
            assertTrue(e.getMessage().contains("lucene47"));
        }
    }

    @Test
    public void testSecondaryTarget_NoProvider_ReturnsNull() throws Exception {
        when(mockProvider.getIndexEditor(anyString(), any(), any(), any())).thenReturn(null);

        IndexWriteOperation op = new IndexWriteOperation(
                "lucene9", false, mockDefinition, mockRoot, mockCallback,
                Collections.singletonList(mockProvider), metrics);

        Editor result = op.execute();

        assertNull(result);
    }

    @Test
    public void testMultipleProviders_FirstMatchWins() throws Exception {
        IndexEditorProvider otherProvider = Mockito.mock(IndexEditorProvider.class);
        Editor otherEditor = Mockito.mock(Editor.class);

        // First provider doesn't handle "lucene47"
        when(mockProvider.getIndexEditor(eq("lucene47"), any(), any(), any())).thenReturn(null);
        // Second provider does
        when(otherProvider.getIndexEditor(eq("lucene47"), any(), any(), any())).thenReturn(otherEditor);

        List<IndexEditorProvider> providers = Arrays.asList(mockProvider, otherProvider);
        IndexWriteOperation op = new IndexWriteOperation(
                "lucene47", true, mockDefinition, mockRoot, mockCallback, providers, metrics);

        Editor result = op.execute();

        assertNotNull(result);
        assertSame(otherEditor, result);
        // Verify first provider was tried before second
        verify(mockProvider).getIndexEditor(eq("lucene47"), any(), any(), any());
        verify(otherProvider).getIndexEditor(eq("lucene47"), any(), any(), any());
    }

    @Test
    public void testPrimaryTarget_ProviderThrows_Propagates() throws Exception {
        when(mockProvider.getIndexEditor(eq("lucene47"), any(), any(), any()))
                .thenThrow(new CommitFailedException("Index", 1, "Provider error"));

        IndexWriteOperation op = new IndexWriteOperation(
                "lucene47", true, mockDefinition, mockRoot, mockCallback,
                Collections.singletonList(mockProvider), metrics);

        try {
            op.execute();
            fail("Expected CommitFailedException");
        } catch (CommitFailedException e) {
            assertTrue(e.getMessage().contains("Provider error"));
        }
    }

    @Test
    public void testEmptyProviderList_Primary_ThrowsCommitFailedException() throws Exception {
        IndexWriteOperation op = new IndexWriteOperation(
                "lucene47", true, mockDefinition, mockRoot, mockCallback,
                Collections.emptyList(), metrics);

        try {
            op.execute();
            fail("Expected CommitFailedException");
        } catch (CommitFailedException e) {
            assertTrue(e.getMessage().contains("lucene47"));
        }
    }

    @Test
    public void testEmptyProviderList_Secondary_ReturnsNull() throws Exception {
        IndexWriteOperation op = new IndexWriteOperation(
                "lucene9", false, mockDefinition, mockRoot, mockCallback,
                Collections.emptyList(), metrics);

        Editor result = op.execute();

        assertNull(result);
    }
}
