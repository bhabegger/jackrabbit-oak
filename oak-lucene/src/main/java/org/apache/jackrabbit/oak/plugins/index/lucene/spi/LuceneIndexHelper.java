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

import org.apache.jackrabbit.oak.plugins.index.search.spi.DocumentBuilder;
import org.apache.jackrabbit.oak.plugins.index.search.spi.QueryBuilder;
import org.apache.jackrabbit.oak.plugins.index.search.spi.IndexVersion;

/**
 * Helper class providing access to Oak Search SPI implementations for Lucene 4.7.
 *
 * <p>This class serves as the integration point between oak-lucene's existing code
 * and the new version-agnostic Search SPI. It provides factory methods for creating
 * documents and queries using the SPI abstractions backed by Lucene 4.7.2.</p>
 *
 * <p><strong>Usage example:</strong></p>
 * <pre>{@code
 * // Get builders via SPI instead of direct Lucene API
 * DocumentBuilder builder = LuceneIndexHelper.newDocumentBuilder();
 * Document doc = builder
 *     .addStringField("path", "/content/page", DocumentBuilder.FieldType.TEXT)
 *     .addLongField("created", timestamp, DocumentBuilder.FieldType.LONG)
 *     .build();
 *
 * QueryBuilder qb = LuceneIndexHelper.newQueryBuilder();
 * Query query = qb.term("path", "/content");
 * }</pre>
 *
 * <p>This abstraction enables future migration to Lucene 9.x by allowing oak-lucene
 * code to work against version-agnostic interfaces.</p>
 */
public final class LuceneIndexHelper {

    private LuceneIndexHelper() {
        // Static utility class
    }

    /**
     * Returns the index version for oak-lucene's embedded Lucene.
     *
     * @return {@link IndexVersion#LUCENE_4_7_2}
     */
    public static IndexVersion getIndexVersion() {
        return IndexVersion.LUCENE_4_7_2;
    }

    /**
     * Creates a new DocumentBuilder for building Lucene 4.7 documents
     * via the Search SPI.
     *
     * <p>This method provides access to document building through version-agnostic
     * interfaces, allowing code to be migrated to newer Lucene versions without
     * changing the document creation logic.</p>
     *
     * @return a new {@link DocumentBuilder} backed by Lucene 4.7.2
     */
    public static DocumentBuilder newDocumentBuilder() {
        return new Lucene47DocumentBuilder();
    }

    /**
     * Creates a new QueryBuilder for building Lucene 4.7 queries
     * via the Search SPI.
     *
     * <p>This method provides access to query building through version-agnostic
     * interfaces, supporting term, range, wildcard, and prefix queries.</p>
     *
     * @return a new {@link QueryBuilder} backed by Lucene 4.7.2
     */
    public static QueryBuilder newQueryBuilder() {
        return new Lucene47QueryBuilder();
    }

    /**
     * Checks if the given version is supported by this helper.
     *
     * @param version the version to check
     * @return true if this is Lucene 4.7.2, false otherwise
     */
    public static boolean supportsVersion(IndexVersion version) {
        return version == IndexVersion.LUCENE_4_7_2;
    }

    /**
     * Returns information about the current Lucene implementation.
     *
     * @return a human-readable string describing the implementation
     */
    public static String getImplementationInfo() {
        return "Oak Lucene 4.7.2 (embedded) via Search SPI";
    }
}
