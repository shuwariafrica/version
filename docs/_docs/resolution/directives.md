---
title: Commit Directives
---

## Commit Directives

Control version derivation through commit message keywords.

### Version Directive

The `version:` keyword supports three forms:

#### Relative Increment

Bump by one:

```
version: major
version: minor
version: patch
```

#### Absolute Set

Set to specific value:

```
version: major: 3
version: minor: 5
version: patch: 2
```

#### Ignore

Exclude commits from version calculation:

```
version: ignore                           # Exclude containing commit
version: ignore: <sha>                    # Exclude specific commit
version: ignore: <sha>, <sha>             # Exclude multiple commits
version: ignore: <sha>..<sha>             # Exclude range (inclusive)
version: ignore-merged                    # Exclude all merged branch commits
```

**SHA prefixes**: Must be at least 7 hexadecimal characters. Invalid SHA references (too short, non-hex characters, incomplete ranges) are silently ignored.

**Use cases**:

- `version: ignore` — Documentation-only or tooling commits
- `version: ignore: abc1234` — Exclude a specific commit by SHA prefix
- `version: ignore-merged` — When merging a PR, ignore all incoming commits and specify your own directive

---

### Synonyms

| Component | Accepted Keywords          |
|-----------|----------------------------|
| Major     | `major`, `breaking`        |
| Minor     | `minor`, `feature`, `feat` |
| Patch     | `patch`, `fix`             |

All forms are equivalent:

```
version: major      ≡  version: breaking
version: minor      ≡  version: feature  ≡  version: feat
version: patch      ≡  version: fix
```

### Standalone Shorthands

Use bump tokens as commit prefixes:

```
breaking: Remove deprecated API    → Major increment
feature: Add caching support       → Minor increment
feat: Add logging                  → Minor increment
fix: Handle edge case              → Patch increment
```

**Requirement**: Non-empty text after the colon.

**Invalid**: `breaking:`, `fix:` (no text)

### Target Directive

Set the target version explicitly:

```
target: 2.0.0
```

Subject to [validation rules](validation.md).

---

### Parsing Rules

#### Case Insensitivity

```
version: MAJOR      ✓
VERSION: minor      ✓
Target: 2.0.0       ✓
```

#### Whitespace Tolerance

```
version:major       ✓
version : major     ✓
version:  major     ✓
```

#### Boundary Alignment

Keywords must be word-boundary aligned:

```
reversion: 1.0.0    ✗ (substring of "reversion")
retarget: 2.0.0     ✗ (substring of "retarget")
```

### Precedence

1. **Ignore** — commit excluded entirely
2. **Valid target** — highest surviving target wins
3. **Absolute sets** — highest value per component
4. **Relative changes** — coalesced, highest-precedence wins
5. **Default** — based on base version

### Coalescing

Duplicate relative changes count as one:

```
Commit 1: version: minor
Commit 2: feature: Add helper

Result: Single minor increment
```

### Component Reset

When higher-precedence components change:

| Change | Resets               |
|--------|----------------------|
| Major  | Minor and Patch to 0 |
| Minor  | Patch to 0           |
| Patch  | Nothing              |

---

### Examples

| Commits                          | Base    | Result Core       |
|----------------------------------|---------|-------------------|
| `version: major`                 | `1.2.3` | `2.0.0`           |
| `version: minor: 9`              | `1.2.3` | `1.9.0`           |
| `version: minor`, `feat: X`      | `1.2.3` | `1.3.0`           |
| `target: 2.5.0`                  | `1.2.3` | `2.5.0`           |
| `version: ignore` on all commits | `1.2.3` | `1.2.4` (default) |

#### Ignore Directive Examples

##### Ignore Specific Commits

When a commit's version impact should be excluded:

```
# Commit A (sha: abc1234): version: major
# Commit B: version: ignore: abc1234

Result: Commit A excluded, default patch applies
```

##### Ignore Merged Branch Commits

When merging a feature branch, consolidate version control in the merge commit:

```
# Feature branch commits:
#   - version: major
#   - version: minor

# Merge commit: version: ignore-merged
#               feature: New consolidated feature

Result: Feature branch commits excluded, merge commit's minor applies
```

##### Ignore Commit Range

Exclude a range of commits from version calculation:

```
# Commits A (abc1234) through C (cde5678)
# Merge commit: version: ignore: abc1234..cde5678

Result: All commits in range excluded
```
