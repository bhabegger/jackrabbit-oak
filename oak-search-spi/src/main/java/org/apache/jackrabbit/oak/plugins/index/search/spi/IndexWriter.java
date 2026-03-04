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
 * Abstraction for writing to an index.
 * Hides Lucene's IndexWriter API.
 */
public interface IndexWriter extends Closeable {

    /**
     * Adds a new document to the index.
     *
     * @param doc the document to add
     * @throws IOException if write fails
     */
    void addDocument(Document doc) throws IOException;

    /**
     * Updates a document (delete by term, then add).
     *
     * @param term the term identifying document(s) to delete
     * @param doc the new document to add
     * @throws IOException if update fails
     */
    void updateDocument(Term term, Document doc) throws IOException;

    /**
     * Deletes documents matching the term.
     *
     * @param term the term identifying document(s) to delete
     * @throws IOException if delete fails
     */
    void deleteDocuments(Term term) throws IOException;

    /**
     * Commits all pending changes.
     *
     * @throws IOException if commit fails
     */
    void commit() throws IOException;

    /**
     * Forces merge of segments (optimization).
     *
     * @param maxNumSegments target number of segments
     * @throws IOException if merge fails
     */
    void forceMerge(int maxNumSegments) throws IOException;

    /**
     * Closes the writer and releases resources.
     */
    @Override
    void close() throws IOException;
}
