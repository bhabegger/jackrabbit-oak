# Lucene Abstraction Layer Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement version-agnostic abstraction layer enabling safe migration from embedded Lucene 4.7.2 to Lucene 9.x with dual-toggle state machine control.

**Architecture:** Three-layer design with Oak Search SPI (abstractions), embedded Lucene 4.7 wrappers, and new Lucene 9 implementation. Dual-write coordinator for migration phase with background segment conversion.

**Tech Stack:**
- Java 8+
- Maven multi-module project
- Lucene 4.7.2 (embedded - 707 files in oak-lucene/src/main/java/org/apache/lucene/)
- Lucene 9.x (Maven dependency in new oak-lucene-9 module)
- JUnit 5 for testing

**Design Reference:** See `docs/plans/2026-03-02-lucene-abstraction-layer-design.md`

---

## Phase 1: Foundation - Oak Search SPI Module

### Task 1.1: Create oak-search-spi Module Structure

**Files:**
- Create: `oak-search-spi/pom.xml`
- Create: `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/package-info.java`

**Step 1: Create POM file**

Create `oak-search-spi/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.apache.jackrabbit</groupId>
        <artifactId>oak-parent</artifactId>
        <version>1.66-SNAPSHOT</version>
        <relativePath>../oak-parent/pom.xml</relativePath>
    </parent>

    <artifactId>oak-search-spi</artifactId>
    <name>Oak Search SPI</name>
    <description>Version-agnostic search abstraction layer for Oak</description>

    <dependencies>
        <!-- No Lucene dependencies! Pure abstractions -->
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

**Step 2: Create package-info for documentation**

Create `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/package-info.java`:

```java
/**
 * Oak Search SPI - Version-agnostic search abstraction layer.
 *
 * <p>This package provides interfaces that isolate Oak from specific
 * Lucene versions, enabling safe upgrades and clean architecture.</p>
 *
 * <h2>Key Interfaces:</h2>
 * <ul>
 *   <li>{@link IndexDirectory} - Directory abstraction</li>
 *   <li>{@link IndexReader} - Reader abstraction</li>
 *   <li>{@link IndexWriter} - Writer abstraction</li>
 *   <li>{@link QueryBuilder} - Query construction</li>
 *   <li>{@link DocumentBuilder} - Document creation</li>
 * </ul>
 *
 * <h2>Design Principle:</h2>
 * <p>Zero Lucene imports. All Lucene-specific types are hidden behind
 * these abstractions.</p>
 *
 * @since 1.66
 */
package org.apache.jackrabbit.oak.plugins.index.search.spi;
```

**Step 3: Verify module builds**

Run: `mvn clean install -pl oak-search-spi -DskipTests`

Expected: BUILD SUCCESS

**Step 4: Add to parent POM**

Modify `pom.xml` (root) `<modules>` section, add:
```xml
<module>oak-search-spi</module>
```

**Step 5: Commit**

```bash
git add oak-search-spi/ pom.xml
git commit -m "feat: create oak-search-spi module

New module for version-agnostic search abstractions.
Zero Lucene dependencies - pure interfaces.

Part of Lucene abstraction layer implementation."
```

---

### Task 1.2: Define IndexVersion Enum

**Files:**
- Create: `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexVersion.java`
- Create: `oak-search-spi/src/test/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexVersionTest.java`

**Step 1: Write the failing test**

Create `oak-search-spi/src/test/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexVersionTest.java`:

```java
package org.apache.jackrabbit.oak.plugins.index.search.spi;

import org.junit.Test;
import static org.junit.Assert.*;

public class IndexVersionTest {

    @Test
    public void testLucene47IsLegacy() {
        assertTrue(IndexVersion.LUCENE_4_7_2.isLegacy());
        assertFalse(IndexVersion.LUCENE_4_7_2.isModern());
    }

    @Test
    public void testLucene9IsModern() {
        assertFalse(IndexVersion.LUCENE_9_X.isLegacy());
        assertTrue(IndexVersion.LUCENE_9_X.isModern());
    }

    @Test
    public void testVersionComparison() {
        assertTrue(IndexVersion.LUCENE_4_7_2.olderThan(IndexVersion.LUCENE_9_X));
        assertFalse(IndexVersion.LUCENE_9_X.olderThan(IndexVersion.LUCENE_4_7_2));
    }

