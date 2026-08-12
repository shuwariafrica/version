---
title: CLI
---

# CLI Reference

`version` resolves a project's version from Git state and records releases. It is a self-contained native binary:
download the build for your platform from [GitHub Releases](https://github.com/shuwariafrica/version/releases) and put it
on your `PATH`.

## Synopsis

```
version [<command>] [options]
```

With no command, `version` prints the resolved version. Any options - command-specific or global - come after the command
(`version target --increment minor --no-sign`, not `version --no-sign target --increment minor`); with no command they
follow `version` directly (`version --emit raw`).

Every command works on the repository containing `--repository` (default: the working directory), searching upward for
it exactly as `git` does, so the CLI can be run from anywhere inside a checkout. `--console-style pretty` names the
repository it settled on.

## Command Reference

| Command                            | Effect                                                        |
|------------------------------------|---------------------------------------------------------------|
| _(none)_                           | Print the resolved version (the default).                     |
| `target`                           | Print the release version the working tree is heading toward. |
| `target --set, -s <version>`       | Aim the next resolution at `<version>` via an empty commit.    |
| `target --increment, -i <keyword>` | Bump `<keyword>` at the next resolution via an empty commit.   |
| `tag [<version>]`                  | Create an annotated tag, defaulting to the target version.     |
| `list`                             | List the release history, newest first.                       |

---

## Core Operations

### Viewing the version

`version`, with no command, prints the resolved version: the verbatim tag on a clean tagged commit, otherwise a
development version. `version target` prints the release the current state is heading toward - the resolved version on a
clean release tag, otherwise the next release core.

Output is shaped by these flags:

- `-e, --emit <sink>[=<file>]` - `console` (default), `raw` (the bare version string), or `json`; repeatable, and writable
  to a file with `=<path>`.
- `--console-style <pretty|compact>` - `pretty` (default) adds resolution detail; `compact` prints the bare version.
- `--sha-length <7..64>` - SHA width in rendered build metadata (default 40).

### Recording directives

`target --set <version>` and `target --increment <keyword>` do not change the version directly. Each writes one empty
commit - no file changes, only a message carrying a [commit directive](../versioning/directives.md) - which the next
`version` resolution reads and applies. Give one or the other, not both. `<keyword>` is one of the active scheme's
[recognised keywords](../versioning/semver.md#keyword-mapping) (for SemVer: `major`, `minor`, `patch`, and their
aliases); an unknown keyword is rejected with the accepted set. `--dry-run` prints the intended commit without creating
it.

### Tagging

`tag` creates an annotated tag. With no argument it derives the target version and tags the commit that derivation
read, so a `HEAD` that moves mid-command cannot receive a version computed for another commit. Given an argument it
tags `HEAD` with that name exactly as written, once the scheme confirms it reads as a version.

- `--prefix <text>` - lead a derived name with `<text>`, for repositories tagging `v1.2.3` rather than `1.2.3`. An
  explicit argument is never prefixed: the name you give is the name written.
- `-m, --message <text>` - the tag message, defaulting to `Release <name>`.
- `--dry-run` - print the intended tag without creating it.

### Signing

Created commits and tags are signed by default whenever `user.signingkey` is configured. A mutating command refuses -
creating nothing - when no key is configured and `--no-sign` is absent, so an object is never silently left unsigned.
Pass `--no-sign` to opt out. Signing shells out to `gpg`, which must be on the `PATH`.

### Listing releases

`version list` prints each annotated release tag, newest first, as `<version>  <release date>`, where the release date is
the tag's creation time. Filtering is scheme-generic:

- `-n, --limit <count>` - Keep the `<count>` newest entries.
- `--final` - Exclude pre-releases.
- `--since <version|line>` - Releases at or above `<version>`, or on or above the line `<line>`.
- `--until <version|line>` - Releases at or below `<version>`, or on or below the line `<line>`.
- `--details` - Also show the tag and source-commit date.

A bound may name a whole version (`1.2.0`) or just a line of them: a dotted prefix of the scheme's numeric positions,
optionally closed by `x` - `1`, `1.x`, `1.2`. A line is judged on the positions it names alone, so `--until 1.x` keeps
every 1-line release and drops the 2 line including its pre-releases, and `--since 1 --until 1.x` is the whole of the
1 line. Whole-version bounds remain inclusive on the version itself. A scheme with no numeric positions names no
lines, and requires whole versions.

`--details` extends each line to `<version>  <release date>  <tag>  <source-commit date>`. The source-commit date is when
the underlying commit was made, which precedes the release date when a release is tagged after its last commit.

---

## Global Options

| Option                     | Effect                                            |
|----------------------------|---------------------------------------------------|
| `-r, --repository <path>`  | Repository directory (default: current directory).|
| `-b, --basis-commit <rev>` | Commit to resolve against (default: `HEAD`).      |
| `--branch-override <name>` | Override the branch name in build metadata.       |
| `--pr <number>`            | Pull request number for build metadata.           |
| `-v, --verbose`            | Diagnostics to `stderr`.                          |
| `--ci`                     | CI mode: compact output, colour off.              |
| `--no-colour`              | Disable ANSI colour.                              |
| `--help`                   | Print usage and exit.                             |
| `--version`                | Print the binary's own version and exit.          |

---

## Exit Codes

| Code | Meaning                                                 |
|------|---------------------------------------------------------|
| `0`  | Success.                                                |
| `1`  | Resolution, Git, or signing failure (details on `stderr`).|
| `2`  | Invalid arguments.                                      |
