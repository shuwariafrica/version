# Version Resolution Technical Specification

This specification defines the deterministic rules used by the `version` libraries and the `version-cli` application to
derive a Semantic Version (SemVer 2.0.0) for a Git repository at a chosen commit (“basis commit”). It is intended for
consumers (library users, build engineers, release engineers) who wish to understand precisely how a reported version is
computed. It deliberately excludes implementation guidance.

---

## 1. Scope

The system reports exactly one of:

1. A **Concrete Version** (an existing, valid SemVer tag at the basis commit, with a clean working directory).
2. A **Development Version** (a snapshot of an upcoming release) when the concrete release conditions are not satisfied.

All semantics rely solely on:

- Git commit graph
- Git tags
- Commit messages
- Configuration inputs (optional PR number, branch override, SHA abbreviation length)

The algorithm is **purely functional** with respect to repository state: no mutation of the repository, and no
heuristics beyond the rules below.

---

## 2. Terminology

| Term                    | Definition                                                                         |
|-------------------------|------------------------------------------------------------------------------------|
| **Basis Commit**        | The commit under evaluation (default `HEAD`).                                      |
| **Reachable Tag**       | A valid version tag whose commit is an ancestor of (or equal to) the basis commit. |
| **Base Version**        | The highest reachable valid SemVer tag (if any).                                   |
| **Target Version**      | The core `MAJOR.MINOR.PATCH` triple produced by analysis of reachable history.     |
| **Pre-release State**   | Always `-snapshot` for development versions; absent for concrete versions.         |
| **Full Version**        | `MAJOR.MINOR.PATCH[-PRERELEASE][+BUILDMETADATA]`.                                  |
| **Concrete Version**    | The exact tag at the basis commit if the working directory is clean.               |
| **Development Version** | A snapshot form with `-snapshot` pre-release plus build metadata.                  |

---

## 3. Tag Recognition

### 3.1 Valid Version Tags

A Git **annotated tag** is considered a **valid version tag** if:

- It is an **annotated tag** (not a lightweight tag). Lightweight tags are silently ignored.
- Its name is a SemVer 2.0.0 string (with optional leading `v` or `V`).
- All components satisfy the domain constraints (non-negative integers; valid pre-release classifier mapping; valid
  build metadata identifiers).
- Pre-release identifiers map to recognised classifiers via the configured classifier alias set (e.g., `rc`, `alpha`,
  `snapshot`, etc.).

**Rationale**: Annotated tags carry metadata (tagger, date, message) and are the recommended practice for releases.
Lightweight tags are intended for temporary or private use and lack the auditability required for version management.

### 3.2 Pre-release Classifier Mapping

Recognised canonical classifiers (case-insensitive via alias forms), in precedence order (lowest to highest):

- `dev` (Development)
- `milestone` / `m`
- `alpha` / `a`
- `beta` / `b`
- `rc` / `cr` (Release Candidate)
- `snapshot`

Versioned classifiers (`dev`, `milestone`, `alpha`, `beta`, `rc`) must carry a positive numeric component (e.g.,
`rc.1`);
`snapshot` must not carry a number.

### 3.3 Multiple Tags on a Single Commit

If multiple valid version tags exist on one commit, the **highest SemVer** takes precedence. A final release (no
pre-release) always outranks a pre-release of the same core.

### 3.4 Ignored Tags

Tags that fail SemVer parsing, classifier validation, or are **lightweight tags** (not annotated) are **silently
ignored**.

### 3.5 Repository-Wide Highest Tag

When no reachable tag exists from the basis commit, the **repository-wide highest** valid tag (across all branches) is
used for:

- Default “no-base” derivation (Section 6.2)
- Target directive validation (Rule C)

---

## 4. Commit Message Keywords

### 4.1 Scope of Keyword Scanning

Only commits **strictly after** the Base Version’s tagged commit up to and including the basis commit are scanned.  
If **no Base Version exists**, all commits reachable from the basis commit are scanned (graph traversal includes
merges).

### 4.2 Case, Spacing, and Boundaries

- Case-insensitive matching.
- Optional whitespace around colons accepted (`version:minor`, `version : minor`).
- Tokens must be boundary-aligned: substrings inside larger words (e.g., `reversion`, `retarget`, `versionX`) are
  ignored.

### 4.3 Accepted Forms

#### Bump Tokens

The following tokens represent version component semantics and may be used interchangeably:

| Component | Tokens (Synonyms)          |
|-----------|----------------------------|
| Major     | `major`, `breaking`        |
| Minor     | `minor`, `feature`, `feat` |
| Patch     | `patch`, `fix`             |

