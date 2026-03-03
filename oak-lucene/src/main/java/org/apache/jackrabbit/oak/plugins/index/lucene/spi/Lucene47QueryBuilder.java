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
import org.apache.jackrabbit.oak.plugins.index.search.spi.QueryBuilder;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.util.BytesRef;

/**
 * Lucene 4.7.x implementation of QueryBuilder.
 * Creates queries using Lucene 4.7.2 query types.
 */
public final class Lucene47QueryBuilder implements QueryBuilder {

    @Override
    public Query term(String field, String value) {
        if (field == null || value == null) {
            throw new IllegalArgumentException("Field and value cannot be null");
        }
        Term term = new Term(field, value);
        return new Lucene47Query(new TermQuery(term));
    }

    @Override
    public Query range(String field, String lowerValue, String upperValue,
                      boolean includeLower, boolean includeUpper) {
        if (field == null) {
            throw new IllegalArgumentException("Field cannot be null");
        }
        // Lucene 4.7 uses TermRangeQuery with BytesRef
        BytesRef lower = lowerValue != null ? new BytesRef(lowerValue) : null;
        BytesRef upper = upperValue != null ? new BytesRef(upperValue) : null;
        return new Lucene47Query(new TermRangeQuery(field, lower, upper,
                                                     includeLower, includeUpper));
    }

    @Override
    public Query wildcard(String field, String pattern) {
        if (field == null || pattern == null) {
            throw new IllegalArgumentException("Field and pattern cannot be null");
        }
        Term term = new Term(field, pattern);
        return new Lucene47Query(new WildcardQuery(term));
    }

    @Override
    public Query prefix(String field, String prefix) {
        if (field == null || prefix == null) {
            throw new IllegalArgumentException("Field and prefix cannot be null");
        }
        Term term = new Term(field, prefix);
        return new Lucene47Query(new PrefixQuery(term));
    }
}
