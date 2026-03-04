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
import java.util.Collection;

/**
 * Abstraction of a directory containing index files.
 *
 * <p>Hides Lucene's Directory API, providing version-agnostic
 * file operations for index storage.</p>
 */
public interface IndexDirectory extends Closeable {

    /**
     * Opens an input stream for reading an index file.
     *
     * @param name the file name
     * @return input stream for reading
     * @throws IOException if file cannot be opened
     */
    IndexInput openInput(String name) throws IOException;

    /**
     * Creates an output stream for writing an index file.
     *
     * @param name the file name
     * @return output stream for writing
     * @throws IOException if file cannot be created
     */
    IndexOutput createOutput(String name) throws IOException;

    /**
     * Lists all files in the directory.
     *
     * @return array of file names
     * @throws IOException if directory cannot be read
     */
    String[] listAll() throws IOException;

    /**
     * Returns the length of a file in bytes.
     *
     * @param name the file name
     * @return file length in bytes
     * @throws IOException if file does not exist
     */
    long fileLength(String name) throws IOException;

    /**
     * Deletes a file.
     *
     * @param name the file name
     * @throws IOException if file cannot be deleted
     */
    void deleteFile(String name) throws IOException;

    /**
     * Ensures all modifications are persisted.
     *
     * @param names collection of file names to sync
     * @throws IOException if sync fails
     */
    void sync(Collection<String> names) throws IOException;

    /**
     * Closes the directory and releases resources.
     */
    @Override
    void close() throws IOException;
}
