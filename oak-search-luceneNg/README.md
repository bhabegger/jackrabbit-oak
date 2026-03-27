# oak-search-luceneNg

Lucene 9 index provider for Oak (`type="lucene9"`).

## Feature parity

| Feature | Legacy Lucene | Elastic | LuceneNg |
|---|---|---|---|
| Property restrictions, path/type filters | ✓ | ✓ | ✓ |
| Fulltext search | ✓ | ✓ | ✓ |
| Facets (insecure / statistical / secure) | ✓ | ✓ | ✓ |
| Excerpts | ✓ | ✓ | ✓ |
| Ordering / sorting | ✓ | ✓ | ✓ |
| Suggestions | ✓ | ✓ | ✗ |
| Spellcheck | ✓ | ✓ | ✗ |
| Similarity / More Like This | ✓ | ✓ (+ KNN) | ✗ |
| Native queries | ✓ | ✓ | ✗ |
| Index statistics / JMX | ✓ | ✓ | ✗ |
| Index augmentors | ✓ | ✗ | ✗ |
| NRT / hybrid indexing | ✓ | ✗ | ✗ |
| Index copier (CopyOnRead/Write) | ✓ | ✗ | ✗ |
| Multi-index queries | ✓ | ✗ | ✗ |
| Inference / vector search | ✗ | ✓ | ✗ |
