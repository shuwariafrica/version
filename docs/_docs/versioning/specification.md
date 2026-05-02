---
title: Specification
---

Formal definition of the version resolution algorithm. This document is the normative reference for implementors of `ResolvableScheme[V]` and for understanding the resolution engine's guarantees.

For user-facing behaviour with SemVer, see [SemVer Behaviour](semver.md).

## Scheme Contract

The resolution algorithm operates on any version type `V` for which a `ResolvableScheme[V]` instance exists. The algorithm calls these scheme operations:

| Operation | Purpose |
|---|---|
| `scheme.parse(input)` | Parse a version string (used for target directives) |
| `scheme.ordering` | Compare and sort versions |
| `scheme.isFinal(v)` | Whether a version has no development markers |
| `scheme.core(v)` | Strip development/pre-release markers |
| `scheme.incrementComponent(v, index)` | Bump a component with lower-component reset |
| `scheme.setComponent(v, index, value)` | Set a component with lower-component reset |
| `scheme.keywordAliases` | Map keyword strings to component indices |
| `scheme.layout` | Component descriptors with semantic roles |
| `scheme.initialVersion` | Version for repositories with no tags |
| `scheme.developmentVersion(core, metadata)` | Assemble a development version |
| `v.defaultBump` | Default advancement when no directives apply |

Tag parsing is configured separately via `config.tagParser: String => Option[V]`, which typically strips a `v`/`V` prefix and delegates to `scheme.parse`.

## Algorithm

### Mode Selection

| Condition | Result |
|---|---|
| Basis commit has >= 1 valid version tag AND working directory is clean | **Concrete Version** |
| Otherwise | **Development Version** |

For bare repositories, the working directory is considered clean. A version tag is valid if `config.tagParser` succeeds on it and it is an annotated Git tag.

### Concrete Version

Return the highest valid version tag at the basis commit by `scheme.ordering`. When multiple tags exist, `scheme.isFinal` versions outrank non-final versions of the same core.

### Development Version

#### Step 1: Base Version

Highest reachable valid tag by `scheme.ordering`, or none.

#### Step 2: Keyword Extraction

Scan commit messages in the range (base tag to basis commit, or all commits if no base) using `scheme.keywordAliases` to map keyword strings to `ComponentBump(index)` and `ComponentSet(index, value)` directives. Target directives carry unparsed strings resolved via `scheme.parse`. Ignore directives exclude commits from the scan.

All paths of the commit graph are scanned (including merge branches). The commit count in metadata uses first-parent only, excluding merges.

#### Step 3: Target Core

In priority order:

1. **Target directive**: Highest valid target core after [validation](validation.md) via `scheme.parse`
2. **Absolute sets**: `scheme.setComponent(v.core, index, value)` (highest value per component wins)
3. **Relative bumps**: `scheme.incrementComponent(v.core, index)` (highest-precedence index wins, coalesced)
4. **Default**: `v.defaultBump.core` if base is final; `scheme.core(v)` if base is non-final; `scheme.incrementComponent(highest, 0)` if no base but repo has tags; `scheme.initialVersion.core` if no tags

#### Step 4: Assembly

`scheme.developmentVersion(targetCore, metadata)` where metadata is a `DevelopmentMetadata` containing branch, commit SHA, commit count, PR number, and dirty flag.

### Build Metadata

Assembled in strict order:

| Position | Identifier | Condition |
|---|---|---|
| 1 | `pr<N>` | PR number supplied |
| 2 | `branch<normalised>` or `branchdetached` | Always |
| 3 | `commits<N>` | Always |
| 4 | `sha<hex>` | Always |
| 5 | `dirty` | Worktree dirty |

Branch normalisation: lowercase, replace non-`[0-9a-z-]` with `-`, collapse consecutive `-`, trim leading/trailing `-`, empty becomes `detached`. Override takes precedence over detection.

Commit count: first-parent non-merge commits from base to basis. Clamped to `Int.MaxValue`. SHA length: configurable, 7 to 40, lowercase hex.

## Keyword Resolution

Keywords are resolved from `scheme.keywordAliases`: a `Map[String, Int]` mapping lowercase keyword strings to zero-based component indices. Keywords whose component has `ComponentRole.Fix` in `scheme.layout` produce no bump for relative increments or standalone shorthands (the fix-role component is the default bump).

The keyword grammar:

```
version: <keyword>                  Relative increment
version: <keyword>: <N>             Absolute set (non-negative integer)
version: ignore                     Exclude this commit
version: ignore: <sha>[, <sha>...]  Exclude commits by SHA prefix (>= 7 hex chars)
version: ignore: <sha>..<sha>       Exclude range (inclusive, positional)
version: ignore-merged              Exclude merged branch commits
<keyword>: <non-empty text>         Standalone shorthand
target: <version-string>            Target directive (parsed via scheme.parse)
```

Matching is case-insensitive with word-boundary alignment.

## Target Validation

A target with core `T` is rejected if:

- **Rule A**: A reachable final tag has core >= `T`
- **Rule B**: Highest reachable non-final has core > `T` (equality accepted)
- **Rule C**: No reachable base, but repository-wide final has core >= `T`
- **Rule D**: Basis commit carries a final tag with core >= `T`
- **Rule E**: Target fails `scheme.parse`
- **Rule F**: Multiple survivors - highest by `scheme.ordering` wins

Equality is permitted against non-final cores only. See [Target Validation](validation.md) for details and examples.

## Edge Cases

| Scenario | Behaviour |
|---|---|
| Empty repository (no commits) | `scheme.initialVersion` as development version; metadata omits SHA and commit count |
| Bare repository | Working directory considered clean; `dirty` never emitted |
| Detached HEAD | `branchdetached` in metadata |
| No tags anywhere | `scheme.initialVersion` |
| Shallow clone | Treated as no base; defaults apply |
| Commit count overflow | Clamped to 2147483647 |

## Determinism

Given fixed repository state and configuration inputs, the derived version is deterministic and idempotent.

## Out of Scope

- Creating or mutating Git tags
- Publishing or uploading artefacts
- Multi-project or path-scoped tagging conventions
- Signature verification (both backends dereference signed objects transparently)
