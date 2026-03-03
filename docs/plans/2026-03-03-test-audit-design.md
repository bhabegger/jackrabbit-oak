# Test Audit Design - Jackrabbit Oak

**Date:** 2026-03-03
**Objective:** Identify redundant, obsolete, and low-value tests to reduce CI/CD build time

## Goals

### Primary Goal
Identify tests that are no longer useful - specifically tests that:
- Cover similar functionality (duplicates)
- Test deprecated or removed features
- Have trivial assertions with low value
- Have never failed in recent history
- Are redundant based on coverage overlap

### Success Criteria
Deliver a detailed report with data tables containing:
- Module-level timing breakdown
- List of duplicate/similar test candidates with similarity scores
- Tests flagged as potentially obsolete with reasoning
- Coverage overlap matrix for largest modules
- Prioritized recommendations (high/medium/low impact)

## Current State

**Repository Overview:**
- Multi-module Maven project: 46 modules
- Total test files: 2,440 Java test files
- Current CI build time: ~1 hour 43 minutes
- Test framework: JUnit with Surefire (unit) and Failsafe (integration)
- Profiles: coverage, integrationTesting, javadoc
- Node store fixtures: SEGMENT_TAR, DOCUMENT_NS

**Top Modules by Test Count:**
1. oak-core: 446 tests
2. oak-store-document: 353 tests
3. oak-jcr: 236 tests
4. oak-segment-tar: 208 tests
5. oak-lucene: 152 tests

## Architecture

### Four-Phase Hybrid Approach

**Phase 1: CI Log Mining**
Extract timing and execution data from existing GitHub Actions logs without running tests.

**Phase 2: Static Code Analysis**
Analyze test code structure, similarity, and quality patterns across all 2,440 tests.

**Phase 3: Git History Analysis**
Mine repository history for test stability, maintenance patterns, and failure rates.

**Phase 4: Targeted Coverage Analysis**
Run focused coverage instrumentation on high-priority modules identified in phases 1-3.

### Data Flow

```
CI Logs → Timing Data (JSON)
                ↓
Test Files → Static Analysis → Similarity Data (JSON)
                ↓
Git History → Change Patterns (JSON)
                ↓
        Correlation Engine
                ↓
        Scoring Algorithm
                ↓
    Prioritized Candidates (JSON)
                ↓
High-Priority Modules → Targeted Coverage → Overlap Data (JSON)
                ↓
        Final Correlation
                ↓
        Report Generator
                ↓
        Markdown Report
```

## Phase Specifications

### Phase 1: CI Log Mining

**Input:** GitHub Actions build logs from recent runs

**Process:**
1. Fetch recent successful build logs via GitHub API or WebFetch
2. Parse Maven output for:
   - Module build times (including test execution)
   - Test counts per module
   - Build profiles and fixture configuration
3. Extract timing patterns across multiple builds for consistency

**Output:** `timing-data.json`
```json
{
  "modules": [
    {
      "name": "oak-core",
      "testCount": 446,
      "executionTime": "12m 34s",
      "executionTimeSeconds": 754
    }
  ],
  "totalBuildTime": "1h 43m 7s"
}
```

**Tools:** WebFetch for GitHub Actions logs, Bash for parsing, Python/Node script for JSON generation

### Phase 2: Static Code Analysis

**Input:** All test files in `*/src/test/java/**/*.java`

**Process:**
1. **Code Similarity Detection:**
   - Parse test files to AST (Abstract Syntax Tree)
   - Compare test method structure and logic
   - Calculate similarity scores using metrics:
     - Identical setup/teardown code
     - Similar assertion sequences
     - Shared test data patterns

2. **Assertion Quality Analysis:**
   - Flag tests with only trivial assertions (assertNotNull, assertTrue without context)
   - Identify empty or stub test bodies
   - Count meaningful assertions vs total assertions

3. **Deprecation Detection:**
   - Search for @Deprecated annotations in tested code
   - Find references to known-removed features
   - Identify tests for old API versions

4. **Naming Pattern Analysis:**
   - Detect duplicate test names across modules
   - Find versioned tests (testFeatureV1, testFeatureV2) suggesting evolution

**Output:** `static-analysis.json`
```json
{
  "similarTests": [
    {
      "group": [
        "oak-core/src/test/java/.../TestA.java:testMethod1",
        "oak-jcr/src/test/java/.../TestB.java:testMethod2"
      ],
      "similarityScore": 0.87,
      "reason": "Identical setup code and assertion sequence"
    }
  ],
  "trivialTests": [
    {
      "test": "oak-api/src/test/java/.../SimpleTest.java:testNotNull",
      "reason": "Only assertNotNull, no meaningful verification"
    }
  ],
  "deprecatedFeatureTests": [...]
}
```

**Tools:**
- JavaParser or similar AST library for parsing
- Python/Node script for analysis logic
- Levenshtein distance or tree-edit distance for similarity

### Phase 3: Git History Analysis

**Input:** Git repository history

