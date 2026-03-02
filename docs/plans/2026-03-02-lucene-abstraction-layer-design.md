# Lucene Abstraction Layer Design

**Date:** 2026-03-02
**Status:** Draft
**Author:** Design Session

---

## Executive Summary

This document describes the design of a **Lucene Abstraction Layer** for Apache Jackrabbit Oak that enables:

1. **Safe migration** from Lucene 4.7.2 → 9.x without breaking production indices
2. **Future-proof architecture** making future upgrades (9→10→11...) straightforward
3. **Dual-toggle state machine** providing maximum control and rollback capability

### Key Design Decisions

- **Two-phase migration** with dual-write capability
- **Two independent toggles** for fine-grained control
- **Clean abstraction** isolating Oak from Lucene API changes
- **No intermediate versions** (no Lucene 5, 6, 7, 8 needed)

---

## Problem Statement

### Current Situation

Oak is tightly coupled to Lucene 4.7.2 (released 2013):
- Direct Lucene imports throughout Oak codebase
- Lucene types exposed in public APIs
- Upgrading Lucene requires changes across ~30 files
- Production indices in Lucene 4.7 format

### Challenges

1. **Lucene 9 cannot read Lucene 4.7 segments** - 5-version gap in backward compatibility
2. **Breaking production indices is unacceptable** - Must maintain read access to existing data
3. **Future upgrades will face similar issues** - Need sustainable upgrade path

### Goals

**Tactical (Immediate):**
- Migrate from Lucene 4.7.2 → 9.x safely
- No production downtime
- Preserve all existing indices
- Rollback capability during migration

**Strategic (Long-term):**
- Make future Lucene upgrades (9→10→11) straightforward
- Isolate Oak from Lucene API changes
- Enable comprehensive testing before production deployment
- Reduce maintenance burden

---

## Architecture Overview

### High-Level Design

```
┌─────────────────────────────────────────────────────┐
│              Oak Core                               │
│         (Business Logic)                            │
└──────────────────┬──────────────────────────────────┘
                   │
                   │ Uses abstractions only
                   ▼
┌─────────────────────────────────────────────────────┐
│         Oak Search SPI                              │
│    (Version-agnostic interfaces)                    │
│                                                     │
│  - IndexDirectory                                   │
│  - IndexReader / IndexWriter                        │
│  - QueryBuilder                                     │
│  - DocumentBuilder                                  │
│  - AnalyzerFactory                                  │
└──────────────────┬──────────────────────────────────┘
                   │
                   │ Implementations
         ┌─────────┴─────────┬──────────────┐
         ▼                   ▼              ▼
┌─────────────────┐  ┌──────────────┐  ┌──────────────┐
│ Lucene 4 Impl   │  │ Lucene 9     │  │ Lucene 10+   │
│ (TEMPORARY)     │  │ Impl         │  │ (Future)     │
│                 │  │ (PRIMARY)    │  │              │
│ Native 4.7.2    │  │ Native 9.x   │  │              │
│ Read-only after │  │ Read/Write   │  │              │
│ migration       │  │              │  │              │
└─────────────────┘  └──────────────┘  └──────────────┘
```

### Module Structure

```
oak-search-spi/
├── src/main/java/
│   └── org.apache.jackrabbit.oak.plugins.index.search.spi/
│       ├── IndexDirectory.java          (Directory abstraction)
│       ├── IndexReader.java             (Reader abstraction)
│       ├── IndexWriter.java             (Writer abstraction)
│       ├── QueryBuilder.java            (Query construction)
│       ├── DocumentBuilder.java         (Document creation)
│       ├── AnalyzerFactory.java         (Analyzer creation)
│       └── IndexVersion.java            (Version detection)

oak-lucene-4/                            [TEMPORARY - Delete after migration]
├── src/main/java/
│   └── org.apache.jackrabbit.oak.plugins.index.lucene4/
│       ├── Lucene4IndexDirectory.java
│       ├── Lucene4IndexReader.java
│       ├── Lucene4IndexWriter.java
│       └── Lucene4QueryBuilder.java
└── pom.xml                              (Depends on Lucene 4.7.2)

oak-lucene-9/                            [PERMANENT]
├── src/main/java/
│   └── org.apache.jackrabbit.oak.plugins.index.lucene9/
│       ├── Lucene9IndexDirectory.java
│       ├── Lucene9IndexReader.java
│       ├── Lucene9IndexWriter.java
│       └── Lucene9QueryBuilder.java
└── pom.xml                              (Depends on Lucene 9.x)

oak-lucene-core/                         [PERMANENT]
├── src/main/java/
│   └── org.apache.jackrabbit.oak.plugins.index.lucene/
│       ├── LuceneIndexManager.java      (Routing, version detection)
│       ├── DualIndexWriter.java         (Dual-write coordinator)
│       ├── MigrationCoordinator.java    (Background converter)
│       └── SegmentVersionDetector.java  (Read segment metadata)
```

