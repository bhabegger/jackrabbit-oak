<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Full SPI Integration for Core Indexing and Querying Flow

**Date:** 2026-03-03
**Status:** Approved
**Scope:** Core indexing and querying flow (defers advanced features like NRT, suggestions, facets)

## Overview

This design migrates the core Lucene indexing and querying flow in oak-lucene to use the Search Provider Interface (SPI) exclusively. This completes the abstraction layer introduced in earlier work, enabling future migration to newer Lucene versions.

### Goals

- Migrate core indexing flow to use SPI exclusively
- Migrate core querying flow to use SPI exclusively
- Remove feature flags and dual-path implementations
- Maintain 100% backward compatibility with existing indices
- Keep all 1246 existing tests passing

### Non-Goals (Deferred)

- NRTIndex (near-real-time hybrid indexing)
- Suggestion/spellcheck indexes
- Faceting support
- PropertyIndex specialized queries
- IndexCopier and remote indexes

## Architecture

### Layer Structure

```
┌─────────────────────────────────────────────┐
│  Oak JCR Layer (consumers)                  │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│  LuceneIndexEditor / LuceneIndex            │
│  (Oak indexing entry points)                │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│  SPI Layer (oak-search-spi)                 │
│  Document, DocumentBuilder, Query,          │
│  QueryBuilder, IndexWriter, IndexReader     │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│  Lucene 4.7 Implementation (oak-lucene/spi) │
│  Lucene47Document, Lucene47IndexWriter, etc │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│  Native Lucene 4.7.2 (embedded)             │
└─────────────────────────────────────────────┘
```

### Key Principle

All Oak indexing code interacts with SPI abstractions. The SPI implementations (Lucene47*) handle native Lucene interaction. No Oak code directly imports `org.apache.lucene.document.*` or `org.apache.lucene.index.*` except in the `oak-lucene/spi` package.

### Compatibility Guarantee

Since Lucene47* implementations wrap native Lucene 4.7, indices written via SPI are byte-for-byte identical to legacy indices. No data migration required. Legacy and SPI code can read each other's indices seamlessly.

## Component Changes

### 1. DefaultIndexWriter (oak-lucene/writer/)

**Current state:** Uses native `org.apache.lucene.index.IndexWriter` directly

**Target state:** Uses SPI `IndexWriter` interface

**Changes:**
- Replace `IndexWriter writer` field with `org.apache.jackrabbit.oak.plugins.index.search.spi.IndexWriter writer`
- Instantiate via `new Lucene47IndexWriter(directory, config)` instead of native IndexWriter
- Method signatures stay the same (implements `LuceneIndexWriter` interface)
- All operations delegate to SPI: `addDocument()`, `updateDocument()`, `deleteDocuments()`, `commit()`

**Impact:** Internal only - LuceneIndexWriter interface unchanged, callers unaffected

### 2. DefaultIndexReader (oak-lucene/reader/)

**Current state:** Uses native `org.apache.lucene.index.IndexReader` directly

**Target state:** Uses SPI `IndexReader` interface

**Changes:**
- Replace `IndexReader reader` field with `org.apache.jackrabbit.oak.plugins.index.search.spi.IndexReader reader`
- Instantiate via `new Lucene47IndexReader(directory)` instead of `DirectoryReader.open()`
- Expose native reader via `((Lucene47IndexReader)reader).getLuceneReader()` for legacy compatibility
- Keep suggestion/lookup logic unchanged (deferred feature)

**Impact:** Internal only - LuceneIndexReader interface unchanged

### 3. LuceneDocumentMaker

**Current state:** Partially SPI-integrated with feature flag

**Target state:** Pure SPI, no feature flags

**Changes:**
- Remove `USE_SPI_FOR_TYPED_PROPERTIES` flag and all `if/else` branches
- Keep only SPI code path: use `Lucene47DocumentBuilder` exclusively
- All methods return SPI `Document` instead of mixing types
- Simplify: `createTypedFieldViaSPI()` becomes the only implementation

**Impact:** Cleaner code, single path, easier to maintain

### 4. LucenePropertyIndex (query building)

**Current state:** Partially uses SPI queries

**Target state:** All query construction via SPI `QueryBuilder`

**Changes:**
- Complete migration of all query types to SPI
- Remove any remaining direct Lucene query construction
- Use `LuceneIndexHelper.newQueryBuilder()` for all queries
- Boolean combinations, filters, all via SPI

**Impact:** Consistent query building API

### 5. LuceneIndex (query execution)

**Current state:** Uses native Lucene readers and searchers

**Target state:** Query execution via SPI readers

**Changes:**
- Get readers via SPI `IndexReader`
- Extract native Lucene reader when needed for searcher: `((Lucene47IndexReader)spiReader).getLuceneReader()`
- Maintains compatibility while using SPI as primary interface

**Impact:** Query execution uses SPI entry point

## Data Flow

### Indexing Flow (Write Path)

