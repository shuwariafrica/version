---
title: Specification
---

# Version Resolution Specification

This specification defines the deterministic rules for deriving a Semantic Version (SemVer 2.0.0) from a Git repository.
It is the authoritative reference for all `version` libraries and the `version-cli` application.

---

## 1. Overview

The system produces exactly one of two outputs for any given repository state:

| Output Type             | Description                                                                     |
|-------------------------|---------------------------------------------------------------------------------|
| **Concrete Version**    | An existing valid SemVer tag at the basis commit with a clean working directory |
| **Development Version** | A snapshot of an upcoming release when concrete conditions are not met          |

All derivation depends solely on:

- Git commit graph and ancestry
- Git tags (annotated only)
- Commit messages
- Configuration inputs (PR number, branch override, SHA abbreviation length)
- Working directory state

The algorithm is purely functional: no repository mutation, no heuristics beyond these rules.

---

## 2. Definitions

| Term               | Definition                                                                        |
|--------------------|-----------------------------------------------------------------------------------|
| **Basis Commit**   | The commit under evaluation (default: `HEAD`)                                     |
| **Reachable Tag**  | A valid version tag whose commit is an ancestor of (or equal to) the basis commit |
| **Base Version**   | The highest reachable valid SemVer tag, if any exists                             |
| **Target Version** | The `MAJOR.MINOR.PATCH` triple produced by analysis                               |
| **Core Version**   | The `MAJOR.MINOR.PATCH` portion without pre-release or metadata                   |
| **Pre-release**    | The `-PRERELEASE` suffix; always `-SNAPSHOT` for development versions             |
| **Build Metadata** | The `+METADATA` suffix containing contextual identifiers                          |
| **Final Release**  | A version with no pre-release component                                           |

---

## 3. Tag Recognition

### 3.1 Valid Version Tags

A Git tag is recognised as a **valid version tag** if and only if:

1. It is an **annotated tag** (lightweight tags are ignored)
2. Its name parses as valid SemVer 2.0.0 (optional leading `v` or `V`)
3. All numeric components are non-negative integers within 32-bit signed bounds
4. Any pre-release classifier maps to a recognised alias
5. Any build metadata identifiers contain only `[0-9A-Za-z-]` and are non-empty

### 3.2 Pre-release Classifier Hierarchy

Classifiers are recognised case-insensitively via aliases. The first alias listed is the canonical output form.
Precedence order (lowest to highest):

| Classifier        | Aliases          | Versioned (requires number ≥ 1) |
|-------------------|------------------|---------------------------------|
| Development       | `dev`            | Yes                             |
| Milestone         | `milestone`, `m` | Yes                             |
| Alpha             | `alpha`, `a`     | Yes                             |
| Beta              | `beta`, `b`      | Yes                             |
| Release Candidate | `rc`, `cr`       | Yes                             |
| Snapshot          | `SNAPSHOT`       | No (must not have number)       |

### 3.3 Multiple Tags on One Commit

When multiple valid version tags exist on a single commit:

- A final release always outranks a pre-release of the same core
- Otherwise, the highest SemVer version takes precedence

### 3.4 Ignored Tags

The following are silently ignored:

- Lightweight tags (not annotated)
- Tags that fail SemVer parsing
- Tags with unrecognised pre-release classifiers
- Tags with invalid build metadata

### 3.5 Repository-Wide Highest Tag

When no reachable tag exists from the basis commit, the **repository-wide highest** valid tag (across all branches) is
used for:

- Default "no-base" derivation (target becomes `(highest.major + 1).0.0`)
- Target directive validation (Rule C)

---

## 4. Commit Message Directives

### 4.1 Scan Scope

Commits are scanned for directives within the following range:

- **With Base Version**: Commits strictly after the base tag up to and including the basis commit
- **Without Base Version**: All commits reachable from the basis commit (including merge paths)

**All commits are scanned**, including:

- Merge commits themselves (allowing directives in merge commit messages)
- Commits from merged branches (reachable via non-first-parent paths)

**Note**: The commit count in build metadata uses first-parent only and excludes merge commits, but keyword scanning
traverses all paths.

