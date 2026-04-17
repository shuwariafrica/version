---
title: Settings
---

## Settings

Configuration settings for the sbt plugin.

### Available Keys

#### versionTagParser

Controls how Git tag names are parsed into SemVer values. The default strips `v`/`V` prefixes and delegates to `SemVer.parse`.

|             |                                                           |
|-------------|-----------------------------------------------------------|
| **Type**    | `String => Option[SemVer]`                                |
| **Default** | Strips `v`/`V` prefix, then `SemVer.parse(...).toOption`  |

Supply a custom function to handle non-standard tag naming conventions:

```scala
import version.semver.*

// Support "release-" prefixed tags
versionTagParser := { name =>
  val normalised = if name.startsWith("release-") then name.stripPrefix("release-") else name
  val stripped = if normalised.startsWith("v") || normalised.startsWith("V") then normalised.drop(1) else normalised
  SemVer.parse(stripped).toOption
}
```

---

#### versionFormatter

Controls how `ThisBuild / version` is rendered. `None` uses `v.show` (standard format, excludes build metadata).
Supply `Some(formatter)` to override.

```scala
versionFormatter := Some(SemVer.Formatter.extended) // include metadata
versionFormatter := None                            // default (no metadata)
```

|             |                                                           |
|-------------|-----------------------------------------------------------|
| **Type**    | `Option[SemVer.Formatter]`                                |
| **Default** | `None` (uses `v.show` - standard SemVer without metadata) |

---

#### resolvedVersion

The fully resolved SemVer value for the current repository state. The plugin records the complete 40-character commit SHA in build metadata; rendering logic decides how much to surface.

```scala
val v = resolvedVersion.value
val core = s"${v.major.value}.${v.minor.value}.${v.patch.value}"
```

|             |                      |
|-------------|----------------------|
| **Type**    | `SettingKey[SemVer]` |

Use this when you need structured data (e.g. to derive Docker tags) rather than the pre-rendered string.

---

#### versionBranchOverride

Override the branch name detected from Git. Useful when CI performs detached checkouts.

```scala
versionBranchOverride := sys.env.get("GITHUB_REF_NAME")
```

|             |                                 |
|-------------|---------------------------------|
| **Type**    | `Option[String]`                |
| **Default** | `sys.env.get("VERSION_BRANCH")` |

When unset, the plugin falls back to Git's current branch (if available).

---

#### Environment Variables

Two environment variables influence resolution:

- `VERSION_BRANCH` - overrides the detected branch name (same effect as `versionBranchOverride`)
- `VERSION_VERBOSE` - enables verbose logging from the resolution engine when set to a truthy value

---

## Example Configuration

```scala
// build.sbt
versionBranchOverride := sys.env.get("GITHUB_REF_NAME")
versionFormatter := Some(SemVer.Formatter.extended)

// Access the structured version when needed
def dockerTag = SemVer.Formatter.extended.format(resolvedVersion.value)
```
