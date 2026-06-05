---
title: Settings
---

## Settings

Configuration for the sbt plugin.

### versionResolver

Bundles the scheme, tag parser, and rendering formatter into a single typed value. All three share the same `V` type
parameter.

|             |                                                                                                    |
|-------------|----------------------------------------------------------------------------------------------------|
| **Type**    | `SettingKey[VersionResolver[? <: Version]]`                                                        |
| **Default** | `VersionResolver.withDefaults[SemVer]` - SemVer scheme, `v`/`V`-stripping tag parser, no formatter |

Customise via builder combinators:

```scala
// Include build metadata in the rendered version
versionResolver := VersionResolver.withDefaults[SemVer]
  .withFormatter(SemVer.Formatter.Full)

// Render with a truncated SHA
versionResolver := VersionResolver.withDefaults[SemVer]
  .withFormatter(SemVer.Formatter.Full.withShaLength(7))

// Custom tag-name parser (for non-standard tag formats)
versionResolver := VersionResolver.withDefaults[SemVer]
  .withTagParser(name => SemVer.parse(name.stripPrefix("release-")).toOption)
```

The bundled `formatter` controls how `version` (the standard sbt setting) is rendered. `None` falls back to canonical
`v.show` (core plus pre-release, excludes build metadata).

---

### resolvedVersion

The resolved version for the current repository state, typed against the `Version` marker.

|          |                       |
|----------|-----------------------|
| **Type** | `SettingKey[Version]` |

For scheme-specific accessors, pattern-match:

```scala
resolvedVersion.value match
  case v: SemVer => s"${v.major.value}.${v.minor.value}.${v.patch.value}"
```

For just the rendered string, use sbt's standard `version` setting - it already applies the formatter from
`versionResolver` and returns `String`.

---

### versionTarget

The target release version the working tree is heading toward, typed against the `Version` marker.

|          |                       |
|----------|-----------------------|
| **Type** | `SettingKey[Version]` |

On a clean release tag this equals `resolvedVersion` - the tag itself. Otherwise it is the next release core the
resolution computed: the version a release cut from the current state would carry, without development metadata. After a
commit past `v1.0.0`, `resolvedVersion` renders `1.0.1-SNAPSHOT+...` while `versionTarget` renders `1.0.1`.

```scala
// Surface the next release line in a banner without the snapshot suffix
ThisBuild / versionTarget
```

---

### versionBranchOverride

Override the branch name detected from Git. Useful when CI performs detached checkouts.

```scala
versionBranchOverride := sys.env.get("GITHUB_REF_NAME")
```

|             |                                 |
|-------------|---------------------------------|
| **Type**    | `SettingKey[Option[String]]`    |
| **Default** | `sys.env.get("VERSION_BRANCH")` |

When unset, the plugin falls back to Git's current branch (if available).

---

### Environment Variables

Two environment variables influence resolution:

- `VERSION_BRANCH` - overrides the detected branch name (same effect as `versionBranchOverride`)
- `VERSION_VERBOSE` - enables verbose logging from the resolution engine when set to a truthy value

---

## Example Configuration

```scala
// build.sbt
versionBranchOverride := sys.env.get("GITHUB_REF_NAME")
versionResolver := VersionResolver.withDefaults[SemVer]
  .withFormatter(SemVer.Formatter.Full.withShaLength(12))

// Compose a Docker tag from the resolved structured value
def dockerTag = (resolvedVersion.value match
  case v: SemVer => s"${v.major.value}.${v.minor.value}.${v.patch.value}"
  case other => sys.error(s"unexpected scheme: ${other.getClass.getSimpleName}")
  )
```
