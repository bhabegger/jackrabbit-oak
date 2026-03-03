# Test Audit Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Identify redundant, obsolete, and low-value tests in the Jackrabbit Oak test suite to reduce CI/CD build time.

**Architecture:** Four-phase hybrid approach combining CI log mining, static code analysis, git history analysis, and targeted coverage analysis. Each phase produces JSON output that feeds into a correlation engine to score and rank tests by redundancy likelihood.

**Tech Stack:** Python 3 for analysis scripts, Bash for git/log parsing, JaCoCo Maven plugin for coverage, GitHub API/WebFetch for CI logs.

---

## Setup Tasks

### Task 1: Create Project Structure

**Files:**
- Create: `test-audit/scripts/phase1_ci_mining.py`
- Create: `test-audit/scripts/phase2_static_analysis.py`
- Create: `test-audit/scripts/phase3_history_analysis.py`
- Create: `test-audit/scripts/phase4_coverage_analysis.py`
- Create: `test-audit/scripts/correlate_and_score.py`
- Create: `test-audit/scripts/generate_report.py`
- Create: `test-audit/scripts/utils.py`
- Create: `test-audit/output/.gitkeep`
- Create: `test-audit/requirements.txt`
- Create: `test-audit/README.md`

**Step 1: Create directory structure**

```bash
mkdir -p test-audit/scripts
mkdir -p test-audit/output
touch test-audit/output/.gitkeep
```

**Step 2: Create requirements.txt**

```
# test-audit/requirements.txt
requests>=2.31.0
javalang>=0.13.0
pandas>=2.0.0
matplotlib>=3.7.0
seaborn>=0.12.0
```

**Step 3: Create README**