---

## Two-Toggle State Machine

### Configuration Properties

```properties
# Toggle 1: Enable/disable migration
oak.lucene.enableMigration = false      # Default: OFF

# Toggle 2: Keep legacy (4.7) updated during/after migration
oak.lucene.keepLegacyUpdated = true     # Default: ON
```

### State Diagram

```
                  ┌──────────────────────────────────────┐
                  │ STATE 0: Pre-Migration               │
                  │ enableMigration = false              │
                  │ keepLegacyUpdated = true             │
                  │                                      │
                  │ Reads:  Lucene 4.7                   │
                  │ Writes: Lucene 4.7 only              │
                  │ JARs:   lucene-4.7.2                 │
                  └──────────────┬───────────────────────┘
                                 │
                                 │ enableMigration → true
                                 ▼
                  ┌──────────────────────────────────────┐
                  │ STATE 1: Active Migration            │
                  │ enableMigration = true               │
                  │ keepLegacyUpdated = true             │
                  │                                      │
                  │ Reads:  4.7 → 9 (after P2)           │
                  │ Writes: DUAL WRITE (4.7 + 9)         │
                  │ JARs:   lucene-4.7.2 + lucene-9.x    │
                  │ Background: Converting 4.7 → 9       │
                  └──┬────────────────────┬──────────────┘
                     │                    │
      enableMigration│                    │ keepLegacyUpdated
      → false        │                    │ → false
                     ▼                    ▼
      ┌─────────────────────┐  ┌──────────────────────────┐
      │ Back to STATE 0     │  │ STATE 2: Point of Return │
      │ (Rollback OK)       │  │ enableMigration = true   │
      │                     │  │ keepLegacyUpdated = false│
      │ Stop conversion     │  │                          │
      │ Stop dual writes    │  │ Reads:  Lucene 9         │
      │ Resume 4.7 only     │  │ Writes: Lucene 9 only    │
      └─────────────────────┘  │ JARs:   lucene-9.x       │
                                │ Background: Cleanup 4.7  │
                                └───────────┬──────────────┘
                                            │
                                            │ enableMigration
                                            │ → false
                                            ▼
                                ┌────────────────────────────┐
                                │ ❌ VALIDATION ERROR        │
                                │                            │
                                │ Cannot disable migration   │
                                │ when keepLegacyUpdated is  │
                                │ false. Legacy segments     │
                                │ deleted. IRREVERSIBLE.     │
                                └────────────────────────────┘
```

### State Transition Rules

| Current State | Toggle Change | New State | Reversible? |
|--------------|---------------|-----------|-------------|
| **0** → | `enableMigration: false→true` | **1** | ✅ Yes |
| **1** → | `enableMigration: true→false` | **0** | ✅ Yes (if keepLegacy=true) |
| **1** → | `keepLegacyUpdated: true→false` | **2** | ❌ No (IRREVERSIBLE) |
| **2** → | `enableMigration: true→false` | ❌ FAIL | ❌ Blocked by validation |

---

## Detailed State Behaviors

### STATE 0: Pre-Migration (Default)

**Configuration:**
```properties
oak.lucene.enableMigration = false
oak.lucene.keepLegacyUpdated = true
```

**Runtime Behavior:**
```java
// Segment format
segmentVersion = LUCENE_4_7_2;

// Read path
IndexReader reader = new Lucene4IndexReader(indexPath);

// Write path
IndexWriter writer = new Lucene4IndexWriter(indexPath);

// Background tasks
backgroundConverter = null; // Not running

// Classpath
// Only lucene-core-4.7.2.jar loaded
```

**Characteristics:**
- ✅ Stable production state
- ✅ No migration overhead
- ✅ All indices in Lucene 4.7 format

---

### STATE 1: Active Migration (Dual Write)

**Configuration:**
```properties
oak.lucene.enableMigration = true
oak.lucene.keepLegacyUpdated = true
```

