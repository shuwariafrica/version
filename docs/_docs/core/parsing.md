---
title: Parsing
---

## Parsing

The core library provides robust parsing for SemVer 2.0.0 strings via two type classes:

- [[version.Version.Read]]`[A]` — converts input types to `Version`
- [[version.PreRelease.Resolver]] — maps pre-release identifiers to structured `PreRelease`

### Basic Usage

```scala
import version.*

// Extension methods require a Read[String] and Resolver in scope.
// Both are provided by default via `import version.{given, *}`.
"1.2.3".toVersion // Right(Version(1, 2, 3))
"1.2.3-alpha.1".toVersion // Right(Version(1, 2, 3, alpha.1))
"invalid".toVersion // Left(ParseError(...))

// Unsafe variant throws on invalid input
"1.2.3".toVersionUnsafe // Version(1, 2, 3)
"invalid".toVersionUnsafe // throws ParseError

// Factory methods (equivalent)
Version.from("1.2.3") // Right(Version(1, 2, 3))
Version.fromUnsafe("1.2.3") // Version(1, 2, 3)
```

---

### Accepted Formats

| Format      | Example                   | Notes                       |
|-------------|---------------------------|-----------------------------|
| Core        | `1.2.3`                   | Required                    |
| With prefix | `v1.2.3`, `V1.2.3`        | Optional `v` stripped       |
| Pre-release | `1.2.3-alpha.1`           | After `-`                   |
| Metadata    | `1.2.3+build.456`         | After `+`                   |
| Full        | `1.2.3-alpha.1+build.456` | Pre-release before metadata |

### Pre-release Formats

The default resolver recognises these classifiers:

| Input            | Parsed As                   |
|------------------|-----------------------------|
| `1.0.0-alpha.1`  | Alpha, number 1             |
| `1.0.0-a.1`      | Alpha, number 1             |
| `1.0.0-beta.2`   | Beta, number 2              |
| `1.0.0-b.2`      | Beta, number 2              |
| `1.0.0-rc.3`     | Release Candidate, number 3 |
| `1.0.0-cr.3`     | Release Candidate, number 3 |
| `1.0.0-m.1`      | Milestone, number 1         |
| `1.0.0-dev.1`    | Dev, number 1               |
| `1.0.0-SNAPSHOT` | Snapshot (canonical)        |
| `1.0.0-snapshot` | Snapshot (case-insensitive) |

Common variations are normalised:

| Input          | Normalised |
|----------------|------------|
| `1.0.0-RC1`    | `rc.1`     |
| `1.0.0-alpha1` | `alpha.1`  |

### Build Metadata

Metadata identifiers must match `[0-9A-Za-z-]+`:

```scala
"1.2.3+sha.abc123".toVersion
// Right(Version(1, 2, 3, None, Some(Metadata(sha, abc123))))

"1.2.3+build.456.dirty".toVersion
// Right(..., Some(Metadata(build, 456, dirty)))
```

---

### Custom Pre-release Mapping

Implement [[version.PreRelease.Resolver]] to handle non-standard pre-release formats. The resolver receives
dot-separated identifier tokens and returns `Some(PreRelease)` on success or `None` to reject.

```scala
import version.*

// Define a custom resolver
val customResolver: PreRelease.Resolver = new PreRelease.Resolver:
  extension (identifiers: List[String])
    def resolve: Option[PreRelease] =
      identifiers match
        // Map "nightly" to snapshot
        case List("nightly") =>
          Some(PreRelease.snapshot)

        // Map "preview.N" to alpha
        case List("preview", n) =>
          n.toIntOption
            .flatMap(i => PreReleaseNumber.from(i).toOption)
            .map(PreRelease.alpha)

        // Delegate unrecognised formats to the default resolver
        case _ =>
          PreRelease.Resolver.given_Resolver.resolve(identifiers)

// Use the custom resolver
given PreRelease.Resolver = customResolver

"1.0.0-nightly".toVersion   // Right(Version(1, 0, 0, SNAPSHOT))
"1.0.0-preview.3".toVersion // Right(Version(1, 0, 0, alpha.3))
"1.0.0-beta.1".toVersion    // Right(Version(1, 0, 0, beta.1)) — delegated
```

#### Resolver API

```scala
trait Resolver:
  extension (identifiers: List[String]) def resolve: Option[PreRelease]
```

The `identifiers` parameter contains the pre-release string split by `.`. For example, `alpha.1` becomes
`List("alpha", "1")`.