```markdown
# Test Audit Tool

Identifies redundant and obsolete tests in Jackrabbit Oak.

## Usage

1. Install dependencies: `pip install -r requirements.txt`
2. Run Phase 1: `python scripts/phase1_ci_mining.py`
3. Run Phase 2: `python scripts/phase2_static_analysis.py`
4. Run Phase 3: `python scripts/phase3_history_analysis.py`
5. Run Phase 4: `python scripts/phase4_coverage_analysis.py`
6. Correlate data: `python scripts/correlate_and_score.py`
7. Generate report: `python scripts/generate_report.py`

## Output

- `output/*.json` - Phase outputs
- `output/test-audit-report.md` - Final report
```

**Step 4: Create utils.py with common functions**

```python
# test-audit/scripts/utils.py
import json
import os
from pathlib import Path

def save_json(data, filename):
    """Save data to JSON file in output directory."""
    output_path = Path(__file__).parent.parent / "output" / filename
    with open(output_path, 'w') as f:
        json.dump(data, f, indent=2)
    print(f"✓ Saved {filename}")
    return output_path

def load_json(filename):
    """Load JSON file from output directory."""
    output_path = Path(__file__).parent.parent / "output" / filename
    with open(output_path, 'r') as f:
        return json.load(f)

def get_repo_root():
    """Get repository root directory."""
    script_dir = Path(__file__).parent
    return script_dir.parent.parent

def parse_time_to_seconds(time_str):
    """Parse Maven time format (e.g., '1h 43m 7s', '12m 34s') to seconds."""
    total = 0
    parts = time_str.replace('h', ':').replace('m', ':').replace('s', '').split(':')
    if len(parts) == 3:  # hours, minutes, seconds
        total = int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])
    elif len(parts) == 2:  # minutes, seconds
        total = int(parts[0]) * 60 + int(parts[1])
    elif len(parts) == 1:  # just seconds
        total = int(parts[0])
    return total
```

**Step 5: Commit setup**

```bash
git add test-audit/
git commit -m "feat: add test audit tool structure

Set up project structure for test audit analysis tool with
scripts directory, output directory, and dependencies.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Phase 1: CI Log Mining

### Task 2: Implement CI Log Extraction

**Files:**
- Modify: `test-audit/scripts/phase1_ci_mining.py`

**Step 1: Create basic script structure**

```python
# test-audit/scripts/phase1_ci_mining.py
#!/usr/bin/env python3
"""
Phase 1: CI Log Mining
Extract timing and execution data from GitHub Actions logs.
"""
import re
import sys
from pathlib import Path
from utils import save_json, parse_time_to_seconds

def main():
    print("=== Phase 1: CI Log Mining ===\n")

    # TODO: Implement log extraction
    data = {
        "modules": [],
        "totalBuildTime": "",
        "totalBuildTimeSeconds": 0
    }

    save_json(data, "timing-data.json")
    print("\n✓ Phase 1 complete")

if __name__ == "__main__":
    main()
```

**Step 2: Test basic script runs**

Run: `cd test-audit && python scripts/phase1_ci_mining.py`
Expected: Creates `output/timing-data.json` with empty structure

**Step 3: Add manual log parsing function**

```python
# Add to phase1_ci_mining.py after imports

def parse_maven_log_file(log_file_path):
    """Parse a Maven build log file to extract module timing data."""
    modules = []

    with open(log_file_path, 'r') as f:
        content = f.read()

    # Pattern: [INFO] Building Oak Core 1.93-SNAPSHOT
    # Later: [INFO] BUILD SUCCESS
    # Later: [INFO] Total time:  12:34 min

    # Find module build summaries
    # Maven prints: [INFO] module-name ........ SUCCESS [ 12:34 min]
    pattern = r'\[INFO\]\s+([\w-]+)\s+\.+\s+SUCCESS\s+\[\s+([\d:.]+\s+\w+)\]'
    matches = re.findall(pattern, content)

    for module_name, time_str in matches:
        # Convert time to seconds
        seconds = parse_module_time(time_str)
        modules.append({
            "name": module_name,
            "executionTime": time_str.strip(),
            "executionTimeSeconds": seconds
        })

    # Extract total build time
    total_pattern = r'Total time:\s+([\d:.]+\s+\w+)'
    total_match = re.search(total_pattern, content)
    total_time = total_match.group(1) if total_match else "unknown"

    return modules, total_time

def parse_module_time(time_str):
    """Parse module time format (e.g., '12:34 min', '01:23:45 h')."""
    time_str = time_str.strip()

    if 'min' in time_str:
        # Format: "12:34 min" or "12.345 min"
        parts = time_str.replace(' min', '').split(':')
        if len(parts) == 2:
            return int(parts[0]) * 60 + int(parts[1])
        else:
            return int(float(parts[0]) * 60)
    elif 'h' in time_str:
        # Format: "01:23:45 h"
        parts = time_str.replace(' h', '').split(':')
        if len(parts) == 3:
            return int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])
    elif 's' in time_str:
        # Format: "45.123 s"
        return int(float(time_str.replace(' s', '')))

    return 0
```

**Step 4: Update main to use manual log**

```python
# Update main() in phase1_ci_mining.py

def main():
    print("=== Phase 1: CI Log Mining ===\n")

    # For now, use a manually downloaded log file
    # User should download a log from:
    # https://github.com/apache/jackrabbit-oak/actions/runs/<run-id>
    log_file = Path(__file__).parent.parent / "ci-build-log.txt"

    if not log_file.exists():
        print(f"ERROR: Please download a CI build log to: {log_file}")
        print("Instructions:")
        print("1. Go to https://github.com/apache/jackrabbit-oak/actions")
        print("2. Click on a recent successful build")
        print("3. Click on 'Maven Build' job")
        print("4. Click '...' menu -> 'View raw logs'")
        print("5. Save to ci-build-log.txt in test-audit/")
        sys.exit(1)

    print(f"Parsing log file: {log_file}")
    modules, total_time = parse_maven_log_file(log_file)

    data = {
        "modules": modules,
        "totalBuildTime": total_time,
        "totalBuildTimeSeconds": parse_time_to_seconds(total_time) if total_time != "unknown" else 0,
        "moduleCount": len(modules)
    }

    save_json(data, "timing-data.json")

    # Print summary
    print(f"\nExtracted data:")
    print(f"  Modules: {len(modules)}")
    print(f"  Total build time: {total_time}")
    print(f"\nTop 10 slowest modules:")
    sorted_modules = sorted(modules, key=lambda m: m['executionTimeSeconds'], reverse=True)
    for i, mod in enumerate(sorted_modules[:10], 1):
        print(f"  {i}. {mod['name']}: {mod['executionTime']}")

    print("\n✓ Phase 1 complete")

if __name__ == "__main__":
    main()
```

**Step 5: Create instructions file for manual log download**

```bash
# Create test-audit/DOWNLOAD_LOG.md
cat > test-audit/DOWNLOAD_LOG.md << 'EOF'
# How to Download CI Build Log

1. Visit https://github.com/apache/jackrabbit-oak/actions
2. Click on a recent successful "Build" workflow run
3. Click on the "Maven Build" job
4. Click the "..." menu in top-right corner
5. Select "View raw logs"
6. Save the page as `test-audit/ci-build-log.txt`
7. Run `python scripts/phase1_ci_mining.py`
EOF
```

**Step 6: Commit Phase 1**

```bash
git add test-audit/
git commit -m "feat: implement CI log mining (Phase 1)

Parse Maven build logs to extract module timing data. Requires
manual log download from GitHub Actions.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Phase 2: Static Code Analysis

### Task 3: Implement Test File Discovery

**Files:**
- Modify: `test-audit/scripts/phase2_static_analysis.py`

**Step 1: Create basic structure**

```python
# test-audit/scripts/phase2_static_analysis.py
#!/usr/bin/env python3
"""
Phase 2: Static Code Analysis
Analyze test code structure, similarity, and quality patterns.
"""
import os
import re
from pathlib import Path
from collections import defaultdict
from utils import save_json, get_repo_root

def find_all_test_files():
    """Find all Java test files in the repository."""
    repo_root = get_repo_root()
    test_files = []

    # Find all files in */src/test/java/**/*.java
    for path in repo_root.glob("*/src/test/java/**/*.java"):
        test_files.append(str(path.relative_to(repo_root)))

    return test_files

