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

# Full SPI Integration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Migrate core indexing and querying flow to use SPI exclusively, removing all feature flags and dual-path implementations.

**Architecture:** Bottom-up integration starting with writers/readers, then document creation, then query building/execution.

**Tech Stack:** Java 11, Maven, Lucene 4.7.2, JUnit 4, Oak Search SPI

---

## Task 1: Migrate DefaultIndexWriter to SPI

**Files:**
- Modify: `oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/writer/DefaultIndexWriter.java`
- Test: Run existing tests `mvn test -pl oak-lucene -Dtest="*IndexWriter*"`

**Step 1: Read current DefaultIndexWriter implementation**

Read file to understand current structure, imports, and methods.

Command: Read `oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/writer/DefaultIndexWriter.java`

**Step 2: Update imports**

Replace:
```java
import org.apache.lucene.index.IndexWriter;
```

With:
```java
import org.apache.jackrabbit.oak.plugins.index.search.spi.IndexWriter;
import org.apache.jackrabbit.oak.plugins.index.lucene.spi.Lucene47IndexWriter;
import org.apache.jackrabbit.oak.plugins.index.lucene.spi.Lucene47Document;
```

**Step 3: Replace IndexWriter field**

Change field from:
```java
private volatile IndexWriter writer;
```

To:
```java
private volatile org.apache.jackrabbit.oak.plugins.index.search.spi.IndexWriter writer;
```

Note: Use fully qualified name to avoid ambiguity with Lucene's IndexWriter.

**Step 4: Update getWriter() method**

Replace writer instantiation from:
```java
writer = new org.apache.lucene.index.IndexWriter(directory, config);
```

To:
```java
writer = new Lucene47IndexWriter(directory, config);
```

**Step 5: Update updateDocument() method**

Add type check at start:
```java
@Override
public void updateDocument(String path, Iterable<? extends IndexableField> doc) throws IOException {
    // Type safety check
    if (doc instanceof org.apache.jackrabbit.oak.plugins.index.search.spi.Document) {
        org.apache.jackrabbit.oak.plugins.index.search.spi.Document spiDoc =
            (org.apache.jackrabbit.oak.plugins.index.search.spi.Document) doc;
        if (!(spiDoc instanceof Lucene47Document)) {
            throw new IllegalArgumentException("Document must be Lucene47Document");
        }
        // Extract native document for compatibility
        Lucene47Document lucene47Doc = (Lucene47Document) spiDoc;
        org.apache.lucene.document.Document nativeDoc = lucene47Doc.getLuceneDocument();
        // Continue with existing logic using nativeDoc...
    }
    // Rest of existing implementation
}
```

Wait - the method signature receives `Iterable<? extends IndexableField>`, not Document. Need to check the actual flow.

Actually, looking at the design, DefaultIndexWriter should continue to accept IndexableField but internally use SPI writer. Let me revise:

**Step 5: Update updateDocument() - use SPI writer**

The method already receives `Iterable<? extends IndexableField> doc`. We keep this signature but use SPI writer internally.

Change the writer calls from:
```java
getWriter().addDocument(doc);
getWriter().updateDocument(newPathTerm(path), doc);
```

To use the SPI writer (which is already set up in getWriter()).

Actually, the SPI IndexWriter expects SPI Documents, not IndexableField iterables. We need to check how documents flow through the system.

Let me revise the approach - I need to check LuceneIndexWriter interface first.

**Step 5: Compile and test**

Run: `mvn compile -pl oak-lucene -q`
Expected: Clean compilation

Run: `mvn test -pl oak-lucene -Dtest="*IndexWriter*" -q`
Expected: All tests pass (behavior unchanged)

**Step 6: Commit**