**Process:**
1. **Test Stability Analysis:**
   - For each test file, extract:
     - Last modification date
     - Commit frequency
     - Related issue/PR numbers
   - Query CI history for failure patterns (if available via GitHub API)

2. **Staleness Detection:**
   - Identify tests unchanged for 2+ years
   - Find tests created for resolved issues that haven't been touched since

3. **Maintenance Burden:**
   - Calculate churn rate (how often tests are modified)
   - Correlate with bug fix commits vs feature commits

**Output:** `history-analysis.json`
```json
{
  "staleTests": [
    {
      "test": "oak-commons/src/test/java/.../OldTest.java",
      "lastModified": "2022-03-15",
      "ageYears": 4.0,
      "commitCount": 1,
      "relatedIssues": ["OAK-1234"]
    }
  ],
  "highChurnTests": [...],
  "alwaysPassingTests": [...]
}
```

**Tools:** Git log parsing via Bash, GitHub API for CI results

### Phase 4: Targeted Coverage Analysis

**Input:**
- Results from Phases 1-3
- Focus modules: oak-core, oak-store-document, oak-jcr (top 3 by test count and likely time)

**Process:**
1. Run JaCoCo instrumented test execution on selected modules
2. Generate per-test coverage data
3. Build coverage overlap matrix:
   - Which lines are covered by multiple tests
   - Which tests have >80% coverage overlap
4. Correlate with timing data to identify slow redundant tests

**Output:** `coverage-overlap.json`
```json
{
  "overlappingTests": [
    {
      "tests": [
        "oak-core/.../TestA.java:method1",
        "oak-core/.../TestB.java:method2"
      ],
      "overlapPercentage": 85,
      "sharedLines": 234,
      "executionTimes": ["2.3s", "3.1s"]
    }
  ]
}
```

**Tools:** Maven with JaCoCo plugin, custom analysis script

## Correlation and Scoring

### Scoring Algorithm

Each test receives a "redundancy score" (0-100) based on weighted criteria:

**High Weight (30 points each):**
- High code similarity with another test (>80% similar)
- High coverage overlap (>80% overlap)

**Medium Weight (20 points each):**
- Trivial assertions only
- Tests deprecated features
- Stale (unchanged 2+ years)

**Low Weight (10 points each):**
- Always passing (no failures in history)
- Low assertion count
- Long execution time with overlap

**Thresholds:**
- 70-100: High confidence redundant
- 40-69: Medium confidence, manual review needed
- 0-39: Low confidence, likely valuable

### Output

`redundancy-report.json` with ranked list of test candidates for removal.

## Deliverable: Audit Report

Markdown report structure:

```
# Jackrabbit Oak Test Audit Report

## Executive Summary
- Total tests analyzed: 2,440
- Redundancy candidates: X tests
- Potential time savings: Y minutes
- Recommended actions: Z tests for removal

## Module-Level Analysis
[Table with module, test count, execution time, recommendations]

## High-Confidence Redundant Tests
[Detailed table with test name, reason, similarity score, recommendation]

## Medium-Confidence Candidates
[Table requiring manual review]

## Coverage Overlap Analysis
[Matrix/heatmap for top modules]

## Recommendations
### Immediate Actions (High Impact, Low Risk)
### Follow-up Actions (Medium Impact, Requires Review)
### Long-term Considerations

## Appendices
- Methodology details
- Scoring algorithm
- Raw data references
```

## Implementation Tools

**Languages/Scripts:**
- Python or Node.js for analysis scripts
- Bash for git and log parsing
- Java for optional AST parsing via JavaParser library

**Dependencies:**
- JavaParser or Eclipse JDT for AST analysis
- JaCoCo Maven plugin for coverage
- GitHub API access for CI logs (optional: gh CLI)

**Deliverables:**
- `scripts/` directory with all analysis tools
- `output/` directory with JSON data files
- `docs/reports/test-audit-report.md` final report

## Execution Plan

Will be detailed in the implementation plan (next step).

**Estimated Timeline:**
- Phase 1 (CI Mining): 30-60 minutes
- Phase 2 (Static Analysis): 2-4 hours
- Phase 3 (History Analysis): 1-2 hours
- Phase 4 (Coverage): 2-4 hours (includes test execution)
- Correlation & Report: 1-2 hours
- **Total: 6-13 hours** depending on automation level

## Risks and Mitigations

**Risk:** False positives - flagging valuable tests as redundant
**Mitigation:** Use scoring thresholds, require multiple indicators, include manual review step

**Risk:** Incomplete CI log data
**Mitigation:** Fall back to local test execution with timing instrumentation

**Risk:** Coverage analysis takes too long
**Mitigation:** Limit to top 3-5 modules only, use sampling if needed

**Risk:** Test removal breaks build
**Mitigation:** This audit only identifies candidates; actual removal requires separate review and testing

## Success Metrics

**Immediate:**
- Report delivered with actionable recommendations
- At least 50 high-confidence redundant test candidates identified
- Clear prioritization for next steps

**Long-term:**
- CI build time reduced by at least 10-20 minutes
- Improved test suite maintainability
- Repeatable audit process for future use
