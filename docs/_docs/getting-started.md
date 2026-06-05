---
title: Getting Started
---

A Scala 3 **versioning toolkit** - version types, parsing, manipulation, automatic derivation from Git, and build
integration.

---

## Modules

| Module               | Platforms   | Description                             |
|----------------------|-------------|-----------------------------------------|
| `version`            | JVM, Native | Version model, parsing, operations      |
| `version-resolution` | JVM, Native | Automatic version derivation from Git   |
| `version-cli`        | Native      | CLI binary (shipped on GitHub Releases) |
| `sbt-version`        | sbt 2.x     | Build integration                       |

```scala
// Core library
libraryDependencies += "africa.shuwari" %%% "version" % "@VERSION@"

// Automatic versioning
libraryDependencies += "africa.shuwari" %%% "version-resolution" % "@VERSION@"

// sbt plugin
addSbtPlugin("africa.shuwari" % "sbt-version" % "@VERSION@")
```

---

## Quick Example

```scala
import version.semver.*

val v = SemVer.parseUnsafe("1.2.3-alpha.1")

v.major.value          // 1
v.preRelease.isDefined // true

v.next[Major]   // 2.0.0
v.next[Minor]   // 1.3.0
v.next[Alpha]   // 1.2.3-alpha.2
v.as[Snapshot]  // 1.2.3-SNAPSHOT
v.core          // 1.2.3
```

---

## Automatic Versioning

The resolution engine and sbt plugin derive versions from Git state:

1. **Git tags mark releases** - annotated tags define version boundaries
2. **Commit directives express intent** - `breaking:`, `feature:`, `target: 2.0.0`
3. **Build tools compute versions** - the sbt plugin resolves versions automatically

---

## Next Steps

- [Version Schemes](schemes/overview.md) - the version model
- [Automatic Versioning](versioning/overview.md) - Git-based version derivation
- [sbt Plugin](sbt/overview.md) - build integration
- [Command-Line Tool](cli/overview.md) - the `version` binary

---

### Platform Support

| Scala             | JDK            | Scala Native           |
|-------------------|----------------|------------------------|
| @SCALA3_VERSION@+ | @JDK_VERSION@+ | @SCALANATIVE_VERSION@+ |

### Licence

Licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
