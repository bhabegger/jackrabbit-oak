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
package org.apache.jackrabbit.oak.plugins.index.search.spi;

import java.io.Closeable;
import java.io.IOException;

/**
 * Abstraction for reading from an index file.
 * Hides Lucene's IndexInput API.
 */
public interface IndexInput extends Closeable {

    /**
     * Reads and returns a single byte.
     */
    byte readByte() throws IOException;

    /**
     * Reads bytes into the given array.
     */
    void readBytes(byte[] b, int offset, int len) throws IOException;

    /**
     * Returns the current position in the file.
     */
    long getFilePointer() throws IOException;

    /**
     * Sets the file pointer to the given position.
     */
    void seek(long pos) throws IOException;

    /**
     * Returns the length of the file.
     */
    long length() throws IOException;

    @Override
    void close() throws IOException;
}
