---
title: Version Schemes
---

# Version Schemes

A version scheme defines how version numbers are structured, parsed, compared, and advanced. The `version` library is
scheme-generic - each scheme plugs in via a single import, and all tools (parsing, resolution, sbt plugin) work with it
automatically.

## Available Schemes

| Scheme                             | Import                    | Status |
|------------------------------------|---------------------------|--------|
| [SemVer 2.0.0](semver/overview.md) | `import version.semver.*` | Stable |

## Using a Scheme

Import the scheme's package. All types, operations, and instances become available:

```scala
import version.semver.*

val v = SemVer.parseUnsafe("1.2.3-alpha.1")
v.next[Major] // 2.0.0
v.show        // "1.2.3-alpha.1"

// Ordering comes from the companion
List(v, SemVer.parseUnsafe("1.0.0")).sorted // List(1.0.0, 1.2.3-alpha.1)
```

No explicit `given` imports are needed - Scala 3 finds instances in the companion automatically.

## Scheme Capabilities

Each scheme provides progressively richer capabilities:

| Capability                 | What It Provides                                                         |
|----------------------------|--------------------------------------------------------------------------|
| **Parsing and rendering**  | Parse strings, produce canonical output, compare and order versions      |
| **Component manipulation** | Bump or set individual components (e.g. increment major, set patch to 5) |
| **Git-based resolution**   | Derive versions from repository state via commit directives and tags     |

The SemVer scheme supports all three.

## Component Roles

Scheme components carry semantic roles that drive automatic versioning:

| Role       | Meaning                        | SemVer |
|------------|--------------------------------|--------|
| `Breaking` | API-incompatible changes       | Major  |
| `Feature`  | Backwards-compatible additions | Minor  |
| `Fix`      | Backwards-compatible fixes     | Patch  |

Commit directives like `breaking:` and `feature:` map to these roles, making automatic versioning work across different
schemes.