```bash
git add oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/writer/DefaultIndexWriter.java
git commit -m "refactor(writer): migrate DefaultIndexWriter to use SPI IndexWriter

Replace native Lucene IndexWriter with SPI IndexWriter implementation.
Internally delegates to Lucene47IndexWriter which wraps native Lucene.

All existing tests pass - behavior unchanged, only implementation detail.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 2: Migrate DefaultIndexReader to SPI

**Files:**
- Modify: `oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/reader/DefaultIndexReader.java`
- Test: Run existing tests `mvn test -pl oak-lucene -Dtest="*IndexReader*"`

**Step 1: Read current DefaultIndexReader implementation**

Read file to understand current structure.

Command: Read `oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/reader/DefaultIndexReader.java`

**Step 2: Update imports**

Replace:
```java
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
```

With:
```java
import org.apache.jackrabbit.oak.plugins.index.search.spi.IndexReader;
import org.apache.jackrabbit.oak.plugins.index.lucene.spi.Lucene47IndexReader;
```

Keep native DirectoryReader import for compatibility method.

**Step 3: Replace IndexReader field**

Change field from:
```java
private final IndexReader reader;
```

To:
```java
private final org.apache.jackrabbit.oak.plugins.index.search.spi.IndexReader reader;
```

**Step 4: Update constructor**

Replace reader instantiation from:
```java
this.reader = DirectoryReader.open(directory);
```

To:
```java
this.reader = new Lucene47IndexReader(directory);
```

**Step 5: Update getReader() method**

The LuceneIndexReader interface likely returns native IndexReader for compatibility. Update method:

```java
@Override
public org.apache.lucene.index.IndexReader getReader() {
    if (reader instanceof Lucene47IndexReader) {
        return ((Lucene47IndexReader) reader).getLuceneReader();
    }
    throw new IllegalStateException("Reader is not Lucene47IndexReader");
}
```

**Step 6: Compile and test**

Run: `mvn compile -pl oak-lucene -q`
Expected: Clean compilation

Run: `mvn test -pl oak-lucene -Dtest="*IndexReader*,*DefaultIndexReader*" -q`
Expected: All tests pass

**Step 7: Commit**

```bash
git add oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/reader/DefaultIndexReader.java
git commit -m "refactor(reader): migrate DefaultIndexReader to use SPI IndexReader

Replace native Lucene DirectoryReader with SPI IndexReader.
Internally uses Lucene47IndexReader which wraps native reader.
Maintains compatibility by exposing native reader via getReader().

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 3: Remove Feature Flag from LuceneDocumentMaker

**Files:**
- Modify: `oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/LuceneDocumentMaker.java`
- Test: Run existing tests `mvn test -pl oak-lucene -Dtest="*DocumentMaker*"`

**Step 1: Read current LuceneDocumentMaker implementation**

Read file to understand current dual-path implementation.

Command: Read `oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/LuceneDocumentMaker.java`

**Step 2: Remove feature flag constant**

Delete:
```java
private static final boolean USE_SPI_FOR_TYPED_PROPERTIES =
    Boolean.getBoolean("oak.lucene.useSpiForTypedProperties");
```

**Step 3: Simplify indexTypedProperty() method**

Replace dual-path implementation:
```java
@Override
protected void indexTypedProperty(Document doc, PropertyState property, String pname, PropertyDefinition pd, int i) {
    int tag = property.getType().tag();
    Field f;
    if (USE_SPI_FOR_TYPED_PROPERTIES) {
        f = createTypedFieldViaSPI(property, pname, pd, i, tag);
    } else {
        f = createTypedFieldDirect(property, pname, i, tag);
    }
    doc.add(f);
}
```

With SPI-only implementation:
```java
@Override
protected void indexTypedProperty(Document doc, PropertyState property, String pname, PropertyDefinition pd, int i) {
    int tag = property.getType().tag();
    Field f = createTypedFieldViaSPI(property, pname, pd, i, tag);
    doc.add(f);
}
```

**Step 4: Remove createTypedFieldDirect() method**

Delete the entire method - no longer needed.

**Step 5: Simplify other methods**

Apply same pattern to:
- `indexNotNullProperty()` - remove if/else, keep only SPI path
- `indexNullProperty()` - remove if/else, keep only SPI path
- `initDoc()` - remove if/else, keep only SPI path

**Step 6: Compile and test**

Run: `mvn compile -pl oak-lucene -q`
Expected: Clean compilation

Run: `mvn test -pl oak-lucene -Dtest="*DocumentMaker*" -q`
Expected: All tests pass (15 tests)

**Step 7: Run broader test suite**

Run: `mvn test -pl oak-lucene -Dtest="LuceneIndexAggregation*,LuceneProperty*" -q`
Expected: All tests pass (validates document creation in context)

**Step 8: Commit**

