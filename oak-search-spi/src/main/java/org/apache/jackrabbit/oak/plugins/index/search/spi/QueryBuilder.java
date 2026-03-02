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
}