### 4.2 Matching Rules

- Case-insensitive matching
- Optional whitespace around colons (`version:minor` ≡ `version : minor`)
- Tokens must be word-boundary aligned (`reversion`, `retarget` do not match)

### 4.3 Bump Tokens

These tokens are interchangeable synonyms:

| Component | Tokens                     |
|-----------|----------------------------|
| Major     | `major`, `breaking`        |
| Minor     | `minor`, `feature`, `feat` |

**Note:** `patch` and `fix` tokens are recognised for the absolute set form (`version: patch: N`) only. Relative
patch increments (`version: patch`, `fix: message`) have no effect because patch increment is already the default
behaviour when no major or minor change is specified.

### 4.4 Version Directive Forms

The `version:` keyword supports three forms:

**Relative Increment** (bump by one):

```
version: <bump-token>
```

Examples: `version: major`, `version: breaking`, `version: minor`, `version: feature`, `version: feat`

**Absolute Set** (set to specific value):

```
version: <bump-token>: <N>
```

Examples: `version: major: 3`, `version: minor: 5`, `version: patch: 5`

Each `<N>` must be a non-negative integer. If multiple absolutes target the same component, the highest value wins.

**Ignore Directives**:

```
version: ignore                           # Ignore this commit
version: ignore: <sha>                     # Ignore specific commit
version: ignore: <sha>, <sha>, ...         # Ignore multiple commits
version: ignore: <sha>..<sha>              # Ignore range (inclusive)
version: ignore-merged                     # Ignore all merged commits
```

Ignore directive forms:

| Form                            | Effect                                                        |
|---------------------------------|---------------------------------------------------------------|
| `version: ignore`               | Excludes the commit containing this directive                 |
| `version: ignore: <sha>`        | Excludes commits matching the SHA prefix (7+ chars)           |
| `version: ignore: <sha>, <sha>` | Excludes multiple commits by SHA prefix                       |
| `version: ignore: <sha>..<sha>` | Excludes commits in the range (inclusive, by commit order)    |
| `version: ignore-merged`        | Excludes all commits from merged branches (merge commit only) |

SHA prefixes must be at least 7 characters. Invalid SHA references are silently ignored.

**Merge Commit Use Case**: When merging a feature branch, the merge commit can use `version: ignore-merged` to
discard all bump directives from the incoming commits, then specify its own directive (e.g., `breaking: API redesign`).

### 4.5 Standalone Shorthands

Bump tokens may appear as commit message prefixes when followed by a colon and **non-empty text**:

```
<bump-token>: <text>
```

Valid examples:

- `breaking: Remove deprecated API` → Major increment
- `feat: Add caching support` → Minor increment

**Note:** `fix: <text>` and `patch: <text>` shorthands are recognised for Conventional Commits compatibility but have
no effect on version calculation — patch increment is already the default behaviour.

Invalid (no text after colon): `breaking:`, `feat:`

### 4.6 Target Directive

```
target: <SEMVER>
```

Rules:

- Optional leading `v` or `V`
- Pre-release and build metadata in the literal are parsed then discarded; only the core is retained
- Multiple valid target directives: highest valid core (post-validation) wins

### 4.7 Directive Coalescing

Duplicate relative changes of the same type within the scan range coalesce to a single increment.

Example: Two `version: minor` directives result in one minor increment, not two.

### 4.8 Precedence

From highest to lowest:

1. Ignore directive (`version: ignore`) — commit excluded entirely
2. Valid target directive (after validation)
3. Absolute component sets
4. Relative changes (via `version:` or standalone shorthands)
5. Default fallback behaviour

### 4.9 Component Reset Semantics

- **Major** change or set: resets Minor and Patch to 0
- **Minor** change or set: resets Patch to 0
- **Patch** change or set: no reset (no lower components)

### 4.10 Invalid Directive Content

The following are silently ignored:

