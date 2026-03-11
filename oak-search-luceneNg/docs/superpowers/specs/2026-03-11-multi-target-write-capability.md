# Multi-Target Write Capability Design

**Date:** 2026-03-11
**Status:** Approved
**Goal:** Enable dual writes to Lucene 4.7 and Lucene 9 simultaneously for safe migrations

---

## Executive Summary

This design enables Oak indexes to write to multiple storage backends simultaneously (e.g., lucene47 and lucene9) while querying from only one. This supports safe migration by allowing shadow indexing to validate new implementations before switching queries over.

**Key Features:**
- Write to multiple index types simultaneously
- Query from only one active target
- Primary/secondary error handling (primary failures block, secondary failures log)
- Full backward compatibility with existing `type` property
- Foundation for index flipping in Phase 3

---

## Requirements

1. **Dual Write Support**: Index definitions can specify multiple storage targets
2. **Single Query Target**: Only one target serves queries (activeTarget)
3. **Error Handling**: Primary target failures propagate, secondary failures are tolerated
4. **Backward Compatibility**: Existing indexes with `type` property continue working
5. **Provider Flexibility**: Missing secondary providers don't block writes
6. **Future Parallelization**: Design allows moving to parallel writes later

---

## Configuration Model

### New Properties

Add to `FulltextIndexConstants.java` (oak-search):

```java
/** Array of storage types to write to (e.g., ["lucene47", "lucene9"]) */
public static final String STORE_TARGETS = "storeTargets";

/** The storage type to use for queries (must be in storeTargets) */
public static final String ACTIVE_TARGET = "activeTarget";
```

### Property Resolution

**Normalization Logic** (applied at IndexDefinition creation):

```
Input validation:
- if (storeTargets defined && !activeTarget defined) → ERROR
- if (activeTarget defined && !storeTargets defined) → normalize: storeTargets = [activeTarget]
- if (!storeTargets && !activeTarget && !type) → ERROR
- if (activeTarget not in storeTargets) → ERROR

Normalization:
- if (storeTargets && activeTarget) → use as-is, log INFO if type also present
- if (activeTarget only) → storeTargets = [activeTarget], log INFO if type present
- if (type only) → storeTargets = [type], activeTarget = type
```

**Result**: All downstream code sees normalized `storeTargets` and `activeTarget`.

### Example Configurations

**Legacy index (unchanged):**
```json
{
  "type": "lucene",
  "async": ["async"]
}
```
Normalized to: `storeTargets=["lucene"], activeTarget="lucene"`

**Migration index:**
```json
{
  "storeTargets": ["lucene47", "lucene9"],
  "activeTarget": "lucene47",
  "async": ["async"]
}
```
Writes to both, queries from lucene47.

**After flip:**
```json
{
  "storeTargets": ["lucene47", "lucene9"],
  "activeTarget": "lucene9",
  "async": ["async"]
}
```
Writes to both, queries from lucene9.

---

## Architecture

### Component Overview

```
AsyncIndexUpdate
    ↓
MultiTargetIndexEditorProvider (new)
    ↓
IndexDefinitionHelper.normalize() (new)
    ↓
For each storeTarget:
    IndexWriteOperation.execute() (new)
        ↓
        registered IndexEditorProvider
        ↓
        if (secondary) → ErrorTolerantEditor (new)
    ↓
CompositeEditor (existing)
```

### New Components

#### 1. IndexDefinitionHelper (oak-core)

**Purpose:** Normalize index properties early so all code uses storeTargets/activeTarget.

**Key Classes:**
```java
/**
 * Immutable holder for normalized index properties.
 */
public class NormalizedIndexProperties {
    private final List<String> storeTargets;  // Never empty
    private final String activeTarget;        // Never null, always in storeTargets

    public List<String> getStoreTargets() { return storeTargets; }
    public String getActiveTarget() { return activeTarget; }
    public boolean isMultiTarget() { return storeTargets.size() > 1; }
}

public class IndexDefinitionHelper {
    /**
     * Normalize type/storeTargets/activeTarget into canonical form.
     * @return normalized properties (storeTargets array, activeTarget string)
     * @throws IllegalArgumentException if validation fails
     */
    public static NormalizedIndexProperties normalize(NodeState definition);

    /**
     * Get active target for queries (reads activeTarget or falls back to type).
     */
    public static String getActiveTarget(NodeState definition);

    /**
     * Get store targets for writes (reads storeTargets or falls back to [type]).
     */
    public static List<String> getStoreTargets(NodeState definition);
}
```