**Runtime Behavior:**
```java
// Segment formats (mixed)
segmentVersions = [LUCENE_4_7_2, LUCENE_9_X];

// Read path (phased)
IndexReader reader;
if (conversionComplete()) {
    // After P2: All segments converted
    reader = new Lucene9IndexReader(indexPath);
} else {
    // Before P2: Still converting
    reader = new Lucene4IndexReader(indexPath);
}

// Write path (DUAL WRITE)
IndexWriter writer = new DualIndexWriter(
    new Lucene4IndexWriter(indexPath),  // Keep 4.7 updated
    new Lucene9IndexWriter(indexPath)   // Write to 9 simultaneously
);

// Background tasks
MigrationCoordinator coordinator = new MigrationCoordinator();
coordinator.startConversion(); // Converts 4.7 → 9 segments

// Classpath
// Both lucene-core-4.7.2.jar and lucene-core-9.x.jar loaded
```

**Key Points:**
- **Can stay in this state indefinitely** - Build confidence over weeks/months
- **Rollback possible** - Toggle `enableMigration → false` to return to STATE 0
- **Two phases within STATE 1:**
  - **P1 (Start)**: Background conversion begins
  - **P2 (Conversion complete)**: All old segments converted, switch reads to Lucene 9

**Conversion Complete Detection:**
```java
public boolean conversionComplete() {
    // All pre-toggle segments converted to Lucene 9
    long unconvertedSegments = segmentTracker.countSegments(LUCENE_4_7_2);
    return unconvertedSegments == 0;
}
```

---

### STATE 2: Point of No Return (Pure Lucene 9)

**Configuration:**
```properties
oak.lucene.enableMigration = true
oak.lucene.keepLegacyUpdated = false  # ⚠️ IRREVERSIBLE
```

**Runtime Behavior:**
```java
// Segment format (single)
segmentVersion = LUCENE_9_X;

// Read path
IndexReader reader = new Lucene9IndexReader(indexPath);

// Write path (single)
IndexWriter writer = new Lucene9IndexWriter(indexPath);

// Background tasks
backgroundConverter.cleanupLegacySegments(); // Delete 4.7 segments

// Classpath
// Only lucene-core-9.x.jar needed (4.7 can be removed after cleanup)
```

**Characteristics:**
- ❌ **IRREVERSIBLE** - Cannot go back to STATE 0 or STATE 1
- ✅ Clean state - single Lucene version
- ✅ No dual-write overhead
- ✅ Better performance (single write path)

**Why Irreversible:**
```java
// After setting keepLegacyUpdated=false:
// 1. Lucene 4.7 segments are deleted
// 2. New writes go only to Lucene 9
// 3. Lucene 4.7 cannot read Lucene 9 segments
// → Cannot rollback!
```

---

## Core Components

### 1. Oak Search SPI (Abstraction Layer)

**Purpose:** Version-agnostic interfaces isolating Oak from Lucene specifics.

#### IndexDirectory Interface

```java
package org.apache.jackrabbit.oak.plugins.index.search.spi;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;

/**
 * Abstraction of a directory containing index files.
 * Hides Lucene's Directory API.
 */
public interface IndexDirectory extends Closeable {

    /**
     * Opens an input stream for reading an index file.
     */
    IndexInput openInput(String name) throws IOException;

    /**
     * Creates an output stream for writing an index file.
     */
    IndexOutput createOutput(String name) throws IOException;

    /**
     * Lists all files in the directory.
     */
    String[] listAll() throws IOException;

    /**
     * Returns the length of a file in bytes.
     */
    long fileLength(String name) throws IOException;

    /**
     * Deletes a file.
     */
    void deleteFile(String name) throws IOException;

    /**
     * Ensures all modifications are persisted.
     */
    void sync(Collection<String> names) throws IOException;

    /**
     * Closes the directory.
     */
    @Override
    void close() throws IOException;
}
```

#### IndexReader Interface

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
     * Returns the total number of documents in the index.
     */
    int numDocs();

    /**
     * Returns the maximum document ID (includes deleted docs).
     */
    int maxDoc();

    /**
     * Reads a document by ID.
     */
    Document document(int docID) throws IOException;

    /**
     * Creates a searcher for querying this index.
     */
    IndexSearcher createSearcher();

    /**
     * Returns index version information.
     */
    IndexVersion getVersion();

    /**
     * Closes the reader.
     */
    @Override
    void close() throws IOException;
}
```

#### IndexWriter Interface

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
     */
    void addDocument(Document doc) throws IOException;

    /**
     * Updates a document (delete by term, then add).
     */
    void updateDocument(Term term, Document doc) throws IOException;

    /**
     * Deletes documents matching the term.
     */
    void deleteDocuments(Term term) throws IOException;

    /**
     * Commits all pending changes.
     */
    void commit() throws IOException;

    /**
     * Forces merge of segments (optimization).
     */
    void forceMerge(int maxNumSegments) throws IOException;

    /**
     * Closes the writer and releases resources.
     */
    @Override
    void close() throws IOException;
}
```