- Negative numbers or overflowed integers in absolute setters (e.g., `version: major: -1`)
- Malformed target directives (unparseable SemVer core, e.g., `target: 1.2` or `target: a.b.c`)
- Unrecognised words after `version:` (e.g., `version: majorx`)
- Standalone shorthands without text after the colon (e.g., bare `fix:` or `breaking:`)

---

## 5. Target Directive Validation

A target directive specifying core `(M, m, p)` is **ignored** if any of these conditions hold:

### Rule A: Regression vs Reachable Final

If a reachable final tag exists with core `Tf` and target core ≤ `Tf` → **ignored**

### Rule B: Regression vs Reachable Pre-release Core

If the highest reachable tag is a pre-release with core `Tpr`:

- Target core < `Tpr` → **ignored**
- Target core = `Tpr` → **accepted** (equality permitted against pre-release)

### Rule C: No Reachable Base (Repository Context)

When no tags are reachable from the basis commit, validation uses repository-wide context:

If any final tag exists anywhere with core `Rf`:

- Target core ≤ `Rf` → **ignored**

Otherwise, if the highest repository tag is a pre-release with core `Rp`:

- Target core < `Rp` → **ignored**
- Target core ≥ `Rp` → **accepted**

### Rule D: Basis Commit Equals Final Tag

If the basis commit carries a final tag with core `Tf`:

- Target core ≤ `Tf` → **ignored**

### Rule E: Malformed Target

Any of these conditions → **ignored**:

- Partial core (e.g., `1.2`)
- Non-numeric components
- Negative values
- Values outside 32-bit signed integer bounds

### Rule F: Multiple Targets

After applying rules A–E, if multiple targets remain, the highest core by SemVer ordering wins; others are ignored.

### Equality Rule Summary

Equality is **never** permitted against a final release core. Equality is **only** permitted against a pre-release
core (reachable or repository-wide highest pre-release).

---

## 6. Version Derivation

### 6.1 Mode Selection

| Condition                                                            | Result                           |
|----------------------------------------------------------------------|----------------------------------|
| Basis commit has ≥1 valid version tag AND working directory is clean | **Concrete Version** (Mode 1)    |
| Otherwise                                                            | **Development Version** (Mode 2) |

Working directory is considered dirty if:

- Modified tracked files exist (index or worktree differences)
- Untracked files exist (excluding files ignored by standard Git excludes)

### 6.2 Concrete Version (Mode 1)

Return the highest valid version tag at the basis commit exactly as parsed (canonical representation). No pre-release or
build metadata is appended.

### 6.3 Development Version (Mode 2)

#### Step 1: Base Version Resolution

- If ≥1 reachable valid tag exists → Base Version = highest reachable tag
- Otherwise → No Base Version

#### Step 2: Directive Extraction

Scan commit messages in the defined range and collect directives.

#### Step 3: Target Core Determination

**A.** If any valid target survives validation → use highest target core; ignore all other directives

**B.** Otherwise, apply absolutes (highest wins per component) then relatives (coalesced), with reset semantics

**C.** If no directives apply:

| Condition                    | Target Core               |
|------------------------------|---------------------------|
| Base is pre-release          | Base core unchanged       |
| Base is final                | Base core with Patch + 1  |
| No base; repository has tags | `(highest.major + 1).0.0` |
| No tags anywhere             | `0.1.0`                   |

#### Step 4: Pre-release

Always set to `-SNAPSHOT`

#### Step 5: Build Metadata

Identifiers are assembled in strict order:

| Position | Identifier   | Format                                   | Condition                     |
|----------|--------------|------------------------------------------|-------------------------------|
| 1        | PR Number    | `pr<N>`                                  | If PR number supplied         |
| 2        | Branch       | `branch<normalised>` or `branchdetached` | Always                        |
| 3        | Commit Count | `commits<N>`                             | Always                        |
| 4        | SHA          | `sha<hex>`                               | Always                        |
| 5        | Dirty        | `dirty`                                  | If working directory is dirty |

**Commit Count**: First-parent non-merge commits strictly after Base tag commit up to basis commit. If no base, count
from root. Clamped to `Int.MaxValue`.

**SHA Length**: Configurable, 7 ≤ L ≤ 40, lowercase hex.

