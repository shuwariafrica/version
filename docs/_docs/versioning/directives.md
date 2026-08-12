---
title: Commit Directives
---

# Commit Directives

A directive is a phrase in a commit message that says how the version should move. Every commit in the range being
released is read, and what the directives collectively ask for decides the next version.

A directive is recognised anywhere in a message - any line, at any position in it, behind a list bullet or indentation.
Matching ignores case, tolerates whitespace around the colon, and never fires inside a longer word, so
`reversion: 1.0.0` says nothing. Anything the grammar does not recognise is prose: a message is never rejected for what
it contains.

Which words carry which meaning belongs to the active scheme. For SemVer, see [SemVer Derivation](semver.md).

---

## Forms

A keyword says its piece in three interchangeable forms:

```
version: breaking          explicit
breaking: drop the API     shorthand, which needs text after the colon
[breaking]                 bracketed
```

So a subject line, a body bullet, and a trailing tag all reach the same result:

```
breaking: drop the legacy handler

* fix: correct the retry delay

Remove the old entry point [breaking]
```

A keyword must sit directly against its colon. `breaking (api): x` and `breaking!: x` say nothing - the colon is what
marks a word as a directive rather than a word in a sentence.

A bracket is a directive when a directive leads its content - a bare keyword (`[breaking]`), or any colon form
(`[version: major]`, `[breaking: drop the API]`) - and it counts once. Brackets annotate the message rather than
replace it: a commit message describes the change, and its brackets say what that change means for the version.
They sit before, after, or between prose, and several on one line each count - `[breaking][feature] Rework the
parser` records a breaking change and a feature, and `Add request caching [breaking]` marks the described change
as breaking. A bracket glued to a word is left alone (`foo[breaking]bar` - letters, digits, and hyphens all bind,
so `-[breaking]` is glued too), and a bracket led by anything else is prose that hides what it contains:
`[skip ci]` and `[see version: major]` say nothing.

### Setting a component

A keyword that names a component of the version, rather than a kind of change, accepts a value:

```
version: minor: 9          the minor component becomes 9, and everything below it resets
```

Words for kinds of change carry no value: `version: breaking: 5` says nothing, because how far a breaking change moves
a version is the scheme's decision, not the committer's.

---

## Naming a version outright

```
target: 2.0.0
```

The version is read by the active scheme and is accepted only where the tags already in the repository admit it - see
[Target Validation](validation.md).

---

## Excluding commits

```
version: ignore                     this commit                (also [ignore])
version: ignore: <id>[, <id>...]    commits by identifier prefix (7 or more hex characters)
version: ignore: <id>..<id>         an inclusive range
version: ignore-merged              the commits a merge brought in   (also [ignore-merged])
```

An identifier too short to be unambiguous, or a range missing an end, excludes nothing. On a merge commit,
`ignore-merged` drops the commits the branch brought in, so one directive on the merge can speak for all of them.

---

## When several apply

1. Ignored commits contribute nothing.
2. A version named outright wins, where the tags admit it. The highest of several does.
3. Otherwise every remaining directive is applied to the base version and the highest result wins.
4. Otherwise the scheme decides what a range that asked for nothing means. For SemVer, that is a patch release, so
   there is always something to release.

A directive the scheme refuses - a component it does not have, a value it cannot hold - is reported and the rest still
decide.

---

## Examples

Against SemVer, from a base of `1.2.3`:

| Commit message                    | Result   |
|-----------------------------------|----------|
| `feat: accept a custom clock`     | `1.3.0`  |
| `* fix: correct the retry delay`  | `1.2.4`  |
| `breaking: drop a handler`        | `2.0.0`  |
| `version: major`                  | `2.0.0`  |
| `version: minor: 9`               | `1.9.0`  |
| `[feat]`, then `version: major`   | `2.0.0`  |
| `target: 2.5.0`                   | `2.5.0`  |
| `Bump a plugin`                   | `1.2.4`  |
| `version: ignore` on every commit | `1.2.4`  |
