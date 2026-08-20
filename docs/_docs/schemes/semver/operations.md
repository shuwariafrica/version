---
title: SemVer Operations
---

# SemVer Operations

## Projections

```scala
import version.semver.*

val v = SemVer.parseUnsafe("1.2.3-alpha.1+build.456")

v.stable   // false - it carries a pre-release
v.snapshot // false - its pre-release is not SNAPSHOT
v.release  // 1.2.3 - pre-release and build metadata both stripped
v.numbers  // IArray(1, 2, 3)
```

`stable` asks only whether a pre-release is present; it says nothing about whether the version has reached `1.0.0`.
To keep the build metadata while dropping only the pre-release, use `copy(preRelease = None)`.

## Classification

`difference` reports the most significant tier in which two versions differ, and nothing about breakage.

```scala
import version.*

val scheme = summon[VersionScheme[SemVer]]

scheme.difference(SemVer.parseUnsafe("1.2.3"), SemVer.parseUnsafe("2.0.0"))     // Release(0)
scheme.difference(SemVer.parseUnsafe("1.2.3"), SemVer.parseUnsafe("1.2.4"))     // Release(2)
scheme.difference(SemVer.parseUnsafe("1.2.3-rc.1"), SemVer.parseUnsafe("1.2.3")) // Qualifier
scheme.difference(SemVer.parseUnsafe("1.0.0+a"), SemVer.parseUnsafe("1.0.0+b")) // Build
```

## Advancement

`VersionArithmetic[SemVer]` interprets a `Request`. Every advancement is computed from the base's `release`, so the
pre-release and build metadata of the base never survive into the result.

```scala
val arithmetic = summon[VersionArithmetic[SemVer]]

arithmetic(SemVer.parseUnsafe("1.2.3"), Request.Advance(Intent.Feature)) // Right(1.3.0)
arithmetic(SemVer.parseUnsafe("1.2.3"), Request.Bump("minor"))           // Right(1.3.0)
arithmetic(SemVer.parseUnsafe("1.2.3"), Request.Assign("major", 5))      // Right(5.0.0)
```

### Which component an intent moves

The component an intent moves is decided by one rule: the compatibility axis is the leftmost non-zero component,
`Breaking` moves it, and `Feature` and `Fix` move one and two positions below it, never past the patch.

| Base    | `Breaking` | `Feature` | `Fix`   |
|---------|------------|-----------|---------|
| `1.2.3` | `2.0.0`    | `1.3.0`   | `1.2.4` |
| `0.4.2` | `0.5.0`    | `0.4.3`   | `0.4.3` |
| `0.0.7` | `0.0.8`    | `0.0.8`   | `0.0.8` |

Below `1.0.0` this leaves `Feature` and `Fix` indistinguishable, which is the honest consequence of the leading
component not yet carrying a compatibility promise.

`Stable` graduates a version out of initial development, and leaves one that has already graduated alone.

| Base    | `Stable` |
|---------|----------|
| `0.4.2` | `1.0.0`  |
| `0.0.7` | `1.0.0`  |
| `1.2.3` | `1.2.3`  |

### Absorption by a pending pre-release

Where the base carries a pre-release and every component below the requested one is already zero, the pending release
already sits on the boundary the request asks for. Advancing again would skip it, so the request is satisfied by
releasing what is pending.

| Base           | Request    | Result  |                                          |
|----------------|------------|---------|------------------------------------------|
| `1.2.3-rc.1`   | `Fix`      | `1.2.3` | the pending release is the fix           |
| `1.3.0-rc.1`   | `Feature`  | `1.3.0` | the pending release is the feature       |
| `2.0.0-rc.1`   | `Breaking` | `2.0.0` | the pending release is the breaking one  |
| `0.5.0-rc.1`   | `Breaking` | `0.5.0` | at `0.x`, the minor carries the boundary |
| `1.2.3-rc.1`   | `Feature`  | `1.3.0` | the pending `1.2.3` is not a feature     |

`Bump` absorbs on the same terms; `Assign` never does, since it states a version outright.

### Named components are exempt from the policy

`Bump` and `Assign` address a component by name and are not redirected, so they can say what no intent can.

```scala
val base = SemVer.parseUnsafe("0.93.9")

arithmetic(base, Request.Advance(Intent.Breaking)) // Right(0.94.0)
arithmetic(base, Request.Bump("major"))            // Right(1.0.0)
```

SemVer names three components. Anything else is rejected, as is a negative assignment:

```scala
arithmetic(base, Request.Bump("epoch"))        // Left(UnsupportedComponent("semver", "epoch"))
arithmetic(base, Request.Assign("minor", -1))  // Left(InvalidComponent(-1, "Minor version", ...))
```

## Compatibility