#### Version Directive

The `version:` keyword controls version computation. It supports three forms:

**Relative Increment** (bump by one):

- `version: <bump-token>`

Examples: `version: major`, `version: breaking`, `version: minor`, `version: feature`, `version: patch`, `version: fix`

**Absolute Set** (set to specific value):

- `version: <bump-token>: <N>`

Examples: `version: major: 3`, `version: breaking: 3`, `version: minor: 5`, `version: feature: 5`

Each `<N>` must be a non-negative integer within standard 32-bit signed integer bounds.
If multiple absolutes target the **same** component, the **highest value wins**.

**Ignore Directive**:

- `version: ignore`

Excludes the commit from version calculation entirely. Useful for documentation, refactoring, or CI configuration
changes that should not affect versioning.

#### Standalone Shorthand

Bump tokens may appear as commit message prefixes when followed by a colon and **non-empty text**:

- `<bump-token>: <text>`

Examples:

- `breaking: Remove deprecated API` → Major increment
- `major: Introduce new module system` → Major increment
- `feat: Add caching support` → Minor increment (Conventional Commits style)
- `feature: Add new endpoint` → Minor increment
- `minor: Extend configuration options` → Minor increment
- `fix: Handle null pointer edge case` → Patch increment
- `patch: Correct typo in error message` → Patch increment

**Invalid** (no text after colon): `breaking:`, `fix:`, `major:`

These forms integrate naturally with conventional commit message styles.

#### Coalescing

Duplicate occurrences of the **same relative change type** within the scanned range **coalesce** to a single increment.

#### Target Directive

Format:

```
target: <SEMVER>
```

Rules:

- Optional leading `v` / `V`.
- Pre-release and build metadata in the literal are parsed then discarded for Target core formation.
- Multiple valid target directives: highest valid target core (post-validation) wins.
- Validation rules (Section 5) may discard target directives.

### 4.4 Precedence

1. Ignore directive (`version: ignore`) — commit excluded from processing
2. Valid Target (after validation)
3. Absolute component sets
4. Relative changes (via `version:` or standalone shorthand)
5. Default fallback behaviour (Section 6)

### 4.5 Component Reset Semantics

- Applying a **major** change or set resets minor and patch to 0.
- Applying a **minor** change or set resets patch to 0.
- Patch does not reset lower components (none exist).

### 4.6 Invalid Keyword Content

- Negative numbers or overflowed integers in absolute setters are ignored.
- Malformed target directives (unparseable SemVer core) are ignored.
- Unrecognised words after `version:` are ignored.
- Standalone shorthands without text after the colon (e.g., bare `fix:`) are ignored.

---

## 5. Target Directive Validation (Ignore Rules)

Given candidate target core `Tcore` as `(M, m, p)`.

Let:

- `HReach` = highest reachable tag (if any).
- `Reachable Final` = highest reachable final tag (no pre-release).
- `Repo Highest Final` = highest final tag anywhere in the repository.
- `Repo Highest` = highest tag anywhere (could be pre-release).
- “Equal pre-release core” means `Tcore` equals the core of the highest pre-release in the relevant comparison set.

A target directive is **ignored** if any of the following hold:

**A. Regressive vs Reachable Final**  
If a reachable final tag `Tf` exists and `Tcore <= Core(Tf)` → ignore.

**B. Regressive vs Reachable Pre-release Core**  
If `HReach` is a pre-release tag `Tpr` and `Tcore < Core(Tpr)` → ignore.  
If `Tcore == Core(Tpr)` → allowed (equality permitted only against a pre-release core).

**C. No Reachable Base (Repository Context)**  
If there exists a final tag anywhere with core `Rf` and `Tcore <= Rf` → ignore.  
Else if highest repo tag is a pre-release with core `Rp` and no final tag ≥ `Rp` exists:

- If `Tcore < Rp` → ignore.
- If `Tcore >= Rp` → accept (equality allowed).

**D. Basis Commit Equals a Final Tag (but Not Mode 1)**  
If basis commit carries a final tag `Tf` and `Tcore <= Core(Tf)` → ignore.

**E. Malformed / Invalid**  
Partial cores (e.g., `1.2`), negative values, or components outside integer bounds → ignore.

**F. Multiple Targets**  
After applying A–E, if multiple remain, choose the highest by SemVer ordering on the core; others are ignored.

**Equality Rule Summary:**  
Equality is **never** permitted against a final tag core; only permitted against a pre-release core (reachable or
repository-wide “highest pre-release” context).