#### QueryBuilder Interface

```java
package org.apache.jackrabbit.oak.plugins.index.search.spi;

/**
 * Builder for constructing queries.
 * Hides Lucene's Query API and version-specific quirks.
 */
public interface QueryBuilder {

    /**
     * Creates a term query (exact match).
     */
    Query term(String field, String value);

    /**
     * Creates a range query.
     * Handles version-specific edge cases (e.g., empty string in Lucene 5+).
     */
    Query range(String field,
                String lowerTerm,
                String upperTerm,
                boolean includeLower,
                boolean includeUpper);

    /**
     * Creates a boolean query (AND/OR/NOT combinations).
     */
    Query bool(BooleanClause... clauses);

    /**
     * Creates a wildcard query.
     */
    Query wildcard(String field, String pattern);

    /**
     * Creates a fuzzy query.
     */
    Query fuzzy(String field, String value, int maxEdits);

    /**
     * Creates a phrase query.
     */
    Query phrase(String field, String... terms);

    /**
     * Creates a prefix query.
     */
    Query prefix(String field, String prefix);
}
```

#### DocumentBuilder Interface

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
        STRING_ANALYZED,      // Tokenized and indexed
        STRING_NOT_ANALYZED,  // Indexed as single term
        TEXT,                 // Full-text indexed
        LONG,                 // Numeric field
        DOUBLE,               // Numeric field
        BINARY,               // Binary data
        STORED_ONLY          // Not indexed, only stored
    }

    /**
     * Adds a string field.
     */
    DocumentBuilder addStringField(String name, String value, FieldType type);

    /**
     * Adds a numeric field (long).
     */
    DocumentBuilder addLongField(String name, long value, FieldType type);

    /**
     * Adds a numeric field (double).
     */
    DocumentBuilder addDoubleField(String name, double value, FieldType type);

    /**
     * Adds a binary field.
     */
    DocumentBuilder addBinaryField(String name, byte[] value);

    /**
     * Builds the document.
     */
    Document build();
}
```

---

### 2. Version Detection

```java
package org.apache.jackrabbit.oak.plugins.index.lucene;

import java.nio.file.Path;
import java.io.IOException;

/**
 * Detects the Lucene version of index segments.
 */
public class SegmentVersionDetector {

    /**
     * Reads the segments_N file and determines Lucene version.
     */
    public static IndexVersion detectVersion(Path indexPath) throws IOException {
        // Read segments_N file
        // Parse codec name and version
        // Return appropriate IndexVersion

        SegmentInfos infos = SegmentInfos.readLatestCommit(indexPath);
        String codecName = infos.getSegmentsFileName();

        if (codecName.contains("Lucene4")) {
            return IndexVersion.LUCENE_4_7_2;
        } else if (codecName.contains("Lucene9")) {
            return IndexVersion.LUCENE_9_X;
        }

        throw new IOException("Unknown index version: " + codecName);
    }
}

/**
 * Enum representing supported Lucene versions.
 */
public enum IndexVersion {
    LUCENE_4_7_2(4, 7, 2),
    LUCENE_9_X(9, 0, 0);

    private final int major;
    private final int minor;
    private final int patch;

    IndexVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public boolean isLegacy() {
        return this == LUCENE_4_7_2;
    }

    public boolean isModern() {
        return this.major >= 9;
    }
}
```

---

### 3. Dual Index Writer

```java
package org.apache.jackrabbit.oak.plugins.index.lucene;

import org.apache.jackrabbit.oak.plugins.index.search.spi.*;
import java.io.IOException;

/**
 * Writer that writes to both Lucene 4 and Lucene 9 simultaneously.
 * Used during STATE 1 (Active Migration).
 */
public class DualIndexWriter implements IndexWriter {

    private final IndexWriter lucene4Writer;
    private final IndexWriter lucene9Writer;
    private final boolean keepLegacyUpdated;

    public DualIndexWriter(
        IndexWriter lucene4Writer,
        IndexWriter lucene9Writer,
        boolean keepLegacyUpdated
    ) {
        this.lucene4Writer = lucene4Writer;
        this.lucene9Writer = lucene9Writer;
        this.keepLegacyUpdated = keepLegacyUpdated;
    }

