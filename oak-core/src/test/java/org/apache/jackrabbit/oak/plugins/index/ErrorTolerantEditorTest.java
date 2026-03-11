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

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState;
import org.apache.jackrabbit.oak.plugins.memory.PropertyStates;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ErrorTolerantEditorTest {

    private Editor mockDelegate;
    private MultiTargetIndexMetrics metrics;
    private ErrorTolerantEditor editor;
    private NodeState nodeState;
    private PropertyState propertyState;

    @Before
    public void setUp() {
        mockDelegate = Mockito.mock(Editor.class);
        metrics = new MultiTargetIndexMetrics();
        editor = new ErrorTolerantEditor(mockDelegate, "lucene9", metrics);
        nodeState = EmptyNodeState.EMPTY_NODE;
        propertyState = PropertyStates.createProperty("test", "value");
    }

    @Test
    public void testEnter_Success() throws Exception {
        editor.enter(nodeState, nodeState);

        verify(mockDelegate).enter(nodeState, nodeState);
        assertEquals(1, metrics.getSuccessCount("lucene9"));
        assertEquals(0, metrics.getFailureCount("lucene9"));
    }

    @Test
    public void testEnter_ExceptionCaught() throws Exception {
        doThrow(new RuntimeException("Test exception")).when(mockDelegate).enter(any(), any());

        // Should not throw
        editor.enter(nodeState, nodeState);

        verify(mockDelegate).enter(nodeState, nodeState);
        assertEquals(0, metrics.getSuccessCount("lucene9"));
        assertEquals(1, metrics.getFailureCount("lucene9"));
    }

    @Test
    public void testLeave_Success() throws Exception {
        editor.leave(nodeState, nodeState);

        verify(mockDelegate).leave(nodeState, nodeState);
        assertEquals(1, metrics.getSuccessCount("lucene9"));
        assertEquals(0, metrics.getFailureCount("lucene9"));
    }

    @Test
    public void testLeave_ExceptionCaught() throws Exception {
        doThrow(new RuntimeException("Test exception")).when(mockDelegate).leave(any(), any());

        // Should not throw
        editor.leave(nodeState, nodeState);

        verify(mockDelegate).leave(nodeState, nodeState);
        assertEquals(0, metrics.getSuccessCount("lucene9"));
        assertEquals(1, metrics.getFailureCount("lucene9"));
    }

    @Test
    public void testPropertyAdded_Success() throws Exception {
        editor.propertyAdded(propertyState);

        verify(mockDelegate).propertyAdded(propertyState);
        assertEquals(1, metrics.getSuccessCount("lucene9"));
        assertEquals(0, metrics.getFailureCount("lucene9"));
    }

    @Test
    public void testPropertyAdded_ExceptionCaught() throws Exception {
        doThrow(new RuntimeException("Test exception")).when(mockDelegate).propertyAdded(any());

        // Should not throw
        editor.propertyAdded(propertyState);

        verify(mockDelegate).propertyAdded(propertyState);
        assertEquals(0, metrics.getSuccessCount("lucene9"));
        assertEquals(1, metrics.getFailureCount("lucene9"));
    }

    @Test
    public void testPropertyChanged_Success() throws Exception {
        editor.propertyChanged(propertyState, propertyState);

        verify(mockDelegate).propertyChanged(propertyState, propertyState);
        assertEquals(1, metrics.getSuccessCount("lucene9"));
        assertEquals(0, metrics.getFailureCount("lucene9"));
    }

    @Test
    public void testPropertyChanged_ExceptionCaught() throws Exception {
        doThrow(new RuntimeException("Test exception")).when(mockDelegate).propertyChanged(any(), any());

        // Should not throw
        editor.propertyChanged(propertyState, propertyState);

        verify(mockDelegate).propertyChanged(propertyState, propertyState);
        assertEquals(0, metrics.getSuccessCount("lucene9"));
        assertEquals(1, metrics.getFailureCount("lucene9"));
    }

    @Test
    public void testPropertyDeleted_Success() throws Exception {
        editor.propertyDeleted(propertyState);

        verify(mockDelegate).propertyDeleted(propertyState);
        assertEquals(1, metrics.getSuccessCount("lucene9"));
        assertEquals(0, metrics.getFailureCount("lucene9"));
    }

    @Test
    public void testPropertyDeleted_ExceptionCaught() throws Exception {
        doThrow(new RuntimeException("Test exception")).when(mockDelegate).propertyDeleted(any());

        // Should not throw
        editor.propertyDeleted(propertyState);

        verify(mockDelegate).propertyDeleted(propertyState);
        assertEquals(0, metrics.getSuccessCount("lucene9"));
        assertEquals(1, metrics.getFailureCount("lucene9"));
    }

    @Test
    public void testChildNodeAdded_Success() throws Exception {
        Editor childDelegate = Mockito.mock(Editor.class);
        when(mockDelegate.childNodeAdded(anyString(), any())).thenReturn(childDelegate);

        Editor childEditor = editor.childNodeAdded("child", nodeState);

        assertNotNull(childEditor);
        assertTrue(childEditor instanceof ErrorTolerantEditor);
        verify(mockDelegate).childNodeAdded("child", nodeState);
        assertEquals(1, metrics.getSuccessCount("lucene9"));
        assertEquals(0, metrics.getFailureCount("lucene9"));
    }

    @Test
    public void testChildNodeAdded_ExceptionCaught() throws Exception {
        when(mockDelegate.childNodeAdded(anyString(), any())).thenThrow(new RuntimeException("Test exception"));

        // Should not throw, returns null
        Editor childEditor = editor.childNodeAdded("child", nodeState);

        assertNull(childEditor);
        verify(mockDelegate).childNodeAdded("child", nodeState);
        assertEquals(0, metrics.getSuccessCount("lucene9"));
        assertEquals(1, metrics.getFailureCount("lucene9"));
    }

    @Test
    public void testChildNodeChanged_Success() throws Exception {
        Editor childDelegate = Mockito.mock(Editor.class);
        when(mockDelegate.childNodeChanged(anyString(), any(), any())).thenReturn(childDelegate);

        Editor childEditor = editor.childNodeChanged("child", nodeState, nodeState);

        assertNotNull(childEditor);
        assertTrue(childEditor instanceof ErrorTolerantEditor);
        verify(mockDelegate).childNodeChanged("child", nodeState, nodeState);
        assertEquals(1, metrics.getSuccessCount("lucene9"));
        assertEquals(0, metrics.getFailureCount("lucene9"));
    }

    @Test
    public void testChildNodeChanged_ExceptionCaught() throws Exception {
        when(mockDelegate.childNodeChanged(anyString(), any(), any())).thenThrow(new RuntimeException("Test exception"));

        // Should not throw, returns null
        Editor childEditor = editor.childNodeChanged("child", nodeState, nodeState);

        assertNull(childEditor);
        verify(mockDelegate).childNodeChanged("child", nodeState, nodeState);
        assertEquals(0, metrics.getSuccessCount("lucene9"));
        assertEquals(1, metrics.getFailureCount("lucene9"));
    }

    @Test
    public void testChildNodeDeleted_Success() throws Exception {
        Editor childDelegate = Mockito.mock(Editor.class);
        when(mockDelegate.childNodeDeleted(anyString(), any())).thenReturn(childDelegate);

        Editor childEditor = editor.childNodeDeleted("child", nodeState);

        assertNotNull(childEditor);
        assertTrue(childEditor instanceof ErrorTolerantEditor);
        verify(mockDelegate).childNodeDeleted("child", nodeState);
        assertEquals(1, metrics.getSuccessCount("lucene9"));
        assertEquals(0, metrics.getFailureCount("lucene9"));
    }

    @Test
    public void testChildNodeDeleted_ExceptionCaught() throws Exception {
        when(mockDelegate.childNodeDeleted(anyString(), any())).thenThrow(new RuntimeException("Test exception"));

        // Should not throw, returns null
        Editor childEditor = editor.childNodeDeleted("child", nodeState);

        assertNull(childEditor);
        verify(mockDelegate).childNodeDeleted("child", nodeState);
        assertEquals(0, metrics.getSuccessCount("lucene9"));
        assertEquals(1, metrics.getFailureCount("lucene9"));
    }

    @Test
    public void testMultipleOperations_MixedSuccessAndFailure() throws Exception {
        // First operation succeeds
        editor.enter(nodeState, nodeState);

        // Second operation fails
        doThrow(new RuntimeException("Test exception")).when(mockDelegate).propertyAdded(any());
        editor.propertyAdded(propertyState);

        // Third operation succeeds
        doNothing().when(mockDelegate).leave(any(), any());
        editor.leave(nodeState, nodeState);

        assertEquals(2, metrics.getSuccessCount("lucene9"));
        assertEquals(1, metrics.getFailureCount("lucene9"));
    }

    @Test
    public void testChildEditor_InheritsErrorTolerance() throws Exception {
        Editor childDelegate = Mockito.mock(Editor.class);
        when(mockDelegate.childNodeAdded(anyString(), any())).thenReturn(childDelegate);

        Editor childEditor = editor.childNodeAdded("child", nodeState);

        // Child editor should also be error-tolerant
        assertNotNull(childEditor);
        assertTrue(childEditor instanceof ErrorTolerantEditor);

        // Verify child editor catches exceptions
        doThrow(new RuntimeException("Child exception")).when(childDelegate).enter(any(), any());
        childEditor.enter(nodeState, nodeState);

        // Should have 2 successes (childNodeAdded + enter), 1 failure (enter exception)
        assertEquals(1, metrics.getSuccessCount("lucene9"));
        assertEquals(1, metrics.getFailureCount("lucene9"));
    }
}