#### Default Resolver

Access the default resolver via `PreRelease.Resolver.given_Resolver`:

```scala
// Delegate to default for standard formats
PreRelease.Resolver.given_Resolver.resolve(List("rc", "2"))
// Some(PreRelease(ReleaseCandidate, Some(2)))
```

---

### Custom Read Instances

Implement [[version.Version.Read]]`[A]` to parse custom input types. The type class requires a contextual
[[version.PreRelease.Resolver]] for pre-release mapping.

```scala
import version.*
import version.errors.InvalidVersionFormat

case class MyVersionFormat(major: Int, minor: Int, patch: Int)

given Version.Read[MyVersionFormat] with
  extension (m: MyVersionFormat)
    def toVersion(using PreRelease.Resolver): Either[errors.ParseError, Version] =
      for
        major <- MajorVersion.from(m.major).left.map(_ =>
          InvalidVersionFormat(s"${m.major}.${m.minor}.${m.patch}"))
        minor <- MinorVersion.from(m.minor).left.map(_ =>
          InvalidVersionFormat(s"${m.major}.${m.minor}.${m.patch}"))
        patch <- PatchNumber.from(m.patch).left.map(_ =>
          InvalidVersionFormat(s"${m.major}.${m.minor}.${m.patch}"))
      yield Version(major, minor, patch)

    def toVersionUnsafe(using PreRelease.Resolver): Version =
      toVersion match
        case Right(v) => v
        case Left(e)  => throw e

// Now works with factory methods
Version.from(MyVersionFormat(1, 2, 3)) // Right(Version(1, 2, 3))
```

#### Read API

```scala
trait Read[A]:
  extension (a: A)
    def toVersion(using PreRelease.Resolver): Either[errors.ParseError, Version]
    def toVersionUnsafe(using PreRelease.Resolver): Version
```

#### String Read with Custom Tag Prefix

A common use case is supporting alternative tag prefixes (e.g., `release-` instead of `v`):

```scala
import version.*

val releaseReader: Version.Read[String] = new Version.Read[String]:
  extension (s: String)
    def toVersion(using PreRelease.Resolver): Either[errors.ParseError, Version] =
      val normalised = if s.startsWith("release-") then s.stripPrefix("release-") else s
      Version.Read.ReadString.toVersion(normalised)

    def toVersionUnsafe(using PreRelease.Resolver): Version =
      toVersion match
        case Right(v) => v
        case Left(e)  => throw e

given Version.Read[String] = releaseReader

"release-1.2.3".toVersion // Right(Version(1, 2, 3))
```

---

### Combining Read and Resolver

When both custom parsing and custom pre-release mapping are required:

```scala
import version.*

// Custom resolver: map "nightly" to SNAPSHOT
val customResolver: PreRelease.Resolver = new PreRelease.Resolver:
  extension (ids: List[String])
    def resolve: Option[PreRelease] = ids match
      case List("nightly") => Some(PreRelease.snapshot)
      case _               => PreRelease.Resolver.given_Resolver.resolve(ids)

// Custom reader: strip "release-" prefix
val customReader: Version.Read[String] = new Version.Read[String]:
  extension (s: String)
    def toVersion(using PreRelease.Resolver): Either[errors.ParseError, Version] =
      val normalised = if s.startsWith("release-") then s.stripPrefix("release-") else s
      Version.Read.ReadString.toVersion(normalised)

    def toVersionUnsafe(using PreRelease.Resolver): Version =
      toVersion.fold(e => throw e, identity)

// Bring both into scope
given PreRelease.Resolver = customResolver
given Version.Read[String] = customReader

// Both customisations apply
"release-2.0.0-nightly".toVersion // Right(Version(2, 0, 0, SNAPSHOT))
```

---

### Error Handling

Parse errors provide context:

```scala
import version.errors.*

"abc".toVersion match
  case Left(err: ParseError) =>
    err.input   // "abc"
    err.message // Descriptive error
  case Right(v) =>
    // ...
```

### Validation Summary

| Component          | Constraint             | Error                     |
|--------------------|------------------------|---------------------------|
| Major              | >= 0                   | `InvalidMajorVersion`     |
| Minor              | >= 0                   | `InvalidMinorVersion`     |
| Patch              | >= 0                   | `InvalidPatchNumber`      |
| Pre-release number | >= 1                   | `InvalidPreReleaseNumber` |
| Build metadata     | Non-empty, valid chars | `InvalidMetadata`         |
