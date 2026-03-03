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
import org.apache.jackrabbit.oak.plugins.index.search.spi.QueryBuilder;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.BytesRef;

/**
 * Lucene 9.x implementation of QueryBuilder.
 * Creates Lucene queries from Oak query specifications.
 */
public final class Lucene9QueryBuilder implements QueryBuilder {

    @Override
    public Query term(String field, String value) {
        if (field == null || value == null) {
            throw new IllegalArgumentException("Field and value cannot be null");
        }
        Term term = new Term(field, value);
        return new Lucene9Query(new TermQuery(term));
    }

    @Override
    public Query range(String field, String lowerTerm, String upperTerm,
                      boolean includeLower, boolean includeUpper) {
        if (field == null) {
            throw new IllegalArgumentException("Field cannot be null");
        }

        // Handle null bounds (Lucene 9 uses null for unbounded)
        BytesRef lower = lowerTerm != null ? new BytesRef(lowerTerm) : null;
        BytesRef upper = upperTerm != null ? new BytesRef(upperTerm) : null;

        TermRangeQuery rangeQuery = new TermRangeQuery(field, lower, upper,
                includeLower, includeUpper);
        return new Lucene9Query(rangeQuery);
    }

    @Override
    public Query wildcard(String field, String pattern) {
        if (field == null || pattern == null) {
            throw new IllegalArgumentException("Field and pattern cannot be null");
        }
        Term term = new Term(field, pattern);
        return new Lucene9Query(new WildcardQuery(term));
    }

    @Override
    public Query prefix(String field, String prefix) {
        if (field == null || prefix == null) {
            throw new IllegalArgumentException("Field and prefix cannot be null");
        }
        Term term = new Term(field, prefix);
        return new Lucene9Query(new PrefixQuery(term));
    }
}
