/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.oak.plugins.index.lucene9.directory;

import java.io.IOException;
import java.io.InputStream;

import org.apache.jackrabbit.oak.api.Blob;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.lucene.store.IndexInput;

import static org.apache.jackrabbit.JcrConstants.JCR_DATA;

/**
 * IndexInput implementation that reads data from Oak repository blobs.
 */
class OakIndexInput extends IndexInput {

    private final String name;
    private final NodeBuilder file;
    private byte[] data;
    private long position;
    private boolean closed;

    public OakIndexInput(String name, NodeBuilder file) {
        super("OakIndexInput(" + name + ")");
        this.name = name;
        this.file = file;
        this.position = 0;
        this.closed = false;
    }

    private OakIndexInput(OakIndexInput other, String sliceDescription) {
        super(other.getFullSliceDescription(sliceDescription));
        this.name = other.name;
        this.file = other.file;
        this.data = other.data;
        this.position = other.position;
        this.closed = false;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        data = null;
    }

    @Override
    public long getFilePointer() {
        return position;
    }

    @Override
    public void seek(long pos) throws IOException {
        checkNotClosed();
        if (pos < 0 || pos > length()) {
            throw new IOException("Invalid seek position: " + pos);
        }
        position = pos;
    }

    @Override
    public long length() {
        ensureDataLoaded();
        return data != null ? data.length : 0;
    }

    @Override
    public IndexInput slice(String sliceDescription, long offset, long length) throws IOException {
        checkNotClosed();
        if (offset < 0 || length < 0 || offset + length > length()) {
            throw new IllegalArgumentException("Invalid slice: offset=" + offset + ", length=" + length);
        }
        OakIndexInput slice = new OakIndexInput(this, sliceDescription);
        slice.position = offset;
        return slice;
    }

    @Override
    public byte readByte() throws IOException {
        checkNotClosed();
        ensureDataLoaded();
        if (position >= data.length) {
            throw new IOException("Read past end of file");
        }
        return data[(int) position++];
    }

    @Override
    public void readBytes(byte[] b, int offset, int len) throws IOException {
        checkNotClosed();
        ensureDataLoaded();
        if (position + len > data.length) {
            throw new IOException("Read past end of file");
        }
        System.arraycopy(data, (int) position, b, offset, len);
        position += len;
    }

    private void ensureDataLoaded() {
        if (data == null && !closed) {
            PropertyState property = file.getProperty(JCR_DATA);
            if (property != null) {
                Blob blob = property.getValue(org.apache.jackrabbit.oak.api.Type.BINARY);
                try (InputStream stream = blob.getNewStream()) {
                    data = stream.readAllBytes();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read blob data", e);
                }
            } else {
                data = new byte[0];
            }
        }
    }

    private void checkNotClosed() throws IOException {
        if (closed) {
            throw new IOException("IndexInput is closed");
        }
    }
}
