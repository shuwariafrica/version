---
title: ZIO Prelude
---

# ZIO Prelude

[ZIO Prelude](https://github.com/zio/zio-prelude) type class instances for version types.

```scala
libraryDependencies += "africa.shuwari" %% /* or `%%%` */ "version-zio-prelude" % "@VERSION@"
```

## Usage

```scala
import zio.prelude.*
import version.*
import version.zio.prelude.given
```

## Provided Instances

### Ord

Ordering for all comparable types:

```scala
import zio.prelude.Ord

Ord[Version].compare(v1, v2) // Ordering.LessThan
Ord[MajorVersion].compare(major1, major2)
```

Types with `Ord`:

- `Version`
- `MajorVersion`, `MinorVersion`, `PatchNumber`, `PreReleaseNumber`
- `PreReleaseClassifier`
- `PreRelease`

### Equal

Equality for all types:

```scala
import zio.prelude.Equal

Equal[Version].equal(v1, v1) // true
Equal[Metadata].equal(bm1, bm2)
```

### Hash

Hashing for all types:

```scala
import zio.prelude.Hash

Hash[Version].hash(v) // Int
```

### Debug

Pretty-printing for all types:

```scala
import zio.prelude.Debug

Debug[Version].debug(v).render
// "Version(1, 0, 0, None, None)"
```

## Example Usage

```scala
import zio.prelude.*
import version.*
import version.zio.prelude.given

val versions = List(
  Version.parseUnsafe("1.0.0"),
  Version.parseUnsafe("1.0.0-alpha.1"),
  Version.parseUnsafe("2.0.0")
)

// Maximum (uses Ord)
versions.maxOption // Some(2.0.0)

// Distinct (uses Hash + Equal)
versions.distinct

// Sorting (uses Ord)
versions.sorted
```

## Platform Support

| Platform     | Status |
|--------------|:------:|
| JVM          |   ✅    |
| Scala.js     |   ✅    |
| Scala Native |   ✅    |
