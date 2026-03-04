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

/**
 * Lucene 4.7.x implementation of Document.
 * Wraps the embedded Lucene 4.7.2 Document.
 */
public final class Lucene47Document implements Document {

    private final org.apache.lucene.document.Document delegate;

    /**
     * Wraps an existing Lucene 4.7 document.
     *
     * @param document the Lucene document
     */
    public Lucene47Document(org.apache.lucene.document.Document document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        this.delegate = document;
    }

    /**
     * Returns the underlying Lucene 4.7 Document.
     *
     * <p>Public for use by oak-lucene components that need native Lucene document access.</p>
     *
     * @return the Lucene document
     */
    public org.apache.lucene.document.Document getLuceneDocument() {
        return delegate;
    }
}
