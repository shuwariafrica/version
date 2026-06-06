---
title: SemVer Parsing
---

# SemVer Parsing

The core library provides robust parsing for SemVer 2.0.0 strings via:

- `SemVer.parse(input)` and `SemVer.parseUnsafe(input)` - parse strings to `SemVer`
- `PreRelease.Resolver` - maps pre-release identifiers to structured `PreRelease`

### Basic Usage

```scala
import version.semver.*

// Safe parsing returns Either
SemVer.parse("1.2.3") // Right(SemVer(1, 2, 3))
SemVer.parse("1.2.3-alpha.1") // Right(SemVer(1, 2, 3, alpha.1))
SemVer.parse("invalid") // Left(ParseError(...))

// Unsafe variant throws on invalid input
SemVer.parseUnsafe("1.2.3") // SemVer(1, 2, 3)
SemVer.parseUnsafe("invalid") // throws ParseError
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
SemVer.parse("1.2.3+sha.abc123")
// Right(SemVer(1, 2, 3, None, Some(Metadata(sha, abc123))))

SemVer.parse("1.2.3+build.456.dirty")
// Right(..., Some(Metadata(build, 456, dirty)))
```

---

### Custom Pre-release Mapping

Implement `PreRelease.Resolver` to handle non-standard pre-release formats. The resolver receives
dot-separated identifier tokens and returns `Some(PreRelease)` on success or `None` to reject.

```scala
import version.semver.*

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

SemVer.parse("1.0.0-nightly")   // Right(SemVer(1, 0, 0, SNAPSHOT))
SemVer.parse("1.0.0-preview.3") // Right(SemVer(1, 0, 0, alpha.3))
SemVer.parse("1.0.0-beta.1")    // Right(SemVer(1, 0, 0, beta.1)) - delegated
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

### Error Handling

Parse errors provide context:

```scala
import version.errors.*

SemVer.parse("abc") match
  case Left(err: InvalidVersionFormat) => err.message
  case Left(err: InvalidNumericField) => err.message
  case Left(err) => err.message
  case Right(v) => v.show
```

### Validation Summary

| Component          | Constraint             | Error                                   |
|--------------------|------------------------|-----------------------------------------|
| Major              | >= 0                   | `InvalidComponent(value, "Major", ...)` |
| Minor              | >= 0                   | `InvalidComponent(value, "Minor", ...)` |
| Patch              | >= 0                   | `InvalidComponent(value, "Patch", ...)` |
| Pre-release number | >= 1                   | `InvalidComponent(value, ...)`          |
| Build metadata     | Non-empty, valid chars | `InvalidMetadata`                       |
