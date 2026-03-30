# oak-search-luceneNg — Open Items

This document tracks known gaps and pending work on the `oak-12089-lucene9-suggest` branch and beyond.

---

## Suggest / Spellcheck branch (`oak-12089-lucene9-suggest`)

### ACL filtering for suggestions and spellcheck

**Status:** Not implemented.

Legacy `LucenePropertyIndex` re-verifies each candidate (suggestion or spellcheck result) against actual index documents using `filter.isAccessible(path)`. A suggestion is only emitted if at least one document containing it is readable by the querying user.

Our implementation checks only path restrictions (`hasMatchingDoc`), not ACL. A user could receive spellcheck or suggestion results that only appear in content they cannot read.

**What to do:**
- Implement ACL-aware document verification in `executeSuggest()` and `executeSpellcheck()`, checking `filter.isAccessible(prefix + doc.get(FieldNames.PATH))` after fetching matching document paths.
- Add tests to a shared common suite (`SpellcheckCommonTest` / `IndexSuggestionCommonTest`) covering the case where the suggested word only appears in ACL-restricted content, so the result is suppressed. These tests should run against all index backends (lucene, lucene9, elastic).
- Note: `LuceneSpellcheckCommonTest` is currently `@Ignore` with a TODO comment — fixing ACL and adding coverage would be the opportunity to remove that ignore.

### Deduplication via re-verification (suggestions)

**Status:** Worked around with `LinkedHashSet` deduplication.

Legacy gets implicit deduplication because it re-verifies each suggestion against real documents and breaks on the first accessible hit — so the same suggestion text is never added twice. Our implementation skips that re-verification loop for suggestions, and compensates with an explicit set. The correct fix is to implement the same document re-verification for suggestions (which also resolves ACL — see above), at which point the `LinkedHashSet` can be removed.

### Multi-word spellcheck (`multipleWords` test)

**Status:** Not implemented. Test is currently failing.

`SPELLCHECK('votin in ontari')` should return `voting in ontario` by correcting each word in the phrase independently and recombining. `DirectSpellChecker` only operates on individual terms, so the phrase must be split, each word corrected, and the results joined.

Note: `LuceneSpellcheckCommonTest` is `@Ignore` in legacy with a similar TODO, so this is a pre-existing open item across all backends.

---

## Core branch (`oak-12089-lucene9-core`) — already filed for a separate commit

### Function-based property restrictions (`localname()`, `path()`)

**Status:** Not implemented. This is a **critical functional gap**.

Oak's query engine translates `localname()` and `fn:local-name()` into a property restriction on `":localname"`. Legacy `LucenePropertyIndex` handles this via `createNodeNameQuery()`, searching the `FieldNames.NODE_NAME` (`StringField`) field.

In `LuceneNgIndex`:
- The `NODE_NAME` field is never written by `LuceneNgIndexEditor`.
- `createPropertyQuery()` has no handler for `":localname"` or `":path"` restrictions — they fall through silently.

**Queries that break:**
- `SELECT * FROM [nt:base] WHERE localname() = 'jcr:content'`
- `SELECT * FROM [nt:base] WHERE localname() LIKE 'jcr:%'`
- Any index configured with `indexNodeName=true`

**What to do:**
- Write `FieldNames.NODE_NAME` (a `StringField`) in `LuceneNgIndexEditor.indexNode()` when `indexingRule.isNodeNameIndexed()`.
- Handle `":localname"` restrictions in `LuceneNgIndex.createPropertyQuery()` using the same `TermQuery` / `WildcardQuery` pattern as legacy.
