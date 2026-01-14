# version

A modular Scala 3 toolkit for **intent-based versioning** conforming to SemVer 2.0.0.

Cross-platform (JVM, Scala.js, Scala Native) with sbt integration, CLI tooling, and codec support.

---

## Overview

Traditional versioning tools derive version numbers from repository _history_—counting commits since the last tag,
appending distance suffixes, or inferring from branch patterns. This answers "how far are we from the last release?" but
leaves the _intended next version_ implicit.

`version` inverts this paradigm: developers declare the **target version** through commit message directives, and the
system validates and enforces that intent. Snapshots represent pre-releases of the _upcoming_ version, not post-release
distances from a _previous_ one.

| Paradigm                         | Question Answered             | Snapshot Semantics                          |
|----------------------------------|-------------------------------|---------------------------------------------|
| History-based (e.g., sbt-dynver) | "How far since last release?" | `1.2.3+5-abc1234` (5 commits after 1.2.3)   |
| Intent-based (version)           | "What are we releasing next?" | `1.3.0-snapshot+...` (working toward 1.3.0) |

**Key properties:**

- **Explicit control** — versioning intent declared in commit history
- **Validation** — target directives validated against regression rules
- **Auditability** — version decisions traceable through commits
- **Determinism** — identical repository state always produces identical versions

---

## Quick Start

### Commit Message Directives

Embed directives anywhere in commit messages to control versioning:

```text
# Set explicit target (validated against regression rules)
target: 2.5.0

# Increment by one (resets lower components)
version: major          # 1.2.3 → 2.0.0
version: minor          # 1.2.3 → 1.3.0
version: patch          # 1.2.3 → 1.2.4

# Set to specific value (resets lower components)
version: major: 3       # → 3.0.0
version: minor: 5       # 1.x.x → 1.5.0
version: patch: 2       # 1.2.x → 1.2.2

# Exclude commit from version calculation
version: ignore
```

**Standalone shorthands** — use as commit message prefixes (requires non-empty text):

```text
breaking: Remove deprecated API     # Major increment
feat: Add caching support           # Minor increment (Conventional Commits)
fix: Handle edge case               # Patch increment
```

**Synonyms:** `major`/`breaking`, `minor`/`feature`/`feat`, `patch`/`fix` are interchangeable.

**Precedence:** Ignore → Target → Absolute sets → Relative changes → Default behaviour.

For complete semantics including validation rules and edge cases, see
the [Version Resolution Specification](docs/version-resolution-technical-specification.md).

### sbt Plugin

> **Note:** Requires sbt 2.x. Not compatible with sbt 1.x.

Add to `project/plugins.sbt`:

```scala
addSbtPlugin("africa.shuwari" % "sbt-version" % "<version>")
```

