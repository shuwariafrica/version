---
title: Versioning
---

# Version Derivation

Derive semantically correct versions from Git repository state and commit messages.

```scala
libraryDependencies += "africa.shuwari" %%% "version-resolution" % "@VERSION@"
```

Available on JVM and Scala Native.

## Mode Selection

| Condition                                                 | Result                                           |
|-----------------------------------------------------------|--------------------------------------------------|
| Basis commit has a valid annotated tag AND clean worktree | **Concrete version** - exact tag                 |
| Otherwise                                                 | **Development version** - snapshot with metadata |

"Dirty" includes modified tracked files and untracked files (excluding ignored).

### Concrete Version

At a clean tagged commit, the exact tag version is returned:

```
Tag: v2.3.1  ->  2.3.1
```

### Development Version

Between releases, a target version is computed:

**Priority:**

1. **A version named outright** - `target: X.Y.Z` in a commit, where the existing tags admit it
2. **What the commits asked for** - every directive is applied to the base version and the highest result wins
3. **Default fallback:**

| Condition              | Target                      |
|------------------------|-----------------------------|
| Base is pre-release    | Its release                 |
| Base is final          | Patch + 1                   |
| No base, repo has tags | Highest tag + breaking bump |
| No tags anywhere       | `0.1.0`                     |

Below `1.0.0`, a stated kind of change means one component less than it would above it, while a component named
outright is obeyed as written. See [SemVer Derivation](semver.md).

### Build Metadata

Development versions ship with structured metadata describing the basis commit. The resolver populates a
`DevelopmentMetadata` record; the chosen [Formatter](../schemes/semver/operations.md#rendering) decides whether and how
it appears in the rendered version string.

For the SemVer scheme, the default `developmentVersion` writes these identifiers, in order, into the `+` build-metadata
section:

| Position | Identifier                           | Condition              |
|----------|--------------------------------------|------------------------|
| 1        | `yyyymmddhhmm` (UTC committer time)  | Basis commit available |
| 2        | `<branch>` (sanitised) or `detached` | Always                 |
| 3        | `<sha>` (lowercase hex, full length) | Always                 |
| 4        | `pr<N>`                              | PR number supplied     |
| 5        | `dirty`                              | Worktree dirty         |

The 12-character UTC timestamp leads so that raw string comparison of two snapshots of the same base sorts them in
commit-time order. The timestamp is the basis commit's committer time, not the build time, so re-runs are reproducible.
Git-recorded times can be skewed (backdated or forward-dated commits); the resolver renders them as-is.

For PR builds, the branch slot carries the **target** branch (where the merge will land); the source branch is typically
too volatile to be useful in a version string. Branch names are sanitised for the SemVer build-metadata grammar at
render time (lowercased; non-`[0-9a-z-]` replaced with `-`; runs of `-` collapsed; leading/trailing `-` trimmed). The
raw branch label remains in `DevelopmentMetadata.branch` for programmatic consumers.

`v.show` and the default `version` setting exclude build metadata. Configure
[`versionResolver`](../sbt/settings.md#versionresolver) with a `SemVer.Formatter.Full` (optionally with a truncated SHA
via `.withShaLength(N)`) to include metadata in the rendered string.

**Example:** `1.2.4-SNAPSHOT+202605170145.main.1234567890ab.pr42.dirty`

## Tag Recognition

A tag is recognised as a valid version tag if:

- It is an **annotated tag** (lightweight tags are ignored)
- Its name parses as valid SemVer (optional `v`/`V` prefix)
- Pre-release classifiers map to known aliases
- All numeric components are within bounds

When multiple valid tags exist on one commit, a final release outranks a pre-release of the same core. Otherwise, the
highest version wins.

## Commit Scanning

| Condition       | Scan Range                      |
|-----------------|---------------------------------|
| Base tag exists | Commits after base up to HEAD   |
| No base tag     | All commits reachable from HEAD |

All commits are scanned for keywords, including merge commits and commits from merged branches. See
the [Specification](specification.md) for ignore-directive semantics and the commit-count convention.

## API Usage

Most consumers use the [sbt plugin](../sbt/overview.md). For direct API usage:

```scala
import version.semver.*
import version.resolution.*

val config = ResolutionConfig.default[SemVer]("/path/to/repo")
Resolver.resolve(config, discoverRepository) match
  case Right(version) => println(version.show)
  case Left(error)    => println(error.getMessage)
```

`discoverRepository` searches upward from the path for the repository containing it; `openRepository` takes the path
as the repository itself. Each operation also accepts a `GitRepository` in place of the function, for a caller that
already holds one: the engine closes only a repository it opened itself. Diagnostics are opt-in - with no `Logger` in
scope nothing is recorded, and passing one as a third argument records to it.

Customise via `.copy`:

```scala
val config = ResolutionConfig.default[SemVer]("/path/to/repo").copy(
  prNumber = Some(42),
  branchOverride = Some("feature/xyz")
)
```

### See Also

- [Commit Directives](directives.md) - the directive language
- [Target Validation](validation.md) - target directive rules
- [SemVer Behaviour](semver.md) - SemVer-specific keywords, defaults, output format
- [Specification](specification.md) - formal algorithm reference
