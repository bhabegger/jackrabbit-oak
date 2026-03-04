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
 * Represents supported Lucene index versions.
 *
 * <p>This enum abstracts version detection logic, allowing
 * version-specific handling without exposing Lucene internals.</p>
 */
public enum IndexVersion {

    /**
     * Lucene 4.7.2 - Embedded in Oak (707 source files).
     * Legacy version requiring special handling for migration.
     */
    LUCENE_4_7_2(4, 7, 2, "4.7.2"),

    /**
     * Lucene 9.x - Modern version.
     * Primary target for migration.
     */
    LUCENE_9_X(9, 0, 0, "9.x");

    private final int major;
    private final int minor;
    private final int patch;
    private final String displayName;

    IndexVersion(int major, int minor, int patch, String displayName) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.displayName = displayName;
    }

    /**
     * Returns true if this is a legacy version (< 9.0).
     */
    public boolean isLegacy() {
        return major < 9;
    }

    /**
     * Returns true if this is a modern version (>= 9.0).
     */
    public boolean isModern() {
        return major >= 9;
    }

    /**
     * Returns true if this version is older than the other version.
     */
    public boolean olderThan(IndexVersion other) {
        if (this.major != other.major) {
            return this.major < other.major;
        }
        if (this.minor != other.minor) {
            return this.minor < other.minor;
        }
        return this.patch < other.patch;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
