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
| Index augmentors [^1] | ✓ | ✗ | ✗ |
| NRT / hybrid indexing | ✓ | ✗ | ✗ |
| Index copier (CopyOnRead/Write) | ✓ | ✗ | ✗ |
| Composite node store queries [^2] | ✓ | ✗ | ✗ |
| Inference / vector search | ✗ | ✓ | ✗ |

[^1]: Index augmentors are OSGi services (`IndexFieldProvider`, `FulltextQueryTermsProvider`) that let third-party code inject additional fields into indexed documents or expand fulltext queries, without modifying the index definition.
[^2]: When the repository is backed by a composite node store (e.g. a read-only `/apps`+`/libs` mount combined with a writeable store), the Lucene index runs one query per mount and merges the results. This feature is not required for a single-store deployment.