---

## 6. Version Derivation Algorithm

### 6.1 Mode Selection

| Condition                                                            | Result                           |
|----------------------------------------------------------------------|----------------------------------|
| Basis commit has ≥1 valid version tag AND working directory is clean | **Mode 1** (Concrete Version)    |
| Otherwise                                                            | **Mode 2** (Development Version) |

“Working directory dirty” includes:

- Modified tracked files (index or worktree differences)
- Presence of untracked files (excluding ignored files by standard Git excludes)

### 6.2 Mode 2 – Development Version Steps

1. **Base Version Resolution**
    - If ≥1 reachable valid tag exists: Base Version = highest reachable tag.
    - Else: No Base Version.

2. **Keyword Extraction**
    - Scan commit messages in the defined range (Section 4.1).
    - Collect and classify directives.

3. **Target Core Determination**  
   A. If **any valid target** survives validation: use the highest target core. Ignore all other directives.  
   B. Else apply absolutes (highest wins per component) then relatives (coalesced).  
   C. If no directives apply:
    - If Base Version exists and is a pre-release ⇒ Target = Base core.
    - If Base Version exists and is final ⇒ Target = Base core with Patch + 1.
    - If **no Base Version** and repository has any tags ⇒ Target = `(highest.major + 1).0.0`.
    - If **no tags anywhere** ⇒ Target = `0.1.0`.

4. **Pre-release State**  
   Always set to `-snapshot`.

5. **Build Metadata Construction (Ordered Identifiers)**
    1. `pr<number>` (if PR number supplied; `<number>` decimal, non-negative)
    2. `branch<normalised-branch-name>` OR `branchdetached`
    3. `commits<count>`
        - Count of non-merge commits (first-parent, excluding merges) strictly after Base tag commit up to basis commit.
        - If no Base Version: first-parent path back to the root.
        - Clamped to `Int.MaxValue`.
    4. `sha<hex>` truncated to configured length L (7 ≤ L ≤ 40) of basis commit full SHA (lowercase).
    5. `dirty` present iff working directory is dirty.
    6. (Reserved for future extensions – any additional identifiers must appear **after** the above if introduced.)

6. **Full Version Assembly**  
   `TargetCore` + `-snapshot` + `+` joined metadata identifiers (if any).

### 6.3 Mode 1 – Concrete Version

Return exactly the highest valid version tag at the basis commit (strip optional leading `v` for parsing only; keep its
parsed canonical representation). Do **not** append pre-release or build metadata.

### 6.4 Deterministic Priority

All operations are pure transformations of:

- Basis commit identity
- Commit graph ancestry
- Tag set
- Commit messages
- Config inputs (PR number, SHA length, branch override)
- Working directory cleanliness (for Mode 1 vs Mode 2 decision and `dirty` metadata)

---

## 7. Build Metadata Rules

| Identifier        | Format                           | Inclusion Conditions                                    |
|-------------------|----------------------------------|---------------------------------------------------------|
| `pr<number>`      | `pr` + decimal digits            | When PR number provided                                 |
| `branch<name>`    | `branch` + normalised name       | Always in snapshots (use `branchdetached` if no branch) |
| `commits<number>` | `commits` + non-negative decimal | Always in snapshots                                     |
| `sha<hex>`        | `sha` + hex (length L)           | Always in snapshots                                     |
| `dirty`           | literal `dirty`                  | Only if worktree is dirty                               |

### 7.1 Branch Name Normalisation

1. Lowercase ASCII
2. Replace each char not `[0-9a-z-]` with `-`
3. Collapse consecutive `-` sequences to one
4. Trim leading/trailing `-`
5. If empty → `detached`

### 7.2 Metadata Ordering

Canonical and **strict**:  
`pr` → `branch` → `commits` → `sha` → `dirty`.

Deviation in ordering is invalid.

### 7.3 Precedence Neutrality

Build metadata **never** affects SemVer precedence.

---

## 8. Edge Cases

