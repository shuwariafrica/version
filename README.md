# version

> ⚠️ **Project Status: Pre‑Release / Not Yet Production Ready**  
> Interfaces, behaviours, and metadata formats MAY still change. Evaluate carefully before adopting in critical
> pipelines.

A modular Scala 3 toolkit and CLI for **deterministic, specification‑driven Semantic Version (SemVer 2.0.0) derivation**
from a Git repository’s state.

It provides:

- A strictly typed core version data model (`version-core`)
- High‑performance JSON codecs (`jsoniter-scala`)
- ZIO JSON codecs
- ZIO Prelude type class instances
- A pure Git‑based version derivation engine (`version-cli-core`)
- A multi‑format command line application (`version-cli`)
- A formal normative specification (`docs/Version-Resolution-Specification.md`)

The central premise is **predictability**: every emitted version (and its ordering) follows a transparent rule set:

- Rigidly typed numeric components eliminate accidental misuse
- A constrained, ordered pre‑release classifier set (milestone < alpha < beta < rc < snapshot)
- Canonical, positionally meaningful build metadata identifiers
- Deterministic snapshot generation for every non‑release commit
- Explicit commit message directives encoded in an auditable history

---

## Contents

| Module                    | Status      | Description                                       |
|---------------------------|-------------|---------------------------------------------------|
| `version-core`            | Pre‑release | SemVer model, parsing, ordering, typed operations |
| `version-codecs-jsoniter` | Pre‑release | jsoniter-scala codecs for the model               |
| `version-codecs-zio`      | Pre‑release | ZIO JSON codecs                                   |
| `version-zio-prelude`     | Pre‑release | Prelude `Equal`, `Ord`, additive combinators      |
| `version-cli-core`        | Pre‑release | Pure Git semantic version derivation              |
| `version-cli`             | Pre‑release | CLI wrapper with text / JSON / YAML output        |

---

## Specification Reference (Normative)

See the [Version Resolution Specification](docs/version-resolution-technical-specification.md) (which fully details:
Mode 1 vs Mode 2, keyword precedence, target ignore rules A–F, defaulting logic, metadata ordering, branch
normalisation, edge cases, etc).

If code comments contradict the specification, **the specification wins**.

---

## Why Another Versioning Toolkit?

| Concern                        | Provided Benefit                                        |
|--------------------------------|---------------------------------------------------------|
| Unstable / ad‑hoc tag formats  | Strict parser with classifier + metadata invariants     |
| Ambiguous pre-release ordering | Fixed classifier hierarchy; numeric sub‑ordering        |
| “Accidental” regressions       | Target directive validation (rules A–F)                 |
| Hard to trace CI snapshots     | Canonical metadata: pr / branch / commits / sha / dirty |
| Unsafe stringly operations     | Opaque numeric types + smart constructors               |
| Deterministic derivation       | Pure functional algorithm over repository state         |

> ⚠️ **Not production hardened**: concurrency stress, pathological repository scale, and exotic platform ports have not
> yet undergone exhaustive validation.

---

## 1. Module: `version-core` (Pre‑Release)

### Overview

A strongly typed representation of SemVer 2.0.0 with:

- Opaque numeric wrappers (`MajorVersion`, `MinorVersion`, `PatchNumber`, `PreReleaseNumber`)
- `PreReleaseClassifier` enum with alias mapping
- `PreRelease` structure (classifier + optional number)
- `BuildMetadata` as validated list
- `Version` (core + optional preRelease + optional buildMetadata)
- Correct SemVer precedence ordering (build metadata ignored)

### Quick Start

```scala
import version.*
import version.PreRelease

val release = Version(
  MajorVersion.from(1).toOption.get,
  MinorVersion.from(2).toOption.get,
  PatchNumber.from(3).toOption.get
)
// "1.2.3"

val snapshot = release.copy(preRelease = Some(PreRelease.snapshot))
// "1.2.3-snapshot"
```

### Parsing

```scala
import version.parser.VersionParser
import version.PreRelease.Resolver

given Resolver = Resolver.default

VersionParser.parse("2.5.0-rc.3+build.sha123") match
  case Right(v) => println(v.preRelease.map(_.toString))
  case Left(err) => println(s"Invalid: ${err.message}")
```

Combined identifiers like `rc3` are split into `rc.3` automatically.

### Operations (extensions in `version.operations`)

