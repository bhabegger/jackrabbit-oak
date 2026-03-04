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

    @Override
    public Query numericRange(String field, Long min, Long max, boolean minInclusive, boolean maxInclusive) {
        if (field == null) {
            throw new IllegalArgumentException("Field cannot be null");
        }
        return new Lucene47Query(NumericRangeQuery.newLongRange(field, min, max, minInclusive, maxInclusive));
    }

    @Override
    public Query numericRange(String field, Double min, Double max, boolean minInclusive, boolean maxInclusive) {
        if (field == null) {
            throw new IllegalArgumentException("Field cannot be null");
        }
        return new Lucene47Query(NumericRangeQuery.newDoubleRange(field, min, max, minInclusive, maxInclusive));
    }

    @Override
    public Query numericRange(String field, Integer min, Integer max, boolean minInclusive, boolean maxInclusive) {
        if (field == null) {
            throw new IllegalArgumentException("Field cannot be null");
        }
        return new Lucene47Query(NumericRangeQuery.newIntRange(field, min, max, minInclusive, maxInclusive));
    }

    @Override
    public Query matchAll() {
        return new Lucene47Query(new MatchAllDocsQuery());
    }

    @Override
    public Query wrap(Object query) {
        if (query == null) {
            throw new IllegalArgumentException("Query cannot be null");
        }
        if (query instanceof org.apache.lucene.search.Query) {
            return new Lucene47Query((org.apache.lucene.search.Query) query);
        }
        throw new IllegalArgumentException("Can only wrap Lucene Query objects");
    }

    @Override
    public BooleanQueryBuilder bool() {
        return new Lucene47BooleanQueryBuilder();
    }

    /**
     * Lucene 4.7 implementation of BooleanQueryBuilder.
     */
    private static class Lucene47BooleanQueryBuilder implements BooleanQueryBuilder {
        private final BooleanQuery booleanQuery = new BooleanQuery();

        @Override
        public BooleanQueryBuilder must(Query query) {
            if (query == null) {
                throw new IllegalArgumentException("Query cannot be null");
            }
            org.apache.lucene.search.Query luceneQuery = ((Lucene47Query) query).getLuceneQuery();
            booleanQuery.add(luceneQuery, BooleanClause.Occur.MUST);
            return this;
        }

        @Override
        public BooleanQueryBuilder should(Query query) {
            if (query == null) {
                throw new IllegalArgumentException("Query cannot be null");
            }
            org.apache.lucene.search.Query luceneQuery = ((Lucene47Query) query).getLuceneQuery();
            booleanQuery.add(luceneQuery, BooleanClause.Occur.SHOULD);
            return this;
        }

        @Override
        public BooleanQueryBuilder mustNot(Query query) {
            if (query == null) {
                throw new IllegalArgumentException("Query cannot be null");
            }
            org.apache.lucene.search.Query luceneQuery = ((Lucene47Query) query).getLuceneQuery();
            booleanQuery.add(luceneQuery, BooleanClause.Occur.MUST_NOT);
            return this;
        }

        @Override
        public Query build() {
            return new Lucene47Query(booleanQuery);
        }
    }
}
