A Scala 3 **versioning toolkit** - version types, parsing, manipulation, automatic derivation from Git, and build integration.

## API Structure

- **`version.semver` ([[version.semver]]):** SemVer 2.0.0 model:
    - Version components ([[version.semver.Major Major]], [[version.semver.Minor Minor]], [[version.semver.Patch Patch]])
    - Pre-release classifiers ([[version.semver.PreReleaseClassifier PreReleaseClassifier]])
    - Structured pre-release ([[version.semver.PreRelease PreRelease]])
    - Build metadata ([[version.semver.Metadata Metadata]])
    - Complete version ([[version.semver.SemVer SemVer]])
    - Configurable formatting ([[version.semver.SemVer.Formatter Formatter]])

- **`version` ([[version]]):** Scheme-generic type class hierarchy:
    - [[version.VersionScheme VersionScheme]] - parse, render, ordering, components
    - [[version.VersionArithmetic VersionArithmetic]] - component manipulation
    - [[version.ResolvableScheme ResolvableScheme]] - Git-based resolution contract
    - [[version.CompatibilityPolicy CompatibilityPolicy]] - API/binary compatibility
    - [[version.ComponentRole ComponentRole]] - semantic role of component positions
    - [[version.ComponentDescriptor ComponentDescriptor]] - component name and role pair

- **`version-resolution` ([[version.resolution]]):** Automatic version derivation:
    - Configuration ([[version.resolution.ResolutionConfig ResolutionConfig]])
    - Keyword parsing ([[version.resolution.parsing.KeywordParser KeywordParser]])
    - Resolution engine ([[version.resolution.Resolver Resolver]])

- **`version-cli`:** Command-line application

- **`sbt-version`:** sbt 2.x build integration ([[version.sbt.VersionPlugin VersionPlugin]])