| Scenario                                                        | Behaviour                                                                     |
|-----------------------------------------------------------------|-------------------------------------------------------------------------------|
| Multiple version tags on a commit                               | Final outranks pre-release; otherwise highest version ordering.               |
| Dirty working directory at a tagged commit                      | Mode 2 (snapshot) with `dirty` identifier.                                    |
| Detached HEAD                                                   | Treated normally; `branchdetached` emitted.                                   |
| No reachable tags; repository has tags elsewhere                | Use repository-wide highest for default bump to `(highest.major + 1).0.0`.    |
| No tags anywhere                                                | Target = `0.1.0-snapshot+...`.                                                |
| Reachable highest is pre-release; target equals its core        | Accepted.                                                                     |
| Reachable highest is final; target equals its core              | Ignored (regression/equality violation).                                      |
| Multiple targets                                                | Highest valid target core after filtering.                                    |
| Invalid target forms (`target: 1.2`, `target: a.b.c`, negative) | Ignored.                                                                      |
| Unparseable tags / malformed pre-release aliases                | Ignored silently.                                                             |
| Merge commits                                                   | Keyword scan traverses *all* reachable paths (full ancestry).                 |
| Commit count for metadata                                       | First-parent, no-merges, exclusive of Base tag commit.                        |
| Shallow clone lacking Base history                              | Treated as no reachable base if the tag boundary not present; defaults apply. |
| Large commit count overflow                                     | Clamped to `2147483647` (Int.MaxValue).                                       |
| Branch override provided                                        | Overrides branch detection for `branch<name>` identifier.                     |
| Pre-release only repository highest (no final ≥ core)           | Equality to that pre-release core for target allowed.                         |

---

## 9. Validity Constraints Summary

| Component                        | Constraint                                   |
|----------------------------------|----------------------------------------------|
| Major / Minor / Patch            | Non-negative integer (≥ 0)                   |
| Pre-release number (if required) | Positive integer (≥ 1)                       |
| Pre-release classifier mapping   | Must resolve via alias mapping               |
| Build metadata identifiers       | One or more; each `[0-9A-Za-z-]+`; non-empty |
| SHA length (snapshot mode)       | 7 ≤ length ≤ 40                              |
| Target directive core            | Must include all three numeric components    |

---

## 10. Keyword Grammar (Informal)

```
version-directive    ::= "version" ":" bump-token
                       | "version" ":" bump-token ":" integer
                       | "version" ":" "ignore"

standalone-shorthand ::= bump-token ":" non-empty-text

bump-token ::= major-token | minor-token | patch-token

major-token ::= "major" | "breaking"
minor-token ::= "minor" | "feature" | "feat"
patch-token ::= "patch" | "fix"

target-directive     ::= "target" ":" semver-literal
```

- `integer` is a canonical decimal without sign.
- `non-empty-text` is any non-whitespace content following the colon.
- `semver-literal` may contain pre-release or build metadata; only `MAJOR.MINOR.PATCH` core is retained for target
  evaluation.

---

## 11. Examples

### 11.1 Concrete Version

| State                                   | Result                                        |
|-----------------------------------------|-----------------------------------------------|
| Basis commit tagged `v2.3.1` and clean  | `2.3.1`                                       |
| Basis commit tagged `2.3.1-rc.1`, clean | `2.3.1-rc.1` (pre-release is part of the tag) |

### 11.2 Snapshot After Final Release (Patch Default)

- Base: `1.4.5` (final)
- No keywords
- Result: `1.4.6-snapshot+branchmain.commits0.sha<...>`

### 11.3 Target Accepted (Maintenance)

- Reachable final: `2.2.5`
- Commit message: `target: 2.2.6`
- Result: `2.2.6-snapshot+...`

### 11.4 Target Ignored (Regression)

- Reachable final: `2.2.5`
- Commit message: `target: 2.2.4` (ignored by Rule A)
- Result: `2.2.6-snapshot+...` (patch default)

### 11.5 Equal to Pre-release Core Accepted

- Reachable highest: `3.1.0-rc.2`
- Commit: `target: 3.1.0`
- Result: `3.1.0-snapshot+...`

### 11.6 Equal to Final Core Rejected

- Reachable final: `1.4.5`
- Commit: `target: 1.4.5`
- Result: `1.4.6-snapshot+...`

### 11.7 No Reachable Base; Repo Highest Final

- Repo highest final: `4.3.0`
- No base tags reachable
- No valid target
- Result: `5.0.0-snapshot+...`

### 11.8 No Reachable Base; Repo Highest Pre-release

- Repo tags only: `2.0.0-rc.1`
- Commit: `target: 2.0.0`
- Accepted (Rule C equality with pre-release core)
- Result: `2.0.0-snapshot+...`

### 11.9 Multiple Targets

- Commits: `target: 1.5.0`, `target: 1.6.0`
- Highest valid: `1.6.0`
- Result: `1.6.0-snapshot+...`

### 11.10 Standalone Shorthand

- Commit: `breaking: Remove legacy API`
- Effect: Major increment (same as `version: major`)