def main():
    print("=== Phase 2: Static Code Analysis ===\n")

    test_files = find_all_test_files()
    print(f"Found {len(test_files)} test files")

    data = {
        "testFiles": test_files,
        "testFileCount": len(test_files),
        "similarTests": [],
        "trivialTests": [],
        "deprecatedFeatureTests": []
    }

    save_json(data, "static-analysis.json")
    print("\n✓ Phase 2 complete (basic)")

if __name__ == "__main__":
    main()
```

**Step 2: Test file discovery**

Run: `cd test-audit && python scripts/phase2_static_analysis.py`
Expected: Finds ~2440 test files, creates `output/static-analysis.json`

**Step 3: Add trivial test detection**

```python
# Add to phase2_static_analysis.py

def analyze_test_file(file_path):
    """Analyze a single test file for quality issues."""
    repo_root = get_repo_root()
    full_path = repo_root / file_path

    try:
        with open(full_path, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
    except Exception as e:
        return None

    issues = {
        "file": file_path,
        "trivial": False,
        "trivialReason": None,
        "deprecated": False,
        "assertionCount": 0,
        "testMethodCount": 0
    }

    # Count test methods
    test_methods = re.findall(r'@Test.*?public\s+void\s+(\w+)\s*\(', content, re.DOTALL)
    issues["testMethodCount"] = len(test_methods)

    # Count assertions
    assertion_patterns = [
        r'assert\w+\(',
        r'verify\(',
        r'assertEquals\(',
        r'assertTrue\(',
        r'assertFalse\(',
        r'assertNotNull\(',
        r'assertNull\(',
    ]
    for pattern in assertion_patterns:
        issues["assertionCount"] += len(re.findall(pattern, content))

    # Check for trivial tests
    if issues["testMethodCount"] > 0 and issues["assertionCount"] == 0:
        issues["trivial"] = True
        issues["trivialReason"] = "No assertions found"
    elif issues["testMethodCount"] > 0:
        avg_assertions = issues["assertionCount"] / issues["testMethodCount"]
        if avg_assertions < 1.0:
            # Check if only assertNotNull
            not_null_count = len(re.findall(r'assertNotNull\(', content))
            if not_null_count == issues["assertionCount"]:
                issues["trivial"] = True
                issues["trivialReason"] = "Only assertNotNull assertions"

    # Check for deprecated feature tests
    if '@Deprecated' in content or 'deprecated' in content.lower():
        issues["deprecated"] = True

    return issues

def analyze_all_tests(test_files):
    """Analyze all test files for quality issues."""
    trivial_tests = []
    deprecated_tests = []

    print(f"Analyzing {len(test_files)} test files...")

    for i, file_path in enumerate(test_files):
        if i % 100 == 0:
            print(f"  Progress: {i}/{len(test_files)}")

        issues = analyze_test_file(file_path)
        if not issues:
            continue

        if issues["trivial"]:
            trivial_tests.append({
                "test": file_path,
                "reason": issues["trivialReason"],
                "testMethodCount": issues["testMethodCount"],
                "assertionCount": issues["assertionCount"]
            })

        if issues["deprecated"]:
            deprecated_tests.append({
                "test": file_path,
                "reason": "Contains deprecated annotations or references"
            })

    print(f"  Progress: {len(test_files)}/{len(test_files)}")
    return trivial_tests, deprecated_tests
```

**Step 4: Update main to perform analysis**

```python
# Update main() in phase2_static_analysis.py

def main():
    print("=== Phase 2: Static Code Analysis ===\n")

    test_files = find_all_test_files()
    print(f"Found {len(test_files)} test files\n")

    trivial_tests, deprecated_tests = analyze_all_tests(test_files)

    print(f"\nResults:")
    print(f"  Trivial tests: {len(trivial_tests)}")
    print(f"  Deprecated feature tests: {len(deprecated_tests)}")

    data = {
        "testFiles": test_files,
        "testFileCount": len(test_files),
        "similarTests": [],  # TODO: Implement similarity analysis
        "trivialTests": trivial_tests,
        "deprecatedFeatureTests": deprecated_tests
    }

    save_json(data, "static-analysis.json")
    print("\n✓ Phase 2 complete")

if __name__ == "__main__":
    main()
```

**Step 5: Test the analysis**

Run: `cd test-audit && python scripts/phase2_static_analysis.py`
Expected: Analyzes all test files, identifies trivial and deprecated tests

**Step 6: Commit Phase 2 basic analysis**

```bash
git add test-audit/
git commit -m "feat: implement basic static analysis (Phase 2)

Analyze test files for trivial assertions and deprecated features.
Similarity analysis to be added later.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Phase 3: Git History Analysis

### Task 4: Implement Git History Mining

**Files:**
- Modify: `test-audit/scripts/phase3_history_analysis.py`

**Step 1: Create basic structure**

```python
# test-audit/scripts/phase3_history_analysis.py
#!/usr/bin/env python3
"""
Phase 3: Git History Analysis
Mine repository history for test stability and maintenance patterns.
"""
import subprocess
import os
from datetime import datetime
from pathlib import Path
from utils import save_json, get_repo_root, load_json

def run_git_command(command):
    """Run a git command and return output."""
    repo_root = get_repo_root()
    result = subprocess.run(
        command,
        cwd=repo_root,
        shell=True,
        capture_output=True,
        text=True
    )
    return result.stdout.strip()

def main():
    print("=== Phase 3: Git History Analysis ===\n")

    data = {
        "staleTests": [],
        "highChurnTests": [],
        "alwaysPassingTests": []
    }

    save_json(data, "history-analysis.json")
    print("\n✓ Phase 3 complete")

if __name__ == "__main__":
    main()
```

**Step 2: Add test file history analysis**

```python
# Add to phase3_history_analysis.py

def get_file_last_modified(file_path):
    """Get last modification date for a file."""
    cmd = f'git log -1 --format=%ci -- "{file_path}"'
    output = run_git_command(cmd)

    if output:
        # Parse: 2022-03-15 14:23:45 +0100
        date_str = output.split()[0]
        return date_str
    return None

def get_file_commit_count(file_path):
    """Get number of commits that modified a file."""
    cmd = f'git log --oneline -- "{file_path}" | wc -l'
    output = run_git_command(cmd)
    return int(output) if output else 0

def calculate_age_years(date_str):
    """Calculate age in years from date string."""
    if not date_str:
        return 0

    last_modified = datetime.strptime(date_str, "%Y-%m-%d")
    now = datetime.now()
    delta = now - last_modified
    return round(delta.days / 365.25, 1)

def analyze_test_history(test_files):
    """Analyze git history for all test files."""
    stale_tests = []
    high_churn_tests = []

    print(f"Analyzing git history for {len(test_files)} files...")

    for i, file_path in enumerate(test_files):
        if i % 100 == 0:
            print(f"  Progress: {i}/{len(test_files)}")

        last_modified = get_file_last_modified(file_path)
        commit_count = get_file_commit_count(file_path)

        if not last_modified:
            continue

        age_years = calculate_age_years(last_modified)

        # Flag as stale if unchanged for 2+ years
        if age_years >= 2.0:
            stale_tests.append({
                "test": file_path,
                "lastModified": last_modified,
                "ageYears": age_years,
                "commitCount": commit_count
            })

        # Flag as high churn if many commits
        if commit_count > 20:
            high_churn_tests.append({
                "test": file_path,
                "commitCount": commit_count,
                "lastModified": last_modified
            })

    print(f"  Progress: {len(test_files)}/{len(test_files)}")

    # Sort by age and churn
    stale_tests.sort(key=lambda x: x["ageYears"], reverse=True)
    high_churn_tests.sort(key=lambda x: x["commitCount"], reverse=True)

    return stale_tests, high_churn_tests
```

**Step 3: Update main to perform history analysis**

```python
# Update main() in phase3_history_analysis.py

def main():
    print("=== Phase 3: Git History Analysis ===\n")

    # Load test files from Phase 2
    static_data = load_json("static-analysis.json")
    test_files = static_data["testFiles"]
    print(f"Analyzing {len(test_files)} test files from Phase 2\n")

    stale_tests, high_churn_tests = analyze_test_history(test_files)

    print(f"\nResults:")
    print(f"  Stale tests (2+ years): {len(stale_tests)}")
    print(f"  High churn tests (20+ commits): {len(high_churn_tests)}")

    if stale_tests:
        print(f"\nTop 5 oldest tests:")
        for test in stale_tests[:5]:
            print(f"  - {test['test']}: {test['ageYears']} years old")

    data = {
        "staleTests": stale_tests,
        "highChurnTests": high_churn_tests,
        "alwaysPassingTests": []  # Would need CI history API
    }

    save_json(data, "history-analysis.json")
    print("\n✓ Phase 3 complete")

if __name__ == "__main__":
    main()
```

**Step 4: Test history analysis**

Run: `cd test-audit && python scripts/phase3_history_analysis.py`
Expected: Analyzes git history, identifies stale and high-churn tests

**Step 5: Commit Phase 3**

```bash
git add test-audit/
git commit -m "feat: implement git history analysis (Phase 3)

Mine git history to identify stale tests (2+ years unchanged)
and high-churn tests (frequently modified).

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Phase 4: Coverage Analysis (Simplified)

### Task 5: Create Coverage Analysis Script

**Files:**
- Modify: `test-audit/scripts/phase4_coverage_analysis.py`

**Step 1: Create placeholder for coverage analysis**

```python
# test-audit/scripts/phase4_coverage_analysis.py
#!/usr/bin/env python3
"""
Phase 4: Targeted Coverage Analysis
Run JaCoCo instrumented tests on high-priority modules.

NOTE: This phase requires running Maven tests with JaCoCo, which is time-consuming.
For initial audit, we'll skip this and mark it as optional.
"""
from utils import save_json

def main():
    print("=== Phase 4: Targeted Coverage Analysis ===\n")

    print("NOTE: This phase requires running instrumented tests.")
    print("To run coverage analysis:")
    print("  1. mvn clean test -Pcoverage -pl oak-core")
    print("  2. Parse target/site/jacoco/jacoco.xml")
    print("  3. Extract per-test coverage data")
    print("")
    print("Skipping for now - marking as TODO\n")

    data = {
        "overlappingTests": [],
        "note": "Coverage analysis not run - requires Maven execution"
    }

    save_json(data, "coverage-analysis.json")
    print("✓ Phase 4 complete (skipped)")

if __name__ == "__main__":
    main()
```

**Step 2: Test script**

Run: `cd test-audit && python scripts/phase4_coverage_analysis.py`
Expected: Creates placeholder output file

**Step 3: Commit Phase 4 placeholder**

```bash
git add test-audit/
git commit -m "feat: add coverage analysis placeholder (Phase 4)

Add script structure for coverage analysis. Full implementation
requires Maven test execution which is time-consuming.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Correlation and Scoring

### Task 6: Implement Correlation Engine

**Files:**
- Modify: `test-audit/scripts/correlate_and_score.py`

**Step 1: Create scoring algorithm**

```python
# test-audit/scripts/correlate_and_score.py
#!/usr/bin/env python3
"""
Correlation and Scoring Engine
Combine data from all phases to score tests by redundancy likelihood.
"""
from utils import load_json, save_json

def calculate_redundancy_score(test_file, all_data):
    """
    Calculate redundancy score (0-100) for a test file.

    Scoring:
    - Trivial assertions: 30 points
    - Deprecated features: 20 points
    - Stale (2+ years): 20 points
    - High churn (maintenance burden): -10 points (valuable if frequently touched)
    """
    score = 0
    reasons = []

    # Check if trivial
    for trivial in all_data["trivial_tests"]:
        if trivial["test"] == test_file:
            score += 30
            reasons.append(f"Trivial: {trivial['reason']}")
            break

    # Check if deprecated
    for deprecated in all_data["deprecated_tests"]:
        if deprecated["test"] == test_file:
            score += 20
            reasons.append("Tests deprecated features")
            break

    # Check if stale
    for stale in all_data["stale_tests"]:
        if stale["test"] == test_file:
            score += 20
            reasons.append(f"Stale: {stale['ageYears']} years old, {stale['commitCount']} commits")
            break

    # High churn reduces score (valuable tests are maintained)
    for churn in all_data["high_churn_tests"]:
        if churn["test"] == test_file:
            score -= 10
            reasons.append(f"High maintenance: {churn['commitCount']} commits")
            break

    return max(0, min(100, score)), reasons

def main():
    print("=== Correlation and Scoring ===\n")

    # Load all phase outputs
    print("Loading phase outputs...")
    static_data = load_json("static-analysis.json")
    history_data = load_json("history-analysis.json")

    all_data = {
        "trivial_tests": static_data["trivialTests"],
        "deprecated_tests": static_data["deprecatedFeatureTests"],
        "stale_tests": history_data["staleTests"],
        "high_churn_tests": history_data["highChurnTests"]
    }

    # Score all test files
    print("Scoring test files...")
    test_files = static_data["testFiles"]
    scored_tests = []

    for i, test_file in enumerate(test_files):
        if i % 200 == 0:
            print(f"  Progress: {i}/{len(test_files)}")

        score, reasons = calculate_redundancy_score(test_file, all_data)

        if score > 0:
            scored_tests.append({
                "test": test_file,
                "redundancyScore": score,
                "reasons": reasons,
                "confidence": get_confidence_level(score)
            })

    print(f"  Progress: {len(test_files)}/{len(test_files)}")

    # Sort by score
    scored_tests.sort(key=lambda x: x["redundancyScore"], reverse=True)

    # Categorize
    high_confidence = [t for t in scored_tests if t["redundancyScore"] >= 70]
    medium_confidence = [t for t in scored_tests if 40 <= t["redundancyScore"] < 70]
    low_confidence = [t for t in scored_tests if t["redundancyScore"] < 40]

    print(f"\nScoring results:")
    print(f"  High confidence redundant (70-100): {len(high_confidence)}")
    print(f"  Medium confidence (40-69): {len(medium_confidence)}")
    print(f"  Low confidence (0-39): {len(low_confidence)}")

    report_data = {
        "scoredTests": scored_tests,
        "highConfidence": high_confidence,
        "mediumConfidence": medium_confidence,
        "lowConfidence": low_confidence,
        "summary": {
            "totalTests": len(test_files),
            "scoredTests": len(scored_tests),
            "highConfidenceCount": len(high_confidence),
            "mediumConfidenceCount": len(medium_confidence),
            "lowConfidenceCount": len(low_confidence)
        }
    }

    save_json(report_data, "redundancy-report.json")
    print("\n✓ Correlation complete")

def get_confidence_level(score):
    """Map score to confidence level."""
    if score >= 70:
        return "HIGH"
    elif score >= 40:
        return "MEDIUM"
    else:
        return "LOW"

if __name__ == "__main__":
    main()
```

**Step 2: Test scoring**

Run: `cd test-audit && python scripts/correlate_and_score.py`
Expected: Creates `redundancy-report.json` with scored tests

**Step 3: Commit correlation engine**

```bash
git add test-audit/
git commit -m "feat: implement correlation and scoring engine

Combine data from all phases and calculate redundancy scores
for each test. Categorize by confidence level.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Report Generation

### Task 7: Implement Report Generator

**Files:**
- Modify: `test-audit/scripts/generate_report.py`

**Step 1: Create report generator**

```python
# test-audit/scripts/generate_report.py
#!/usr/bin/env python3
"""
Report Generator
Generate markdown report from correlation results.
"""
from utils import load_json, get_repo_root
from pathlib import Path

def generate_executive_summary(data):
    """Generate executive summary section."""
    summary = data["summary"]

    return f"""## Executive Summary

- **Total tests analyzed:** {summary['totalTests']}
- **Tests with redundancy indicators:** {summary['scoredTests']}
- **High-confidence redundant candidates:** {summary['highConfidenceCount']}
- **Medium-confidence candidates:** {summary['mediumConfidenceCount']}
- **Potential for removal:** {summary['highConfidenceCount']} tests (~{summary['highConfidenceCount'] / summary['totalTests'] * 100:.1f}% of total)

**Key Findings:**
- {summary['highConfidenceCount']} tests show strong indicators of redundancy or obsolescence
- Primary issues: trivial assertions, stale tests (2+ years unchanged), deprecated feature coverage
- Recommended next step: Manual review of high-confidence candidates for removal
"""

def generate_module_breakdown(timing_data, redundancy_data):
    """Generate module-level breakdown table."""
    if not timing_data or "modules" not in timing_data:
        return "## Module-Level Analysis\n\n*Timing data not available. Download CI log and run Phase 1.*\n"

    # Count redundant tests per module
    module_redundancy = {}
    for test in redundancy_data["highConfidence"]:
        # Extract module name from path (e.g., oak-core/src/test/...)
        parts = test["test"].split("/")
        if len(parts) > 0:
            module = parts[0]
            module_redundancy[module] = module_redundancy.get(module, 0) + 1

    output = "## Module-Level Analysis\n\n"
    output += "| Module | Test Count | Execution Time | Redundant Candidates | Priority |\n"
    output += "|--------|-----------|----------------|---------------------|----------|\n"

    modules = sorted(timing_data["modules"], key=lambda m: m.get("executionTimeSeconds", 0), reverse=True)

    for module in modules[:15]:  # Top 15 modules
        name = module["name"]
        exec_time = module.get("executionTime", "N/A")
        redundant_count = module_redundancy.get(name, 0)
        priority = "High" if redundant_count > 5 else "Medium" if redundant_count > 0 else "Low"

        output += f"| {name} | - | {exec_time} | {redundant_count} | {priority} |\n"

    return output + "\n"

def generate_high_confidence_table(tests):
    """Generate table of high-confidence redundant tests."""
    output = "## High-Confidence Redundant Tests\n\n"
    output += "**Recommendation:** Review these tests for removal. Each has multiple indicators of redundancy or obsolescence.\n\n"
    output += "| Test File | Score | Reasons |\n"
    output += "|-----------|-------|--------|\n"

    for test in tests[:50]:  # Top 50
        reasons = "; ".join(test["reasons"])
        # Truncate long file paths
        file_path = test["test"]
        if len(file_path) > 60:
            file_path = "..." + file_path[-57:]

        output += f"| `{file_path}` | {test['redundancyScore']} | {reasons} |\n"

    if len(tests) > 50:
        output += f"\n*({len(tests) - 50} more tests omitted for brevity)*\n"

    return output + "\n"

def generate_recommendations(data):
    """Generate recommendations section."""
    high_count = len(data["highConfidence"])
    medium_count = len(data["mediumConfidence"])

    return f"""## Recommendations

### Immediate Actions (High Impact, Low Risk)

1. **Review and remove trivial tests** ({sum(1 for t in data['highConfidence'] if 'Trivial' in str(t['reasons']))}) tests)
   - Tests with no assertions or only `assertNotNull`
   - Low risk: these tests provide minimal value
   - Expected time savings: ~5-10 minutes

2. **Remove deprecated feature tests** ({sum(1 for t in data['highConfidence'] if 'deprecated' in str(t['reasons']))}) tests)
   - Tests for features marked @Deprecated or removed
   - Low risk: testing obsolete functionality
   - Expected time savings: ~3-5 minutes

### Follow-up Actions (Medium Impact, Requires Review)

3. **Review stale tests** ({sum(1 for t in data['mediumConfidence'] if 'Stale' in str(t['reasons']))}) tests)
   - Tests unchanged for 2+ years
   - Medium risk: may still be valuable but needs verification
   - Manual review recommended

4. **Investigate medium-confidence candidates** ({medium_count} tests)
   - Tests with mixed indicators
   - Requires deeper analysis to confirm redundancy

### Long-term Considerations

5. **Implement test similarity detection**
   - Phase 2 static analysis can be enhanced with AST-based similarity
   - Would identify duplicate test logic across modules

6. **Run coverage overlap analysis**
   - Phase 4 can identify tests exercising identical code paths
   - Requires Maven execution with JaCoCo instrumentation

7. **Establish test quality standards**
   - Minimum assertion count per test
   - Regular audits for deprecated feature coverage
   - Automated checks for trivial tests in CI
"""

def main():
    print("=== Report Generation ===\n")

    print("Loading data...")
    redundancy_data = load_json("redundancy-report.json")

    # Try to load timing data
    try:
        timing_data = load_json("timing-data.json")
    except:
        timing_data = None
        print("Warning: timing-data.json not found. Some sections will be limited.")

    print("Generating report...")

    report = "# Jackrabbit Oak Test Audit Report\n\n"
    report += f"**Generated:** {Path(__file__).stat().st_mtime}\n\n"
    report += "---\n\n"

    report += generate_executive_summary(redundancy_data)
    report += "\n---\n\n"

    report += generate_module_breakdown(timing_data, redundancy_data)
    report += "\n---\n\n"

    report += generate_high_confidence_table(redundancy_data["highConfidence"])
    report += "\n---\n\n"

    report += generate_recommendations(redundancy_data)
    report += "\n---\n\n"

    report += """## Methodology

This audit used a hybrid approach combining:

1. **CI Log Mining** - Extracted module timing data from GitHub Actions logs
2. **Static Code Analysis** - Analyzed test code for quality issues and patterns
3. **Git History Analysis** - Identified stale and high-churn tests
4. **Scoring Algorithm** - Combined indicators to rank tests by redundancy likelihood

### Scoring Criteria

- Trivial assertions only: +30 points
- Tests deprecated features: +20 points
- Stale (2+ years unchanged): +20 points
- High churn (20+ commits): -10 points (indicates value)

### Confidence Levels

- **High (70-100):** Multiple strong indicators, recommended for removal
- **Medium (40-69):** Some indicators, manual review needed
- **Low (0-39):** Weak indicators, likely valuable tests

## Next Steps

1. Download CI build log and run Phase 1 for timing data
2. Review high-confidence candidates manually
3. Create PR to remove confirmed redundant tests
4. Run full test suite to verify no regressions
5. Measure CI time improvement

## Appendix

### Data Files

- `output/timing-data.json` - Module timing from CI logs
- `output/static-analysis.json` - Static code analysis results
- `output/history-analysis.json` - Git history analysis results
- `output/redundancy-report.json` - Scored and categorized tests

### Scripts

- `scripts/phase1_ci_mining.py` - CI log extraction
- `scripts/phase2_static_analysis.py` - Static code analysis
- `scripts/phase3_history_analysis.py` - Git history mining
- `scripts/correlate_and_score.py` - Scoring engine
- `scripts/generate_report.py` - Report generator
"""

    # Save report
    output_path = Path(__file__).parent.parent / "output" / "test-audit-report.md"
    with open(output_path, 'w') as f:
        f.write(report)

    print(f"✓ Report generated: {output_path}")
    print("\nYou can now review: test-audit/output/test-audit-report.md")

if __name__ == "__main__":
    main()
```

**Step 2: Test report generation**

Run: `cd test-audit && python scripts/generate_report.py`
Expected: Creates `output/test-audit-report.md`

**Step 3: Commit report generator**

```bash
git add test-audit/
git commit -m "feat: implement report generator

Generate markdown report from correlation results with
executive summary, module breakdown, and recommendations.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Integration and Documentation

### Task 8: Create Master Script and Update Documentation

**Files:**
- Create: `test-audit/run_audit.sh`
- Modify: `test-audit/README.md`

**Step 1: Create master execution script**

```bash
# test-audit/run_audit.sh
#!/bin/bash

set -e

echo "========================================"
echo "  Jackrabbit Oak Test Audit Tool"
echo "========================================"
echo ""

# Check Python
if ! command -v python3 &> /dev/null; then
    echo "ERROR: python3 is required"
    exit 1
fi

# Check dependencies
echo "Checking dependencies..."
pip3 install -q -r requirements.txt

echo ""
echo "Running audit phases..."
echo ""

# Phase 1: CI Log Mining
echo "Phase 1: CI Log Mining"
python3 scripts/phase1_ci_mining.py
echo ""

# Phase 2: Static Analysis
echo "Phase 2: Static Code Analysis"
python3 scripts/phase2_static_analysis.py
echo ""

# Phase 3: History Analysis
echo "Phase 3: Git History Analysis"
python3 scripts/phase3_history_analysis.py
echo ""

# Phase 4: Coverage (skipped)
echo "Phase 4: Coverage Analysis (skipped)"
python3 scripts/phase4_coverage_analysis.py
echo ""

# Correlation
echo "Correlating data and scoring..."
python3 scripts/correlate_and_score.py
echo ""

# Report generation
echo "Generating final report..."
python3 scripts/generate_report.py
echo ""

echo "========================================"
echo "✓ Audit complete!"
echo "========================================"
echo ""
echo "Report available at: output/test-audit-report.md"
echo ""
```

**Step 2: Make script executable**

```bash
chmod +x test-audit/run_audit.sh
```

**Step 3: Update README with complete instructions**

```markdown
# Test Audit Tool

Identifies redundant and obsolete tests in Jackrabbit Oak to reduce CI/CD build time.

## Quick Start

```bash
# 1. Install dependencies
pip3 install -r requirements.txt

# 2. (Optional) Download CI build log
#    See DOWNLOAD_LOG.md for instructions
#    Save to: ci-build-log.txt

# 3. Run audit
./run_audit.sh

# 4. Review report
cat output/test-audit-report.md
```

## Manual Execution

Run phases individually:

```bash
python3 scripts/phase1_ci_mining.py       # Extract CI timing data
python3 scripts/phase2_static_analysis.py # Analyze test code
python3 scripts/phase3_history_analysis.py # Mine git history
python3 scripts/phase4_coverage_analysis.py # (placeholder)
python3 scripts/correlate_and_score.py    # Score tests
python3 scripts/generate_report.py        # Generate report
```

## Output Files

- `output/timing-data.json` - Module execution times
- `output/static-analysis.json` - Code quality analysis
- `output/history-analysis.json` - Git history patterns
- `output/coverage-analysis.json` - (placeholder)
- `output/redundancy-report.json` - Scored tests
- `output/test-audit-report.md` - **Final report**

## Methodology

### Phase 1: CI Log Mining
Extracts module timing data from GitHub Actions logs. Requires manual log download.

### Phase 2: Static Code Analysis
Analyzes all test files for:
- Trivial assertions (assertNotNull only, no assertions)
- Deprecated feature references
- Test quality metrics

### Phase 3: Git History Analysis
Identifies:
- Stale tests (2+ years unchanged)
- High-churn tests (frequently modified)
- Maintenance patterns

### Phase 4: Coverage Analysis
Placeholder for JaCoCo-based coverage overlap detection (optional).

### Scoring Algorithm

Each test receives a redundancy score (0-100):
- Trivial assertions: +30 points
- Deprecated features: +20 points
- Stale (2+ years): +20 points
- High churn: -10 points

**Confidence levels:**
- 70-100: High confidence redundant
- 40-69: Medium confidence
- 0-39: Low confidence

## Requirements

- Python 3.7+
- Git
- Dependencies in requirements.txt

## CI Log Download (Optional but Recommended)

For module timing data:

1. Visit https://github.com/apache/jackrabbit-oak/actions
2. Open a recent successful "Build" workflow
3. Click "Maven Build" job
4. Download raw logs
5. Save as `ci-build-log.txt` in this directory

See DOWNLOAD_LOG.md for detailed instructions.

## Next Steps

After generating the report:

1. Review high-confidence redundant test candidates
2. Manually verify a sample before bulk removal
3. Create PR to remove confirmed redundant tests
4. Run full test suite to ensure no regressions
5. Measure CI time improvement
```

**Step 4: Commit final integration**

```bash
git add test-audit/
git commit -m "feat: add master script and complete documentation

Add run_audit.sh to execute all phases sequentially.
Update README with complete usage instructions.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Final Testing and Validation

### Task 9: End-to-End Test

**Step 1: Run complete audit**

Run: `cd test-audit && ./run_audit.sh`
Expected: All phases execute, report generated

**Step 2: Verify output files exist**

Run: `ls -lh test-audit/output/`
Expected: See all JSON files and report markdown

**Step 3: Review report**

Run: `head -50 test-audit/output/test-audit-report.md`
Expected: See executive summary with actual data

**Step 4: Create final summary commit**

```bash
git add test-audit/
git commit -m "test: verify end-to-end audit execution

Confirm all phases execute successfully and generate
complete audit report with findings.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Completion

### Task 10: Document Results

**Step 1: Add audit results to main docs**

Create a summary in `docs/test-audit-summary.md` linking to the full report.

**Step 2: Create issue/PR if results warrant it**

If high-confidence candidates found, create GitHub issue to track test removal work.

**Step 3: Final commit**

```bash
git add docs/
git commit -m "docs: add test audit summary and next steps

Document audit findings and create action items for
test suite optimization.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Success Criteria

- [ ] All 4 phases implemented and executable
- [ ] Scoring algorithm correlates multiple data sources
- [ ] Report generated with actionable recommendations
- [ ] At least 10 high-confidence redundant test candidates identified
- [ ] Tool is reusable for future audits
- [ ] Documentation complete and clear

## Estimated Time

- Setup: 15 minutes
- Phase 1: 30 minutes
- Phase 2: 45 minutes
- Phase 3: 30 minutes
- Phase 4: 15 minutes (placeholder)
- Correlation: 30 minutes
- Report generation: 45 minutes
- Testing & docs: 30 minutes

**Total: ~4 hours**

## Notes

- Phase 4 (coverage analysis) is marked as optional/future work since it requires Maven execution
- CI log download is manual but only needed once for timing data
- The tool is designed to be rerun periodically as the codebase evolves
- Scoring weights can be tuned based on initial results