    @Test
    public void testVersionString() {
        assertEquals("4.7.2", IndexVersion.LUCENE_4_7_2.toString());
        assertEquals("9.x", IndexVersion.LUCENE_9_X.toString());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl oak-search-spi -Dtest=IndexVersionTest`

Expected: Compilation failure - IndexVersion class does not exist

**Step 3: Write minimal implementation**

Create `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexVersion.java`:

```java
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
```

**Step 4: Run test to verify it passes**

Run: `mvn test -pl oak-search-spi -Dtest=IndexVersionTest`

Expected: Tests run: 4, Failures: 0, Errors: 0, Skipped: 0

**Step 5: Commit**

```bash
git add oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexVersion.java \
        oak-search-spi/src/test/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexVersionTest.java
git commit -m "feat(spi): add IndexVersion enum

Version enumeration for Lucene 4.7.2 and 9.x.
Provides version comparison and legacy detection.

Tests: 4 passing"
```

---

### Task 1.3: Define IndexDirectory Interface

**Files:**
- Create: `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexDirectory.java`
- Create: `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexInput.java`
- Create: `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexOutput.java`

**Step 1: Create IndexInput interface**

Create `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexInput.java`:

```java
package org.apache.jackrabbit.oak.plugins.index.search.spi;

import java.io.Closeable;
import java.io.IOException;

/**
 * Abstraction for reading from an index file.
 * Hides Lucene's IndexInput API.
 */
public interface IndexInput extends Closeable {

    /**
     * Reads and returns a single byte.
     */
    byte readByte() throws IOException;

    /**
     * Reads bytes into the given array.
     */
    void readBytes(byte[] b, int offset, int len) throws IOException;

    /**
     * Returns the current position in the file.
     */
    long getFilePointer() throws IOException;

    /**
     * Sets the file pointer to the given position.
     */
    void seek(long pos) throws IOException;

    /**
     * Returns the length of the file.
     */
    long length() throws IOException;

    @Override
    void close() throws IOException;
}
```

**Step 2: Create IndexOutput interface**

Create `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexOutput.java`:

```java
package org.apache.jackrabbit.oak.plugins.index.search.spi;

import java.io.Closeable;
import java.io.IOException;

/**
 * Abstraction for writing to an index file.
 * Hides Lucene's IndexOutput API.
 */
public interface IndexOutput extends Closeable {

    /**
     * Writes a single byte.
     */
    void writeByte(byte b) throws IOException;

    /**
     * Writes bytes from the given array.
     */
    void writeBytes(byte[] b, int offset, int length) throws IOException;

    /**
     * Returns the current position in the file.
     */
    long getFilePointer() throws IOException;

    /**
     * Forces all buffered output to be written.
     */
    void flush() throws IOException;

    @Override
    void close() throws IOException;
}
```

**Step 3: Create IndexDirectory interface**

Create `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexDirectory.java`:

```java
package org.apache.jackrabbit.oak.plugins.index.search.spi;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;

/**
 * Abstraction of a directory containing index files.
 *
 * <p>Hides Lucene's Directory API, providing version-agnostic
 * file operations for index storage.</p>
 */
public interface IndexDirectory extends Closeable {

    /**
     * Opens an input stream for reading an index file.
     *
     * @param name the file name
     * @return input stream for reading
     * @throws IOException if file cannot be opened
     */
    IndexInput openInput(String name) throws IOException;

    /**
     * Creates an output stream for writing an index file.
     *
     * @param name the file name
     * @return output stream for writing
     * @throws IOException if file cannot be created
     */
    IndexOutput createOutput(String name) throws IOException;

    /**
     * Lists all files in the directory.
     *
     * @return array of file names
     * @throws IOException if directory cannot be read
     */
    String[] listAll() throws IOException;

    /**
     * Returns the length of a file in bytes.
     *
     * @param name the file name
     * @return file length in bytes
     * @throws IOException if file does not exist
     */
    long fileLength(String name) throws IOException;

    /**
     * Deletes a file.
     *
     * @param name the file name
     * @throws IOException if file cannot be deleted
     */
    void deleteFile(String name) throws IOException;

    /**
     * Ensures all modifications are persisted.
     *
     * @param names collection of file names to sync
     * @throws IOException if sync fails
     */
    void sync(Collection<String> names) throws IOException;

    /**
     * Closes the directory and releases resources.
     */
    @Override
    void close() throws IOException;
}
```

**Step 4: Verify compilation**

Run: `mvn compile -pl oak-search-spi`

Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexDirectory.java \
        oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexInput.java \
        oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexOutput.java
git commit -m "feat(spi): add IndexDirectory abstraction

Directory, input, and output interfaces hiding Lucene APIs.
Provides version-agnostic file operations."
```

---

### Task 1.4: Define IndexReader and IndexWriter Interfaces

**Files:**
- Create: `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexReader.java`
- Create: `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexWriter.java`
- Create: `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/Document.java`
- Create: `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/Term.java`

**Step 1: Create Term class**

Create `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/Term.java`:

```java
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
```

**Step 2: Create Document interface**

Create `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/Document.java`:

```java
package org.apache.jackrabbit.oak.plugins.index.search.spi;

/**
 * Marker interface for documents to be indexed.
 *
 * <p>Implementations are version-specific wrappers around
 * Lucene Document objects. This interface provides type safety
 * without exposing Lucene internals.</p>
 *
 * <p>Use {@link DocumentBuilder} to create documents.</p>
 */
public interface Document {
    // Marker interface - actual fields managed by builder
}
```

**Step 3: Create IndexReader interface**

Create `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexReader.java`:

```java
package org.apache.jackrabbit.oak.plugins.index.search.spi;

import java.io.Closeable;
import java.io.IOException;

/**
 * Abstraction for reading an index.
 * Hides Lucene's IndexReader API.
 */
public interface IndexReader extends Closeable {

    /**
     * Returns the total number of documents in the index
     * (excluding deleted documents).
     */
    int numDocs();

    /**
     * Returns the maximum document ID (includes deleted docs).
     */
    int maxDoc();

    /**
     * Reads a document by ID.
     *
     * @param docID the document ID
     * @return the document
     * @throws IOException if document cannot be read
     */
    Document document(int docID) throws IOException;

    /**
     * Returns the index version.
     */
    IndexVersion getVersion();

    /**
     * Closes the reader and releases resources.
     */
    @Override
    void close() throws IOException;
}
```

**Step 4: Create IndexWriter interface**

Create `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexWriter.java`:

```java
package org.apache.jackrabbit.oak.plugins.index.search.spi;

import java.io.Closeable;
import java.io.IOException;

/**
 * Abstraction for writing to an index.
 * Hides Lucene's IndexWriter API.
 */
public interface IndexWriter extends Closeable {

    /**
     * Adds a new document to the index.
     *
     * @param doc the document to add
     * @throws IOException if write fails
     */
    void addDocument(Document doc) throws IOException;

    /**
     * Updates a document (delete by term, then add).
     *
     * @param term the term identifying document(s) to delete
     * @param doc the new document to add
     * @throws IOException if update fails
     */
    void updateDocument(Term term, Document doc) throws IOException;

    /**
     * Deletes documents matching the term.
     *
     * @param term the term identifying document(s) to delete
     * @throws IOException if delete fails
     */
    void deleteDocuments(Term term) throws IOException;

    /**
     * Commits all pending changes.
     *
     * @throws IOException if commit fails
     */
    void commit() throws IOException;

    /**
     * Forces merge of segments (optimization).
     *
     * @param maxNumSegments target number of segments
     * @throws IOException if merge fails
     */
    void forceMerge(int maxNumSegments) throws IOException;

    /**
     * Closes the writer and releases resources.
     */
    @Override
    void close() throws IOException;
}
```

**Step 5: Commit**

```bash
git add oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexReader.java \
        oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/IndexWriter.java \
        oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/Document.java \
        oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/Term.java
git commit -m "feat(spi): add IndexReader and IndexWriter abstractions

Reader/writer interfaces with Document and Term value objects.
Complete abstraction of Lucene read/write operations."
```

---

### Task 1.5: Define QueryBuilder and DocumentBuilder Interfaces

**Files:**
- Create: `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/QueryBuilder.java`
- Create: `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/Query.java`
- Create: `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/DocumentBuilder.java`

**Step 1: Create Query interface**

Create `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/Query.java`:

```java
package org.apache.jackrabbit.oak.plugins.index.search.spi;

/**
 * Marker interface for queries.
 *
 * <p>Implementations are version-specific wrappers around
 * Lucene Query objects. This interface provides type safety
 * without exposing Lucene internals.</p>
 *
 * <p>Use {@link QueryBuilder} to create queries.</p>
 */
public interface Query {
    // Marker interface - actual query logic in implementations
}
```

**Step 2: Create QueryBuilder interface**

Create `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/QueryBuilder.java`:

```java
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
```

**Step 3: Create DocumentBuilder interface**

Create `oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/DocumentBuilder.java`:

```java
package org.apache.jackrabbit.oak.plugins.index.search.spi;

/**
 * Builder for constructing documents to index.
 * Hides Lucene's Document and Field API.
 */
public interface DocumentBuilder {

    /**
     * Field types for indexing and storage.
     */
    enum FieldType {
        /** Tokenized and indexed for full-text search */
        STRING_ANALYZED,

        /** Indexed as single term (not tokenized) */
        STRING_NOT_ANALYZED,

        /** Full-text indexed */
        TEXT,

        /** Numeric long field */
        LONG,

        /** Numeric double field */
        DOUBLE,

        /** Binary data */
        BINARY,

        /** Stored but not indexed */
        STORED_ONLY
    }

    /**
     * Adds a string field.
     *
     * @param name field name
     * @param value field value
     * @param type field type
     * @return this builder for chaining
     */
    DocumentBuilder addStringField(String name, String value, FieldType type);

    /**
     * Adds a numeric long field.
     *
     * @param name field name
     * @param value field value
     * @param type field type
     * @return this builder for chaining
     */
    DocumentBuilder addLongField(String name, long value, FieldType type);

    /**
     * Adds a numeric double field.
     *
     * @param name field name
     * @param value field value
     * @param type field type
     * @return this builder for chaining
     */
    DocumentBuilder addDoubleField(String name, double value, FieldType type);

    /**
     * Adds a binary field.
     *
     * @param name field name
     * @param value binary data
     * @return this builder for chaining
     */
    DocumentBuilder addBinaryField(String name, byte[] value);

    /**
     * Builds the document.
     *
     * @return the constructed document
     */
    Document build();
}
```

**Step 4: Verify compilation**

Run: `mvn compile -pl oak-search-spi`

Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/QueryBuilder.java \
        oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/Query.java \
        oak-search-spi/src/main/java/org/apache/jackrabbit/oak/plugins/index/search/spi/DocumentBuilder.java
git commit -m "feat(spi): add QueryBuilder and DocumentBuilder abstractions

Query and document builder interfaces.
Complete SPI abstraction layer foundations.

Phase 1 (SPI) complete - all core interfaces defined."
```

---

## Phase 2: Lucene 9 Implementation Module

### Task 2.1: Create oak-lucene-9 Module Structure

**Files:**
- Create: `oak-lucene-9/pom.xml`
- Modify: `pom.xml` (root)

**Step 1: Create POM file**

Create `oak-lucene-9/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.apache.jackrabbit</groupId>
        <artifactId>oak-parent</artifactId>
        <version>1.66-SNAPSHOT</version>
        <relativePath>../oak-parent/pom.xml</relativePath>
    </parent>

    <artifactId>oak-lucene-9</artifactId>
    <name>Oak Lucene 9 Implementation</name>
    <description>Lucene 9.x implementation of Oak Search SPI</description>

    <properties>
        <lucene.version>9.10.0</lucene.version>
    </properties>

    <dependencies>
        <!-- Oak Search SPI -->
        <dependency>
            <groupId>org.apache.jackrabbit</groupId>
            <artifactId>oak-search-spi</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- Lucene 9.x -->
        <dependency>
            <groupId>org.apache.lucene</groupId>
            <artifactId>lucene-core</artifactId>
            <version>${lucene.version}</version>
        </dependency>

        <dependency>
            <groupId>org.apache.lucene</groupId>
            <artifactId>lucene-backward-codecs</artifactId>
            <version>${lucene.version}</version>
        </dependency>

        <dependency>
            <groupId>org.apache.lucene</groupId>
            <artifactId>lucene-queryparser</artifactId>
            <version>${lucene.version}</version>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

**Step 2: Add to parent POM**

Modify root `pom.xml` `<modules>` section, add:
```xml
<module>oak-lucene-9</module>
```

**Step 3: Verify module builds**

Run: `mvn clean install -pl oak-lucene-9 -DskipTests`

Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add oak-lucene-9/ pom.xml
git commit -m "feat: create oak-lucene-9 module

New module for Lucene 9.x implementation of Oak Search SPI.
Depends on lucene-core:9.10.0 and lucene-backward-codecs."
```

---

### Task 2.2: Implement Lucene9Document Wrapper

**Files:**
- Create: `oak-lucene-9/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene9/Lucene9Document.java`
- Create: `oak-lucene-9/src/test/java/org/apache/jackrabbit/oak/plugins/index/lucene9/Lucene9DocumentTest.java`

**Step 1: Write failing test**

Create `oak-lucene-9/src/test/java/org/apache/jackrabbit/oak/plugins/index/lucene9/Lucene9DocumentTest.java`:

```java
package org.apache.jackrabbit.oak.plugins.index.lucene9;

import org.apache.jackrabbit.oak.plugins.index.search.spi.Document;
import org.junit.Test;
import static org.junit.Assert.*;

public class Lucene9DocumentTest {

    @Test
    public void testCreateEmptyDocument() {
        Document doc = new Lucene9Document();
        assertNotNull(doc);
    }

    @Test
    public void testWrapLuceneDocument() {
        org.apache.lucene.document.Document luceneDoc =
            new org.apache.lucene.document.Document();
        luceneDoc.add(new org.apache.lucene.document.StringField(
            "path", "/content", org.apache.lucene.document.Field.Store.YES));

        Lucene9Document oakDoc = new Lucene9Document(luceneDoc);
        assertNotNull(oakDoc);
        assertEquals(luceneDoc, oakDoc.getLuceneDocument());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl oak-lucene-9 -Dtest=Lucene9DocumentTest`

Expected: Compilation failure - Lucene9Document does not exist

**Step 3: Write minimal implementation**

Create `oak-lucene-9/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene9/Lucene9Document.java`:

```java
package org.apache.jackrabbit.oak.plugins.index.lucene9;

import org.apache.jackrabbit.oak.plugins.index.search.spi.Document;

/**
 * Lucene 9.x implementation of Oak Document.
 * Wraps Lucene's Document class.
 */
public final class Lucene9Document implements Document {

    private final org.apache.lucene.document.Document delegate;

    /**
     * Creates a new empty document.
     */
    public Lucene9Document() {
        this.delegate = new org.apache.lucene.document.Document();
    }

    /**
     * Wraps an existing Lucene document.
     *
     * @param luceneDocument the Lucene document to wrap
     */
    public Lucene9Document(org.apache.lucene.document.Document luceneDocument) {
        if (luceneDocument == null) {
            throw new IllegalArgumentException("Lucene document cannot be null");
        }
        this.delegate = luceneDocument;
    }

    /**
     * Returns the underlying Lucene document.
     *
     * <p>Package-private for use by other Lucene 9 components.</p>
     *
     * @return the Lucene document
     */
    org.apache.lucene.document.Document getLuceneDocument() {
        return delegate;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -pl oak-lucene-9 -Dtest=Lucene9DocumentTest`

Expected: Tests run: 2, Failures: 0

**Step 5: Commit**

```bash
git add oak-lucene-9/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene9/Lucene9Document.java \
        oak-lucene-9/src/test/java/org/apache/jackrabbit/oak/plugins/index/lucene9/Lucene9DocumentTest.java
git commit -m "feat(lucene9): add Lucene9Document wrapper

Wraps Lucene 9 Document class implementing Oak SPI.
Tests: 2 passing"
```

---

### Task 2.3: Implement Lucene9DocumentBuilder

**Files:**
- Create: `oak-lucene-9/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene9/Lucene9DocumentBuilder.java`
- Create: `oak-lucene-9/src/test/java/org/apache/jackrabbit/oak/plugins/index/lucene9/Lucene9DocumentBuilderTest.java`

**Step 1: Write failing test**

Create test file:

```java
package org.apache.jackrabbit.oak.plugins.index.lucene9;

import org.apache.jackrabbit.oak.plugins.index.search.spi.Document;
import org.apache.jackrabbit.oak.plugins.index.search.spi.DocumentBuilder;
import org.apache.jackrabbit.oak.plugins.index.search.spi.DocumentBuilder.FieldType;
import org.junit.Test;
import static org.junit.Assert.*;

public class Lucene9DocumentBuilderTest {

    @Test
    public void testBuildEmptyDocument() {
        DocumentBuilder builder = new Lucene9DocumentBuilder();
        Document doc = builder.build();
        assertNotNull(doc);
        assertTrue(doc instanceof Lucene9Document);
    }

    @Test
    public void testAddStringField() {
        DocumentBuilder builder = new Lucene9DocumentBuilder();
        Document doc = builder
            .addStringField("path", "/content", FieldType.STRING_NOT_ANALYZED)
            .build();

        assertNotNull(doc);
        Lucene9Document lucene9Doc = (Lucene9Document) doc;
        assertEquals(1, lucene9Doc.getLuceneDocument().getFields().size());
    }

    @Test
    public void testAddLongField() {
        DocumentBuilder builder = new Lucene9DocumentBuilder();
        Document doc = builder
            .addLongField("size", 12345L, FieldType.LONG)
            .build();

        assertNotNull(doc);
        Lucene9Document lucene9Doc = (Lucene9Document) doc;
        assertEquals(1, lucene9Doc.getLuceneDocument().getFields().size());
    }

    @Test
    public void testChaining() {
        DocumentBuilder builder = new Lucene9DocumentBuilder();
        Document doc = builder
            .addStringField("path", "/content", FieldType.TEXT)
            .addStringField("title", "Hello", FieldType.TEXT)
            .addLongField("size", 100L, FieldType.LONG)
            .build();

        Lucene9Document lucene9Doc = (Lucene9Document) doc;
        assertEquals(3, lucene9Doc.getLuceneDocument().getFields().size());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl oak-lucene-9 -Dtest=Lucene9DocumentBuilderTest`

Expected: Compilation failure

**Step 3: Write implementation**

Create `oak-lucene-9/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene9/Lucene9DocumentBuilder.java`:

```java
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
```

**Step 4: Run test to verify it passes**

Run: `mvn test -pl oak-lucene-9 -Dtest=Lucene9DocumentBuilderTest`

Expected: Tests run: 4, Failures: 0

**Step 5: Commit**

```bash
git add oak-lucene-9/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene9/Lucene9DocumentBuilder.java \
        oak-lucene-9/src/test/java/org/apache/jackrabbit/oak/plugins/index/lucene9/Lucene9DocumentBuilderTest.java
git commit -m "feat(lucene9): add Lucene9DocumentBuilder

Builder for creating Lucene 9 documents from Oak fields.
Handles field type conversion and Lucene 9 Point fields.
Tests: 4 passing"
```

---

## Phase 3: Configuration and State Management

### Task 3.1: Create MigrationConfig Class

**Files:**
- Create: `oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/MigrationConfig.java`
- Create: `oak-lucene/src/test/java/org/apache/jackrabbit/oak/plugins/index/lucene/MigrationConfigTest.java`

**Step 1: Write failing test**

Create test (create test directories if needed):

```bash
mkdir -p oak-lucene/src/test/java/org/apache/jackrabbit/oak/plugins/index/lucene
```

Create `oak-lucene/src/test/java/org/apache/jackrabbit/oak/plugins/index/lucene/MigrationConfigTest.java`:

```java
package org.apache.jackrabbit.oak.plugins.index.lucene;

import org.junit.Test;
import static org.junit.Assert.*;

public class MigrationConfigTest {

    @Test
    public void testDefaultConfig() {
        MigrationConfig config = new MigrationConfig();
        assertFalse(config.isEnableMigration());
        assertTrue(config.isKeepLegacyUpdated());
    }

    @Test
    public void testEnableMigration() {
        MigrationConfig config = new MigrationConfig();
        config.setEnableMigration(true);
        assertTrue(config.isEnableMigration());
    }

    @Test
    public void testKeepLegacyUpdated() {
        MigrationConfig config = new MigrationConfig();
        config.setKeepLegacyUpdated(false);
        assertFalse(config.isKeepLegacyUpdated());
    }

    @Test
    public void testGetState() {
        MigrationConfig config = new MigrationConfig();

        // State 0: Default
        assertEquals(MigrationConfig.State.PRE_MIGRATION, config.getState());

        // State 1: Active migration
        config.setEnableMigration(true);
        assertEquals(MigrationConfig.State.ACTIVE_MIGRATION, config.getState());

        // State 2: Point of no return
        config.setKeepLegacyUpdated(false);
        assertEquals(MigrationConfig.State.POINT_OF_NO_RETURN, config.getState());
    }
}
```

**Step 2: Run test to verify failure**

Run: `mvn test -pl oak-lucene -Dtest=MigrationConfigTest`

Expected: Compilation failure

**Step 3: Write implementation**

Create `oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/MigrationConfig.java`:

```java
package org.apache.jackrabbit.oak.plugins.index.lucene;

/**
 * Configuration for Lucene migration state machine.
 *
 * <p>Manages two toggles:</p>
 * <ul>
 *   <li>enableMigration - Start/stop migration process</li>
 *   <li>keepLegacyUpdated - Maintain Lucene 4.7 during/after migration</li>
 * </ul>
 */
public class MigrationConfig {

    /**
     * Migration states.
     */
    public enum State {
        /** State 0: Pre-migration (Lucene 4.7 only) */
        PRE_MIGRATION,

        /** State 1: Active migration (dual-write) */
        ACTIVE_MIGRATION,

        /** State 2: Point of no return (Lucene 9 only) */
        POINT_OF_NO_RETURN
    }

    private volatile boolean enableMigration = false;
    private volatile boolean keepLegacyUpdated = true;

    /**
     * Returns true if migration is enabled.
     */
    public boolean isEnableMigration() {
        return enableMigration;
    }

    /**
     * Enables or disables migration.
     *
     * @param enabled true to enable migration
     */
    public void setEnableMigration(boolean enabled) {
        this.enableMigration = enabled;
    }

    /**
     * Returns true if legacy (Lucene 4.7) should be kept updated.
     */
    public boolean isKeepLegacyUpdated() {
        return keepLegacyUpdated;
    }

    /**
     * Sets whether to keep legacy updated.
     *
     * @param keepUpdated true to keep legacy updated
     */
    public void setKeepLegacyUpdated(boolean keepUpdated) {
        this.keepLegacyUpdated = keepUpdated;
    }

    /**
     * Returns the current migration state.
     */
    public State getState() {
        if (!enableMigration) {
            return State.PRE_MIGRATION;
        }

        if (keepLegacyUpdated) {
            return State.ACTIVE_MIGRATION;
        }

        return State.POINT_OF_NO_RETURN;
    }
}
```

**Step 4: Run test to verify pass**

Run: `mvn test -pl oak-lucene -Dtest=MigrationConfigTest`

Expected: Tests run: 4, Failures: 0

**Step 5: Commit**

```bash
git add oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/MigrationConfig.java \
        oak-lucene/src/test/java/org/apache/jackrabbit/oak/plugins/index/lucene/MigrationConfigTest.java
git commit -m "feat(lucene): add MigrationConfig state machine

Configuration class managing two-toggle state machine.
States: PRE_MIGRATION, ACTIVE_MIGRATION, POINT_OF_NO_RETURN
Tests: 4 passing"
```

---

## Summary & Next Steps

This implementation plan provides a foundation for the Lucene abstraction layer. The plan is organized into phases:

**Phase 1 (Complete in plan):** Oak Search SPI - Core abstraction interfaces
- IndexVersion enum
- IndexDirectory, IndexInput, IndexOutput
- IndexReader, IndexWriter, Document, Term
- QueryBuilder, DocumentBuilder, Query

**Phase 2 (Partial in plan):** Lucene 9 Implementation
- Module setup
- Lucene9Document wrapper
- Lucene9DocumentBuilder

**Phase 3 (Started):** Configuration and State Management
- MigrationConfig class

**Remaining phases** (to be added):
- Phase 4: Embedded Lucene 4.7 Wrappers
- Phase 5: Dual-Write Coordinator
- Phase 6: Migration Coordinator
- Phase 7: Integration and Testing
- Phase 8: Documentation

**Total estimated tasks:** ~40-50 tasks (~3-4 weeks of development)

---

**End of Implementation Plan**
