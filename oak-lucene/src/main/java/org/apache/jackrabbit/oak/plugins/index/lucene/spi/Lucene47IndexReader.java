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
package org.apache.jackrabbit.oak.plugins.index.lucene.spi;

import org.apache.jackrabbit.oak.plugins.index.search.spi.Document;
import org.apache.jackrabbit.oak.plugins.index.search.spi.IndexReader;
import org.apache.jackrabbit.oak.plugins.index.search.spi.IndexVersion;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.Directory;

import java.io.IOException;

/**
 * Lucene 4.7.x implementation of IndexReader.
 * Wraps Lucene's DirectoryReader for version-agnostic access.
 */
public final class Lucene47IndexReader implements IndexReader {

    private final DirectoryReader delegate;

    /**
     * Opens an IndexReader on the given directory.
     *
     * @param directory the Lucene directory
     * @throws IOException if the reader cannot be opened
     */
    public Lucene47IndexReader(Directory directory) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException("Directory cannot be null");
        }
        this.delegate = DirectoryReader.open(directory);
    }

    /**
     * Wraps an existing DirectoryReader.
     *
     * @param reader the Lucene directory reader
     */
    public Lucene47IndexReader(DirectoryReader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("Reader cannot be null");
        }
        this.delegate = reader;
    }

    @Override
    public int numDocs() {
        return delegate.numDocs();
    }

    @Override
    public int maxDoc() {
        return delegate.maxDoc();
    }

    @Override
    public Document document(int docID) throws IOException {
        if (docID < 0 || docID >= maxDoc()) {
            throw new IllegalArgumentException("Invalid docID: " + docID);
        }
        org.apache.lucene.document.Document luceneDoc = delegate.document(docID);
        return new Lucene47Document(luceneDoc);
    }

    @Override
    public IndexVersion getVersion() {
        return IndexVersion.LUCENE_4_7_2;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    /**
     * Returns the underlying Lucene DirectoryReader.
     *
     * <p>Public for use by oak-lucene components that need native Lucene reader.</p>
     *
     * @return the Lucene DirectoryReader
     */
    public DirectoryReader getLuceneReader() {
        return delegate;
    }
}