### 11.11 Absolute Overrides Relative

- Base: `1.2.3`
- Keywords: `version: minor: 9`, `version: minor`
- Result core: `1.9.0`

### 11.12 Duplicate Relative Coalescing

- Base: `1.2.3`
- Keywords: `version: minor`, `feature: Add helper`
- Result core: `1.3.0` (single increment)

### 11.13 Pre-release Default Collapse

- Base: `3.0.0-rc.3`
- No directives
- Result core: `3.0.0` (not bumped), final core + snapshot:
  `3.0.0-snapshot+...`

### 11.14 Invalid Target Ignored

- Reachable final: `2.2.5`
- Keyword: `target: 2.2` (partial core) → ignored
- Result: `2.2.6-snapshot+...`

### 11.15 Branch Normalisation

Raw branch: `Feature/ABC_123!!`  
Normalised identifier fragment: `feature-abc-123`

Example metadata:  
`+branchfeature-abc-123.commits7.shaabc1234def56`

### 11.16 Ignore Directive

- Base: `1.2.3`
- Commits: `version: ignore` (documentation update), `fix: Correct edge case`
- Result core: `1.2.4` (ignored commit excluded)

### 11.17 Synonym Equivalence

- `version: breaking` ≡ `version: major`
- `version: feature: 5` ≡ `version: minor: 5` ≡ `version: feat: 5`
- `feat: Add feature` ≡ `feature: Add feature` (both → Minor increment)
- `fix: Handle null` ≡ `patch: Handle null` (both → Patch increment)

---

## 12. Invalid Example Catalogue

| Input                                                          | Reason Ignored / Rejected         |
|----------------------------------------------------------------|-----------------------------------|
| `target: 1.2`                                                  | Partial core (Rule E)             |
| `version: major: -1`                                           | Negative absolute                 |
| `target: a.b.c`                                                | Non-numeric core                  |
| `target: 1.0.0` when reachable final is `1.0.0`                | Equality vs final (Rule A / D)    |
| `version: majorx`                                              | Not boundary aligned              |
| `retarget: 2.0.0`                                              | Not a keyword (boundary rule)     |
| `target: 3.0.0` when repo highest final is `4.3.0` and no base | Regression (Rule C)               |
| `fix:`                                                         | Standalone shorthand without text |
| `breaking:`                                                    | Standalone shorthand without text |
| `change: minor`                                                | `change:` keyword not recognised  |

---

## 13. Determinism & Idempotency

Given:

- A fixed repository state (tags, commits, working tree)
- Fixed configuration inputs

The derived version is deterministic and idempotent: repeated executions produce the identical version string until
repository state or inputs change.

---

## 14. Summary Decision Table (Abbreviated)

| Situation                          | Target Determination                                                  |
|------------------------------------|-----------------------------------------------------------------------|
| Valid target(s) survive rules A–F  | Highest target core                                                   |
| No valid target; absolutes present | Apply absolutes (highest per component) with resets                   |
| No absolutes; relatives present    | Apply highest-precedence relative (Major > Minor > Patch) with resets |
| None; base pre-release             | Core unchanged                                                        |
| None; base final                   | Patch + 1                                                             |
| None; no base; repo has tags       | (Highest.major + 1).0.0                                               |
| None; no tags at all               | 0.1.0                                                                 |

---

## 15. Out of Scope

- Creating or mutating Git tags
- Publishing or uploading artifacts
- Multi-project or path-scoped tagging conventions (e.g., tag prefixes)
- Extended pre-release classification beyond the defined alias set
- Precedence influence from build metadata (never applied)

---

## 16. Canonical Output Forms

| Mode                   | Example                                                    |
|------------------------|------------------------------------------------------------|
| Concrete               | `2.4.1`                                                    |
| Development (snapshot) | `2.4.2-snapshot+branchmain.commits5.sha1234567abcde`       |
| Development (dirty)    | `2.4.2-snapshot+branchmain.commits5.sha1234567abcde.dirty` |

---

## 17. Compliance Checklist

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
| Snapshot always uses `-snapshot` pre-release                    | Yes      |
| Build metadata ordering                                         | Yes      |
| Branch normalisation rules                                      | Yes      |
| Ignoring invalid tags/keywords                                  | Yes      |

---

## 18. Version String Rendering

`<major>.<minor>.<patch>`  
Optional `-<pre-release>` (always `snapshot` in Mode 2)  
Optional `+<id>(.<id>)*` build metadata (Mode 2 only)

---

**End of Specification**