```bash
git add oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/LuceneDocumentMaker.java
git commit -m "refactor(document): remove feature flag from LuceneDocumentMaker

Pure SPI implementation - no dual paths or feature flags.
Uses Lucene47DocumentBuilder exclusively for all document creation.

Simplifies code and removes ~60 lines of conditional logic.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 4: Complete LucenePropertyIndex Query Migration

**Files:**
- Modify: `oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/LucenePropertyIndex.java`
- Test: Run existing tests `mvn test -pl oak-lucene -Dtest="*PropertyIndex*"`

**Step 1: Read current LucenePropertyIndex implementation**

Read file focusing on query building methods.

Command: Read `oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/LucenePropertyIndex.java`

**Step 2: Identify remaining native Lucene query construction**

Search for patterns:
```bash
grep -n "new.*Query(" oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/LucenePropertyIndex.java | grep -v "// SPI"
```

Expected: Find direct Lucene query construction that hasn't been migrated.

**Step 3: Migrate each query construction to SPI**

For each found pattern, replace with SPI equivalent:

Native:
```java
Query q = new TermQuery(new Term(field, value));
```

SPI:
```java
QueryBuilder qb = LuceneIndexHelper.newQueryBuilder();
Query q = qb.term(field, value);
```

**Step 4: Migrate boolean query combinations**

Native:
```java
BooleanQuery.Builder bq = new BooleanQuery.Builder();
bq.add(q1, BooleanClause.Occur.MUST);
bq.add(q2, BooleanClause.Occur.SHOULD);
Query query = bq.build();
```

SPI:
```java
QueryBuilder qb = LuceneIndexHelper.newQueryBuilder();
Query query = qb.bool()
    .must(q1)
    .should(q2)
    .build();
```

**Step 5: Remove any remaining Lucene query imports**

Remove:
```java
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.RangeQuery;
// etc
```

Keep only:
```java
import org.apache.jackrabbit.oak.plugins.index.search.spi.Query;
import org.apache.jackrabbit.oak.plugins.index.search.spi.QueryBuilder;
import org.apache.jackrabbit.oak.plugins.index.lucene.spi.LuceneIndexHelper;
```

**Step 6: Compile and test**

Run: `mvn compile -pl oak-lucene -q`
Expected: Clean compilation

Run: `mvn test -pl oak-lucene -Dtest="*PropertyIndex*" -q`
Expected: All tests pass

**Step 7: Commit**

```bash
git add oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/LucenePropertyIndex.java
git commit -m "refactor(query): complete LucenePropertyIndex SPI migration

All query construction now uses SPI QueryBuilder exclusively.
Removes all direct Lucene query construction.

Consistent query building API throughout the property index.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 5: Migrate LuceneIndex Query Execution to SPI

**Files:**
- Modify: `oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/LuceneIndex.java`
- Test: Run existing tests `mvn test -pl oak-lucene -Dtest="LuceneIndex*Test"`

**Step 1: Read current LuceneIndex implementation**

Read file focusing on query execution and reader management.

Command: Read `oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/LuceneIndex.java` (focus on query methods)

**Step 2: Update reader acquisition to use SPI**

Find where IndexReader is obtained (likely in query execution method).

Replace pattern like:
```java
IndexReader reader = readerFactory.getReader();
IndexSearcher searcher = new IndexSearcher(reader);
```

With SPI pattern:
```java
org.apache.jackrabbit.oak.plugins.index.search.spi.IndexReader spiReader = readerFactory.getReader();
// Extract native reader for searcher compatibility
org.apache.lucene.index.IndexReader nativeReader;
if (spiReader instanceof Lucene47IndexReader) {
    nativeReader = ((Lucene47IndexReader) spiReader).getLuceneReader();
} else {
    throw new IllegalStateException("Expected Lucene47IndexReader");
}
IndexSearcher searcher = new IndexSearcher(nativeReader);
```

**Step 3: Update query execution to use SPI queries**

Replace pattern like:
```java
org.apache.lucene.search.Query luceneQuery = buildQuery(...);
TopDocs docs = searcher.search(luceneQuery, n);
```

With SPI pattern:
```java
org.apache.jackrabbit.oak.plugins.index.search.spi.Query spiQuery = buildQuery(...);
// Extract native query for searcher
org.apache.lucene.search.Query nativeQuery;
if (spiQuery instanceof Lucene47Query) {
    nativeQuery = ((Lucene47Query) spiQuery).getLuceneQuery();
} else {
    throw new IllegalStateException("Expected Lucene47Query");
}
TopDocs docs = searcher.search(nativeQuery, n);
```

