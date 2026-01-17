---
title: Automatic Versioning
---

# Automatic Versioning

The `version-cli-core` module provides Git-based version resolution, deriving versions from repository state and commit
messages.

```scala
libraryDependencies += "africa.shuwari" %% /* or `%%%` */ "version-cli-core" % "@VERSION@"
```

> **Note**: JVM and Native Platforms only at this point in time.

## How It Works

The resolution engine analyses:

1. **Git tags** — annotated version tags define release boundaries
2. **Commit messages** — directives like `breaking:` or `target: 2.0.0`
3. **Working directory** — clean vs dirty state

And produces one of:

| Mode            | Condition                       | Result                 |
|-----------------|---------------------------------|------------------------|
| **Concrete**    | Clean worktree at annotated tag | Exact tag version      |
| **Development** | Any other state                 | Snapshot with metadata |

## Quick Example

```scala
import version.cli.core.*
import version.cli.core.domain.*

val config = CliConfig(
  repo = os.pwd,
  basisCommit = "HEAD",
  prNumber = None,
  branchOverride = None,
  shaLength = 7,
  verbose = false
)

VersionCliCore.resolve(config) match
  case Right(version) =>
    println(s"Version: $version")
  case Left(error) =>
    println(s"Error: ${error.message}")
```

## Development Versions

When not at a clean tag, the engine produces:

```
<TARGET>-SNAPSHOT+<METADATA>
```

Where metadata includes:

| Identifier     | Example      | Purpose             |
|----------------|--------------|---------------------|
| `pr<N>`        | `pr42`       | Pull request number |
| `branch<name>` | `branchmain` | Current branch      |
| `commits<N>`   | `commits5`   | Commits since tag   |
| `sha<hex>`     | `shaabc1234` | Commit SHA          |
| `dirty`        | `dirty`      | Uncommitted changes |

Example: `1.2.4-SNAPSHOT+pr42.branchmain.commits5.shaabc1234.dirty`

## In This Section

- [How It Works](how-it-works.md) — detailed resolution flow
- [Commit Directives](directives.md) — controlling versions via commits
- [Validation Rules](validation.md) — target directive validation
