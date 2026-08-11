A Scala 3 **versioning toolkit** - version types, parsing, manipulation, automatic derivation from Git, and build
integration.

## API Structure

- **`version` ([[version]]):** the scheme-generic algebra, one instance per capability:
    - [[version.VersionScheme VersionScheme]] - parse, render, order, and classify a difference
    - [[version.VersionArithmetic VersionArithmetic]] - interpret a [[version.Request Request]] against a version
    - [[version.CompatibilityPolicy CompatibilityPolicy]] - whether one version may stand in for another
    - [[version.ResolvableScheme ResolvableScheme]] - what a release workflow needs of a scheme
    - [[version.Request Request]] and [[version.Intent Intent]] - reified advancements, by significance or by name
    - [[version.Difference Difference]] - the tier in which two versions differ
    - [[version.Formatter Formatter]] - a rendering other than the canonical one
    - [[version.VersionResolver VersionResolver]] - the capabilities, tag parser, and formatter behind one type

- **`version.semver` ([[version.semver]]):** SemVer 2.0.0, and the only scheme shipped:
    - Numeric
      components ([[version.semver.Major Major]], [[version.semver.Minor Minor]], [[version.semver.Patch Patch]])
    - Pre-release identifiers ([[version.semver.PreRelease PreRelease]]) and the labels named among
      them ([[version.semver.PreReleaseClassifier PreReleaseClassifier]])
    - Build metadata ([[version.semver.Metadata Metadata]])
    - Complete version ([[version.semver.SemVer SemVer]])
    - Named formatter instances ([[version.semver.SemVer.Formatter Formatter]] with `Standard` and `Full`)

- **`version-resolution` ([[version.resolution]]):** Automatic version derivation:
    - Entry point ([[version.resolution.VersionCliCore VersionCliCore]]) - resolve a version or list release history
    - Result ([[version.resolution.ResolutionResult ResolutionResult]],
      [[version.resolution.ResolutionMode ResolutionMode]]) and release entries
      ([[version.resolution.domain.Release Release]])
    - Configuration ([[version.resolution.ResolutionConfig ResolutionConfig]])
    - Keyword parsing ([[version.resolution.parsing.KeywordParser KeywordParser]])

- **`version-cli`:** Command-line application

- **`sbt-version`:** sbt 2.x build integration ([[version.sbt.VersionPlugin VersionPlugin]])