```scala
import version.operations.*

given version.PreRelease.Resolver = version.PreRelease.Resolver.default

val base = Version(MajorVersion.unsafe(2), MinorVersion.unsafe(0), PatchNumber.unsafe(5))
val nextMinor = base.next[MinorVersion] // 2.1.0
val nextMajor = base.next[MajorVersion] // 3.0.0
val withSnapshot = base.toSnapshot // 2.0.5-snapshot
val released = withSnapshot.release // 2.0.5
```

Pre‑release advancement:

```scala
val alpha1 = base.as[PreReleaseClassifier.Alpha.type] // 2.0.5-alpha.1
val alpha2 = alpha1.advance[PreReleaseClassifier.Alpha.type] // 2.0.5-alpha.2
val beta1 = alpha2.advance[PreReleaseClassifier.Beta.type] // 2.0.5-beta.1
```

### Build Metadata

```scala
import version.BuildMetadata

val meta = BuildMetadata.from(List("branchmain", "commits12", "shaabc12def")).toOption.get
val versionWithMeta = base.copy(buildMetadata = Some(meta))
// 2.0.5+branchmain.commits12.shaabc12def
```

### Ordering

Final outranks pre-release of the same core version; numeric precedence first, classifier precedence second.

---

## 2. Module: `version-codecs-jsoniter` (Pre‑Release)

High‑performance JSON codecs (jsoniter-scala) with validation on decode.

```scala
import version.*
import version.codecs.jsoniter.given
import com.github.plokhotnyuk.jsoniter_scala.core.*

val v = Version(MajorVersion.unsafe(1), MinorVersion.unsafe(0), PatchNumber.unsafe(0))
val json = writeToString(v)
val roundTrip = readFromString[Version](json)
assert(roundTrip == v)
```

> ⚠️ Not production stabilised: field arrangement may change if upstream model evolves pre‑1.0.0.

---

## 3. Module: `version-codecs-zio` (Pre‑Release)

ZIO JSON codecs enhancing functional integration. They produce descriptive decode failures.

```scala
import version.*
import version.codecs.zio.given
import zio.json.*

val v = Version(MajorVersion.unsafe(1), MinorVersion.unsafe(1), PatchNumber.unsafe(0))
val json = v.toJson
val parsed = json.fromJson[Version]
```

---

## 4. Module: `version-zio-prelude` (Pre‑Release)

ZIO Prelude type class instances:

- `Equal`, `Ord` for all core opaque types and `Version`
- `Commutative` numeric addition for numeric wrappers (utility; *not* SemVer bump semantics)

```scala
import version.*
import version.zio.prelude.prelude.given
import zio.prelude.*

val mv = MajorVersion.unsafe(2)
assert(Ord[MajorVersion].compare(mv, MajorVersion.unsafe(3)) < 0)
```

---

## 5. Module: `version-cli-core` (Pre‑Release)

Pure Git-based semantic version derivation library (no CLI parsing).

### Configuration

```scala
import version.cli.core.domain.*

val cfg = CliConfig(
  repo = os.pwd,
  basisCommit = "HEAD",
  prNumber = Some(42),
  branchOverride = None,
  shaLength = 12
)
```

### Derivation

```scala
import version.cli.core.VersionCliCore

given version.PreRelease.Resolver = version.PreRelease.Resolver.default

VersionCliCore.resolve(cfg) match
case Right(v)
=> println(s"Resolved: $v")
case Left(err)
=> println(s"Error: ${err.message}")
```

### Commit Message Directives (scanned between Base and basis commit; or whole reachable history if no Base):

| Directive                     | Effect (if valid)                       |
|-------------------------------|-----------------------------------------|
| `target: 2.5.0`               | Force target core (validated rules A–F) |
| `version: minor: 7`           | Set minor = 7 (resets patch)            |
| `change: major` / `breaking:` | Increment major; reset minor & patch    |
| `feature:` / `change: minor`  | Increment minor; reset patch            |
| `fix:` / `change: patch`      | Increment patch                         |

Shorthand synonyms accepted: `breaking:`, `feature:`, `fix:`.

### Snapshot Metadata Construction (in order):

`pr<number>` → `branch<normalised>` → `commits<count>` → `sha<abbr>` → `dirty`

> ⚠️ Rule set is stable conceptually but **fields and naming could still change** prior to 1.0.0 if new requirements
> emerge.

---

## 6. Module: `version-cli` (Pre‑Release)

High-level executable that wraps `version-cli-core`, handling argument parsing, multi-sink output, CI ergonomics and
human vs machine readable formats.

### Design Overview

The CLI now separates **what** to emit (sinks) from **how** the human console is rendered (style):

