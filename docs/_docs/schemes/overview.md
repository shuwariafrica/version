---
title: Version Schemes
---

# Version Schemes

A version scheme decides how version strings are read, how two versions rank against one another, and how a version
advances. Nothing in this library assumes a scheme has three numeric components, a pre-release, or any particular
notion of a breaking change - those are the SemVer scheme's answers, not the model's.

## Available Schemes

| Scheme                             | Import                    | Status |
|------------------------------------|---------------------------|--------|
| [SemVer 2.0.0](semver/overview.md) | `import version.semver.*` | Stable |

## Using a Scheme

Import the scheme's package. Its types and its instances both become available; no `given` import is needed, because
Scala 3 finds the instances in the version type's companion.

```scala
import version.semver.*

val v = SemVer.parseUnsafe("1.2.3-alpha.1")

v.show     // "1.2.3-alpha.1"
v.stable   // false
v.release  // 1.2.3
v.numbers  // IArray(1, 2, 3)
```

## Capabilities

A scheme supplies one instance per capability it actually has, so code states what it needs in its context bounds and
a scheme that cannot do something has no instance to summon.

| Capability          | Instance                 | What it answers                                           |
|---------------------|--------------------------|-----------------------------------------------------------|
| Reading and ranking | `VersionScheme[V]`       | parse, render, order, classify a difference               |
| Advancement         | `VersionArithmetic[V]`   | what a version becomes when a change is applied to it     |
| Compatibility       | `CompatibilityPolicy[V]` | whether one version may stand in for another              |
| Release workflow    | `ResolvableScheme[V]`    | where a project starts, how snapshots and directives look |
| Ranges              | `RangeScheme[V, R]`      | what a written constraint admits, and how it is rewritten |

Only the first is mandatory. A scheme that merely orders published versions - a plain list of numbers, say - supplies
that one alone, and an attempt to advance such a version then fails to compile rather than at run time.

```scala
def latest[V](candidates: List[V])(using scheme: VersionScheme[V]): Option[V] =
  candidates.maxOption(using scheme.precedence)
```

### Ranking is not equality

`precedence` is the scheme's own comparison, and it may rank two structurally different values alike - SemVer holds
`1.0.0+build1` and `1.0.0+build2` to be of equal precedence, though they are distinct values. Use `precedence` to
choose between versions and `==` to ask whether two of them are the same.

### Differences are not breakage

`difference(a, b)` reports the most significant tier in which two versions differ: an `Epoch`, a numbered `Release`
position, a `Qualifier`, build-only `Build`, or `None`. It deliberately does not say whether the change breaks
anything, because that follows from a policy rather than from the version structure - below `1.0.0` the leading
component moves without breaking anything under the rule most package ecosystems apply. `CompatibilityPolicy` answers
that question instead.

## Advancement

An advancement is expressed as a `Request`, which the scheme interprets.

| Request                    | Addressed by                 | Subject to scheme policy |
|----------------------------|------------------------------|--------------------------|
| `Advance(intent)`          | the significance of a change | Yes                      |
| `Bump(component)`          | the component's name         | No                       |
| `Assign(component, value)` | the component's name         | No                       |

`Intent` names what a change means - `Fix`, `Feature`, `Breaking`, or `Stable` for graduating out of initial
development - and leaves the scheme to decide which of its components that moves. Two intents may well move the same
component: below `1.0.0`, SemVer moves the patch for both `Fix` and `Feature`.

`Bump` and `Assign` address a component by the scheme's own name for it and are exempt from that policy, so an
explicit `major` moves the major even where an intent would have been redirected. This is the difference between
saying "this change breaks things" and saying "release this as 1.0.0".

```scala
import version.*
import version.semver.*

val arithmetic = summon[VersionArithmetic[SemVer]]
val base = SemVer.parseUnsafe("0.93.9")

arithmetic(base, Request.Advance(Intent.Breaking)) // Right(0.94.0) - the policy applies
arithmetic(base, Request.Bump("major"))            // Right(1.0.0)  - the policy does not
arithmetic(base, Request.Bump("epoch"))            // Left(UnsupportedComponent("semver", "epoch"))
```

## Compatibility

A compatibility rule is a plain value rather than a `given`, because a scheme has several defensible rules that
disagree with one another and choosing between them is a commitment the consumer makes.

```scala
import version.CompatibilityPolicy
import version.semver.SemVer

val policy = SemVer.Compatibility.leftmostNonZero
policy.compatible(SemVer.parseUnsafe("0.4.2"), SemVer.parseUnsafe("0.4.9")) // true

CompatibilityPolicy.strict[SemVer] // admits a version only in place of an equal one
```

See [SemVer Operations](semver/operations.md) for what each SemVer rule decides.

## Ranges

Where an ecosystem defines a language for written constraints - `^1.2.3`, `>=1.2.3 <2.0.0` - the scheme owns a range
type of its own and supplies `RangeScheme[V, R]` over it. A range is a value distinct from a version: it is read,
rendered, tested for membership and rewritten around a new version, all in the form its author wrote.

```scala
import version.semver.*

val range = SemVerRange.parse("^1.2.3").toOption.get
range.admits(SemVer.parseUnsafe("1.9.9")) // true
```

See [Ranges](ranges.md) for the surface, the membership rule, and the rewrite strategies.

## Implementing a Scheme

Supply `VersionScheme[V]` in the companion of `V`, and add the other capabilities only where the scheme really defines
them. Beyond the signatures, the contract is:

- `parse` is strict. Reject what the scheme's grammar rejects rather than coercing it into something valid.
- `show` is canonical, and `parse` must read it back as an equal value.
- `numbers` is a lossy convenience for bucketing. An empty array is a legitimate answer for a scheme with no numeric
  positions.
- `stable` and `release` concern below-release qualifiers. A scheme with no such concept answers `true` and returns
  the value unchanged, and should do so deliberately rather than by accident.

A range language brings its own type and its own contract - see [Ranges](ranges.md#supplying-ranges-for-another-scheme).
