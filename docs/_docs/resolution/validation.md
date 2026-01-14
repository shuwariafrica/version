---
title: Validation Rules
---

# Validation Rules

Target directives are validated to prevent version regression.

## Target Directive Format

```
target: MAJOR.MINOR.PATCH
```

Optional `v` prefix and trailing pre-release/metadata are stripped.

## Validation Rules

A target directive is **ignored** if any of these conditions hold:

### A. Regression vs Reachable Final

If a reachable final tag exists with core `F`:

```
target <= F  →  ignored
```

Example:

```
Reachable final: 2.2.5
target: 2.2.4  →  ignored (regression)
target: 2.2.5  →  ignored (equality with final)
target: 2.2.6  →  accepted
```

### B. Regression vs Reachable Pre-release

If the highest reachable tag is a pre-release with core `P`:

```
target < P   →  ignored
target == P  →  accepted (equality allowed with pre-release)
```

Example:

```
Reachable highest: 3.1.0-rc.2
target: 3.0.0  →  ignored
target: 3.1.0  →  accepted
```

### C. No Reachable Base

When no tags are reachable from HEAD, validation uses repository-wide highest:

```
Repository highest final: 4.3.0
target: 4.3.0  →  ignored (regression)
target: 5.0.0  →  accepted
```

If only pre-releases exist:

```
Repository highest: 2.0.0-rc.1
target: 2.0.0  →  accepted (equality with pre-release)
```

### D. At a Final Tag

If HEAD carries a final tag with core `T`:

```
target <= T  →  ignored
```

### E. Malformed

Invalid targets are ignored:

- Partial cores: `target: 1.2`
- Non-numeric: `target: a.b.c`
- Negative values: `target: 1.-1.0`
- Overflow: `target: 999999999999.0.0`

### F. Multiple Targets

If multiple valid targets survive A–E:

```
Highest core wins; others ignored
```

## Equality Rule Summary

| Comparison       | Equality Allowed? |
|------------------|:-----------------:|
| vs Final release |         ❌         |
| vs Pre-release   |         ✅         |

## Examples

| Scenario                         | Target  | Result           |
|----------------------------------|---------|------------------|
| Final `2.2.5` reachable          | `2.2.4` | Ignored (A)      |
| Final `2.2.5` reachable          | `2.2.5` | Ignored (A)      |
| Final `2.2.5` reachable          | `2.2.6` | Accepted         |
| Pre-release `3.1.0-rc.2` highest | `3.1.0` | Accepted (B)     |
| Pre-release `3.1.0-rc.2` highest | `3.0.0` | Ignored (B)      |
| No base, repo final `4.3.0`      | `4.3.0` | Ignored (C)      |
| Partial core                     | `1.2`   | Ignored (E)      |
| Multiple: `1.5.0`, `1.6.0`       | —       | `1.6.0` wins (F) |
