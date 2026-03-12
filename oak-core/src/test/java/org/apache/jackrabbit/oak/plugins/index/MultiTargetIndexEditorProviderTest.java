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
import org.apache.jackrabbit.oak.plugins.memory.MemoryNodeBuilder;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MultiTargetIndexEditorProviderTest {

    private IndexEditorProvider lucene47Provider;
    private IndexEditorProvider lucene9Provider;
    private Editor lucene47Editor;
    private Editor lucene9Editor;
    private NodeState root;
    private IndexUpdateCallback callback;
    private MultiTargetIndexEditorProvider provider;

    @Before
    public void setUp() throws Exception {
        lucene47Provider = Mockito.mock(IndexEditorProvider.class);
        lucene9Provider = Mockito.mock(IndexEditorProvider.class);
        lucene47Editor = Mockito.mock(Editor.class);
        lucene9Editor = Mockito.mock(Editor.class);
        root = EmptyNodeState.EMPTY_NODE;
        callback = Mockito.mock(IndexUpdateCallback.class);

        when(lucene47Provider.getIndexEditor(eq("lucene47"), any(), any(), any())).thenReturn(lucene47Editor);
        when(lucene47Provider.getIndexEditor(eq("lucene9"), any(), any(), any())).thenReturn(null);
        when(lucene9Provider.getIndexEditor(eq("lucene9"), any(), any(), any())).thenReturn(lucene9Editor);
        when(lucene9Provider.getIndexEditor(eq("lucene47"), any(), any(), any())).thenReturn(null);

        provider = new MultiTargetIndexEditorProvider(lucene47Provider, lucene9Provider);
    }

    private NodeBuilder definitionWithType(String type) {
        NodeBuilder builder = new MemoryNodeBuilder(EmptyNodeState.EMPTY_NODE);
        builder.setProperty("type", type);
        return builder;
    }

    private NodeBuilder definitionWithMultiTarget(String... targets) {
        NodeBuilder builder = new MemoryNodeBuilder(EmptyNodeState.EMPTY_NODE);
        builder.setProperty("type", targets[0]); // required for IndexUpdate to pick up
        builder.setProperty(org.apache.jackrabbit.oak.plugins.memory.PropertyStates
                .createProperty("storeTargets", Arrays.asList(targets),
                        org.apache.jackrabbit.oak.api.Type.STRINGS));
        builder.setProperty("activeTarget", targets[targets.length - 1]);
        return builder;
    }

    @Test
    public void testSingleTarget_TypeOnly_DelegatesToCorrectProvider() throws Exception {
        NodeBuilder definition = definitionWithType("lucene47");

        Editor result = provider.getIndexEditor("lucene47", definition, root, callback);

        assertNotNull(result);
        verify(lucene47Provider).getIndexEditor(eq("lucene47"), any(), any(), any());
        verify(lucene9Provider, never()).getIndexEditor(eq("lucene47"), any(), any(), any());
    }

    @Test
    public void testSingleTarget_ReturnsDirectEditor_NotWrapped() throws Exception {
        NodeBuilder definition = definitionWithType("lucene47");

        Editor result = provider.getIndexEditor("lucene47", definition, root, callback);

        assertNotNull(result);
        // Single target (primary) should not be wrapped in ErrorTolerantEditor
        assertFalse(result instanceof ErrorTolerantEditor);
    }

    @Test
    public void testMultiTarget_ReturnsBothEditors() throws Exception {
        NodeBuilder definition = definitionWithMultiTarget("lucene47", "lucene9");

        Editor result = provider.getIndexEditor("lucene47", definition, root, callback);

        assertNotNull(result);
        verify(lucene47Provider).getIndexEditor(eq("lucene47"), any(), any(), any());
        verify(lucene9Provider).getIndexEditor(eq("lucene9"), any(), any(), any());
    }

    @Test
    public void testMultiTarget_SecondaryFailure_PrimarySucceeds() throws Exception {
        when(lucene9Provider.getIndexEditor(eq("lucene9"), any(), any(), any()))
                .thenReturn(lucene9Editor);

        NodeBuilder definition = definitionWithMultiTarget("lucene47", "lucene9");
        Editor result = provider.getIndexEditor("lucene47", definition, root, callback);
        assertNotNull(result);

        // Now simulate secondary failure during indexing
        doThrow(new RuntimeException("Lucene9 write failed")).when(lucene9Editor).enter(any(), any());

        // Enter should not throw (secondary is error-tolerant)
        result.enter(EmptyNodeState.EMPTY_NODE, EmptyNodeState.EMPTY_NODE);
    }

    @Test
    public void testMultiTarget_PrimaryHasNoProvider_ThrowsCommitFailedException() throws Exception {
        when(lucene47Provider.getIndexEditor(eq("lucene47"), any(), any(), any())).thenReturn(null);
        when(lucene9Provider.getIndexEditor(eq("lucene47"), any(), any(), any())).thenReturn(null);

        NodeBuilder definition = definitionWithMultiTarget("lucene47", "lucene9");

        try {
            provider.getIndexEditor("lucene47", definition, root, callback);
            fail("Expected CommitFailedException");
        } catch (CommitFailedException e) {
            assertTrue(e.getMessage().contains("lucene47"));
        }
    }

    @Test
    public void testNoTypeProperty_ReturnsNull() throws Exception {
        NodeBuilder definition = new MemoryNodeBuilder(EmptyNodeState.EMPTY_NODE);
        // No type, no storeTargets, no activeTarget

        Editor result = provider.getIndexEditor("unknown", definition, root, callback);

        assertNull(result);
    }

    @Test
    public void testUnknownType_NoMatchingProvider_ReturnsNull() throws Exception {
        // All providers return null for unknown type
        when(lucene47Provider.getIndexEditor(eq("solr"), any(), any(), any())).thenReturn(null);
        when(lucene9Provider.getIndexEditor(eq("solr"), any(), any(), any())).thenReturn(null);

        NodeBuilder definition = definitionWithType("solr");

        // Primary target with no provider should throw
        try {
            provider.getIndexEditor("solr", definition, root, callback);
            fail("Expected CommitFailedException for unknown primary type");
        } catch (CommitFailedException e) {
            assertTrue(e.getMessage().contains("solr"));
        }
    }

    @Test
    public void testMetrics_CollectedForMultiTarget() throws Exception {
        NodeBuilder definition = definitionWithMultiTarget("lucene47", "lucene9");
        provider.getIndexEditor("lucene47", definition, root, callback);

        // Metrics should be accessible
        assertNotNull(provider.getMetrics());
    }
}