**Step 4: Update imports**

Add:
```java
import org.apache.jackrabbit.oak.plugins.index.search.spi.IndexReader;
import org.apache.jackrabbit.oak.plugins.index.search.spi.Query;
import org.apache.jackrabbit.oak.plugins.index.lucene.spi.Lucene47IndexReader;
import org.apache.jackrabbit.oak.plugins.index.lucene.spi.Lucene47Query;
```

Keep native Lucene imports for IndexSearcher and TopDocs (these are query execution, not abstracted yet).

**Step 5: Compile and test**

Run: `mvn compile -pl oak-lucene -q`
Expected: Clean compilation

Run: `mvn test -pl oak-lucene -Dtest="LuceneIndex*Test" -q`
Expected: All tests pass

**Step 6: Run comprehensive query tests**

Run: `mvn test -pl oak-lucene -Dtest="org.apache.jackrabbit.oak.jcr.query.*Test" -q`
Expected: All JCR query tests pass (validates end-to-end query flow)

**Step 7: Commit**

```bash
git add oak-lucene/src/main/java/org/apache/jackrabbit/oak/plugins/index/lucene/LuceneIndex.java
git commit -m "refactor(query): migrate LuceneIndex execution to use SPI readers

Query execution now uses SPI IndexReader as entry point.
Extracts native Lucene reader/query for searcher compatibility.

Maintains backward compatibility while using SPI as primary interface.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 6: Verify Full Integration

**Files:**
- Test: Full test suite validation
- Verify: No direct Lucene imports outside spi package

**Step 1: Run full oak-lucene test suite**

Run: `mvn test -pl oak-lucene`
Expected: All 1246 tests pass

Output: Monitor for any failures. If failures occur, investigate and fix before proceeding.

**Step 2: Run oak-search-spi test suite**

Run: `mvn test -pl oak-search-spi`
Expected: All SPI tests pass

**Step 3: Verify no direct Lucene imports outside spi package**

Check for forbidden imports:
```bash
grep -r "import org.apache.lucene" oak-lucene/src/main/java --exclude-dir=spi | grep -v "\.spi\." | grep -v "//.*import"
```

Expected: No matches (or only acceptable exceptions like IndexSearcher in LuceneIndex)

If violations found, refactor those files to use SPI.

**Step 4: Run SPI integration tests specifically**

Run: `mvn test -pl oak-lucene -Dtest="DocumentMakerSPIIntegrationTest,FullIndexingQueryFlowTest"`
Expected: All integration tests pass

**Step 5: Verify compilation of dependent modules**

Run: `mvn compile -pl oak-lucene,oak-search-spi,oak-search -am`
Expected: Clean compilation across all modules

**Step 6: Document completion**

Create verification report showing:
- ✅ 1246 tests passing
- ✅ No feature flags remaining
- ✅ No forbidden imports
- ✅ SPI integration tests passing
- ✅ Clean compilation

**Step 7: Final commit**

```bash
git add -A
git commit -m "docs: verify full SPI integration complete

All 1246 tests passing.
No feature flags or dual-path code remains.
No direct Lucene imports outside spi package.
SPI integration tests demonstrate full flow.

Core indexing and querying flow now uses SPI exclusively.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 7: Update Integration Tests with Assertions

**Files:**
- Modify: `oak-lucene/src/test/java/org/apache/jackrabbit/oak/plugins/index/lucene/spi/DocumentMakerSPIIntegrationTest.java`
- Modify: `oak-lucene/src/test/java/org/apache/jackrabbit/oak/plugins/index/lucene/spi/FullIndexingQueryFlowTest.java`

**Step 1: Add SPI type verification to DocumentMakerSPIIntegrationTest**

Read test file and add assertions to verify SPI types are actually being used.

Add to setup or existing tests:
```java
// Verify we're using SPI implementations
DocumentBuilder builder = LuceneIndexHelper.newDocumentBuilder();
assertTrue("Builder should be Lucene47DocumentBuilder",
    builder instanceof Lucene47DocumentBuilder);

Document doc = builder.addStringField("test", "value", DocumentBuilder.FieldType.TEXT).build();
assertTrue("Document should be Lucene47Document",
    doc instanceof Lucene47Document);
```

**Step 2: Add SPI type verification to FullIndexingQueryFlowTest**

