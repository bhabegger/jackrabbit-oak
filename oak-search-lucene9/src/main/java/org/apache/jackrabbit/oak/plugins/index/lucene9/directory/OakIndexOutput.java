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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.jackrabbit.oak.api.Blob;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.lucene.store.IndexOutput;

import static org.apache.jackrabbit.JcrConstants.JCR_DATA;

/**
 * IndexOutput implementation that writes data to Oak repository as blobs.
 */
class OakIndexOutput extends IndexOutput {

    private final String name;
    private final NodeBuilder file;
    private final ByteArrayOutputStream buffer;
    private long position;

    public OakIndexOutput(String name, NodeBuilder file) {
        super("OakIndexOutput(" + name + ")", name);
        this.name = name;
        this.file = file;
        this.buffer = new ByteArrayOutputStream();
        this.position = 0;
    }

    @Override
    public void writeByte(byte b) throws IOException {
        buffer.write(b);
        position++;
    }

    @Override
    public void writeBytes(byte[] b, int offset, int length) throws IOException {
        buffer.write(b, offset, length);
        position += length;
    }

    @Override
    public long getFilePointer() {
        return position;
    }

    @Override
    public long getChecksum() throws IOException {
        // For now, return a simple checksum
        return position;
    }

    @Override
    public void close() throws IOException {
        flush();
    }

    private void flush() throws IOException {
        if (buffer.size() > 0) {
            byte[] data = buffer.toByteArray();
            Blob blob = file.createBlob(new ByteArrayInputStream(data));
            file.setProperty(JCR_DATA, blob);
        }
    }
}
