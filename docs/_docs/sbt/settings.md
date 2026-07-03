---
title: Plugin Settings
---

# `sbt-version` Settings

## versionResolver

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

## resolvedVersion

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

## versionTarget

The target release version the working tree is heading toward, typed against the `Version` marker.

|          |                       |
|----------|-----------------------|
| **Type** | `SettingKey[Version]` |

On a clean release tag this equals `resolvedVersion` - the tag itself. Otherwise it is the next release core the
resolution computed: the version a release cut from the current state would carry, without development metadata. After a
commit past `v1.0.0`, `resolvedVersion` renders `1.0.1-SNAPSHOT+...` while `versionTarget` renders `1.0.1`.

```scala
// The next release line without the snapshot suffix, e.g. for release notes
releaseNotesHeader := s"Notes for ${versionTarget.value.show}"
```

---

## VersionPlugin.versionHistory

Every released version parsed from the repository's annotated version tags, as a `Set[Version]`.

|          |                                |
|----------|--------------------------------|
| **Type** | `Def.Initialize[Set[Version]]` |

It sits on the plugin object rather than among the auto-imported settings because evaluating it walks the Git tags; that
cost then falls only on builds that ask for it. Splice it into a setting with `.value` - the plugin object is already in
scope, so no import is needed. For example, deriving the previous artifacts for a binary-compatibility check:

```scala
mimaPreviousArtifacts := VersionPlugin.versionHistory.value.collect {
  case v: SemVer if v.isFinal => organization.value %% moduleName.value % v.show
}
```

The set is empty when the base directory is not a Git repository. Filter and order with the scheme's own API - `isFinal`
and the scheme `Ordering` - not string comparison.

---

## versionBranchOverride

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

## Environment Variables

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

// Compose a Docker tag from the resolved structured value.
// `.value` reads the setting, so it must sit inside a setting/task (`:=`), not a plain `def`.
lazy val dockerTag = settingKey[String]("major.minor.patch for the container tag")
dockerTag := (resolvedVersion.value match
  case v: SemVer => s"${v.major.value}.${v.minor.value}.${v.patch.value}"
  case other     => sys.error(s"unexpected scheme: ${other.getClass.getSimpleName}")
)
```
