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
package org.apache.jackrabbit.oak.plugins.index.lucene9;

import org.apache.jackrabbit.oak.plugins.index.search.spi.Document;

/**
 * Lucene 9.x implementation of Oak Document.
 * Wraps Lucene's Document class.
 */
public final class Lucene9Document implements Document {

    private final org.apache.lucene.document.Document delegate;

    /**
     * Creates a new empty document.
     */
    public Lucene9Document() {
        this.delegate = new org.apache.lucene.document.Document();
    }

    /**
     * Wraps an existing Lucene document.
     *
     * @param luceneDocument the Lucene document to wrap
     */
    public Lucene9Document(org.apache.lucene.document.Document luceneDocument) {
        if (luceneDocument == null) {
            throw new IllegalArgumentException("Lucene document cannot be null");
        }
        this.delegate = luceneDocument;
    }

    /**
     * Returns the underlying Lucene document.
     *
     * <p>Package-private for use by other Lucene 9 components.</p>
     *
     * @return the Lucene document
     */
    org.apache.lucene.document.Document getLuceneDocument() {
        return delegate;
    }
}