    @Override
    public void addDocument(Document doc) throws IOException {
        // Write to both indices
        IOException firstException = null;

        try {
            if (keepLegacyUpdated) {
                lucene4Writer.addDocument(doc);
            }
        } catch (IOException e) {
            firstException = e;
            logger.error("Failed to write to Lucene 4", e);
        }

        try {
            lucene9Writer.addDocument(doc);
        } catch (IOException e) {
            if (firstException != null) {
                e.addSuppressed(firstException);
            }
            throw e; // Lucene 9 is primary, must succeed
        }

        // If Lucene 4 failed but 9 succeeded, log warning
        if (firstException != null) {
            logger.warn("Lucene 4 write failed but Lucene 9 succeeded", firstException);
        }
    }

    @Override
    public void updateDocument(Term term, Document doc) throws IOException {
        // Similar dual-write logic
        if (keepLegacyUpdated) {
            lucene4Writer.updateDocument(term, doc);
        }
        lucene9Writer.updateDocument(term, doc); // Primary
    }

    @Override
    public void deleteDocuments(Term term) throws IOException {
        if (keepLegacyUpdated) {
            lucene4Writer.deleteDocuments(term);
        }
        lucene9Writer.deleteDocuments(term); // Primary
    }

    @Override
    public void commit() throws IOException {
        // Commit both (transactional)
        IOException firstException = null;

        try {
            if (keepLegacyUpdated) {
                lucene4Writer.commit();
            }
        } catch (IOException e) {
            firstException = e;
            logger.error("Lucene 4 commit failed", e);
        }

        try {
            lucene9Writer.commit();
        } catch (IOException e) {
            if (firstException != null) {
                e.addSuppressed(firstException);
            }
            throw e;
        }

        if (firstException != null) {
            logger.warn("Lucene 4 commit failed but Lucene 9 succeeded", firstException);
        }
    }

    @Override
    public void close() throws IOException {
        // Close both
        IOException firstException = null;

        try {
            if (keepLegacyUpdated) {
                lucene4Writer.close();
            }
        } catch (IOException e) {
            firstException = e;
        }

        try {
            lucene9Writer.close();
        } catch (IOException e) {
            if (firstException != null) {
                e.addSuppressed(firstException);
            }
            throw e;
        }

        if (firstException != null) {
            throw firstException;
        }
    }
}
```

---

### 4. Migration Coordinator

```java
package org.apache.jackrabbit.oak.plugins.index.lucene;

import java.util.concurrent.*;
import java.io.IOException;

/**
 * Coordinates background conversion of Lucene 4.7 → 9.x segments.
 * Runs during STATE 1 (Active Migration).
 */
public class MigrationCoordinator {

    private final ExecutorService executor;
    private final SegmentConverter converter;
    private final SegmentTracker tracker;
    private volatile boolean running;

    public MigrationCoordinator(Path indexRoot) {
        this.executor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "lucene-migration-background")
        );
        this.converter = new SegmentConverter();
        this.tracker = new SegmentTracker(indexRoot);
        this.running = false;
    }

    /**
     * Starts background conversion.
     * Called automatically when enableMigration=true.
     */
    public synchronized void startConversion() {
        if (running) {
            logger.info("Migration already running");
            return;
        }

        running = true;
        logger.info("Starting Lucene 4→9 migration");

        executor.submit(() -> {
            try {
                runConversion();
            } catch (Exception e) {
                logger.error("Migration failed", e);
            }
        });
    }

    /**
     * Stops background conversion.
     * Called when enableMigration → false.
     */
    public synchronized void stopConversion() {
        if (!running) {
            return;
        }

        logger.info("Stopping migration");
        running = false;
        executor.shutdownNow();
    }

    /**
     * Main conversion loop.
     */
    private void runConversion() {
        while (running) {
            try {
                // Find next unconverted segment
                SegmentInfo segment = tracker.getNextUnconverted();

                if (segment == null) {
                    // All segments converted!
                    logger.info("Migration complete");
                    onConversionComplete();
                    running = false;
                    break;
                }

                // Convert segment
                logger.info("Converting segment: {}", segment.name());
                converter.convert(segment, IndexVersion.LUCENE_4_7_2, IndexVersion.LUCENE_9_X);

                // Mark as converted
                tracker.markConverted(segment);

                // Throttle to avoid overwhelming system
                Thread.sleep(100);

            } catch (InterruptedException e) {
                logger.info("Migration interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                logger.error("Failed to convert segment", e);
                // Continue with next segment
            }
        }
    }

    /**
     * Called when all segments converted (P2 reached).
     */
    private void onConversionComplete() {
        logger.info("🎉 All segments converted to Lucene 9!");

        // Switch read path to Lucene 9
        LuceneIndexManager.switchReaderToLucene9();

        // Notify monitoring
        Metrics.counter("lucene.migration.completed").increment();
    }

    /**
     * Returns conversion progress (0.0 to 1.0).
     */
    public double getProgress() {
        return tracker.getConversionProgress();
    }

    /**
     * Returns true if conversion complete (P2 reached).
     */
    public boolean isComplete() {
        return tracker.countUnconverted() == 0;
    }
}
```

---

### 5. Segment Converter

```java
package org.apache.jackrabbit.oak.plugins.index.lucene;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Converts a single segment from one Lucene version to another.
 */
