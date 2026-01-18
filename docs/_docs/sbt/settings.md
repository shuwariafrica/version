---
title: Settings
---

## Settings

Configuration settings for the sbt plugin.

### Available Keys

#### versionRead

Customise how Git tags are parsed into `Version` values. The plugin uses the standard SemVer parser by default.

```scala
versionRead := Version.Read.ReadString
```

|             |                                    |
|-------------|------------------------------------|
| **Type**    | [[version.Version.Read]]`[String]` |
| **Default** | [[version.Version.Read.ReadString]] |

For custom parsers, see [Parsing — Custom Read](../core/parsing.md#custom-read-instances).

---

#### versionResolver

Customise how pre-release identifiers from Git tags are mapped to [[version.PreRelease]] values. The default resolver
recognises standard classifiers (alpha, beta, rc, etc.) and their aliases.

```scala
versionResolver := PreRelease.Resolver.given_Resolver
```

|             |                                               |
|-------------|-----------------------------------------------|
| **Type**    | [[version.PreRelease.Resolver]]               |
| **Default** | [[version.PreRelease.Resolver.given_Resolver]] |

For custom resolvers, see [Parsing — Custom Resolver](../core/parsing.md#custom-pre-release-mapping).

---

#### versionShow

Controls how `ThisBuild / version` is rendered. `None` means "use [[version.Version.Show.Standard]]," which excludes
build metadata. Supply `Some(showInstance)` to override the behaviour.

```scala
versionShow := Some(Version.Show.Extended) // include metadata
versionShow := None                        // default (no metadata)
```

|             |                                        |
|-------------|----------------------------------------|
| **Type**    | `Option[`[[version.Version.Show]]`]`   |
| **Default** | `None` (→ [[version.Version.Show.Standard]]) |

For custom Show implementations, see [Operations — Rendering](../core/operations.md#rendering).

---

#### resolvedVersion

The fully resolved [[version.Version]] value for the current repository state. The plugin always records the complete
40-character commit SHA in build metadata; rendering logic decides how much to surface.

```scala
val v = resolvedVersion.value
val core = s"${v.major.value}.${v.minor.value}.${v.patch.value}"
```

|             |                        |
|-------------|------------------------|
| **Type**    | `SettingKey[Version]`  |

Use this when you need structured data (e.g. to derive Docker tags) rather than the pre-rendered string.

---

#### versionBranchOverride

Override the branch name detected from Git. Useful when CI performs detached checkouts.

```scala
versionBranchOverride := sys.env.get("GITHUB_REF_NAME")
```

|             |                        |
|-------------|------------------------|
| **Type**    | `Option[String]`       |
| **Default** | `sys.env.get("VERSION_BRANCH")` |

When unset, the plugin falls back to Git's current branch (if available).

---

#### Environment Hooks

Two environment variables influence resolution:

- `VERSION_BRANCH` — overrides the detected branch name (same effect as `versionBranchOverride`)
- `VERSION_VERBOSE` — enables verbose logging from the underlying CLI core when set to a truthy value

---

## Example Configuration

```scala
// build.sbt
versionBranchOverride := sys.env.get("GITHUB_REF_NAME")
versionRead := Version.Read.ReadString
versionShow := Some(Version.Show.Extended)

// Access the structured version when needed
def dockerTag = resolvedVersion.value.show(using Version.Show.Extended)
```