**Decision:** Place in oak-core (not oak-search) to avoid circular dependencies. Constants stay in oak-search/FulltextIndexConstants.

#### 2. MultiTargetIndexEditorProvider (oak-core)

**Purpose:** Orchestrate writes to multiple targets.

**Key Logic:**
```java
public class MultiTargetIndexEditorProvider implements IndexEditorProvider {
    private final List<IndexEditorProvider> providers;

    @Override
    public Editor getIndexEditor(String type, NodeBuilder definition,
                                 NodeState root, IndexUpdateCallback callback) {
        // Normalize properties
        List<String> storeTargets = IndexDefinitionHelper.getStoreTargets(definition);

        List<Editor> editors = new ArrayList<>();
        for (int i = 0; i < storeTargets.size(); i++) {
            String targetType = storeTargets.get(i);
            boolean isPrimary = (i == 0);

            IndexWriteOperation op = new IndexWriteOperation(
                targetType, definition, root, callback, providers, isPrimary);
            Editor editor = op.execute();

            if (editor != null) {
                editors.add(editor);
            }
        }

        return CompositeEditor.compose(editors);
    }
}
```

**Decision:** First target in storeTargets array is primary. Simple, deterministic.

**Important:** Primary target is determined by position in `storeTargets`, NOT by `activeTarget`. Example:
```json
{
  "storeTargets": ["lucene47", "lucene9"],
  "activeTarget": "lucene9"
}
```
Primary for writes: `lucene47` (first in storeTargets)
Active for queries: `lucene9` (value of activeTarget)

This separation allows safe migrations: keep stable target as primary write (lucene47), test new target for queries (lucene9).

#### 3. IndexWriteOperation (oak-core)

**Purpose:** Encapsulate write to one target. Enables future parallelization.

**Key Logic:**
```java
class IndexWriteOperation {
    private final String targetType;
    private final boolean isPrimary;
    private final NodeBuilder definition;
    private final NodeState root;
    private final IndexUpdateCallback callback;
    private final List<IndexEditorProvider> providers;

    public IndexWriteOperation(String targetType, NodeBuilder definition,
                               NodeState root, IndexUpdateCallback callback,
                               List<IndexEditorProvider> providers, boolean isPrimary) {
        this.targetType = targetType;
        this.definition = definition;
        this.root = root;
        this.callback = callback;
        this.providers = providers;
        this.isPrimary = isPrimary;
    }

    public Editor execute() throws CommitFailedException {
        // Find provider for targetType by asking each provider if it handles this type
        IndexEditorProvider provider = findProvider();

        if (provider == null) {
            if (isPrimary) {
                throw new IllegalStateException(
                    "Primary target provider not found: " + targetType);
            } else {
                LOG.warn("Secondary target provider not found: {}, skipping", targetType);
                return null;
            }
        }

        Editor editor = provider.getIndexEditor(targetType, definition, root, callback);

        if (editor == null) {
            return null;
        }

        // Wrap secondary editors in error tolerance
        if (!isPrimary) {
            editor = new ErrorTolerantEditor(editor, targetType);
        }

        return editor;
    }

    private IndexEditorProvider findProvider() throws CommitFailedException {
        // Ask each provider if it can handle this targetType
        for (IndexEditorProvider provider : providers) {
            Editor editor = provider.getIndexEditor(targetType, definition, root, callback);
            if (editor != null) {
                return provider;
            }
        }
        return null;
    }
}
```

**Decision:** Self-contained operation makes it easy to parallelize later (wrap in Future/CompletableFuture). Provider lookup done by attempting to get editor from each provider (existing pattern).

#### 4. ErrorTolerantEditor (oak-core)

**Purpose:** Prevent secondary target failures from blocking commits.

