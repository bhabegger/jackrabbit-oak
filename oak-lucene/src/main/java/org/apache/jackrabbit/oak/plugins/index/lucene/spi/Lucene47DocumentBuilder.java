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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.DoubleField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.util.BytesRef;

/**
 * Lucene 4.7.x implementation of DocumentBuilder.
 * Creates documents using Lucene 4.7.2 field types.
 */
public final class Lucene47DocumentBuilder implements DocumentBuilder {

    private final org.apache.lucene.document.Document document;

    /**
     * Creates a new builder for a Lucene 4.7 document.
     */
    public Lucene47DocumentBuilder() {
        this.document = new org.apache.lucene.document.Document();
    }

    @Override
    public DocumentBuilder addStringField(String name, String value, org.apache.jackrabbit.oak.plugins.index.search.spi.DocumentBuilder.FieldType type) {
        if (name == null || value == null) {
            throw new IllegalArgumentException("Field name and value cannot be null");
        }

        Field field;
        switch (type) {
            case STRING_ANALYZED:
                // Analyzed and indexed
                field = new Field(name, value, Field.Store.YES, Field.Index.ANALYZED);
                break;
            case STRING_NOT_ANALYZED:
                // Not analyzed but indexed
                field = new Field(name, value, Field.Store.YES, Field.Index.NOT_ANALYZED);
                break;
            case TEXT:
                // Analyzed, indexed, and stored
                field = new Field(name, value, Field.Store.YES, Field.Index.ANALYZED);
                break;
            case STORED_ONLY:
                // Stored but not indexed
                field = new Field(name, value, Field.Store.YES, Field.Index.NO);
                break;
            default:
                throw new IllegalArgumentException("Invalid field type for string: " + type);
        }
        document.add(field);
        return this;
    }

    @Override
    public DocumentBuilder addLongField(String name, long value, org.apache.jackrabbit.oak.plugins.index.search.spi.DocumentBuilder.FieldType type) {
        if (name == null) {
            throw new IllegalArgumentException("Field name cannot be null");
        }

        // Lucene 4.7 uses LongField for numeric indexing
        Field field;
        switch (type) {
            case LONG:
                // Indexed as numeric field with default precision step
                field = new LongField(name, value, Field.Store.YES);
                break;
            case STORED_ONLY:
                // Just store the value
                field = new LongField(name, value, Field.Store.YES);
                break;
            default:
                throw new IllegalArgumentException("Invalid field type for long: " + type);
        }
        document.add(field);
        return this;
    }

    @Override
    public DocumentBuilder addDoubleField(String name, double value, org.apache.jackrabbit.oak.plugins.index.search.spi.DocumentBuilder.FieldType type) {
        if (name == null) {
            throw new IllegalArgumentException("Field name cannot be null");
        }

        // Lucene 4.7 uses DoubleField for numeric indexing
        Field field;
        switch (type) {
            case DOUBLE:
                // Indexed as numeric field
                field = new DoubleField(name, value, Field.Store.YES);
                break;
            case STORED_ONLY:
                // Just store the value
                field = new DoubleField(name, value, Field.Store.YES);
                break;
            default:
                throw new IllegalArgumentException("Invalid field type for double: " + type);
        }
        document.add(field);
        return this;
    }

    @Override
    public DocumentBuilder addBinaryField(String name, byte[] value) {
        if (name == null || value == null) {
            throw new IllegalArgumentException("Field name and value cannot be null");
        }

        // Lucene 4.7 uses StoredField for binary data
        Field field = new StoredField(name, value);
        document.add(field);
        return this;
    }

    @Override
    public Document build() {
        return new Lucene47Document(document);
    }
}