public class SegmentConverter {

    /**
     * Converts a segment from source to target version.
     */
    public void convert(
        SegmentInfo segment,
        IndexVersion sourceVersion,
        IndexVersion targetVersion
    ) throws IOException {

        logger.info("Converting {} from {} to {}",
            segment.name(), sourceVersion, targetVersion);

        // Step 1: Open segment with source version reader
        IndexReader sourceReader = openReader(segment, sourceVersion);

        // Step 2: Create new segment with target version writer
        Path targetPath = createTempSegment(segment.name());
        IndexWriter targetWriter = openWriter(targetPath, targetVersion);

        try {
            // Step 3: Copy all documents
            int numDocs = sourceReader.maxDoc();
            for (int i = 0; i < numDocs; i++) {
                if (sourceReader.isDeleted(i)) {
                    continue; // Skip deleted docs
                }

                Document doc = sourceReader.document(i);
                targetWriter.addDocument(doc);
            }

            // Step 4: Commit target
            targetWriter.commit();

            // Step 5: Atomic replacement
            atomicReplace(segment.path(), targetPath);

            logger.info("Successfully converted {}", segment.name());

        } finally {
            sourceReader.close();
            targetWriter.close();
        }
    }

    /**
     * Atomically replaces old segment with new segment.
     */
    private void atomicReplace(Path oldSegment, Path newSegment) throws IOException {
        // Implementation depends on filesystem
        // Use atomic move if available, otherwise:
        // 1. Write new segment to temp location
        // 2. Rename temp → final (atomic on most filesystems)
        // 3. Delete old segment

        Files.move(newSegment, oldSegment,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
    }
}
```

---

## Configuration & Validation

### Configuration Manager

```java
package org.apache.jackrabbit.oak.plugins.index.lucene;

/**
 * Manages migration configuration and validates state transitions.
 */
public class MigrationConfigManager {

    private volatile boolean enableMigration;
    private volatile boolean keepLegacyUpdated;

    public MigrationConfigManager() {
        // Defaults
        this.enableMigration = false;
        this.keepLegacyUpdated = true;
    }

    /**
     * Updates configuration with validation.
     */
    public synchronized void updateConfig(
        boolean newEnableMigration,
        boolean newKeepLegacyUpdated
    ) throws ConfigValidationException {

        // Validate state transition
        validateTransition(
            enableMigration, keepLegacyUpdated,
            newEnableMigration, newKeepLegacyUpdated
        );

        // Apply changes
        boolean migrationChanged = (this.enableMigration != newEnableMigration);
        boolean legacyChanged = (this.keepLegacyUpdated != newKeepLegacyUpdated);

        this.enableMigration = newEnableMigration;
        this.keepLegacyUpdated = newKeepLegacyUpdated;

        // Trigger actions
        if (migrationChanged) {
            onMigrationToggleChanged(newEnableMigration);
        }

        if (legacyChanged) {
            onLegacyToggleChanged(newKeepLegacyUpdated);
        }
    }

    /**
     * Validates configuration change is legal.
     */
    private void validateTransition(
        boolean currentEnable, boolean currentKeepLegacy,
        boolean newEnable, boolean newKeepLegacy
    ) throws ConfigValidationException {

        // RULE 1: Cannot disable migration if legacy is false
        if (currentEnable && !newEnable && !currentKeepLegacy) {
            throw new ConfigValidationException(
                "Cannot disable enableMigration when keepLegacyUpdated is false. " +
                "Legacy segments have been deleted. Migration is irreversible."
            );
        }

        // RULE 2: Warn on point of no return
        if (currentKeepLegacy && !newKeepLegacy) {
            logger.warn(
                "⚠️ WARNING: Setting keepLegacyUpdated=false is IRREVERSIBLE. " +
                "Lucene 4.7 segments will be deleted. " +
                "You will NOT be able to rollback to Lucene 4.7 after this."
            );

            // Could require confirmation here
            requireAdminConfirmation("delete-legacy-segments");
        }
    }

