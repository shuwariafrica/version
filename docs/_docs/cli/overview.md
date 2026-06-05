---
title: CLI
---

`version` resolves a project's version from Git state and records releases. It is a self-contained native binary:
download the build for your platform from the [GitHub Releases](https://github.com/shuwariafrica/version/releases) and
put it on your `PATH`.

## Commands

A subcommand selects the action; with none, `version` prints the resolved version. Place the subcommand first - its
options, and any global options, follow it (`version bump minor --no-sign`, not `version --no-sign bump minor`).

| Command                       | Effect                                                            |
|-------------------------------|------------------------------------------------------------------|
| `version` / `version current` | Print the resolved version                                       |
| `version target`              | Print the release version the working tree is heading toward     |
| `version target <version>`    | Record a `target: <version>` directive as an empty commit        |
| `version bump <keyword>`      | Record a `version: <keyword>` directive as an empty commit       |
| `version tag [<version>]`     | Create an annotated tag (defaults to the resolved target)        |
| `version list`                | List the release history, newest first                           |

### Showing the version

`version` (equivalently `version current`) prints the resolved version: the tag verbatim on a clean tagged commit,
otherwise a development version. `version target` prints the release the current state is heading toward - equal to the
resolved version on a clean release tag, otherwise the next release core.

Output is shaped by global flags:

- `--emit <sink>[=<file>]` - `console` (default), `raw` (the bare version string), or `json`; repeatable, and writable
  to a file with `=<path>`.
- `--console-style <pretty|compact>` - `pretty` (default) adds resolution detail; `compact` prints the bare version.
- `--sha-length <7..64>` - SHA width in rendered build metadata (default 40).

### Recording directives

`bump <keyword>` and `target <version>` write a single empty commit carrying the [directive](../versioning/directives.md),
so the next resolution acts on it. `<keyword>` is validated against the active scheme; an unknown keyword is rejected
with the accepted set. `--dry-run` prints the intended commit without creating it.

### Tagging

`tag` creates an annotated tag at HEAD. With no argument it tags the resolved target. The message defaults to
`Release <version>` and is overridable with `--message`; `--dry-run` previews without tagging.

### Signing

Created commits and tags are signed by default whenever `user.signingkey` is configured. A mutating command refuses -
creating nothing - when no key is configured and `--no-sign` is absent, so an object is never silently left unsigned.
Pass `--no-sign` to opt out. Signing shells out to `gpg`, which must be on the `PATH`.

### Listing releases

`version list` prints each annotated release tag, newest first, as `<version>  <release date>`, where the release date
is when the tag was created. Filtering is scheme-generic:

| Option                | Effect                                  |
|-----------------------|-----------------------------------------|
| `-n, --limit <count>` | Keep the `<count>` newest entries       |
| `--final`             | Exclude pre-releases                    |
| `--since <version>`   | Releases at or above `<version>`        |
| `--until <version>`   | Releases at or below `<version>`        |
| `--details`           | Also show the tag and source-commit date |

`--details` extends each line to `<version>  <release date>  <tag>  <source-commit date>`. The source-commit date is
when the underlying commit was made, which precedes the release date when a release is tagged after its last commit.

## Global options

| Option                     | Effect                                              |
|----------------------------|-----------------------------------------------------|
| `-r, --repository <path>`   | Repository directory (default: current directory)   |
| `-b, --basis-commit <rev>`  | Commit to resolve against (default: HEAD)           |
| `--branch-override <name>`  | Branch name for build metadata                      |
| `--pr <number>`             | Pull request number for build metadata              |
| `-v, --verbose`             | Diagnostics to stderr                               |
| `--ci`                      | CI mode: compact output, colour off                 |
| `--no-colour`               | Disable ANSI colour                                 |

## Exit codes

| Code | Meaning                                            |
|------|----------------------------------------------------|
| `0`  | Success                                            |
| `1`  | Resolution, Git, or signing failure (message on stderr) |
| `2`  | Invalid arguments                                  |
