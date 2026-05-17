---
title: Operations
---

The core library provides composable operations for version manipulation and rendering.

### Status Checks

```scala
import version.semver.*

val v = SemVer.parseUnsafe("1.2.3-alpha.1")

v.stable // true (major > 0 and not snapshot)
v.snapshot // false
v.preRelease.isDefined // true

// Check via PreRelease extensions
v.preRelease.get.isAlpha // true
v.preRelease.get.isSnapshot // false
```

### Core Extraction

Strip pre-release and metadata:

```scala
val v = SemVer.parseUnsafe("1.2.3-alpha.1+build.456")
v.core // SemVer(1, 2, 3)
```

---

### Bumping

#### Core Components

Use `next[F]` for type-safe bumping. Clears pre-release and metadata:

```scala
val v = SemVer.parseUnsafe("1.2.3")

v.next[Major] // 2.0.0 (resets minor and patch)
v.next[Minor] // 1.3.0 (resets patch)
v.next[Patch] // 1.2.4

// Useful in generic code
def bump[F](v: SemVer)(using SemVer.Increment[F]): SemVer = v.next[F]
```

#### Pre-release Classifiers

`next[C]` also supports versioned pre-release classifiers with precedence-aware semantics:

```scala
import version.semver.PreReleaseClassifier.*

val release = SemVer.parseUnsafe("1.2.3")
val alpha = SemVer.parseUnsafe("1.2.3-alpha.1")
val beta = SemVer.parseUnsafe("1.2.3-beta.1")
val snap = SemVer.parseUnsafe("1.2.3-SNAPSHOT")

// Final version - start pre-release cycle
release.next[Alpha] // 1.2.3-alpha.1

// Same classifier - increment number
alpha.next[Alpha] // 1.2.3-alpha.2

// Higher-precedence classifier - advance within cycle
alpha.next[Beta] // 1.2.3-beta.1
alpha.next[ReleaseCandidate] // 1.2.3-rc.1

// Lower-precedence classifier - new patch cycle
beta.next[Alpha] // 1.2.4-alpha.1

// Snapshot (highest) - any versioned = new patch cycle
snap.next[Alpha] // 1.2.4-alpha.1
```

**Note:** `Snapshot` has no `Increment` instance - use `as[Snapshot]` instead.

---

### Pre-release Operations

#### Setting Classifier Directly

`as[C]` sets the pre-release classifier without precedence logic. Clears build metadata.

```scala
val v = SemVer.parseUnsafe("1.2.3")

// With default number (1 for versioned classifiers)
v.as[Alpha] // 1.2.3-alpha.1
v.as[ReleaseCandidate] // 1.2.3-rc.1
v.as[Snapshot] // 1.2.3-SNAPSHOT

// With specific number
v.as[Alpha](5)
// Right(1.2.3-alpha.5)

// Error: snapshot cannot have a number
v.as[Snapshot](1)
// Left(ClassifierNotVersioned(Snapshot))
```

#### Removing Pre-release

Use `core` or `copy` to remove pre-release:

```scala
val v = SemVer.parseUnsafe("1.2.3-alpha.1")
v.core // 1.2.3 (also removes metadata)
v.copy(preRelease = None) // 1.2.3 (preserves metadata)
```

---

### Build Metadata

Use `copy` for metadata operations:

```scala
val v = SemVer.parseUnsafe("1.2.3")

// Setting metadata
v.copy(metadata = Some(Metadata(List("build", "456"))))
// 1.2.3+build.456

// Removing metadata
val vMeta = SemVer.parseUnsafe("1.2.3+build.456")
vMeta.copy(metadata = None) // 1.2.3
```

### Comparison

Versions are compared via `given Ordering[SemVer]`. Import `Ordering.Implicits.infixOrderingOps` for operator syntax:

```scala
import scala.math.Ordering.Implicits.infixOrderingOps

val a = SemVer.parseUnsafe("1.0.0")
val b = SemVer.parseUnsafe("2.0.0")

a < b // true
List(b, a).sorted // List(a, b)
```

### Rendering

`v.show` produces the canonical SemVer string (core + pre-release, excludes build metadata).
`SemVer.Formatter` provides two ready instances:

| Formatter                         | Behaviour                                             | Example Output                                                              |
|-----------------------------------|-------------------------------------------------------|-----------------------------------------------------------------------------|
| `Formatter.Standard`              | Core plus pre-release; no build metadata              | `1.2.3-SNAPSHOT`                                                            |
| `Formatter.Full`                  | Verbatim build metadata (round-trips through `parse`) | `1.2.3-SNAPSHOT+202605170145.main.0123456789abcdef0123456789abcdef01234567` |
| `Formatter.Full.withShaLength(N)` | As `Full`, with the commit SHA truncated to `N` chars | `1.2.3-SNAPSHOT+202605170145.main.0123456789ab` (with `N = 12`)             |

```scala
import version.Formatter
import version.semver.*

val v = SemVer.parseUnsafe("1.2.3-SNAPSHOT+202605170145.main.0123456789abcdef0123456789abcdef01234567")
v.show // "1.2.3-SNAPSHOT"
SemVer.Formatter.Full.format(v) // verbatim, round-trips
SemVer.Formatter.Full.withShaLength(12).format(v) // "1.2.3-SNAPSHOT+202605170145.main.0123456789ab"
```

`withShaLength` accepts values in `[7, 64]` (SHA-1 hash length is 40; SHA-256 is 64). The truncation applies only to
the commit-SHA identifier; the timestamp, branch, `pr<N>`, and `dirty` identifiers are emitted unchanged.

#### Custom Formatters

Implement `Formatter[SemVer]` for full control:

```scala
val minimal: Formatter[SemVer] = (v: SemVer) =>
  val core = s"${v.major.value}.${v.minor.value}.${v.patch.value}"
  if v.snapshot then s"$core-dev" else core
```

Output: `1.2.3-dev`
