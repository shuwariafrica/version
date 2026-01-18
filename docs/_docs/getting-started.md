## Getting Started

A Scala 3 toolkit for **intent-based versioning** conforming to [Semantic Versioning 2.0.0](https://semver.org/).

### What's Included

| Module                               | Dependency                                                     | Purpose                             |
|--------------------------------------|----------------------------------------------------------------|-------------------------------------|
| [Core library](core/overview.md)     | `"africa.shuwari" %% "version" % "@VERSION@"`                  | SemVer types and operations         |
| [CLI core](resolution/overview.md)   | `"africa.shuwari" %% "version-cli-core" % "@VERSION@"`         | Git-based version derivation engine |
| [Jsoniter codec](codecs/overview.md) | `"africa.shuwari" %% "version-codecs-jsoniter" % "@VERSION@"`  | JSON codecs                         |
| [ZIO JSON codec](codecs/overview.md) | `"africa.shuwari" %% "version-codecs-zio" % "@VERSION@"`       | JSON codecs                         |
| [YAML codec](codecs/overview.md)     | `"africa.shuwari" %% "version-codecs-yaml" % "@VERSION@"`      | YAML codecs                         |
| [ZIO Prelude](zio-prelude.md)        | `"africa.shuwari" %% "version-zio-prelude" % "@VERSION@"`      | Type class instances                |
| [sbt Plugin](sbt/overview.md)        | `addSbtPlugin("africa.shuwari" % "sbt-version" % "@VERSION@")` | sbt plugin implementation           |

Use `%%%` instead of `%%` for Scala.js and Scala Native.

### Platform Support

| Scala             | JDK            | Scala.js           | Scala Native           |
|-------------------|----------------|--------------------|------------------------|
| @SCALA3_VERSION@+ | @JDK_VERSION@+ | @SCALAJS_VERSION@+ | @SCALANATIVE_VERSION@+ |

---

### Quick Example

```scala
import version.*
import version.given

// Parse a version string
val v = "1.2.3-alpha.1".toVersionUnsafe

// Inspect components
v.major.value // 1
v.minor.value // 2
v.patch.value // 3
v.preRelease.isDefined // true

// Bump versions
v.next[MajorVersion] // 2.0.0
v.next[MinorVersion] // 1.3.0
v.next[PatchNumber] // 1.2.4

// Advance within same pre-release
v.next[Alpha] // 1.2.3-alpha.2

// Work with pre-releases
v.core // 1.2.3
v.as[Snapshot] // 1.2.3-SNAPSHOT
```

---

### Objectives

Version numbers should encode **intent**, not build artefacts. This toolkit supports a declarative approach:

1. **Commit messages express semantic intent** — use directives like `breaking:`, `feature:`, or `target: 2.0.0`
2. **Git tags mark releases** — annotated tags define version boundaries
3. **Build tools derive versions** — the sbt plugin computes versions from Git state

This eliminates manual version bumping whilst preserving control over version semantics.

### Next steps

- [Core Library](core/overview.md) — understand the version model
- [Automatic Versioning](resolution/overview.md) — set up Git-based resolution
- [sbt Plugin](sbt/overview.md) — integrate with your build

---

### Licence

Licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