| Concept          | Flag / Argument                     | Notes |
|------------------|-------------------------------------|-------|
| Repository path  | `-r, --repository <path>`           | Any path inside the git repo (default: current dir) |
| Basis commit     | `-b, --basis-commit <rev>`          | Commit-ish to resolve against (default: `HEAD`) |
| PR number        | `--pr <n>`                          | Adds `pr<n>` metadata segment |
| Branch override  | `--branch-override <name>`          | Bypass branch detection (e.g. CI env) |
| SHA length       | `--sha-length <7..40>`              | Abbrev length for `sha<abbr>` metadata (default 12) |
| Verbose logs     | `-v, --verbose`                     | Diagnostic tracing to stderr |
| CI mode          | `--ci`                              | Forces compact console style & disables colours |
| Disable colour   | `--no-colour` / `--no-color`        | Independent of `--ci` |
| Emit sink        | `-e, --emit sink[=path]` (repeat)   | `console` | `raw` | `json` | `yaml` optionally with file target |
| Console style    | `--console-style pretty|compact`    | Affects only `console` sink (default pretty; compact in CI) |
| Help / Version   | `--help` / `--version`              | Standard scopt behaviours |

Sinks:

| Sink     | Description                                           | Default Destination |
|----------|-------------------------------------------------------|---------------------|
| console  | Human readable multi-line (pretty or compact)         | stdout              |
| raw      | Pure SemVer string (no adornment)                     | stdout              |
| json     | Structured JSON (stable fields subject to 1.0 final)  | stdout or file      |
| yaml     | YAML representation                                   | stdout or file      |

If no `--emit` is provided, a single `console` sink is assumed.

`--console-style` only matters if at least one `console` sink is present (otherwise it is ignored).

In CI (`--ci`), a missing explicit style auto-switches `console` to `compact`. You can still force pretty:
`--ci --console-style pretty`.

### Usage Synopsis

```
version-cli [global options] [--emit sink[=file]]...

Examples:
  version-cli                          # pretty console output
  version-cli --ci                     # compact console output
  version-cli --emit raw               # just the version string
  version-cli --emit console --console-style compact
  version-cli --emit json=dist/version.json --emit yaml=dist/version.yaml
  version-cli -r ../repo --pr 42 --emit console --emit raw
  version-cli --basis-commit abcdef1234567890 --emit json
```

### Sink Spec Grammar

```
<spec>  ::= <sink> ["=" <path>]
<sink>  ::= console | raw | json | yaml
<path>  ::= POSIX-like path (no spaces recommended)
```

Examples:

```
--emit console              # console to stdout
--emit raw                  # raw version to stdout
--emit json=build/ver.json  # write JSON to file (parents created)
--emit yaml                 # yaml to stdout
```

Multiple emits are independent. Ordering is preserved as provided on the command line.

### Pretty vs Compact

Pretty console output (multi-line, annotated):

```
Version:
  full      : 3.2.0-snapshot+pr123.branchmain.commits4.sha1a2b3c4d5e6
  core      : 3.2.0
  preRelease: snapshot
  metadata  : +pr123.branchmain.commits4.sha1a2b3c4d5e6
```

Compact console output (`--console-style compact`) is identical to the raw semver string:

```
3.2.0-snapshot+pr123.branchmain.commits4.sha1a2b3c4d5e6
```

If you only want the semver string with no formatting overhead use `--emit raw`.

### File Outputs

When `=path` is supplied the sink content is written to that file (overwritten if exists). Parent directories are
created. Failures to write any file will cause a non-zero exit (after attempting all writes).

### Exit Codes

| Code | Meaning                           |
|------|-----------------------------------|
| 0    | Success                           |
| 1    | Resolution failure (core layer)   |
| 2    | CLI argument / command parse error |

### Migration Notes

Earlier versions used `--format` with values `pretty|compact|json|yaml`. That flag has been **removed** in favour of
`--emit` + `--console-style` which removes ambiguous combinations and enables explicit file targets.

### Future Commands

The parser already reserves a hidden `release` command placeholder; future versions may introduce automated tagging /
annotation without altering current resolve workflows.

> ⚠️ Pretty layout & field ordering may evolve pre‑1.0.0. Machine formats aim for stability once the core model is stable.

---

## 7. Version Derivation Behaviour (Abbreviated Reminder)