#### Step 6: Assembly

```
<TargetCore>-SNAPSHOT+<metadata-identifiers-joined-by-dots>
```

---

## 7. Branch Name Normalisation

1. Convert to lowercase ASCII
2. Replace each character not in `[0-9a-z-]` with `-`
3. Collapse consecutive `-` sequences to single `-`
4. Trim leading and trailing `-`
5. If empty → `detached`

Examples:

- `Feature/ABC_123!!` → `feature-abc-123`
- `main` → `main`
- `///` → `detached`

---

## 8. Validity Constraints

| Component                 | Constraint                                       |
|---------------------------|--------------------------------------------------|
| Major, Minor, Patch       | Non-negative integer (≥ 0)                       |
| Pre-release number        | Positive integer (≥ 1) for versioned classifiers |
| Pre-release classifier    | Must resolve via alias mapping                   |
| Build metadata identifier | Non-empty, characters from `[0-9A-Za-z-]` only   |
| SHA length                | 7 ≤ length ≤ 40                                  |
| Target directive core     | Must include all three numeric components        |

---

## 9. Edge Cases

| Scenario                                                 | Behaviour                                             |
|----------------------------------------------------------|-------------------------------------------------------|
| Multiple version tags on one commit                      | Final outranks pre-release; otherwise highest version |
| Dirty working directory at tagged commit                 | Development version with `dirty` identifier           |
| Detached HEAD                                            | Normal processing; `branchdetached` in metadata       |
| No reachable tags; repository has tags elsewhere         | Default bump to `(highest.major + 1).0.0`             |
| No tags anywhere                                         | Target = `0.1.0-SNAPSHOT+...`                         |
| Reachable highest is pre-release; target equals its core | Accepted                                              |
| Reachable highest is final; target equals its core       | Ignored (regression)                                  |
| Multiple targets                                         | Highest valid core after filtering                    |
| Invalid target forms                                     | Ignored silently                                      |
| Merge commits                                            | Keyword scan traverses all reachable paths            |
| Commit count for metadata                                | First-parent only, excludes merges                    |
| Shallow clone lacking base history                       | Treated as no base; defaults apply                    |
| Large commit count overflow                              | Clamped to 2147483647                                 |
| Branch override provided                                 | Overrides branch detection                            |

---

## 10. Keyword Grammar

```bnf
version-directive    ::= "version" ":" bump-token
                       | "version" ":" bump-token ":" integer
                       | "version" ":" patch-token ":" integer
                       | "version" ":" "ignore"
                       | "version" ":" "ignore" ":" sha-list
                       | "version" ":" "ignore" ":" sha-range
                       | "version" ":" "ignore-merged"

standalone-shorthand ::= bump-token ":" non-empty-text

bump-token           ::= major-token | minor-token

major-token          ::= "major" | "breaking"
minor-token          ::= "minor" | "feature" | "feat"
patch-token          ::= "patch" | "fix"

target-directive     ::= "target" ":" semver-literal

sha-list             ::= sha-prefix ("," sha-prefix)*
sha-range            ::= sha-prefix ".." sha-prefix
sha-prefix           ::= <7-40 hexadecimal characters>

integer              ::= <decimal digits without sign>
non-empty-text       ::= <any non-whitespace content>
semver-literal       ::= <valid SemVer string; only core retained>
```

**Note:** `patch-token` only applies to the absolute set form. Relative patch increments and standalone
`fix:`/`patch:` shorthands are recognised but have no effect (patch increment is the default).

---

## 11. Examples

### 11.1 Concrete Version

| State                            | Result       |
|----------------------------------|--------------|
| Basis tagged `v2.3.1`, clean     | `2.3.1`      |
| Basis tagged `2.3.1-rc.1`, clean | `2.3.1-rc.1` |

### 11.2 Snapshot After Final Release

- Base: `1.4.5` (final)
- No keywords
- Result: `1.4.6-SNAPSHOT+branchmain.commits0.sha1234567`

### 11.3 Target Accepted