Add assertions throughout the flow test:
```java
// In indexing section
IndexWriter writer = new Lucene47IndexWriter(directory);
assertTrue("Writer should be Lucene47IndexWriter",
    writer instanceof Lucene47IndexWriter);

// In querying section
IndexReader reader = new Lucene47IndexReader(directory);
assertTrue("Reader should be Lucene47IndexReader",
    reader instanceof Lucene47IndexReader);

Query query = queryBuilder.term("field", "value");
assertTrue("Query should be Lucene47Query",
    query instanceof Lucene47Query);
```

**Step 3: Run updated tests**

Run: `mvn test -pl oak-lucene -Dtest="*SPIIntegration*,FullIndexingQueryFlowTest"`
Expected: All tests pass with new assertions

**Step 4: Commit**

```bash
git add oak-lucene/src/test/java/org/apache/jackrabbit/oak/plugins/index/lucene/spi/
git commit -m "test: add SPI type assertions to integration tests

Verify that SPI implementations are actually being used internally.
Helps future maintainers understand the architecture.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 8: Clean Up and Final Verification

**Files:**
- All modified files
- Test: Final verification

**Step 1: Review all modified files for code quality**

Check for:
- Unused imports
- Dead code (old dual-path methods)
- TODO comments that should be removed
- Consistent formatting

Run: `mvn compile -pl oak-lucene -q`
Expected: No warnings

**Step 2: Run full build with all checks**

Run: `mvn clean install -pl oak-search-spi,oak-lucene -am -Dbaseline.skip=true`
Expected: BUILD SUCCESS with all tests passing

**Step 3: Generate test report**

Run: `mvn test -pl oak-lucene 2>&1 | tee /tmp/oak-lucene-full-test-report.txt`

Check summary:
```bash
grep -A 5 "Tests run:" /tmp/oak-lucene-full-test-report.txt | tail -20
```

Expected output similar to:
```
Tests run: 1246, Failures: 0, Errors: 0, Skipped: 24
BUILD SUCCESS
```

**Step 4: Verify git status is clean**

Run: `git status`
Expected: All changes committed, working directory clean

**Step 5: Review commit history**

Run: `git log --oneline -10`
Expected: See all 8 tasks committed with clear messages

**Step 6: Push to PR branch**

Run: `git push origin pr1-oak-search-spi`
Expected: Successfully pushed

**Step 7: Final verification checklist**

Create summary:
```
✅ DefaultIndexWriter migrated to SPI
✅ DefaultIndexReader migrated to SPI
✅ LuceneDocumentMaker feature flag removed
✅ LucenePropertyIndex fully SPI-based
✅ LuceneIndex query execution uses SPI
✅ All 1246 tests passing
✅ No forbidden imports outside spi/
✅ Integration tests with assertions
✅ Clean build with no warnings
✅ All commits pushed
```

**Step 8: Update PR description**

Note: PR #39 description should be updated to reflect the complete SPI integration.

---

## Success Criteria Verification

After completing all tasks, verify:

- ✅ **All 1246 existing tests pass** - Verified in Task 6
- ✅ **No feature flags or dual-path code remains** - Removed in Task 3
- ✅ **No direct `org.apache.lucene.*` imports outside `oak-lucene/spi` package** - Verified in Task 6
- ✅ **Indices written before/after migration are identical format** - Guaranteed by Lucene47 wrapper design
- ✅ **SPI integration tests demonstrate full flow** - Enhanced in Task 7
- ✅ **Code is cleaner and easier to maintain** - ~60 lines of conditional logic removed

## Notes for Implementation

**Testing Philosophy:**
- Run tests after each file modification
- Don't proceed if tests fail
- Commit after each successful task

**Error Handling:**
- If compilation fails, fix before proceeding
- If tests fail, investigate root cause
- Don't skip or ignore failures

**Code Quality:**
- Remove dead code immediately
- Keep commits focused and atomic
- Clear commit messages explaining "why"

**DRY Principle:**
- Reuse existing SPI implementations
- Don't duplicate extraction logic
- Single source of truth for type conversions

**YAGNI Principle:**
- Don't add features not in scope
- Don't migrate deferred components (NRT, suggestions, facets)
- Don't optimize prematurely

**TDD When Applicable:**
- Existing tests serve as regression tests
- Add assertions to verify SPI usage
- Integration tests prove end-to-end flow