| Scenario                         | Result Core                                |
|----------------------------------|--------------------------------------------|
| Valid target(s) survive          | Highest valid target core                  |
| Absolutes only                   | Apply highest per component (with resets)  |
| Relatives only                   | Highest precedence (major > minor > patch) |
| No directives + final base       | Patch + 1                                  |
| No directives + pre-release base | Base core                                  |
| No base + repo has tags          | (highest.major + 1).0.0                    |
| No tags anywhere                 | 0.1.0                                      |

All non-concrete states become `-snapshot` with ordered metadata.

---

## 8. Branch Normalisation

Rules: lowercase → non `[0-9a-z-]` ⇒ `-` → collapse `-` sequences → trim → empty ⇒ `detached`.

Example:

```
"Feature/ABC_123!!" -> "feature-abc-123"
```

---

## 9. Error Surface

Representative `version-cli-core` error ADT:

- `GitCommandFailed(cmd, exitCode, out, err)`
- `NotAGitRepository(path)`
- `InvalidShaLength(length)`
- `Message(msg)`

CLI exits:

- `0` success
- `1` resolution failure
- `2` argument parsing failure

---

## 10. Determinism & Idempotency

Given unchanged:

- Commit graph
- Tag set
- Working directory state
- Inputs (PR number, overrides, sha length, basis commit)

Resulting version string is stable.  
Changes in *any* component produce deterministic new outputs.

---

## 11. API & Stability Caveats

| Aspect                        | Stability                         |
|-------------------------------|-----------------------------------|
| Core numeric types & ordering | High                              |
| Pre-release classifier set    | High                              |
| Commit keyword grammar        | Medium (minor additions possible) |
| Snapshot metadata ordering    | High                              |
| Metadata field names          | Medium (extensions possible)      |
| CLI pretty formatting         | Low (UI/format may evolve)        |
| Error message wording         | Low                               |

> ⚠️ Do not rely on exact error message substrings for logic; match by variant/type where possible.

---

## 12. Integration Patterns

**SBT task integration (illustrative):**

```scala
import version.cli.core.*
import version.cli.core.domain.*

lazy val deriveVersion = taskKey[String]("Derive semantic version")

deriveVersion := {
  given version.PreRelease.Resolver = version.PreRelease.Resolver.default

  VersionCliCore.resolve(
    CliConfig(
      repo = os.Path((ThisBuild / baseDirectory).value.getAbsolutePath),
      basisCommit = "HEAD",
      prNumber = sys.env.get("CI_PR").flatMap(_.toIntOption),
      branchOverride = sys.env.get("CI_BRANCH"),
      shaLength = 12
    )
  ) match
    case Right(v) => v.toString
    case Left(e) => throw new RuntimeException("Version derivation failed: " + e.message)
}
```

---

## 13. Limitations (Current)

- No multi‑module selective tagging strategy (monorepo sub‑path filtering out of scope)
- No “prefix” or “namespace” tag mode (e.g., `lib1-1.2.3`)
- No direct release creation (pure read‑only logic)
- Performance not yet benchmarked on extremely large monorepos
- No native Windows path normalisation edge case audit (expected to work; unverified extremes)

---

## 14. Example Commit Directive Timeline

| Commit Message      | Effect                 | Core Progression (start 1.4.5 final) |
|---------------------|------------------------|--------------------------------------|
| (none)              | default patch          | 1.4.6                                |
| `change: minor`     | bump minor             | 1.5.0                                |
| `version: minor: 9` | set minor=9            | 1.9.0                                |
| `fix:`              | patch                  | 1.9.1                                |
| `target: 2.0.0`     | force major (accepted) | 2.0.0                                |
| `target: 1.4.5`     | ignored (regression)   | — (remains rule-driven)              |

---

## 15. Contributing

Not yet accepting external large-scale feature PRs while core semantics settle.  
Bug reports, specification clarifications, and minimal reproduction cases **welcome**.

---

## 16. Licence

Apache 2.0. See headers and associated licence metadata.

---

## 17. Quick Cheat Sheet

| Task                      | Snippet                                       |
|---------------------------|-----------------------------------------------|
| Parse version             | `VersionParser.parse("1.2.3-rc.1+meta")`      |
| Next patch                | `v.next[PatchNumber]`                         |
| Mark snapshot             | `v.toSnapshot`                                |
| Remove pre-release        | `v.release`                                   |
| CLI compact               | `version-cli --emit console --console-style compact` |
| Add JSON output           | `version-cli --emit console --emit json`      |
| Branch override           | `version-cli --branch-override Release/2.1.x` |
| Force target (if allowed) | `commit msg: target: 3.0.0`                   |

---