The plugin automatically derives and sets `version` for all projects—no additional configuration required.
See [sbt Plugin](#sbt-plugin) for settings, environment variables, and custom rendering.

### CLI

```bash
# Resolve version for current repository
version-cli

# Plain version string
version-cli --emit raw

# Multiple output formats
version-cli --emit console --emit json=dist/version.json

# With PR metadata (CI integration)
version-cli --pr 42 --branch-override main
```

See [CLI](#cli) for all options and output sinks.

### Library

```scala
//> using dep "africa.shuwari::version::<version>"

import version.*

// Parse
val v = Version.parse("2.1.0-rc.1+build.123") // Either[ParseError, Version]

// Construct
val release = Version(
  MajorVersion.fromUnsafe(1),
  MinorVersion.fromUnsafe(2),
  PatchNumber.fromUnsafe(3)
) // 1.2.3

// Operations
release.nextMinor // 1.3.0
release.toSnapshot // 1.2.3-snapshot
release.as[PreReleaseClassifier.Alpha.type] // 1.2.3-alpha.1
```

See [Core Library](#core-library) for components, parsing, operations, and ordering.

---

## Modules

| Module                    | Platforms       | Description                                            |
|---------------------------|-----------------|--------------------------------------------------------|
| `sbt-version`             | sbt 2.x         | sbt plugin for build integration                       |
| `version-cli`             | JVM, Native     | CLI application (text/JSON/YAML output)                |
| `version-cli-core`        | JVM, Native     | Git-based version derivation engine                    |
| `version`                 | JVM, JS, Native | Core SemVer model, parsing, ordering, typed operations |
| `version-codecs-jsoniter` | JVM, JS, Native | jsoniter-scala codecs                                  |
| `version-codecs-zio`      | JVM, JS, Native | ZIO JSON codecs                                        |
| `version-codecs-yaml`     | JVM, JS, Native | scala-yaml codecs                                      |
| `version-zio-prelude`     | JVM, JS, Native | ZIO Prelude type class instances                       |

---

## sbt Plugin

### Settings

| Key                     | Type                   | Description                                        |
|-------------------------|------------------------|----------------------------------------------------|
| `resolvedVersion`       | `Version`              | Full resolved version object (40-char SHA)         |
| `version`               | `String`               | SemVer string rendered via `versionShow`           |
| `isSnapshot`            | `Boolean`              | `true` if resolved version is a snapshot           |
| `versionBranchOverride` | `Option[String]`       | Override branch detection                          |
| `versionShow`           | `Option[Version.Show]` | Custom renderer (default: `Version.Show.Standard`) |

### Environment Variables

| Variable          | Effect                                  |
|-------------------|-----------------------------------------|
| `VERSION_BRANCH`  | Override detected branch name           |
| `VERSION_VERBOSE` | Enable verbose logging (`true`/`false`) |

CI-specific variables (GitHub Actions, GitLab CI, etc.) are auto-detected for PR numbers and branch names.

### Accessing the Version Object

The `resolvedVersion` setting provides full access to the computed `Version` instance, enabling programmatic inspection
and usage or manipulation.

### Custom Rendering

The `version` setting string is rendered using `version.Show`. By default, `Version.Show.Standard` excludes build
metadata. To include metadata or use a custom format:

```scala
// Include build metadata
versionShow := Some(Version.Show.Extended)

// Custom format with 'v' prefix
versionShow := Some(new Version.Show {
  extension (v: Version) def show: String =
    s"v${v.major.value}.${v.minor.value}.${v.patch.value}" +
      v.preRelease.fold("")(pr => s"-${pr.show}")
})
```

**Output comparison:**

```scala
// Standard (default) — excludes build metadata
version.value // "1.2.3-snapshot"

// Extended — includes build metadata
versionShow := Some(Version.Show.Extended)
version.value // "1.2.3-snapshot+pr42.branchmain.commits5.sha1a2b3c4"

// Direct access to Version with any Show instance

import version.Version.Show.Extended

resolvedVersion.value.show(using Extended)
```

---

## CLI

### Options

| Option                              | Description                                  |
|-------------------------------------|----------------------------------------------|
| `-r, --repository <path>`           | Repository path (default: current directory) |
| `-b, --basis-commit <rev>`          | Commit to resolve (default: HEAD)            |
| `--pr <n>`                          | PR number for metadata                       |
| `--branch-override <name>`          | Override branch detection                    |
| `--sha-length <7-40>`               | SHA abbreviation length (default: 12)        |
| `-v, --verbose`                     | Enable diagnostic logging                    |
| `--ci`                              | CI mode (compact console, no colours)        |
| `--no-colour`                       | Disable ANSI colours                         |
| `-e, --emit <sink>[=<path>]`        | Output sink (repeatable)                     |
| `--console-style <pretty\|compact>` | Console format                               |

### Output Sinks

| Sink      | Description                        |
|-----------|------------------------------------|
| `console` | Human-readable (pretty or compact) |
| `raw`     | Plain SemVer string                |
| `json`    | Structured JSON                    |
| `yaml`    | Structured YAML                    |

```bash
# Multiple outputs
version-cli --emit console --emit json=build/version.json --emit yaml=build/version.yaml

# CI pipeline
version-cli --ci --emit raw > version.txt
```

### Exit Codes

| Code | Meaning                |
|------|------------------------|
| 0    | Success                |
| 1    | Resolution failure     |
| 2    | Argument parsing error |

---

## Version Derivation

The derivation engine (`version-cli-core`) operates in two modes:

| Mode        | Condition                                                      | Output                                             |
|-------------|----------------------------------------------------------------|----------------------------------------------------|
| Concrete    | Basis commit has valid version tag AND clean working directory | Exact tag version                                  |
| Development | Otherwise                                                      | Target version with `-snapshot` and build metadata |

### Default Behaviour

When no directives are present in the commit range:

| Base Version State               | Target Core             |
|----------------------------------|-------------------------|
| Final release                    | Base patch + 1          |
| Pre-release                      | Base core (unchanged)   |
| No reachable base, repo has tags | (highest major + 1).0.0 |
| No tags anywhere                 | 0.1.0                   |

### Build Metadata

Ordered identifiers appended to development snapshots:

| Identifier     | Format       | Condition                    |
|----------------|--------------|------------------------------|
| `pr<n>`        | `pr42`       | PR number provided           |
| `branch<name>` | `branchmain` | Always (normalised)          |
| `commits<n>`   | `commits5`   | Always                       |
| `sha<hex>`     | `sha1a2b3c4` | Always (configurable length) |
| `dirty`        | `dirty`      | Working directory modified   |

Branch normalisation: lowercase, non-alphanumeric replaced with `-`, sequences collapsed, trimmed.

### Programmatic Usage

```scala
import version.cli.core.VersionCliCore
import version.cli.core.domain.CliConfig

val config = CliConfig(
  repo = os.pwd,
  basisCommit = "HEAD",
  prNumber = Some(42),
  branchOverride = None,
  shaLength = 12
)

VersionCliCore.resolve(config) match
  case Right(v) => println(v.show)
  case Left(err) => System.err.println(err.message)
```

---

## Core Library

### Version Components

All numeric components are opaque types with validation:

| Type               | Valid Range | Description                 |
|--------------------|-------------|-----------------------------|
| `MajorVersion`     | ≥ 0         | Major version number        |
| `MinorVersion`     | ≥ 0         | Minor version number        |
| `PatchNumber`      | ≥ 0         | Patch version number        |
| `PreReleaseNumber` | ≥ 1         | Pre-release sequence number |

```scala
import version.*

// Safe construction
MajorVersion.from(1) // Right(MajorVersion(1))
MajorVersion.from(-1) // Left(InvalidMajorVersion(-1))

// Unsafe construction
MajorVersion.fromUnsafe(1)

// Extension syntax
1.as[MajorVersion] // Right(MajorVersion(1))
```

### Pre-release Classifiers

Ordered hierarchy (lowest to highest precedence):

```
Dev < Milestone < Alpha < Beta < ReleaseCandidate < Snapshot
```

| Classifier       | Aliases          | Requires Number |
|------------------|------------------|-----------------|
| Dev              | `dev`            | Yes             |
| Milestone        | `milestone`, `m` | Yes             |
| Alpha            | `alpha`, `a`     | Yes             |
| Beta             | `beta`, `b`      | Yes             |
| ReleaseCandidate | `rc`, `cr`       | Yes             |
| Snapshot         | `snapshot`       | No              |

```scala
import version.*

PreRelease.snapshot // snapshot
PreRelease.alpha(PreReleaseNumber.fromUnsafe(1)) // alpha.1
PreRelease.releaseCandidate(PreReleaseNumber.fromUnsafe(2)) // rc.2

// Validated construction
PreRelease.from(PreReleaseClassifier.Alpha, Some(PreReleaseNumber.fromUnsafe(1))) // Right(...)
PreRelease.from(PreReleaseClassifier.Alpha, None) // Left(MissingPreReleaseNumber)
```

### Parsing

```scala
import version.*

// Combined identifiers normalised (rc3 → rc.3)
Version.parse("1.2.3-rc3") // Right(1.2.3-rc.3)
Version.parse("v2.0.0-alpha.1") // Right(2.0.0-alpha.1)

// With build metadata
Version.parse("1.0.0+build.123") // Right(1.0.0+build.123)

// Extension syntax
"1.2.3".toVersion // Either[ParseError, Version]
```

### Operations

```scala
import version.*

val v = Version.parseUnsafe("2.0.5")

// Increment (clears pre-release and metadata)
v.nextMajor // 3.0.0
v.nextMinor // 2.1.0
v.nextPatch // 2.0.6
v.next[MajorVersion] // 3.0.0 (generic)

// Pre-release
v.toSnapshot // 2.0.5-snapshot
v.as[PreReleaseClassifier.Alpha.type] // 2.0.5-alpha.1
v.as[PreReleaseClassifier.Beta.type](3) // 2.0.5-beta.3

val alpha = v.as[PreReleaseClassifier.Alpha.type]
alpha.advance[PreReleaseClassifier.Alpha.type] // 2.0.5-alpha.2
alpha.advance[PreReleaseClassifier.Beta.type] // 2.0.5-beta.1

// Finalise
alpha.release // 2.0.5

// Query
v.isStable // true (major > 0)
v.isFinal // true (no pre-release)
v.isPreRelease // false
v.isSnapshot // false
v.core // Version without pre-release/metadata
```

### Rendering

```scala
import version.*

val v = Version.parseUnsafe("1.2.3-alpha.1+sha1234567")

// Standard (excludes build metadata) — default
v.show // "1.2.3-alpha.1"

// Extended (includes build metadata)
v.show(using Version.Show.Extended) // "1.2.3-alpha.1+sha1234567"

// toString uses extended format
v.toString // "1.2.3-alpha.1+sha1234567"
```

### Ordering

Follows SemVer 2.0.0 precedence:

1. Compare major, minor, patch numerically
2. Pre-release versions have lower precedence than final releases
3. Pre-release comparison: classifier ordinal, then number
4. Build metadata is ignored for precedence

```scala
import version.*

val versions = List("1.0.0", "1.0.0-alpha.1", "1.0.0-rc.1", "0.9.0").map(Version.parseUnsafe)
versions.sorted // 0.9.0, 1.0.0-alpha.1, 1.0.0-rc.1, 1.0.0
```

---

## Codec Modules

### jsoniter-scala

```scala
import version.*
import version.codecs.jsoniter.given
import com.github.plokhotnyuk.jsoniter_scala.core.*

val v = Version.parseUnsafe("1.2.3-alpha.1")
val json = writeToString(v)
val decoded = readFromString[Version](json)
```

### ZIO JSON

```scala
import version.*
import version.codecs.zio.given
import zio.json.*

val v = Version.parseUnsafe("1.2.3")
val json = v.toJson
val decoded = json.fromJson[Version]
```

### scala-yaml

```scala
import version.*
import version.codecs.yaml.given
import org.virtuslab.yaml.*

val v = Version.parseUnsafe("1.2.3")
val yaml = v.asYaml
```

---

## ZIO Prelude

Type class instances for ZIO Prelude:

```scala
import version.*
import version.zio.prelude.given
import zio.prelude.*

val v1 = Version.parseUnsafe("1.0.0")
val v2 = Version.parseUnsafe("2.0.0")

Ord[Version].compare(v1, v2) // Ordering.LessThan
Equal[Version].equal(v1, v1) // true
```

---

## Specification

The [Version Resolution Technical Specification](docs/version-resolution-technical-specification.md) is the normative
reference for derivation behaviour:

- Tag recognition and validation rules
- Commit message directive grammar
- Target validation rules (A–F)
- Default behaviour matrices
- Build metadata construction
- Edge case handling

If implementation behaviour differs from the specification, the specification is authoritative.

---

## Licence

Apache 2.0
