---
title: Derivation Specification
---

# Version Derivation Specification

Normative definition of the version derivation algorithm. This document is what a scheme must satisfy to be released
from a repository, and the precise statement of what the resolver guarantees its callers.

For what SemVer itself does with each of these - the tags it recognises, the words it maps, the strings it renders -
see [SemVer Derivation](semver.md).

## Scheme Contract

Derivation operates on any version type `V` for which three instances exist. A scheme supplying only the first can be
read, ordered and compared, but not released from a repository.

`VersionScheme[V]` - reading, rendering and ordering:

| Operation             | Purpose                                                                 |
|-----------------------|-------------------------------------------------------------------------|
| `name`                | The identifier the scheme is selected by, as its ecosystem publishes it  |
| `parse(input)`        | Read a version, rejecting anything outside the scheme's grammar          |
| `precedence`          | The scheme's normative comparison, which may equate distinct spellings   |
| `difference(a, b)`    | The most significant tier separating two versions                        |
| `v.show`              | The canonical rendering, which `parse` reads back as an equal value      |
| `v.stable`            | Whether the version carries no below-release qualifier                   |
| `v.release`           | The version with every below-release qualifier stripped                  |
| `v.numbers`           | The leading numeric components, for coarse bucketing                     |

`VersionArithmetic[V]` - one operation, `apply(v, request)`, interpreting a `Request` against a version and returning
either the result or the reason the scheme refused it. A request is one of:

| Request                   | Meaning                                                                    |
|---------------------------|----------------------------------------------------------------------------|
| `Advance(intent)`         | Move by the significance of the change: `Fix`, `Feature`, `Breaking`, `Stable` |
| `Bump(component)`         | Move the named component by one step                                       |
| `Assign(component, value)`| Set the named component, resetting every component below it                |

An intent is subject to the scheme's own advancement policy and may be redirected by it. A named component is not: an
explicit `major` moves the major even where an intent would not.

`ResolvableScheme[V]` - what releasing from a repository additionally needs:

| Operation                              | Purpose                                                    |
|----------------------------------------|-------------------------------------------------------------|
| `initialVersion`                       | Where a project with no releases starts                     |
| `developmentVersion(release, metadata)`| The in-development version for a release                    |
| `defaultTarget(base)`                  | The target when no directive applies                        |
| `directives`                           | The words this scheme's commit messages are read for, and the request each stands for |
| `v.snapshot`                           | Whether the version names an in-development build           |

These three are the whole of what derivation reads. A scheme whose ecosystem also defines a language for written
constraints supplies `RangeScheme[V, R]` over a range type of its own, described under
[Ranges](../schemes/ranges.md).

Tag parsing is configured separately via `config.tagParser: String => Option[V]`, which typically strips a `v`/`V`
prefix and delegates to `scheme.parse`. Versions named by a `target:` directive are read by `scheme.parse` itself, so a
scheme accepts the spellings it means to accept.

---

## Algorithm

### Mode Selection

| Condition                                                              | Result                  |
|------------------------------------------------------------------------|-------------------------|
| Basis commit has >= 1 valid version tag AND working directory is clean | **Concrete Version**    |
| Otherwise                                                              | **Development Version** |

The basis is HEAD unless `config.basisCommit` names a revision, in which case that revision is resolved strictly. Only
the unpinned basis tolerates a repository with no commits at all. For bare repositories, the working directory is
considered clean. A version tag is valid if `config.tagParser` succeeds on it and it is an annotated Git tag.

### Concrete Version

Return the highest valid version tag at the basis commit by `scheme.precedence`. Where several tags sit on the commit,
precedence decides between them, so a release outranks a pre-release of the same numbers.

### Development Version

#### Step 1: Base Version

Highest reachable valid tag by `scheme.precedence`, or none. With none, the range has released nothing to advance
from: the target is `scheme.initialVersion`, which only a version named outright may raise.

#### Step 2: Directive Extraction

