---
title: Commit Directives
---

Commit messages steer version derivation. A directive is recognised anywhere in a message, case-insensitively, with
whitespace around the colon tolerated, and must be word-boundary aligned so it is never matched inside a larger word
(`reversion: 1.0.0` is not a directive).

## Bump

A bump advances a component of the version. The keywords - and which component each advances - belong to the active
scheme; for SemVer see [Keyword Mapping](semver.md#keyword-mapping). Three equivalent forms are accepted:

```
version: <keyword>     # explicit
<keyword>: <text>      # shorthand - requires non-empty text after the colon
[<keyword>]            # bracketed
```

So `version: breaking`, `breaking: drop the legacy API`, and `[breaking]` all request the same bump, and a directive may
sit within surrounding text (`[breaking] drop the legacy API`). `breaking:` with no text is ignored. A bracket is a
directive only when its content is a single keyword, boundary-aligned on both sides, so prose (`[skip ci]`) and embedded
brackets (`foo[breaking]bar`) are left alone; bracketing a directive such as `[version: major]` is counted once, never
twice.

## Absolute set

Set a component to a value instead of incrementing it (`version: minor: 9` yields `*.9.0`):

```
version: <keyword>: <N>     # N is a non-negative integer
```

## Target

Set the resulting version explicitly, subject to [validation](validation.md):

```
target: 2.0.0
```

## Ignore

Exclude commits from the scan:

```
version: ignore                     # this commit            (also [ignore])
version: ignore: <sha>[, <sha>...]  # commits by SHA prefix   (>= 7 hex characters)
version: ignore: <sha>..<sha>       # an inclusive range
version: ignore-merged              # the merged-in commits   (also [ignore-merged])
```

Invalid SHA references (too short, non-hex, incomplete range) are ignored. On a merge commit, `ignore-merged` drops the
commits the branch brought in, so one consolidating directive on the merge can speak for them.

## Resolution

When several directives apply across the scanned range:

1. **Ignore** removes a commit's directives entirely.
2. **Target** wins if present - the highest surviving target.
3. **Absolute sets** apply per component, highest value winning.
4. **Bumps** coalesce, so duplicates count once, and the highest-precedence component wins.
5. Otherwise the scheme's default advancement applies.

Advancing a component resets every lower-precedence component as the scheme defines - for SemVer, a minor bump zeroes the
patch.

## Examples

| Commits                            | Base    | Result core       |
|------------------------------------|---------|-------------------|
| `version: major`                   | `1.2.3` | `2.0.0`           |
| `version: minor: 9`                | `1.2.3` | `1.9.0`           |
| `[feat]`, then `version: minor`    | `1.2.3` | `1.3.0`           |
| `target: 2.5.0`                    | `1.2.3` | `2.5.0`           |
| `version: ignore` on every commit  | `1.2.3` | `1.2.4` (default) |