**Key Logic:**
```java
public class ErrorTolerantEditor implements Editor {
    private final Editor delegate;
    private final String targetType;
    private final MultiTargetIndexMetrics metrics;

    public ErrorTolerantEditor(Editor delegate, String targetType,
                               MultiTargetIndexMetrics metrics) {
        this.delegate = delegate;
        this.targetType = targetType;
        this.metrics = metrics;
    }

    @Override
    public void enter(NodeState before, NodeState after) {
        safeExecute(() -> delegate.enter(before, after));
    }

    @Override
    public void leave(NodeState before, NodeState after) {
        safeExecute(() -> delegate.leave(before, after));
    }

    @Override
    public void propertyAdded(PropertyState after) {
        safeExecute(() -> delegate.propertyAdded(after));
    }

    @Override
    public void propertyChanged(PropertyState before, PropertyState after) {
        safeExecute(() -> delegate.propertyChanged(before, after));
    }

    @Override
    public void propertyDeleted(PropertyState before) {
        safeExecute(() -> delegate.propertyDeleted(before));
    }

    @Override
    public Editor childNodeAdded(String name, NodeState after) {
        return safeExecuteWithResult(
            () -> delegate.childNodeAdded(name, after),
            childEditor -> childEditor != null ?
                new ErrorTolerantEditor(childEditor, targetType, metrics) : null
        );
    }

    @Override
    public Editor childNodeChanged(String name, NodeState before, NodeState after) {
        return safeExecuteWithResult(
            () -> delegate.childNodeChanged(name, before, after),
            childEditor -> childEditor != null ?
                new ErrorTolerantEditor(childEditor, targetType, metrics) : null
        );
    }

    @Override
    public Editor childNodeDeleted(String name, NodeState before) {
        return safeExecuteWithResult(
            () -> delegate.childNodeDeleted(name, before),
            childEditor -> childEditor != null ?
                new ErrorTolerantEditor(childEditor, targetType, metrics) : null
        );
    }

    private void safeExecute(ThrowingRunnable action) {
        try {
            action.run();
            metrics.incrementSuccess(targetType);
        } catch (Exception e) {
            LOG.error("Secondary target write failed: {}", targetType, e);
            metrics.incrementFailure(targetType);
            // Do NOT propagate exception
        }
    }

    private <T> T safeExecuteWithResult(ThrowingSupplier<T> action,
                                         Function<T, T> wrapper) {
        try {
            T result = action.get();
            metrics.incrementSuccess(targetType);
            return wrapper.apply(result);
        } catch (Exception e) {
            LOG.error("Secondary target write failed: {}", targetType, e);
            metrics.incrementFailure(targetType);
            return null;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
```

**Decision:** Wrap ALL Editor methods (8 total) to ensure complete error tolerance. Child editors are also wrapped to maintain error tolerance recursively. Metrics collection integrated directly.

---

## Query Routing

**No changes needed to query engine.** Existing code:

```java
String type = indexDef.getString(TYPE_PROPERTY_NAME);
QueryIndexProvider provider = getProviderForType(type);
```

Becomes:

```java
String activeTarget = IndexDefinitionHelper.getActiveTarget(indexDef);
QueryIndexProvider provider = getProviderForType(activeTarget);
```

**Decision:** Update query engine in oak-core to use IndexDefinitionHelper.

---

## Error Handling

### Primary Target Failures

- **Behavior:** Exception propagates to AsyncIndexUpdate
- **Result:** Index update cycle fails, reindex flag stays true, retry on next cycle
- **User Impact:** Index becomes stale if primary target persistently fails

### Secondary Target Failures

- **Behavior:** Exception caught, logged at ERROR level, JMX counter incremented
- **Result:** Commit succeeds, other targets continue
- **User Impact:** Secondary index becomes stale but doesn't block primary

### Missing Provider Failures

- **Primary:** Throw IllegalStateException immediately, fail fast
- **Secondary:** Log WARNING, skip target, return null editor (no writes)
- **User Impact:** Clear error for primary, graceful degradation for secondary

### Concurrent Modification

**Scenario:** Index definition properties (storeTargets/activeTarget) change while async indexing cycle is running.

**Solution:** Properties are read and normalized ONCE at the start of each async indexing cycle. Changes made during a cycle take effect on the NEXT cycle.

**Rationale:**
- Simpler implementation (no mid-cycle re-evaluation)
- Consistent behavior (all indexed content in one cycle uses same targets)
- Async cycles are typically short (seconds to minutes)

**Implementation:** MultiTargetIndexEditorProvider.getIndexEditor() calls normalize() first, caches result, uses it for entire cycle.

### Empty Editor List

**Scenario:** CompositeEditor.compose() called with empty list (all providers returned null).

**Behavior:** Returns null (no editor).

**Impact:** Index update skipped for this cycle. AsyncIndexUpdate handles null editor gracefully (no-op).

**When This Happens:**
- All providers unavailable (unlikely)
- Index definition type doesn't match any provider (misconfiguration)
- All providers explicitly returned null (index disabled)

---

## JMX Monitoring

**New Classes:**