Two rules ship, and neither is a `given`: they disagree below `1.0.0`, so the consumer names the one it means.

| Rule              | Admits                                                          | `0.4.2` and `0.4.9` |
|-------------------|-----------------------------------------------------------------|---------------------|
| `sameMajor`       | stable releases at or above `1.0.0` sharing a major             | Incompatible        |
| `leftmostNonZero` | stable releases sharing their leftmost non-zero component       | Compatible          |

`sameMajor` reads clause 8 strictly, so every `0.x` release is incompatible with every other, including itself.
`leftmostNonZero` is the caret rule of Cargo, npm, and Composer. Neither admits a pre-release on either side.

```scala
SemVer.Compatibility.sameMajor.compatible(a, b)
SemVer.Compatibility.leftmostNonZero.compatible(a, b)
CompatibilityPolicy.strict[SemVer].compatible(a, b) // equal versions only
```

## Typed Advancement

`next[F]` moves a component or a pre-release label named at the type level, and clears the build metadata.

```scala
import version.semver.PreReleaseClassifier.*

val v = SemVer.parseUnsafe("1.2.3")

v.next[Major] // 2.0.0
v.next[Minor] // 1.3.0
v.next[Patch] // 1.2.4

// Useful in generic code
def bump[F](v: SemVer)(using SemVer.Increment[F]): SemVer = v.next[F]
```

For a label, `next` never returns a version lower than the one it was given. It numbers on where the label is already
in place, replaces the label where the new one outranks it, and otherwise moves the patch first.

```scala
val alpha = SemVer.parseUnsafe("1.2.3-alpha.1")

SemVer.parseUnsafe("1.2.3").next[Alpha] // 1.2.3-alpha.1 - start the cycle
alpha.next[Alpha]                       // 1.2.3-alpha.2 - number on
alpha.next[Beta]                        // 1.2.3-beta.1  - "beta" outranks "alpha"
SemVer.parseUnsafe("1.2.3-beta.1").next[Alpha] // 1.2.4-alpha.1 - it does not
```

Ranking here is the ordering of the rendered labels, so it follows the same ASCII comparison as everything else -
`SNAPSHOT` is outranked by every lowercase label. `Snapshot` has no `Increment` instance; use `as[Snapshot]`.

`as[C]` sets a label outright, with no regard to what was there:

```scala
v.as[Alpha]     // 1.2.3-alpha.1
v.as[Snapshot]  // 1.2.3-SNAPSHOT
v.as[Alpha](5)  // Right(1.2.3-alpha.5)
v.as[Snapshot](1) // Left(ClassifierNotVersioned("SNAPSHOT"))
v.as[Alpha](0)  // Left(InvalidComponent(0, "Pre-release number", "a positive number (>= 1)"))
```

## Comparison

```scala
import scala.math.Ordering.Implicits.infixOrderingOps

SemVer.parseUnsafe("1.0.0") < SemVer.parseUnsafe("2.0.0") // true
List(b, a).sorted                                         // List(a, b)
```

## Rendering

`show` is the canonical form and carries every part, build metadata included, so it round-trips through `parse`.
`SemVer.Formatter` supplies the renderings that deliberately depart from it.

| Formatter                         | Behaviour                                             | Example output                                                              |
|-----------------------------------|-------------------------------------------------------|-----------------------------------------------------------------------------|
| `Formatter.Standard`              | Numbers and pre-release; omits build metadata         | `1.2.3-SNAPSHOT`                                                            |
| `Formatter.Full`                  | Every part, matching `show`                           | `1.2.3-SNAPSHOT+202605170145.main.0123456789abcdef0123456789abcdef01234567` |
| `Formatter.Full.withShaLength(N)` | As `Full`, with the commit SHA shortened to `N` chars | `1.2.3-SNAPSHOT+202605170145.main.0123456789ab` (`N = 12`)                  |

```scala
val v = SemVer.parseUnsafe("1.2.3-SNAPSHOT+202605170145.main.0123456789abcdef0123456789abcdef01234567")

v.show                                           // every part, round-trips
SemVer.Formatter.Standard.format(v)              // "1.2.3-SNAPSHOT"
SemVer.Formatter.Full.withShaLength(12).format(v) // "1.2.3-SNAPSHOT+202605170145.main.0123456789ab"
```

`withShaLength` accepts `[7, 64]` (SHA-1 is 40 characters, SHA-256 is 64) and shortens only the identifier shaped
like a full digest; the timestamp, branch, `pr<N>`, and `dirty` identifiers are emitted unchanged.

### Custom Renderings

```scala
import version.Formatter
import version.semver.*

val minimal: Formatter[SemVer] = (v: SemVer) =>
  val numbers = s"${v.major.value}.${v.minor.value}.${v.patch.value}"
  if v.snapshot then s"$numbers-dev" else numbers
```