Read every commit message in the range (base tag to basis commit, or all commits if no base) as a `List[Directive]`
under [the grammar below](#directive-grammar). Ignore directives withdraw the commits they name; what survives is a
list of requests and of versions named outright.

All paths of the commit graph are scanned (including merge branches). The commit count in metadata uses first-parent
only, excluding merges.

#### Step 3: Target

In priority order:

1. **Named outright**: the highest version a `target:` directive names that survives [validation](#target-validation),
   reduced to its `release`
2. **Requested**: `arithmetic(base, request)` for every surviving request, the highest result by `scheme.precedence`
   winning. Every request contributes a candidate; none is discarded for being of a different kind from another
3. **Default**: `workflow.defaultTarget(base)`

A request the scheme refuses contributes no candidate and is returned beside the target as a `VersionError`, so a
caller can report it. Refusal is never fatal: the remaining requests still decide, and step 3 always yields a version.

Aggregating by precedence rather than by kind means the outcome does not depend on the order commits are read in, and
that a scheme's own policy - not the grammar - decides how far each request moves the version. Where SemVer caps an
intent below `1.0.0`, a `major` named as a component is exempt, so `1.0.0` is reached deliberately rather than by
accident.

#### Step 4: Assembly

`scheme.developmentVersion(targetCore, metadata)` where metadata is a `DevelopmentMetadata` containing branch (verbatim,
including separators), commit SHA, commit count, basis commit committer time, PR number, and dirty flag. The scheme
decides how to render each field; the resolution engine does not pre-sanitise.

### Build Metadata (SemVer reference rendering)

The SemVer scheme renders development metadata as follows. Other schemes implementing `ResolvableScheme` may encode the
same data differently.

Identifiers appear in strict order. The 12-character UTC committer timestamp leads so that raw string comparison of two
snapshots sharing the same base version produces chronological order. The commit count is carried in the metadata model
but is not rendered by the default SemVer scheme.

| Position | Identifier                           | Condition             |
|----------|--------------------------------------|-----------------------|
| 1        | `yyyymmddhhmm` (UTC committer time)  | Basis commit supplied |
| 2        | `<branch>` (sanitised) or `detached` | Always                |
| 3        | `<sha>` (full hash)                  | Always                |
| 4        | `pr<N>`                              | PR number supplied    |
| 5        | `dirty`                              | Worktree dirty        |

Branch sanitisation (SemVer-specific, applied at render time): lowercase, replace non-`[0-9a-z-]` with `-`, collapse
consecutive `-`, trim leading/trailing `-`, empty becomes `detached`. The raw branch name remains intact in
`DevelopmentMetadata.branch`. Override takes precedence over detection. For PR builds, the target branch (where the
merge will land) is preferred over the source branch.

Commit count: first-parent non-merge commits from base to basis. Available via
`DevelopmentMetadata.commitCount` for custom formatters. SHA: lowercase hex of the basis commit's full hash, supplied
verbatim by the model; truncation, when desired, is applied at render time by the chosen
[Formatter](../schemes/semver/operations.md#rendering). Timestamp uses the basis commit's committer time as recorded
by Git, formatted in UTC; the resolver does not normalise skewed times.

Sortability invariant: for two development versions sharing the same target core, lexicographic ordering of the rendered
string preserves the chronological order of their basis commits. SemVer build metadata is ignored by SemVer 2.0.0
precedence rules; the invariant is provided for consumers that sort raw rendered strings.

---

## Directive Grammar

The vocabulary is `workflow.directives: Map[String, Request]`, keyed by lowercase keyword. The grammar itself names no
component and no kind of change: a scheme that maps `epoch` to `Bump("epoch")` gains `version: epoch` and `[epoch]` by
saying so, and one that maps nothing has only the target and ignore forms.

```
version: <keyword>                Emit(directives(keyword))
version: <keyword>: <N>           Emit(Assign(component, N)), where directives(keyword) is Bump(component)
<keyword>: <non-empty text>       Emit(directives(keyword))
[<keyword>]                       Emit(directives(keyword))
target: <version>                 Target(<version>), read later by scheme.parse
version: ignore                   IgnoreSelf                   (also [ignore])
version: ignore: <id>[, <id>...]  IgnoreCommits, by identifier prefix of 7 or more hex characters
version: ignore: <id>..<id>       IgnoreRange, inclusive
version: ignore-merged            IgnoreMerged                 (also [ignore-merged])
```

`version` and `target` head the grammar's own forms and are never read as keywords, whatever a scheme maps them to. `N`
is a decimal integer within `Long`; a value after a keyword that maps to anything but a `Bump` is unrecognised, since
intents carry no magnitude.

Matching is case-insensitive over ASCII, tolerates whitespace around the colon, and requires word-boundary alignment
against the character class `[0-9A-Za-z-]`. A bracket is a directive when its trimmed content is one bare keyword, or
when a colon directive leads it (`[version: major]`, `[target: 2.0.0]`, `[breaking: <text>]`); it then counts exactly
once. Brackets are boundary-aligned on both sides. A bracket whose content is neither is opaque prose (`[skip ci]`,
`[see version: major]`), so a directive embedded mid-content does not match.

Every line is read the same way and no position within one is privileged, so a directive is found equally in a subject,
in a body paragraph, and behind the bullet and indentation of a merged-in subject. What makes a word a directive is the
boundary alignment and the colon adjacency alone: `breaking (api): x` and `breaking!: x` are prose, because the word
does not meet its colon.

## Target Validation

A version `T` named by a `target:` directive, reduced to `T.release`, is rejected if:

- **Rule A**: a reachable tag that is `stable` has a `release` at or above `T`
- **Rule B**: the highest reachable tag is not `stable` and its `release` is above `T` (equality accepted)
- **Rule C**: the highest `stable` tag anywhere in the repository has a `release` at or above `T`; or, where the
  repository has no stable tag at all, the highest tag's `release` is above `T`. This floor applies whether or not a
  base is reachable
- **Rule D**: `scheme.parse` rejects the version as written
- **Rule E**: of the survivors, the highest by `scheme.precedence` wins

Equality is permitted against pre-release versions only. See [Target Validation](validation.md) for worked examples.

## Edge Cases

| Scenario                        | Behaviour                                                                                       |
|---------------------------------|---------------------------------------------------------------------------------------------------|
| Unborn repository (no commits)  | `scheme.initialVersion` as a Concrete resolution: there is nothing to develop from, so no development metadata is built |
| Unborn repository, pinned basis | Rejected: only the unpinned basis tolerates a repository with no commits                          |
| Bare repository                 | Working directory considered clean; `dirty` never emitted                                         |
| Detached HEAD                   | `branch = None` in `DevelopmentMetadata`; the SemVer scheme renders the slot as `detached`        |
| No tags anywhere                | `scheme.initialVersion`                                                                           |
| Shallow clone                   | Treated as no base; defaults apply                                                                |

---

## Determinism

Given fixed repository state and configuration inputs, the derived version is deterministic and idempotent.
