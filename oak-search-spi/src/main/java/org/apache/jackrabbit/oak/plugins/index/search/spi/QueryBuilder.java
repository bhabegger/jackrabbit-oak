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

/**
 * Builder for constructing queries.
 *
 * <p>Hides Lucene's Query API and version-specific quirks
 * (e.g., empty string handling in range queries).</p>
 */
public interface QueryBuilder {

    /**
     * Creates a term query (exact match).
     *
     * @param field the field name
     * @param value the term value
     * @return the query
     */
    Query term(String field, String value);

    /**
     * Creates a range query.
     *
     * <p>Handles version-specific edge cases automatically
     * (e.g., empty string lower bound in Lucene 5+).</p>
     *
     * @param field the field name
     * @param lowerTerm lower bound (inclusive/exclusive based on param)
     * @param upperTerm upper bound (inclusive/exclusive based on param)
     * @param includeLower true if lower bound is inclusive
     * @param includeUpper true if upper bound is inclusive
     * @return the query
     */
    Query range(String field, String lowerTerm, String upperTerm,
                boolean includeLower, boolean includeUpper);

    /**
     * Creates a wildcard query.
     *
     * @param field the field name
     * @param pattern wildcard pattern (* and ? supported)
     * @return the query
     */
    Query wildcard(String field, String pattern);

    /**
     * Creates a prefix query.
     *
     * @param field the field name
     * @param prefix the prefix string
     * @return the query
     */
    Query prefix(String field, String prefix);

    /**
     * Creates a numeric range query for Long values.
     *
     * @param field the field name
     * @param min minimum value (null for unbounded)
     * @param max maximum value (null for unbounded)
     * @param minInclusive true if minimum is inclusive
     * @param maxInclusive true if maximum is inclusive
     * @return the query
     */
    Query numericRange(String field, Long min, Long max, boolean minInclusive, boolean maxInclusive);

    /**
     * Creates a numeric range query for Double values.
     *
     * @param field the field name
     * @param min minimum value (null for unbounded)
     * @param max maximum value (null for unbounded)
     * @param minInclusive true if minimum is inclusive
     * @param maxInclusive true if maximum is inclusive
     * @return the query
     */
    Query numericRange(String field, Double min, Double max, boolean minInclusive, boolean maxInclusive);

    /**
     * Creates a numeric range query for Integer values.
     *
     * @param field the field name
     * @param min minimum value (null for unbounded)
     * @param max maximum value (null for unbounded)
     * @param minInclusive true if minimum is inclusive
     * @param maxInclusive true if maximum is inclusive
     * @return the query
     */
    Query numericRange(String field, Integer min, Integer max, boolean minInclusive, boolean maxInclusive);

    /**
     * Creates a match-all query that matches every document.
     *
     * @return the query
     */
    Query matchAll();

    /**
     * Wraps an existing query into an SPI Query.
     * This is useful for combining queries built directly with queries built via SPI.
     *
     * @param query the native query to wrap
     * @return the wrapped query
     */
    Query wrap(Object query);

    /**
     * Creates a boolean query builder for combining multiple queries.
     *
     * @return a boolean query builder
     */
    BooleanQueryBuilder bool();

    /**
     * Builder for constructing boolean queries with must/should/mustNot clauses.
     */
    interface BooleanQueryBuilder {
        /**
         * Adds a MUST clause (equivalent to AND).
         *
         * @param query the query to add
         * @return this builder for chaining
         */
        BooleanQueryBuilder must(Query query);

        /**
         * Adds a SHOULD clause (equivalent to OR).
         *
         * @param query the query to add
         * @return this builder for chaining
         */
        BooleanQueryBuilder should(Query query);

        /**
         * Adds a MUST_NOT clause (equivalent to NOT).
         *
         * @param query the query to add
         * @return this builder for chaining
         */
        BooleanQueryBuilder mustNot(Query query);

        /**
         * Builds the boolean query.
         *
         * @return the constructed query
         */
        Query build();
    }
}