- Reachable final: `2.2.5`
- Commit: `target: 2.2.6`
- Result: `2.2.6-SNAPSHOT+...`

### 11.4 Target Ignored (Regression)

- Reachable final: `2.2.5`
- Commit: `target: 2.2.4`
- Result: `2.2.6-SNAPSHOT+...` (patch default; target ignored)

### 11.5 Equality to Pre-release Core Accepted

- Reachable highest: `3.1.0-rc.2`
- Commit: `target: 3.1.0`
- Result: `3.1.0-SNAPSHOT+...`

### 11.6 Equality to Final Core Rejected

- Reachable final: `1.4.5`
- Commit: `target: 1.4.5`
- Result: `1.4.6-SNAPSHOT+...` (target ignored)

### 11.7 No Reachable Base; Repo Highest Final

- Repo highest final: `4.3.0`
- No base tags reachable
- No valid target
- Result: `5.0.0-SNAPSHOT+...`

### 11.8 No Reachable Base; Repo Highest Pre-release

- Repo tags: only `2.0.0-rc.1`
- Commit: `target: 2.0.0`
- Result: `2.0.0-SNAPSHOT+...` (equality accepted)

### 11.9 Multiple Targets

- Commits: `target: 1.5.0`, `target: 1.6.0`
- Result: `1.6.0-SNAPSHOT+...` (highest wins)

### 11.10 Standalone Shorthand

- Commit: `breaking: Remove legacy API`
- Effect: Major increment

### 11.11 Absolute Overrides Relative

- Base: `1.2.3`
- Keywords: `version: minor: 9`, `version: minor`
- Result core: `1.9.0` (absolute wins)

### 11.12 Duplicate Relative Coalescing

- Base: `1.2.3`
- Keywords: `version: minor`, `feature: Add helper`
- Result core: `1.3.0` (single increment)

### 11.13 Pre-release Base Default

- Base: `3.0.0-rc.3`
- No directives
- Result: `3.0.0-SNAPSHOT+...` (core unchanged)

### 11.14 Invalid Target Ignored

- Reachable final: `2.2.5`
- Keyword: `target: 2.2` (partial)
- Result: `2.2.6-SNAPSHOT+...`

### 11.15 Branch Normalisation

- Raw: `Feature/ABC_123!!`
- Metadata: `+branchfeature-abc-123.commits7.sha1234567`

### 11.16 Ignore Directive

- Base: `1.2.3`
- Commits: `version: ignore` (docs), `fix: Edge case`
- Result: `1.2.4-SNAPSHOT+...` (ignored commit excluded; `fix:` has no effect as patch is default)

### 11.17 Synonym Equivalence

- `version: breaking` ≡ `version: major`
- `version: feat: 5` ≡ `version: feature: 5` ≡ `version: minor: 5`
- `feat: Add X` ≡ `feature: Add X` (both → minor)
- `fix: Y` ≡ `patch: Y` (both → no effect; patch is default)

### 11.18 Ignore Specific Commits

- Base: `1.2.3`
- Commit A (sha: `abc1234`): `breaking: API change`
- Commit B: `version: ignore: abc1234`
- Result: `1.2.4-SNAPSHOT+...` (Commit A excluded; default patch applies)

### 11.19 Ignore Multiple Commits

- Base: `1.2.3`
- Commit A (sha: `abc1234`): `version: major`
- Commit B (sha: `def5678`): `version: minor`
- Commit C: `version: ignore: abc1234, def5678`
- Result: `1.2.4-SNAPSHOT+...` (both A and B excluded)

### 11.20 Ignore Commit Range

- Base: `1.2.3`
- Commits in order: A (`abc1234`), B (`bcd2345`), C (`cde3456`)
- Commit A: `version: major`
- Commit C: `version: minor`
- Merge commit: `version: ignore: abc1234..cde3456`
- Result: `1.2.4-SNAPSHOT+...` (all three excluded)

### 11.21 Ignore Merged Commits