```java
/** Thread-safe metrics collection */
public class MultiTargetIndexMetrics {
    private final ConcurrentHashMap<String, AtomicLong> successCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> failureCounts = new ConcurrentHashMap<>();

    public void incrementSuccess(String targetType) {
        successCounts.computeIfAbsent(targetType, k -> new AtomicLong()).incrementAndGet();
    }

    public void incrementFailure(String targetType) {
        failureCounts.computeIfAbsent(targetType, k -> new AtomicLong()).incrementAndGet();
    }

    public long getSuccessCount(String targetType) {
        return successCounts.getOrDefault(targetType, new AtomicLong()).get();
    }

    public long getFailureCount(String targetType) {
        return failureCounts.getOrDefault(targetType, new AtomicLong()).get();
    }

    public Map<String, Long> getAllSuccesses() {
        return successCounts.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }

    public Map<String, Long> getAllFailures() {
        return failureCounts.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }
}

/** MBean interface */
public interface MultiTargetIndexMBean {
    /** Count of failed writes per target type */
    CompositeData getWriteFailures();

    /** Count of successful writes per target type */
    CompositeData getWriteSuccesses();

    /** List of index paths with active multi-target write */
    String[] getMultiTargetIndexes();

    /** Get storeTargets for specific index */
    String[] getStoreTargets(String indexPath);

    /** Get activeTarget for specific index */
    String getActiveTarget(String indexPath);
}
```

**Metrics Collection Points:**
- ErrorTolerantEditor: Increments success/failure after each method call
- Metrics instance passed to ErrorTolerantEditor constructor
- MultiTargetIndexEditorProvider creates single shared metrics instance

**Thread Safety:** AtomicLong for counters, ConcurrentHashMap for map operations.

**Decision:** Expose metrics per target type (not per index) to keep cardinality manageable.

---

## Testing Strategy

### Unit Tests

1. **IndexDefinitionHelperTest**
   - Property normalization (all combinations)
   - Validation (error cases)
   - Backward compatibility (type-only definitions)

2. **MultiTargetIndexEditorProviderTest**
   - Single target (backward compat)
   - Multiple targets (dual write)
   - Missing primary provider (fail)
   - Missing secondary provider (skip)

3. **ErrorTolerantEditorTest**
   - Exception caught and logged
   - Other editors continue
   - JMX metrics incremented

### Integration Tests

1. **MultiTargetWriteIntegrationTest**
   - Index content with storeTargets=["lucene47", "lucene9"]
   - Verify both indexes contain content
   - Query from activeTarget="lucene47" returns results
   - Query from lucene9 directly returns same results

2. **FailureHandlingIntegrationTest**
   - Primary failure blocks commit
   - Secondary failure allows commit
   - Missing secondary provider gracefully skips

3. **BackwardCompatibilityTest**
   - Existing type-only indexes continue working
   - No migration required for legacy indexes
   - Index with both `type` and `storeTargets` logs INFO, uses storeTargets

4. **ConcurrentModificationTest**
   - Start async indexing cycle
   - Modify index definition (change storeTargets mid-cycle)
   - Verify cycle completes with original targets
   - Verify next cycle uses new targets

5. **ProviderTimingTest**
   - Create index with storeTargets=["lucene47", "lucene9"]
   - Register lucene47 provider immediately
   - Delay lucene9 provider registration
   - Verify lucene47 indexes content, lucene9 skipped with warning
   - Register lucene9 provider
   - Verify next cycle indexes to both

6. **ErrorToleranceEdgeCaseTest**
   - Trigger OutOfMemoryError in secondary editor
   - Verify primary completes, secondary failure logged
   - Verify metrics incremented for secondary failure
   - Verify system remains stable (no crash)

7. **QueryRoutingTest**
   - Index with storeTargets=["lucene47", "lucene9"], activeTarget="lucene47"
   - Execute query, verify uses lucene47
   - Change activeTarget to "lucene9"
   - Execute same query, verify uses lucene9
   - Verify results match between both targets

---

## Migration Path

### Phase 1: Enable Shadow Writing

Update index definition:
```json
{
  "storeTargets": ["lucene47", "lucene9"],
  "activeTarget": "lucene47",
  "async": ["async"]
}
```

**Result:** Writes go to both, queries from lucene47. Lucene9 builds in background.

### Phase 2: Flip Queries (covered in Phase 3 design)

Update `activeTarget` to lucene9 once secondary index is built.

### Phase 3: Remove Old Target (covered in Phase 3 design)

Remove lucene47 from storeTargets once confident in lucene9.

---

## Implementation Order

1. **Constants** (oak-search)
   - Add STORE_TARGETS, ACTIVE_TARGET to FulltextIndexConstants
   - String constants only, no logic

