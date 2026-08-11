---
title: SemVer Parsing
---

# SemVer Parsing

```scala
import version.semver.*

SemVer.parse("1.2.3-alpha.1")  // Right(1.2.3-alpha.1)
SemVer.parse("not a version")  // Left(InvalidVersionFormat("not a version"))

SemVer.parseUnsafe("1.2.3")    // 1.2.3
SemVer.parseUnsafe("nonsense") // throws the same error parse reports
```

## Accepted Formats

| Format      | Example                   | Notes                                    |
|-------------|---------------------------|------------------------------------------|
| Core        | `1.2.3`                   | Exactly three components, all required   |
| With prefix | `v1.2.3`, `V1.2.3`        | A leading `v` or `V` is accepted         |
| Pre-release | `1.2.3-alpha.1`           | After `-`                                |
| Metadata    | `1.2.3+build.456`         | After `+`                                |
| Full        | `1.2.3-alpha.1+build.456` | Pre-release precedes build metadata      |

Components are carried as `Long`, so a date-stamped component such as `20260731093000.0.0` parses.

## Identifiers Are Preserved

Parsing does not rewrite what it reads. An identifier is kept exactly as written, whether or not this library has a
name for it, and no attempt is made to split a combined form into a label and a number.

```scala
SemVer.parseUnsafe("1.0.0-x.7.z.92").preRelease.map(_.identifiers) // Some(List("x", "7", "z", "92"))
SemVer.parseUnsafe("1.0.0-rc3").preRelease.map(_.identifiers)      // Some(List("rc3"))
SemVer.parseUnsafe("1.0.0-rc.3").preRelease.map(_.identifiers)     // Some(List("rc", "3"))
```

`rc3` and `rc.3` are therefore different versions, and they rank differently - see
[Ordering](types.md#ordering). Versions this library emits always use the dotted form.

The classifier a pre-release names, where it names one, is available separately and is matched without regard to case:

```scala
SemVer.parseUnsafe("1.0.0-RC.1").preRelease.flatMap(_.classifier) // Some(ReleaseCandidate)
SemVer.parseUnsafe("1.0.0-rc3").preRelease.flatMap(_.classifier)  // None
```

## What Is Rejected

Parsing is strict: input outside the grammar is rejected rather than coerced.

| Input                        | Rejected because                             | Error                  |
|------------------------------|----------------------------------------------|------------------------|
| `1.0`, `1.0.0.0`             | the core is not three components             | `InvalidVersionFormat` |
| `01.0.0`, `1.0.01`           | a core component carries a leading zero      | `InvalidVersionFormat` |
| `1.2.3x`, `a.b.c`, `""`      | the shape is not a version at all            | `InvalidVersionFormat` |
| `99999999999999999999.0.0`   | a component is too large to carry            | `InvalidNumericField`  |
| `1.0.0-`, `1.0.0-alpha..1`   | a pre-release identifier is empty            | `InvalidPreRelease`    |
| `1.0.0-alpha_1`              | a pre-release identifier is outside the set  | `InvalidPreRelease`    |
| `1.0.0-alpha.01`             | a numeric pre-release has a leading zero     | `InvalidPreRelease`    |
| `1.0.0+`, `1.0.0+build_1`    | a build identifier is empty or outside the set | `InvalidMetadata`    |

A leading zero is allowed in build metadata, where the specification places no numeric meaning on identifiers:
`1.0.0+01` parses.

Every one of these is a `ParseError`, so a consumer can match the shape it cares about and treat the rest uniformly:

```scala
import version.errors.*

SemVer.parse(input) match
  case Left(e: InvalidNumericField) => s"component out of range: ${e.message}"
  case Left(e: ParseError)          => e.message
  case Right(v)                     => v.show
```

## Round Tripping

`show` renders the canonical form of every part, build metadata included, and `parse` reads it back as an equal value.

```scala
val v = SemVer.parseUnsafe("1.2.3-rc.1+sha.5114f85")
SemVer.parse(v.show) == Right(v) // true
```
