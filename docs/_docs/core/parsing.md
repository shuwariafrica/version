---
title: Parsing
---

# Parsing

The core library provides robust parsing for SemVer 2.0.0 strings.

## Basic Usage

```scala
import version.*
import version.given

// Safe parsing returns Either
Version.parse("1.2.3") // Right(Version(1, 2, 3))
Version.parse("1.2.3-alpha.1") // Right(Version(1, 2, 3, alpha.1))
Version.parse("invalid") // Left(ParseError(...))

// Unsafe parsing throws on invalid input
Version.parseUnsafe("1.2.3") // Version(1, 2, 3)
Version.parseUnsafe("invalid") // throws ParseError
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
Version.parse("1.2.3+sha.abc123")
// Right(Version(1, 2, 3, None, Some(BuildMetadata(sha, abc123))))

Version.parse("1.2.3+build.456.dirty")
// Right(..., Some(BuildMetadata(build, 456, dirty)))
```

## Custom Pre-release Mapping

Implement `PreRelease.Resolver` to handle non-standard formats:

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

        // Delegate to default
        case _ =>
          summon[PreRelease.Resolver].resolve(identifiers)

Version.parse("1.0.0-nightly") // snapshot
Version.parse("1.0.0-preview.3") // alpha.3
```

## Error Handling

Parse errors provide context:

```scala
import version.errors.*

Version.parse("abc") match
  case Left(err: ParseError) =>
    err.input // "abc"
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
| Build metadata     | Non-empty, valid chars | `InvalidBuildMetadata`    |
