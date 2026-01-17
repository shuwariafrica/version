---
title: Parsing
---

# Parsing

The core library provides robust parsing for SemVer 2.0.0 strings via the [[version.Version.Read]]`[A]` type class.

## Basic Usage

```scala
import version.*

// Extension methods (via default given Read[String] in instances.scala)
"1.2.3".toVersion           // Right(Version(1, 2, 3))
"1.2.3-alpha.1".toVersion   // Right(Version(1, 2, 3, alpha.1))
"invalid".toVersion         // Left(ParseError(...))

// Unsafe variant throws on invalid input
"1.2.3".toVersionUnsafe     // Version(1, 2, 3)
"invalid".toVersionUnsafe   // throws ParseError

// Factory methods (equivalent to extension methods)
Version.from("1.2.3")       // Right(Version(1, 2, 3))
Version.fromUnsafe("1.2.3") // Version(1, 2, 3)
```

## Explicit Reader Instance

For explicit instance passing or when avoiding implicit resolution:

```scala
import version.*

// Use the singleton instance directly
val reader: Version.Read[String] = Version.Read.ReadString

// Use directly
reader.toVersion("1.2.3")       // Right(Version(...))
reader.toVersionUnsafe("1.2.3") // Version(...)
```

## Accepted Formats

| Format      | Example                   | Notes                       |
|-------------|---------------------------|-----------------------------|
| Core        | `1.2.3`                   | Required                    |
| With prefix | `v1.2.3`, `V1.2.3`        | Optional `v` stripped       |
| Pre-release | `1.2.3-alpha.1`           | After `-`                   |
| Metadata    | `1.2.3+build.456`         | After `+`                   |
| Full        | `1.2.3-alpha.1+build.456` | Pre-release before metadata |

## Pre-release Formats

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

## Build Metadata

Metadata identifiers must match `[0-9A-Za-z-]+`:

```scala
"1.2.3+sha.abc123".toVersion
// Right(Version(1, 2, 3, None, Some(Metadata(sha, abc123))))

"1.2.3+build.456.dirty".toVersion
// Right(..., Some(Metadata(build, 456, dirty)))
```

## Custom Pre-release Mapping

Implement [[version.PreRelease.Resolver]] to handle non-standard formats:

```scala
import version.*

given PreRelease.Resolver with
  extension (identifiers: List[String])
    def resolve: Option[PreRelease] =
      identifiers match
        // Treat "nightly" as snapshot
        case List("nightly") =>
          Some(PreRelease.snapshot)

        // Treat "preview.N" as alpha
        case List("preview", n) =>
          n.toIntOption
            .flatMap(i => PreReleaseNumber.from(i).toOption)
            .map(PreRelease.alpha)

        // Delegate to default resolver
        case _ =>
          PreRelease.Resolver.given_Resolver.resolve(identifiers)

"1.0.0-nightly".toVersion   // Right(Version(1, 0, 0, snapshot))
"1.0.0-preview.3".toVersion // Right(Version(1, 0, 0, alpha.3))
```

## Custom Read Instances

Implement [[version.Version.Read]]`[A]` to parse custom input types. The typeclass requires a
contextual [[version.PreRelease.Resolver]] for pre-release identifier mapping:

```scala
import version.*
import version.errors.InvalidVersionFormat

case class MyVersionFormat(major: Int, minor: Int, patch: Int)

given Version.Read[MyVersionFormat] with
  extension (m: MyVersionFormat)
    def toVersion(using PreRelease.Resolver): Either[errors.ParseError, Version] =
      // Component from() methods return Either[InvalidComponent, T]
      // We map errors to ParseError for consistency with the Read contract
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

## Error Handling

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

## Validation Summary

| Component          | Constraint             | Error                     |
|--------------------|------------------------|---------------------------|
| Major              | >= 0                   | `InvalidMajorVersion`     |
| Minor              | >= 0                   | `InvalidMinorVersion`     |
| Patch              | >= 0                   | `InvalidPatchNumber`      |
| Pre-release number | >= 1                   | `InvalidPreReleaseNumber` |
| Build metadata     | Non-empty, valid chars | `InvalidMetadata`         |
