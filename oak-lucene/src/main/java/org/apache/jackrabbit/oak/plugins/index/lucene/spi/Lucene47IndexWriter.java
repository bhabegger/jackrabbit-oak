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
import org.apache.jackrabbit.oak.plugins.index.search.spi.IndexWriter;
import org.apache.jackrabbit.oak.plugins.index.search.spi.Term;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Version;

import java.io.IOException;

/**
 * Lucene 4.7.x implementation of IndexWriter.
 * Wraps Lucene's IndexWriter for version-agnostic access.
 */
public final class Lucene47IndexWriter implements IndexWriter {

    private final org.apache.lucene.index.IndexWriter delegate;

    /**
     * Creates a new IndexWriter on the given directory.
     *
     * @param directory the Lucene directory
     * @throws IOException if the writer cannot be created
     */
    public Lucene47IndexWriter(Directory directory) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException("Directory cannot be null");
        }

        IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_47, new StandardAnalyzer(Version.LUCENE_47));
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        this.delegate = new org.apache.lucene.index.IndexWriter(directory, config);
    }

    /**
     * Creates a new IndexWriter with custom configuration.
     *
     * @param directory the Lucene directory
     * @param config the index writer configuration
     * @throws IOException if the writer cannot be created
     */
    public Lucene47IndexWriter(Directory directory, IndexWriterConfig config) throws IOException {
        if (directory == null || config == null) {
            throw new IllegalArgumentException("Directory and config cannot be null");
        }
        this.delegate = new org.apache.lucene.index.IndexWriter(directory, config);
    }

    @Override
    public void addDocument(Document doc) throws IOException {
        if (doc == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        if (!(doc instanceof Lucene47Document)) {
            throw new IllegalArgumentException("Document must be Lucene47Document");
        }

        Lucene47Document lucene47Doc = (Lucene47Document) doc;
        delegate.addDocument(lucene47Doc.getLuceneDocument());
    }

    @Override
    public void updateDocument(Term term, Document doc) throws IOException {
        if (term == null || doc == null) {
            throw new IllegalArgumentException("Term and document cannot be null");
        }
        if (!(doc instanceof Lucene47Document)) {
            throw new IllegalArgumentException("Document must be Lucene47Document");
        }

        org.apache.lucene.index.Term luceneTerm =
            new org.apache.lucene.index.Term(term.field(), term.value());
        Lucene47Document lucene47Doc = (Lucene47Document) doc;
        delegate.updateDocument(luceneTerm, lucene47Doc.getLuceneDocument());
    }

    @Override
    public void deleteDocuments(Term term) throws IOException {
        if (term == null) {
            throw new IllegalArgumentException("Term cannot be null");
        }

        org.apache.lucene.index.Term luceneTerm =
            new org.apache.lucene.index.Term(term.field(), term.value());
        delegate.deleteDocuments(luceneTerm);
    }

    @Override
    public void commit() throws IOException {
        delegate.commit();
    }

    @Override
    public void forceMerge(int maxNumSegments) throws IOException {
        if (maxNumSegments < 1) {
            throw new IllegalArgumentException("maxNumSegments must be >= 1");
        }
        delegate.forceMerge(maxNumSegments);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    /**
     * Returns the underlying Lucene IndexWriter.
     *
     * <p>Package-private for use by other Lucene 4.7 components.</p>
     *
     * @return the Lucene IndexWriter
     */
    org.apache.lucene.index.IndexWriter getLuceneWriter() {
        return delegate;
    }
}
