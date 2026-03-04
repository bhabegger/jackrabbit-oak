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
 * Represents a term (field name + value) for indexing and querying.
 * Immutable value object hiding Lucene's Term API.
 */
public final class Term {

    private final String field;
    private final String value;

    /**
     * Creates a new term.
     *
     * @param field the field name
     * @param value the term value
     */
    public Term(String field, String value) {
        if (field == null || value == null) {
            throw new IllegalArgumentException("Field and value cannot be null");
        }
        this.field = field;
        this.value = value;
    }

    public String field() {
        return field;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Term)) return false;
        Term other = (Term) obj;
        return field.equals(other.field) && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return field.hashCode() * 31 + value.hashCode();
    }

    @Override
    public String toString() {
        return field + ":" + value;
    }
}