2. **IndexDefinitionHelper + NormalizedIndexProperties** (oak-core)
   - Implement NormalizedIndexProperties immutable class
   - Implement normalize() with full validation logic
   - Unit tests for all property combinations

3. **MultiTargetIndexMetrics** (oak-core)
   - Thread-safe metrics collection
   - Unit tests for concurrent updates

4. **ErrorTolerantEditor** (oak-core)
   - Wrap delegate editor (all 8 Editor methods)
   - Catch all exceptions in safeExecute/safeExecuteWithResult
   - Pass metrics instance, increment on success/failure
   - Unit tests for all methods, exception handling, metrics updates

5. **IndexWriteOperation** (oak-core)
   - Encapsulate single-target write
   - Provider lookup by attempting getIndexEditor on each provider
   - Primary vs secondary behavior (throw vs log)
   - Pass metrics to ErrorTolerantEditor
   - Unit tests for provider routing, primary/secondary behavior

6. **MultiTargetIndexEditorProvider** (oak-core)
   - Create shared MultiTargetIndexMetrics instance
   - Orchestrate multiple IndexWriteOperations sequentially
   - Use IndexDefinitionHelper for normalization (cache result)
   - Compose editors with CompositeEditor
   - Unit tests for orchestration, normalization caching

7. **Query Engine Updates** (oak-core)
   - Update to use IndexDefinitionHelper.getActiveTarget()
   - Minimal change, backward compatible
   - Test with type-only and activeTarget configs

8. **Integration Tests** (oak-search-luceneNg)
   - End-to-end dual write scenarios
   - Verify both indexes populated
   - Test failure handling (primary vs secondary)
   - Concurrent modification test
   - Provider timing test
   - Query routing test

9. **JMX Monitoring** (oak-core)
   - Implement MultiTargetIndexMBean interface
   - Wire to shared MultiTargetIndexMetrics instance
   - Register as OSGi service
   - Can be added in follow-up commit (non-blocking)

---

## Design Decisions Summary

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Constants location | oak-search | Search-index-specific concern |
| Normalization timing | Early (at IndexDefinition load) | All code sees consistent properties |
| Primary target | First in storeTargets array | Simple, deterministic |
| Write threading | Sequential (A) | Simple, reuses async thread |
| Future parallelization | IndexWriteOperation encapsulation | Easy to wrap in Future later |
| Missing secondary provider | Log and skip | Graceful degradation |
| Missing primary provider | Fail fast | Clear error, prevents broken state |
| Secondary error handling | Catch all exceptions | Storage errors can be various types |
| JMX metrics | Per-type aggregation | Keep cardinality manageable |
| Query routing | Use activeTarget via helper | Minimal change, backward compat |

---

## Non-Goals (Deferred)

- **Parallel writes** - Design supports, but implement sequential first (simpler)
- **Index flipping validation** - Covered in Phase 3 (commit hook)
- **Cleanup of removed targets** - Covered in Phase 3 (manual initially)
- **NRT support** - Covered in Phase 4
- **Distributed coordination** - Out of scope

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Secondary writes lag primary | Acceptable - async indexing is eventually consistent anyway |
| Provider missing at runtime | Primary: fail fast with clear error. Secondary: log and skip |
| Exception in secondary blocks primary | ErrorTolerantEditor catches ALL exceptions |
| Performance impact of dual writes | Sequential writes extend async cycle but don't block user commits |
| Inconsistent state between targets | Monitoring via JMX, manual resync if needed (reindex) |

---

## Success Criteria

- ✅ Existing type-only indexes work without changes
- ✅ Can configure storeTargets with multiple types
- ✅ Primary target failures block commits
- ✅ Secondary target failures are logged and tolerated
- ✅ Missing secondary providers log warnings and skip
- ✅ Queries route to activeTarget correctly
- ✅ Both targets receive indexed content
- ✅ All tests pass (unit + integration)

---

## Follow-Up Work

After this phase completes:

1. **Phase 3: Index Flipping** - Commit hook validation for activeTarget changes
2. **Phase 3: Cleanup** - Remove old target data when removed from storeTargets
3. **Phase 4: NRT Support** - Near-real-time indexing research and implementation
4. **Performance:** Parallel writes implementation (B strategy)
5. **Monitoring:** Enhanced JMX operations for index health

---

**Status:** Ready for Implementation
**Next Step:** Implement Phase 2 Step 5 (Highlighting) to complete luceneNg query features, then implement this design
