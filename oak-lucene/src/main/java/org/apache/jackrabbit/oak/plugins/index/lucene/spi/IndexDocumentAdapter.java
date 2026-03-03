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
import org.apache.jackrabbit.oak.plugins.index.search.spi.DocumentBuilder;
import org.apache.jackrabbit.oak.plugins.index.search.FieldNames;

/**
 * Adapter providing common Oak document creation patterns via the Search SPI.
 *
 * <p>This class demonstrates how oak-lucene code can migrate to the Search SPI
 * for common indexing operations like creating documents with path, fulltext,
 * and property fields.</p>
 *
 * <p><strong>Runtime integration:</strong> This class is used by oak-lucene's
 * indexing pipeline to create documents through version-agnostic interfaces,
 * enabling future migration to Lucene 9.x without changing calling code.</p>
 */
public final class IndexDocumentAdapter {

    private IndexDocumentAdapter() {
        // Static utility class
    }

    /**
     * Creates a basic index document with path field using Search SPI.
     *
     * <p>This replaces direct Lucene Document creation:</p>
     * <pre>{@code
     * // Old: Document doc = new Document(); doc.add(new StringField("path", path, YES));
     * // New: Document doc = createPathDocument(path);
     * }</pre>
     *
     * @param path the node path
     * @return a Document containing the path field
     */
    public static Document createPathDocument(String path) {
        DocumentBuilder builder = LuceneIndexHelper.newDocumentBuilder();
        return builder
            .addStringField(FieldNames.PATH, path, DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .build();
    }

    /**
     * Creates a fulltext index document using Search SPI.
     *
     * <p>This method demonstrates SPI usage for fulltext indexing,
     * a common pattern in oak-lucene's {@code LuceneDocumentMaker}.</p>
     *
     * @param path the node path
     * @param text the fulltext content
     * @return a Document with path and fulltext fields
     */
    public static Document createFulltextDocument(String path, String text) {
        DocumentBuilder builder = LuceneIndexHelper.newDocumentBuilder();
        return builder
            .addStringField(FieldNames.PATH, path, DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField(FieldNames.FULLTEXT, text, DocumentBuilder.FieldType.TEXT)
            .build();
    }

    /**
     * Creates a property index document using Search SPI.
     *
     * <p>Demonstrates property indexing via SPI, commonly used for
     * structured property queries in Oak.</p>
     *
     * @param path the node path
     * @param propertyName the property name
     * @param propertyValue the property value
     * @param analyzed whether the property should be analyzed
     * @return a Document with path and property fields
     */
    public static Document createPropertyDocument(String path, String propertyName,
                                                  String propertyValue, boolean analyzed) {
        DocumentBuilder builder = LuceneIndexHelper.newDocumentBuilder();
        DocumentBuilder.FieldType fieldType = analyzed
            ? DocumentBuilder.FieldType.TEXT
            : DocumentBuilder.FieldType.STRING_NOT_ANALYZED;

        return builder
            .addStringField(FieldNames.PATH, path, DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addStringField(propertyName, propertyValue, fieldType)
            .build();
    }

    /**
     * Creates a document with numeric property using Search SPI.
     *
     * <p>Demonstrates numeric field indexing, commonly used for
     * dates, scores, and numeric range queries.</p>
     *
     * @param path the node path
     * @param propertyName the property name
     * @param value the numeric value
     * @return a Document with path and numeric fields
     */
    public static Document createNumericPropertyDocument(String path,
                                                         String propertyName,
                                                         long value) {
        DocumentBuilder builder = LuceneIndexHelper.newDocumentBuilder();
        return builder
            .addStringField(FieldNames.PATH, path, DocumentBuilder.FieldType.STRING_NOT_ANALYZED)
            .addLongField(propertyName, value, DocumentBuilder.FieldType.LONG)
            .build();
    }

    /**
     * Extracts the underlying Lucene 4.7 document for use with existing code.
     *
     * <p>This bridge method allows gradual migration: new code uses SPI to create
     * documents, but they can still be passed to existing Lucene 4.7 APIs.</p>
     *
     * @param spiDocument the SPI document
     * @return the underlying Lucene 4.7 Document
     * @throws IllegalArgumentException if document is not a Lucene47Document
     */
    public static org.apache.lucene.document.Document toLuceneDocument(Document spiDocument) {
        if (!(spiDocument instanceof Lucene47Document)) {
            throw new IllegalArgumentException(
                "Document must be created via LuceneIndexHelper for Lucene 4.7 compatibility");
        }
        return ((Lucene47Document) spiDocument).getLuceneDocument();
    }
}
