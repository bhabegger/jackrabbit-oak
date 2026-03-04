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
 * Abstraction for reading an index.
 * Hides Lucene's IndexReader API.
 */
public interface IndexReader extends Closeable {

    /**
     * Returns the total number of documents in the index
     * (excluding deleted documents).
     */
    int numDocs();

    /**
     * Returns the maximum document ID (includes deleted docs).
     */
    int maxDoc();

    /**
     * Reads a document by ID.
     *
     * @param docID the document ID
     * @return the document
     * @throws IOException if document cannot be read
     */
    Document document(int docID) throws IOException;

    /**
     * Returns the index version.
     */
    IndexVersion getVersion();

    /**
     * Closes the reader and releases resources.
     */
    @Override
    void close() throws IOException;
}
