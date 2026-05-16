---
title: Automatic Versioning
---

Derive semantic versions from Git repository state and commit messages.

```scala
libraryDependencies += "africa.shuwari" %%% "version-resolution" % "@VERSION@"
```

Available on JVM and Scala Native.

## Mode Selection

| Condition | Result |
|-----------|--------|
| Basis commit has a valid annotated tag AND clean worktree | **Concrete version** - exact tag |
| Otherwise | **Development version** - snapshot with metadata |

"Dirty" includes modified tracked files and untracked files (excluding ignored).

### Concrete Version

At a clean tagged commit, the exact tag version is returned:

```
Tag: v2.3.1  ->  2.3.1
```

### Development Version

Between releases, a target version is computed:

**Priority:**

1. **Valid target directive** - explicit `target: X.Y.Z` in commits
2. **Absolute sets** - `version: major: 3` (highest per component wins)
3. **Relative changes** - `version: major` or `breaking:` (coalesced)
4. **Default fallback:**

| Condition | Target Core |
|-----------|-------------|
| Base is pre-release | Core unchanged |
| Base is final | Patch + 1 |
| No base, repo has tags | Highest major + 1 |
| No tags anywhere | `0.1.0` |

### Build Metadata

Development versions include metadata identifiers in strict order:

| Position | Identifier | Condition |
|----------|------------|-----------|
| 1 | `pr<N>` | PR number provided |
| 2 | `branch<name>` | Always (or `branchdetached`) |
| 3 | `commits<N>` | Always |
| 4 | `sha<hex>` | Always (7-40 chars) |
| 5 | `dirty` | Worktree dirty |

Whether metadata appears in the rendered string depends on the [Formatter](../schemes/semver/operations.md#rendering) used.

**Example:** `1.2.4-SNAPSHOT+pr42.branchmain.commits5.shaabc1234.dirty`

## Tag Recognition

A tag is recognised as a valid version tag if:

- It is an **annotated tag** (lightweight tags are ignored)
- Its name parses as valid SemVer (optional `v`/`V` prefix)
- Pre-release classifiers map to known aliases
- All numeric components are within bounds

When multiple valid tags exist on one commit, a final release outranks a pre-release of the same core. Otherwise, the highest version wins.

## Commit Scanning

| Condition | Scan Range |
|-----------|------------|
| Base tag exists | Commits after base up to HEAD |
| No base tag | All commits reachable from HEAD |

All commits are scanned for keywords, including merge commits and commits from merged branches. The commit count in build metadata uses first-parent only, excluding merges.

## API Usage

Most consumers use the [sbt plugin](../sbt/overview.md). For direct API usage:

```scala
import version.semver.*
import version.resolution.*

val config = ResolutionConfig.default[SemVer]("/path/to/repo")
val result = VersionCliCore.resolve(config, openRepository)
result match
  case Right(version) => println(version.show)
  case Left(error)    => println(error.message)
```

Customise via `.copy`:

```scala
val config = ResolutionConfig.default[SemVer]("/path/to/repo").copy(
  shaLength = 7,
  prNumber = Some(42),
  branchOverride = Some("feature/xyz")
)
```

### See Also

- [Commit Directives](directives.md) - the directive language
- [Target Validation](validation.md) - target directive rules
- [SemVer Behaviour](semver.md) - SemVer-specific keywords, defaults, output format
- [Specification](specification.md) - formal algorithm reference
