# Lucene 4.7.2 to 9.x Migration Strategy

## Challenge

**Key Finding**: Lucene 9's `lucene-backward-codecs` library only supports reading indexes from Lucene 7.0+. It does **not** support Lucene 4.x formats directly.

## Oak's Situation

- Oak currently has **embedded Lucene 4.7.2** in `oak-lucene` module (707 source files)
- Existing production indexes are in Lucene 4.7.2 format
- Cannot use standard Lucene 9 backward-codecs to read these indexes directly

## Migration Approach

### Option 1: Two-Stage Migration (Recommended)

1. **Stage 1**: Use Oak's embedded Lucene 4.7.2 to read old indexes
2. **Stage 2**: Convert to Lucene 9 format using `IndexWriter.addIndexes()` or segment-by-segment copy

```
Lucene 4.7.2 Index (oak-lucene)
    -> Read with embedded Lucene 4
    -> Write with Lucene 9 (oak-lucene-9)
    -> Lucene 9 Index
```

### Option 2: Lucene 4 Codec Port

Port Oak's Lucene 4.7.2 codec to work with Lucene 9's codec SPI. This would require:
- Extracting codec classes from embedded Lucene
- Adapting them to Lucene 9's codec interfaces
- Registering as a custom codec in Lucene 9

**Complexity**: High - involves maintaining custom codec code

### Option 3: Intermediate Version

Use an intermediate Lucene version (5.x or 7.x) as a bridge:

```
Lucene 4.7.2 -> Lucene 5.x -> Lucene 9.x
```

**Pros**: Each step has backward compatibility support
**Cons**: Requires maintaining two upgrade paths

## Recommended Implementation

### Phase 1: Lucene 4 Reader (using oak-lucene)

Create wrapper in `oak-lucene-9` that delegates to oak-lucene for reading:

```java
public class Lucene47IndexReader {
    // Uses oak-lucene's embedded Lucene 4.7.2
    private final org.apache.lucene.index.IndexReader lucene4Reader;

    // Provides Oak SPI interface
    public Document document(int docID) {
        // Read from Lucene 4, convert to Oak SPI Document
    }
}
```

### Phase 2: Segment Converter

Create converter that reads Lucene 4 segments and writes Lucene 9 segments:

```java
public class SegmentConverter {
    public void convert(IndexDirectory source, IndexDirectory target) {
        // Open source with Lucene 4 (oak-lucene)
        // Open target with Lucene 9 (oak-lucene-9)
        // Copy documents, converting field types
    }
}
```

### Phase 3: Dual-Write Coordinator

Implement the dual-write pattern from the design:

```java
public class DualWriteCoordinator {
    private final IndexWriter lucene4Writer; // oak-lucene
    private final IndexWriter lucene9Writer; // oak-lucene-9

    public void addDocument(Document doc) {
        lucene4Writer.addDocument(convert4(doc));
        lucene9Writer.addDocument(convert9(doc));
    }
}
```

## Testing Strategy

### Unit Tests

1. **Lucene 4 Reading**: Verify we can read embedded Lucene 4 indexes
2. **Lucene 9 Writing**: Verify we can write new Lucene 9 indexes
3. **Conversion**: Verify segment-by-segment conversion preserves data

### Integration Tests

1. **Round-trip**: Write with Lucene 4, convert, read with Lucene 9
2. **Dual-write**: Verify both indexes stay in sync
3. **Switchover**: Verify queries work during migration

## Dependencies

- `oak-lucene`: Provides embedded Lucene 4.7.2 for reading old indexes
- `oak-lucene-9`: New module with Lucene 9.10.0
- `oak-search-spi`: Version-agnostic abstractions

## Next Steps

1. ✅ Create oak-search-spi with abstractions
2. ✅ Create oak-lucene-9 with Lucene 9 wrappers
3. ✅ Document migration limitations
4. ⏭️ Create Lucene47IndexReader wrapper (using oak-lucene)
5. ⏭️ Implement SegmentConverter
6. ⏭️ Add comprehensive conversion tests

## References

- [Lucene 9 Backward Codecs](https://lucene.apache.org/core/9_10_0/backward-codecs/index.html) - Only supports 7.0+
- [Implementation Plan](./2026-03-02-lucene-abstraction-implementation.md)
- [Design Document](./2026-03-02-lucene-abstraction-layer-design.md)
