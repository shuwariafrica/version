---
title: How It Works
---

## How It Works

The version resolution engine follows a deterministic algorithm in one of two modes:

| Condition                                       | Result                  |
|-------------------------------------------------|-------------------------|
| HEAD has valid annotated tag AND clean worktree | **Concrete Version**    |
| Otherwise                                       | **Development Version** |

"Dirty" includes modified tracked files and untracked files (excluding ignored).

### Concrete Version

At a clean tagged commit, the engine returns the exact tag:

```
Tag: v2.3.1
Result: 2.3.1
```

### Development Version

Between releases, the engine computes a target version:

#### Priority Order

1. **Valid target directive** — explicit `target: X.Y.Z` in commits
2. **Absolute sets** — `version: major: 3` (highest per component)
3. **Relative changes** — `version: major` or `breaking:` (coalesced)
4. **Default fallback**:
    - Base is pre-release → core unchanged
    - Base is final → patch + 1
    - No base, repo has tags → highest major + 1
    - No tags anywhere → `0.1.0`

---

### Metadata Identifiers

The resolution engine attaches build metadata to development versions in the following order:

| Order | Identifier     | Condition                    |
|-------|----------------|------------------------------|
| 1     | `pr<N>`        | PR number provided           |
| 2     | `branch<name>` | Always (or `branchdetached`) |
| 3     | `commits<N>`   | Always                       |
| 4     | `sha<hex>`     | Always (7-40 chars)          |
| 5     | `dirty`        | Worktree dirty               |

Whether metadata appears in the rendered string depends on the [Show instance](../core/operations.md#rendering).

---

### Tag Recognition

#### Valid Tags

A tag is recognised if:

- It is an **annotated tag** (lightweight ignored)
- Name is valid SemVer (optional `v` prefix)
- Pre-release classifiers map to known aliases
- All components are valid integers

#### Multiple Tags

If multiple valid tags exist on one commit:

- Final release outranks pre-release of same core
- Otherwise, highest SemVer wins

---

### Commit Scanning

#### Scope

| Condition       | Scan Range                      |
|-----------------|---------------------------------|
| Base tag exists | Commits after base up to HEAD   |
| No base tag     | All commits reachable from HEAD |

#### Traversal

- **All commits are scanned**, including merge commits themselves
- Keywords scanned from all reachable paths (traverses entire merge graph)
- Commit count for metadata uses first-parent only, excluding merges

#### Ignore Directives

Commits can be excluded from version calculation:

| Directive                    | Effect                                            |
|------------------------------|---------------------------------------------------|
| `version: ignore`            | Excludes the commit containing this directive     |
| `version: ignore: <sha>`     | Excludes specific commits by SHA prefix           |
| `version: ignore: <a>..<b>`  | Excludes commits in the range (inclusive)         |
| `version: ignore-merged`     | Excludes all commits from the merged branch       |

**Use case**: When merging a PR, use `version: ignore-merged` in the merge commit to discard all incoming version directives and specify a consolidated directive instead.

---

### Examples

| Scenario               | Base            | Result               |
|------------------------|-----------------|----------------------|
| Tagged `v2.3.1`, clean | —               | `2.3.1`              |
| No keywords            | `1.4.5` (final) | `1.4.6-SNAPSHOT+...` |
| `target: 2.0.0`        | `1.9.0`         | `2.0.0-SNAPSHOT+...` |
| `breaking: API change` | `1.0.0`         | `2.0.0-SNAPSHOT+...` |
| No keywords            | `3.0.0-rc.3`    | `3.0.0-SNAPSHOT+...` |
| No tags in repo        | —               | `0.1.0-SNAPSHOT+...` |