- Base: `1.2.3`
- Feature branch commits: `version: major`, `version: minor`, `version: patch: 5`
- Merge commit: `version: ignore-merged` + `feature: New consolidated feature`
- Result: `1.3.0-SNAPSHOT+...` (merged commits excluded; merge commit's minor applies)

---

## 12. Invalid Input Catalogue

| Input                                              | Reason                               |
|----------------------------------------------------|--------------------------------------|
| `target: 1.2`                                      | Partial core                         |
| `version: major: -1`                               | Negative absolute                    |
| `target: a.b.c`                                    | Non-numeric core                     |
| `target: 1.0.0` when final `1.0.0` is reachable    | Equality vs final                    |
| `version: majorx`                                  | Not boundary-aligned                 |
| `retarget: 2.0.0`                                  | Not a keyword                        |
| `target: 3.0.0` when repo highest final is `4.3.0` | Regression                           |
| `breaking:`                                        | Empty standalone shorthand           |
| `change: minor`                                    | Unrecognised keyword                 |
| `version: ignore: abc`                             | SHA prefix too short (< 7 chars)     |
| `version: ignore: xyz1234`                         | Non-hexadecimal characters           |
| `version: ignore: abc1234..`                       | Incomplete range                     |
| `version: ignore-merged` on non-merge commit       | Silently ignored (no merged commits) |

---

## 13. Canonical Output Forms

| Mode                | Format                          | Example                                               |
|---------------------|---------------------------------|-------------------------------------------------------|
| Concrete            | `M.m.p` or `M.m.p-prerelease`   | `2.4.1`, `2.4.1-rc.1`                                 |
| Development         | `M.m.p-SNAPSHOT+metadata`       | `2.4.2-SNAPSHOT+branchmain.commits5.sha1234567`       |
| Development (dirty) | `M.m.p-SNAPSHOT+metadata.dirty` | `2.4.2-SNAPSHOT+branchmain.commits5.sha1234567.dirty` |

---

## 14. Summary Decision Table

| Situation                          | Target Determination                                                  |
|------------------------------------|-----------------------------------------------------------------------|
| Valid target(s) survive rules A–F  | Highest target core                                                   |
| No valid target; absolutes present | Apply absolutes (highest per component) with resets                   |
| No absolutes; relatives present    | Apply highest-precedence relative (Major > Minor > Patch) with resets |
| None; base pre-release             | Core unchanged                                                        |
| None; base final                   | Patch + 1                                                             |
| None; no base; repo has tags       | `(Highest.major + 1).0.0`                                             |
| None; no tags at all               | `0.1.0`                                                               |

---

## 15. Determinism

Given fixed repository state and configuration inputs, the derived version is deterministic and idempotent. Repeated
executions produce identical results until repository state or inputs change.

---

## 16. Compliance Checklist

| Rule                                                            | Enforced |
|-----------------------------------------------------------------|----------|
| Optional leading `v` tag prefix                                 | Yes      |
| Final outranks pre-release (same core)                          | Yes      |
| Target precedence over all else                                 | Yes      |
| Absolute > Relative                                             | Yes      |
| Duplicate relatives coalesce                                    | Yes      |
| Component resets (Major resets Minor+Patch; Minor resets Patch) | Yes      |
| Equality allowed only vs pre-release core                       | Yes      |
| Commit count excludes merges (first-parent only)                | Yes      |
| Development versions always use `-SNAPSHOT` pre-release         | Yes      |
| Build metadata ordering                                         | Yes      |
| Branch normalisation rules                                      | Yes      |
| Ignoring invalid tags/keywords                                  | Yes      |

---

## 17. Version String Rendering

```
<major>.<minor>.<patch>[-<pre-release>][+<metadata>]
```

Where:

- `<major>.<minor>.<patch>` — required numeric components
- `-<pre-release>` — optional; always `SNAPSHOT` for development versions
- `+<metadata>` — optional; dot-separated identifiers (development versions only)

---

## 18. Out of Scope

The following are explicitly not handled by this specification:

- Creating or mutating Git tags
- Publishing or uploading artefacts
- Multi-project or path-scoped tagging conventions
- Extended pre-release classification beyond defined aliases
- Precedence influence from build metadata (never applied per SemVer 2.0.0)