```
JCR Node Change
      ↓
LuceneIndexEditor.enter()
      ↓
FulltextIndexEditor.addOrUpdate()
      ↓
LuceneDocumentMaker.makeDocument()
      │ Creates: DocumentBuilder builder = new Lucene47DocumentBuilder()
      │ Builds: Document doc = builder.addStringField(...).build()
      ↓ Returns SPI Document
FulltextIndexEditorContext.indexUpdate()
      ↓
LuceneIndexWriter.updateDocument(path, doc)
      ↓
DefaultIndexWriter.updateDocument()
      │ Uses: SPI IndexWriter (Lucene47IndexWriter)
      │ Converts: Extract native Lucene doc from SPI Document
      │ Writes: delegate.updateDocument(term, nativeDoc)
      ↓
Native Lucene 4.7 Index on disk
```

**Key transformation points:**
- LuceneDocumentMaker → SPI Document
- DefaultIndexWriter → Extract native doc, write via SPI wrapper
- Compatibility: Native Lucene format unchanged

### Query Flow (Read Path)

```
JCR Query
      ↓
LuceneIndex.query()
      ↓
LucenePropertyIndex.createQuery()
      │ Creates: QueryBuilder qb = LuceneIndexHelper.newQueryBuilder()
      │ Builds: Query q = qb.term(field, value)
      ↓ Returns SPI Query
LuceneIndex.executeQuery()
      ↓
Get IndexReader via SPI
      │ Opens: IndexReader reader = new Lucene47IndexReader(directory)
      │ Extracts: DirectoryReader nativeReader = ((Lucene47IndexReader)reader).getLuceneReader()
      │ Creates: IndexSearcher searcher = new IndexSearcher(nativeReader)
      ↓
Execute query
      │ Extracts: org.apache.lucene.search.Query nativeQuery = ((Lucene47Query)spiQuery).getLuceneQuery()
      │ Searches: searcher.search(nativeQuery)
      ↓
Results
```

**Key transformation points:**
- Query building → SPI Query
- Reader opening → SPI IndexReader
- Execution → Extract native objects from SPI wrappers
- Compatibility: Native Lucene query execution unchanged

## Error Handling & Edge Cases

### Type Safety at Boundaries

Since we're wrapping Lucene types, we need runtime checks:

```java
// In DefaultIndexWriter.updateDocument()
if (!(doc instanceof Lucene47Document)) {
    throw new IllegalArgumentException("Document must be Lucene47Document");
}

// In query execution
if (!(query instanceof Lucene47Query)) {
    throw new IllegalArgumentException("Query must be Lucene47Query");
}
```

**Rationale:** The SPI is abstract, but Lucene47* implementations expect specific types. Better to fail fast with clear error than ClassCastException.

### Null Handling

Existing null checks remain:
- Document cannot be null
- Query cannot be null
- Path cannot be null

SPI implementations already validate in constructors.

### IOException Propagation

No change - IOExceptions propagate from native Lucene through SPI wrapper to callers. Same error handling as before.

### Backward Compatibility

**Reading old indices:** SPI readers can read any Lucene 4.7 index (written by legacy or SPI code)

**Writing to existing indices:** SPI writers append to existing indices seamlessly

**Mixed code:** If any legacy code remains (deferred features), it can read SPI-written indices

**Guarantee:** Index format is identical - this is not a migration, just an API refactoring.

## Testing Strategy

### Existing Tests (1246 tests)

All existing oak-lucene tests must pass without modification. These validate:
- ✓ Indexing correctness
- ✓ Query correctness
- ✓ Performance characteristics
- ✓ Edge cases

**Why they'll pass:** Index format unchanged, behavior unchanged, only implementation details changed.

### SPI Integration Tests (Already Exist)

Keep and expand:
- `DocumentMakerSPIIntegrationTest` - Shows document creation patterns
- `FullIndexingQueryFlowTest` - End-to-end via SPI

These prove the SPI works in isolation.

### New Validation

After migration, add assertions to key tests:

```java
// Verify we're actually using SPI types internally
assertTrue(writer instanceof Lucene47IndexWriter);
assertTrue(doc instanceof Lucene47Document);
```

Optional but helpful for future maintainers.

## Implementation Strategy

**Bottom-Up Approach:**

1. **Make writers/readers SPI-based**
   - DefaultIndexWriter uses SPI IndexWriter
   - DefaultIndexReader uses SPI IndexReader
   - Test: existing writer/reader tests pass

2. **Update document creation**
   - Remove feature flag from LuceneDocumentMaker
   - Pure SPI document building via DocumentBuilder
   - Test: document maker tests pass

3. **Update query building and execution**
   - LucenePropertyIndex uses QueryBuilder exclusively
   - LuceneIndex query execution uses SPI readers
   - Test: query tests pass

4. **Verify full integration**
   - Run full test suite (1246 tests)
   - Verify all pass
   - Check no direct Lucene imports outside spi package

## Success Criteria

- ✅ All 1246 existing tests pass
- ✅ No feature flags or dual-path code remains
- ✅ No direct `org.apache.lucene.*` imports outside `oak-lucene/spi` package
- ✅ Indices written before/after migration are identical format
- ✅ SPI integration tests demonstrate full flow
- ✅ Code is cleaner and easier to maintain

## Future Work (Out of Scope)

- Migrate NRTIndex to SPI
- Migrate suggestion/spellcheck indexes to SPI
- Migrate faceting to SPI
- Migrate PropertyIndex specializations to SPI
- Add Lucene 9.x implementation of SPI
- Switch runtime SPI implementation via configuration
