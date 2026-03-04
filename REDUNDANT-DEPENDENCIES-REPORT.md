# Redundant Dependencies Analysis

## Summary

This report identifies redundant dependency declarations in the jackrabbit-oak project.

- Total managed dependencies in oak-parent: 84
- Total redundant version declarations found: 33

## What are Redundant Dependencies?

In Maven, when a parent POM defines dependencies in the `<dependencyManagement>` section, child modules should reference those dependencies WITHOUT specifying versions. This ensures consistent versions across all modules and makes version management easier.

### Redundant Version Declarations

The following modules declare dependency versions that are already managed in oak-parent/pom.xml. These version specifications should be removed:

### Module: `oak-api`

Found 1 redundant version declarations:

- **javax.jcr:jcr**
  - Module specifies version: `2.0`
  - Managed version: `2.0`


### Module: `oak-auth-external`

Found 2 redundant version declarations:

- **org.apache.sling:org.apache.sling.testing.osgi-mock.core**
  - Module specifies version: `3.4.2`
  - Managed version: `2.4.18`
  - ⚠️ **VERSION MISMATCH** - Module uses different version!

- **org.apache.sling:org.apache.sling.testing.osgi-mock.junit4**
  - Module specifies version: `3.4.2`
  - Managed version: `2.4.18`
  - ⚠️ **VERSION MISMATCH** - Module uses different version!


### Module: `oak-auth-ldap`

Found 1 redundant version declarations:

- **javax.jcr:jcr**
  - Module specifies version: `2.0`
  - Managed version: `2.0`


### Module: `oak-authorization-cug`

Found 1 redundant version declarations:

- **javax.jcr:jcr**
  - Module specifies version: `2.0`
  - Managed version: `2.0`


### Module: `oak-authorization-principalbased`

Found 1 redundant version declarations:

- **javax.jcr:jcr**
  - Module specifies version: `2.0`
  - Managed version: `2.0`


### Module: `oak-benchmarks`

Found 2 redundant version declarations:

- **javax.jcr:jcr**
  - Module specifies version: `2.0`
  - Managed version: `2.0`

- **org.apache.sling:org.apache.sling.testing.osgi-mock.core**
  - Module specifies version: `3.4.2`
  - Managed version: `2.4.18`
  - ⚠️ **VERSION MISMATCH** - Module uses different version!


### Module: `oak-blob`

Found 1 redundant version declarations:

- **javax.jcr:jcr**
  - Module specifies version: `2.0`
  - Managed version: `2.0`


### Module: `oak-blob-cloud`

Found 2 redundant version declarations:

- **javax.jcr:jcr**
  - Module specifies version: `2.0`
  - Managed version: `2.0`

- **org.reactivestreams:reactive-streams**
  - Module specifies version: `1.0.4`
  - Managed version: `1.0.4`


### Module: `oak-blob-plugins`

Found 1 redundant version declarations:

- **javax.jcr:jcr**
  - Module specifies version: `2.0`
  - Managed version: `2.0`


### Module: `oak-core`

Found 1 redundant version declarations:

- **javax.jcr:jcr**
  - Module specifies version: `2.0`
  - Managed version: `2.0`


### Module: `oak-core-spi`

Found 1 redundant version declarations:

- **javax.jcr:jcr**
  - Module specifies version: `2.0`
  - Managed version: `2.0`


### Module: `oak-examples/webapp`

Found 1 redundant version declarations:

- **javax.jcr:jcr**
  - Module specifies version: `2.0`
  - Managed version: `2.0`


### Module: `oak-exercise`

Found 1 redundant version declarations:

- **javax.jcr:jcr**
  - Module specifies version: `2.0`
  - Managed version: `2.0`


### Module: `oak-jackrabbit-api`

Found 2 redundant version declarations:

- **javax.jcr:jcr**
  - Module specifies version: `2.0`
  - Managed version: `2.0`

- **org.osgi:org.osgi.annotation.versioning**
  - Module specifies version: `1.0.0`
  - Managed version: `1.1.2`
  - ⚠️ **VERSION MISMATCH** - Module uses different version!


### Module: `oak-jcr`

Found 1 redundant version declarations:

- **javax.jcr:jcr**
  - Module specifies version: `2.0`
  - Managed version: `2.0`


### Module: `oak-lucene`

Found 1 redundant version declarations:

- **com.github.stefanbirkner:system-rules**
  - Module specifies version: `1.19.0`
  - Managed version: `1.19.0`


### Module: `oak-pojosr`

Found 1 redundant version declarations:

- **org.osgi:osgi.core**
  - Module specifies version: `6.0.0`
  - Managed version: `7.0.0`
  - ⚠️ **VERSION MISMATCH** - Module uses different version!


### Module: `oak-run`

Found 2 redundant version declarations:

- **org.apache.commons:commons-csv**
  - Module specifies version: `1.1`
  - Managed version: `1.14.1`
  - ⚠️ **VERSION MISMATCH** - Module uses different version!

- **com.github.stefanbirkner:system-rules**
  - Module specifies version: `1.19.0`
  - Managed version: `1.19.0`


### Module: `oak-run-commons`

Found 1 redundant version declarations:

- **com.github.stefanbirkner:system-rules**
  - Module specifies version: `1.19.0`
  - Managed version: `1.19.0`


### Module: `oak-search-elastic`

Found 1 redundant version declarations:

- **com.github.stefanbirkner:system-rules**
  - Module specifies version: `1.19.0`
  - Managed version: `1.19.0`


### Module: `oak-security-spi`

Found 1 redundant version declarations:

- **javax.jcr:jcr**
  - Module specifies version: `2.0`
  - Managed version: `2.0`


### Module: `oak-segment-azure`

Found 4 redundant version declarations:

- **com.azure:azure-storage-blob**
  - Module specifies version: `12.25.3`
  - Managed version: `12.27.1`
  - ⚠️ **VERSION MISMATCH** - Module uses different version!

- **com.azure:azure-storage-common**
  - Module specifies version: `12.24.3`
  - Managed version: `12.27.1`
  - ⚠️ **VERSION MISMATCH** - Module uses different version!

- **com.azure:azure-xml**
  - Module specifies version: `1.0.0`
  - Managed version: `1.2.0`
  - ⚠️ **VERSION MISMATCH** - Module uses different version!

- **com.google.code.findbugs:jsr305**
  - Module specifies version: `3.0.2`
  - Managed version: `3.0.2`


### Module: `oak-segment-tar`

Found 1 redundant version declarations:

- **javax.jcr:jcr**
  - Module specifies version: `2.0`
  - Managed version: `2.0`


### Module: `oak-store-document`

Found 1 redundant version declarations:

- **javax.jcr:jcr**
  - Module specifies version: `2.0`
  - Managed version: `2.0`


### Module: `oak-store-spi`

Found 1 redundant version declarations:

- **javax.jcr:jcr**
  - Module specifies version: `2.0`
  - Managed version: `2.0`


## Recommendations

1. Remove version specifications from child modules for dependencies managed in oak-parent
2. This will ensure version consistency across all modules
3. Version updates will only need to be made in one place (oak-parent/pom.xml)
4. For dependencies with version mismatches, verify which version is correct before removing

## Analysis Methodology

This analysis was performed by:
1. Parsing oak-parent/pom.xml to extract all managed dependencies
2. Parsing each module's pom.xml to extract declared dependencies
3. Comparing module dependencies against managed dependencies
4. Flagging cases where modules specify versions for managed dependencies

---
*Generated by Autonomous Agent*
