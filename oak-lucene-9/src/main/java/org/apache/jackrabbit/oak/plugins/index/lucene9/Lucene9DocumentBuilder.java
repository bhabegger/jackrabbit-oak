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

import org.apache.jackrabbit.oak.plugins.index.search.spi.Document;
import org.apache.jackrabbit.oak.plugins.index.search.spi.DocumentBuilder;
import org.apache.lucene.document.*;

/**
 * Lucene 9.x implementation of DocumentBuilder.
 * Creates Lucene documents from Oak field specifications.
 */
public final class Lucene9DocumentBuilder implements DocumentBuilder {

    private final org.apache.lucene.document.Document document;

    public Lucene9DocumentBuilder() {
        this.document = new org.apache.lucene.document.Document();
    }

    @Override
    public DocumentBuilder addStringField(String name, String value, FieldType type) {
        if (name == null || value == null) {
            throw new IllegalArgumentException("Name and value cannot be null");
        }

        Field field;
        switch (type) {
            case STRING_ANALYZED:
                field = new TextField(name, value, Field.Store.NO);
                break;
            case STRING_NOT_ANALYZED:
                field = new StringField(name, value, Field.Store.NO);
                break;
            case TEXT:
                field = new TextField(name, value, Field.Store.YES);
                break;
            case STORED_ONLY:
                field = new StoredField(name, value);
                break;
            default:
                throw new IllegalArgumentException("Invalid field type for string: " + type);
        }

        document.add(field);
        return this;
    }

    @Override
    public DocumentBuilder addLongField(String name, long value, FieldType type) {
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null");
        }

        Field field;
        switch (type) {
            case LONG:
                // Lucene 9: Use LongPoint for indexing, StoredField for storage
                document.add(new LongPoint(name, value));
                field = new StoredField(name, value);
                break;
            case STORED_ONLY:
                field = new StoredField(name, value);
                break;
            default:
                throw new IllegalArgumentException("Invalid field type for long: " + type);
        }

        document.add(field);
        return this;
    }

    @Override
    public DocumentBuilder addDoubleField(String name, double value, FieldType type) {
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null");
        }

        Field field;
        switch (type) {
            case DOUBLE:
                // Lucene 9: Use DoublePoint for indexing, StoredField for storage
                document.add(new DoublePoint(name, value));
                field = new StoredField(name, value);
                break;
            case STORED_ONLY:
                field = new StoredField(name, value);
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
            throw new IllegalArgumentException("Name and value cannot be null");
        }

        document.add(new StoredField(name, value));
        return this;
    }

    @Override
    public Document build() {
        return new Lucene9Document(document);
    }
}
