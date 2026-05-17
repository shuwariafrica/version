---
title: Automatic Versioning
---

Derive semantic versions from Git repository state and commit messages.

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

1. **Valid target directive** - explicit `target: X.Y.Z` in commits
2. **Absolute sets** - `version: major: 3` (highest per component wins)
3. **Relative changes** - `version: major` or `breaking:` (coalesced)
4. **Default fallback:**

| Condition              | Target Core       |
|------------------------|-------------------|
| Base is pre-release    | Core unchanged    |
| Base is final          | Patch + 1         |
| No base, repo has tags | Highest major + 1 |
| No tags anywhere       | `0.1.0`           |

### Build Metadata

Development versions ship with structured metadata describing the basis commit. The resolver populates a
`DevelopmentMetadata` record; the chosen [Formatter](../schemes/semver/operations.md#rendering) decides whether and how
it appears in the rendered version string.

For the SemVer scheme, the default `developmentVersion` writes these identifiers, in order, into the `+` build-metadata
section:

| Position | Identifier                                                   | Condition              |
|----------|--------------------------------------------------------------|------------------------|
| 1        | `yyyymmddhhmm` (UTC committer time)                          | Basis commit available |
| 2        | `<branch>` (sanitised) or `detached`                         | Always                 |
| 3        | `<short-sha>` (lowercase hex; length from `shaLength`, 7-40) | Always                 |
| 4        | `pr<N>`                                                      | PR number supplied     |
| 5        | `dirty`                                                      | Worktree dirty         |

The 12-character UTC timestamp leads so that raw string comparison of two snapshots of the same base sorts them in
commit-time order. The timestamp is the basis commit's committer time, not the build time, so re-runs are reproducible.
Git-recorded times can be skewed (backdated or forward-dated commits); the resolver renders them as-is.

For PR builds, the branch slot carries the **target** branch (where the merge will land); the source branch is typically
too volatile to be useful in a version string. Branch names are sanitised for the SemVer build-metadata grammar at
render time (lowercased; non-`[0-9a-z-]` replaced with `-`; runs of `-` collapsed; leading/trailing `-` trimmed). The
raw branch label remains in `DevelopmentMetadata.branch` for programmatic consumers.

`v.show` and the default `version` setting exclude build metadata. Set `versionFormatter := Some(SemVer.Formatter.full)`
to include it.

**Example:** `1.2.4-SNAPSHOT+202605170145.main.abcdef123456.pr42.dirty`

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