    private void onMigrationToggleChanged(boolean enabled) {
        if (enabled) {
            logger.info("Migration enabled - starting background conversion");
            MigrationCoordinator.getInstance().startConversion();
        } else {
            logger.info("Migration disabled - stopping background conversion");
            MigrationCoordinator.getInstance().stopConversion();
        }
    }

    private void onLegacyToggleChanged(boolean keepLegacyUpdated) {
        if (!keepLegacyUpdated) {
            logger.info("Legacy updates disabled - entering point of no return");
            MigrationCoordinator.getInstance().cleanupLegacySegments();
        }
    }
}
```

---

## Testing Strategy

### Unit Tests

```java
// Test SPI implementations
Lucene4IndexReaderTest
Lucene9IndexReaderTest
DualIndexWriterTest
SegmentConverterTest

// Test state machine
MigrationConfigManagerTest
StateTransitionTest
```

### Integration Tests

```java
// Test with real Lucene indices
BackwardCompatibilityTest
  - testReadLucene4Segments()
  - testWriteLucene9Segments()
  - testMixedSegments()

MigrationEndToEndTest
  - testFullMigration()
  - testRollback()
  - testPointOfNoReturn()
```

### Test Data Repository

```
oak-lucene/src/test/resources/
├── lucene-4-sample/         # Real Lucene 4.7 index
├── lucene-9-sample/         # Real Lucene 9.x index
└── test-queries.json        # Standard query suite
```

### Performance Tests

```java
BenchmarkTest
  - testQueryLatency()       // Compare 4.7 vs 9.x
  - testIndexingThroughput() // Single vs dual write
  - testMemoryUsage()        // Heap consumption
```

---

## Migration Timeline

### Example Production Rollout

```
Week 1-2: Development
  - Implement SPI interfaces
  - Implement Lucene 4 & 9 adapters
  - Implement dual writer
  - Unit tests

Week 3-4: Integration & Testing
  - Integration tests with real data
  - Performance benchmarking
  - Load testing with dual writes

Week 5: Staging Deployment
  - Deploy to staging
  - Enable migration toggle
  - Monitor conversion progress
  - Validate query results

Week 6-8: Production Deployment (STATE 1)
  - Deploy to production
  - Enable migration toggle: enableMigration=true
  - Monitor closely
  - Dual writes active
  - Background conversion running

Week 9-12: Confidence Building (STATE 1)
  - Monitor Lucene 9 performance
  - Compare query results (4.7 vs 9)
  - Check for errors/issues
  - Still in STATE 1 (can rollback)

Week 13: Point of No Return (STATE 2)
  - After confidence built
  - Set keepLegacyUpdated=false
  - Delete Lucene 4.7 segments
  - Pure Lucene 9 going forward

Week 14+: Cleanup
  - Remove Lucene 4.7 JAR
  - Remove oak-lucene-4 module
  - Update documentation
```

---

## Monitoring & Observability

### Metrics

```java
// State
gauge("lucene.migration.state")         // 0, 1, or 2

// Progress
gauge("lucene.segments.v4.count")       // Number of 4.7 segments
gauge("lucene.segments.v9.count")       // Number of 9.x segments
gauge("lucene.conversion.progress")     // 0.0 to 1.0

// Performance
timer("lucene.read.v4.latency")
timer("lucene.read.v9.latency")
timer("lucene.write.single.latency")
timer("lucene.write.dual.latency")

// Events
counter("lucene.migration.started")
counter("lucene.migration.completed")
counter("lucene.migration.failed")
counter("lucene.segment.converted")
```

### Logging

```java
// State transitions
INFO: "Migration enabled (STATE 0 → STATE 1)"
INFO: "Migration disabled (STATE 1 → STATE 0)"
WARN: "Entering point of no return (STATE 1 → STATE 2)"

// Conversion progress
INFO: "Segment converted: segments_a (4.7 → 9)"
INFO: "Conversion progress: 573/1000 segments (57%)"
INFO: "Conversion complete (P2 reached)"

// Errors
ERROR: "Failed to convert segment: segments_b"
ERROR: "Dual write failed: Lucene 4 writer error"
```

---

## Rollback Procedures

### Rollback from STATE 1 → STATE 0

```bash
# 1. Disable migration
oak-config set oak.lucene.enableMigration=false

# 2. System automatically:
#    - Stops background conversion
#    - Stops dual writes
#    - Switches back to Lucene 4.7 reads/writes

# 3. Verify
oak-status check lucene.migration.state
# Expected: 0 (Pre-Migration)
```

**Requirements:**
- ✅ Must be in STATE 1 with `keepLegacyUpdated=true`
- ✅ Lucene 4.7 segments must exist and be up-to-date

---

### Cannot Rollback from STATE 2

```bash
# Attempting to disable from STATE 2:
oak-config set oak.lucene.enableMigration=false

# Result:
ERROR: Cannot disable enableMigration when keepLegacyUpdated is false.
       Legacy segments have been deleted. Migration is irreversible.
       Current state: STATE 2 (Point of No Return)
```

**Why:** Lucene 4.7 segments deleted, cannot read Lucene 9 segments with 4.7

---

## Future Upgrades (9 → 10 → 11)

### Simplified Process

With abstraction layer in place, future upgrades are straightforward:

```
1. Create oak-lucene-10 module
   - Implement SPI interfaces for Lucene 10
   - Uses lucene-backward-codecs to read Lucene 9

2. Swap implementation
   - Remove oak-lucene-9
   - Add oak-lucene-10
   - No dual writes needed! (Lucene 10 reads 9 natively)

3. Test thoroughly
   - Backward compatibility tests
   - Performance benchmarks
   - Integration tests

4. Deploy
   - Single-step deployment
   - No toggles needed (backward compat built-in)
```

**Key Difference:** After 4→9 migration, all future upgrades use Lucene's native backward compatibility (N reads N-1).

---

## Success Criteria

### Must Have
- ✅ All production indices remain readable
- ✅ Zero data loss during migration
- ✅ Zero downtime deployments
- ✅ Rollback capability during STATE 1
- ✅ All tests passing
- ✅ Performance within 20% of baseline

### Should Have
- ✅ Clear monitoring dashboards
- ✅ Automated alerts for migration issues
- ✅ Documentation for operators
- ✅ Runbooks for common scenarios

### Nice to Have
- 🎯 Performance improvements over Lucene 4.7
- 🎯 Reduced index size
- 🎯 Faster query execution

---

## Risks & Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Segment conversion fails | HIGH | Low | Retry logic, detailed error logging, keep 4.7 segments |
| Dual write performance degradation | MEDIUM | Medium | Throttling, monitoring, can disable if needed |
| Lucene 9 has unknown bugs | HIGH | Low | Extended STATE 1 period, comprehensive testing |
| Disk space exhaustion | MEDIUM | Low | Monitor disk usage, alert on thresholds |
| Classloader conflicts | MEDIUM | Low | Careful module isolation, testing |

---

## Open Questions

1. **Module isolation:** Use separate classloaders or rely on Maven module separation?
2. **OSGi compatibility:** How to handle OSGi bundle exports for dual Lucene versions?
3. **Disk space management:** How to handle temp segments during conversion?
4. **Crash recovery:** How to resume conversion after Oak restart?
5. **Performance target:** What's acceptable overhead for dual writes?

---

## Next Steps

1. ✅ Get design approval
2. Create implementation plan with tasks
3. Set up feature branch
4. Implement SPI interfaces
5. Implement Lucene 4 & 9 adapters
6. Implement dual writer & migration coordinator
7. Write comprehensive tests
8. Performance benchmarking
9. Documentation
10. Staged rollout

---

## Appendix: Code Organization

### Maven Module Dependencies

```
oak-search-spi
  └─ (no Lucene dependencies)

oak-lucene-4
  └─ depends on: oak-search-spi, lucene-4.7.2

oak-lucene-9
  └─ depends on: oak-search-spi, lucene-9.x

oak-lucene-core
  └─ depends on: oak-search-spi, oak-lucene-4, oak-lucene-9
```

### Package Structure

```
org.apache.jackrabbit.oak.plugins.index.search.spi/
  ├─ IndexDirectory
  ├─ IndexReader
  ├─ IndexWriter
  ├─ QueryBuilder
  ├─ DocumentBuilder
  └─ IndexVersion

org.apache.jackrabbit.oak.plugins.index.lucene4/
  ├─ Lucene4IndexDirectory
  ├─ Lucene4IndexReader
  ├─ Lucene4IndexWriter
  └─ Lucene4QueryBuilder

org.apache.jackrabbit.oak.plugins.index.lucene9/
  ├─ Lucene9IndexDirectory
  ├─ Lucene9IndexReader
  ├─ Lucene9IndexWriter
  └─ Lucene9QueryBuilder

org.apache.jackrabbit.oak.plugins.index.lucene/
  ├─ LuceneIndexManager       (main entry point)
  ├─ DualIndexWriter
  ├─ MigrationCoordinator
  ├─ MigrationConfigManager
  ├─ SegmentConverter
  ├─ SegmentVersionDetector
  └─ SegmentTracker
```

---

**End of Design Document**
