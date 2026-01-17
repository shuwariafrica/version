# `version` — a modular Scala 3 toolkit for **intent-based versioning** conforming to SemVer 2.0.0.

## API Structure

This library is organised into multiple modules, each addressing distinct concerns:

- **`version` ([[version]]):** Core SemVer model providing:
    - Opaque types for version components ([[version.MajorVersion MajorVersion]], [[version.MinorVersion MinorVersion]],
      [[version.PatchNumber PatchNumber]], [[version.PreReleaseNumber PreReleaseNumber]])
    - Pre-release classifiers with precedence ordering ([[version.PreReleaseClassifier PreReleaseClassifier]])
    - Structured pre-release information ([[version.PreRelease PreRelease]])
    - Build metadata handling ([[version.Metadata Metadata]])
    - Complete version representation with parsing and operations ([[version.Version Version]])

- **`version-cli-core` ([[version.cli.core]]):** Git-based version derivation engine providing:
    - Resolution configuration ([[version.cli.core.domain.CliConfig CliConfig]])
    - Commit message keyword parsing ([[version.cli.core.parsing.KeywordParser KeywordParser]])
    - Version resolution ([[version.cli.core.Resolver Resolver]])

- **`version-cli` ([[version.cli]]):** Command-line application for version resolution:
    - CLI entry point ([[version.cli.CLI CLI]])
    - Option parsing ([[version.cli.CliOptions CliOptions]])

- **`sbt-version`:** sbt 2.x plugin for build integration (see [[version.sbt.VersionPlugin VersionPlugin]])

- **Codec modules:** Serialisation support for various formats:
    - `version-codecs-jsoniter` ([[version.codecs.jsoniter]]) — jsoniter-scala codecs
    - `version-codecs-zio` ([[version.codecs.zio]]) — ZIO JSON codecs
    - `version-codecs-yaml` ([[version.codecs.yaml]]) — scala-yaml codecs

- **`version-zio-prelude` ([[version.zio.prelude]]):** ZIO Prelude type class instances
