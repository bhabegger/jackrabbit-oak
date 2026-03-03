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

import org.apache.jackrabbit.oak.plugins.index.search.spi.Query;

/**
 * Lucene 4.7.x implementation of Query.
 * Wraps the embedded Lucene 4.7.2 Query.
 */
public final class Lucene47Query implements Query {

    private final org.apache.lucene.search.Query delegate;

    /**
     * Wraps an existing Lucene 4.7 query.
     *
     * @param query the Lucene query
     */
    public Lucene47Query(org.apache.lucene.search.Query query) {
        if (query == null) {
            throw new IllegalArgumentException("Query cannot be null");
        }
        this.delegate = query;
    }

    /**
     * Returns the underlying Lucene 4.7 Query.
     *
     * <p>Public for use by oak-lucene components that need to extract
     * the native Lucene query from the SPI wrapper.</p>
     *
     * @return the Lucene query
     */
    public org.apache.lucene.search.Query getLuceneQuery() {
        return delegate;
    }
}
