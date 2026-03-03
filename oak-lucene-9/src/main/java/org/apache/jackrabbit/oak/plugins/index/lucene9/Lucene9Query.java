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

import org.apache.jackrabbit.oak.plugins.index.search.spi.Query;

/**
 * Lucene 9.x implementation of Oak Query.
 * Wraps Lucene's Query class.
 */
public final class Lucene9Query implements Query {

    private final org.apache.lucene.search.Query delegate;

    /**
     * Wraps an existing Lucene query.
     *
     * @param luceneQuery the Lucene query to wrap
     */
    public Lucene9Query(org.apache.lucene.search.Query luceneQuery) {
        if (luceneQuery == null) {
            throw new IllegalArgumentException("Lucene query cannot be null");
        }
        this.delegate = luceneQuery;
    }

    /**
     * Returns the underlying Lucene query.
     *
     * <p>Package-private for use by other Lucene 9 components.</p>
     *
     * @return the Lucene query
     */
    org.apache.lucene.search.Query getLuceneQuery() {
        return delegate;
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
