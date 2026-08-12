---
title: Plugin Settings
---

# `sbt-version` Settings

The plugin resolves against the repository containing the build, found by searching upward from the build's root
directory to the filesystem boundary. A build in a linked worktree or in a subdirectory of a monorepo therefore
resolves against the repository it really belongs to; where the search finds none, the plugin falls back to the
scheme's initial development version. The resolved root is named in the plugin's info line, so an unintended hit on an
enclosing repository is visible in the build log.

## versionResolver

Bundles the scheme, tag parser, and rendering formatter into a single typed value. All three share the same `V` type
parameter.

|             |                                                                                                    |
|-------------|----------------------------------------------------------------------------------------------------|
| **Type**    | `SettingKey[VersionResolver[?]]`                                                |
| **Default** | `VersionResolver.withDefaults[SemVer].withFormatter(SemVer.Formatter.Standard)` |

Customise via builder combinators:

```scala
// Publish build metadata, with a truncated SHA
versionResolver := VersionResolver.withDefaults[SemVer]
  .withFormatter(SemVer.Formatter.Full.withShaLength(7))

// Custom tag-name parser (for non-standard tag formats)
versionResolver := VersionResolver.withDefaults[SemVer]
  .withTagParser(name => SemVer.parse(name.stripPrefix("release-")).toOption)
```

The bundled `formatter` decides how `version` - the standard sbt setting, and so the coordinate the build publishes
under - is rendered. The default renders the numbers and any pre-release and stops there, so a development build
publishes as `1.0.1-SNAPSHOT`: one stable coordinate a dependent build can pin, which each publish replaces.
Telling individual snapshots apart is the repository's job, and naming a particular pre-release is what a classifier
such as `rc` or `M1` is for.

The full rendering, build metadata included, is always available from the resolved value itself
(`resolvedVersion.value.show`); `withFormatter(SemVer.Formatter.Full)` publishes under it where every artefact should
carry the commit it was built from.

---

## resolvedVersion

The resolved version for the current repository state, carried with the scheme that read it.

|          |                        |
|----------|------------------------|
| **Type** | `SettingKey[Versioned]` |

`show`, `stable`, `release` and `numbers` are available without naming the scheme. Match `.value` for scheme-specific
accessors:

```scala
resolvedVersion.value.value match
  case v: SemVer => s"${v.major.value}.${v.minor.value}.${v.patch.value}"
```

For just the rendered string, use sbt's standard `version` setting - it already applies the formatter from
`versionResolver` and returns `String`.

---

## versionTarget

The target release version the working tree is heading toward, carried with the scheme that read it.

|          |                        |
|----------|------------------------|
| **Type** | `SettingKey[Versioned]` |

On a clean release tag this equals `resolvedVersion` - the tag itself. Otherwise it is the next release the resolution
computed: the version a release cut from the current state would carry, without development metadata. After a commit
past `v1.0.0`, `resolvedVersion` renders `1.0.1-SNAPSHOT+...` while `versionTarget` renders `1.0.1`.

```scala
// The next release line without the snapshot suffix, e.g. for release notes
releaseNotesHeader := s"Notes for ${versionTarget.value.show}"
```

---

## VersionPlugin.versionHistory

Every released version the repository's annotated version tags name.

|          |                                  |
|----------|----------------------------------|
| **Type** | `Def.Initialize[Set[Versioned]]` |

It sits on the plugin object rather than among the auto-imported settings because evaluating it walks the Git tags; that
cost then falls only on builds that ask for it. Splice it into a setting with `.value` - the plugin object is already in
scope, so no import is needed. For example, deriving the previous artifacts for a binary-compatibility check:

```scala
mimaPreviousArtifacts := VersionPlugin.versionHistory.value.collect {
  case v if v.stable => organization.value %% moduleName.value % v.show
}
```

Which past releases the current one must stay compatible with is a commitment the build makes, so SemVer names two
rules rather than assuming one, and neither is supplied implicitly:

- `SemVer.Compatibility.sameMajor` - the specification read strictly: both stable, at or above `1.0.0`, sharing a
  major.
- `SemVer.Compatibility.leftmostNonZero` - the caret rule of Cargo, npm and Composer, which additionally keeps each
  `0.x` line apart from the next.

```scala
SemVer.Compatibility.leftmostNonZero.compatible(SemVer.parseUnsafe("0.4.2"), SemVer.parseUnsafe("0.4.0")) // true
SemVer.Compatibility.leftmostNonZero.compatible(SemVer.parseUnsafe("0.4.2"), SemVer.parseUnsafe("0.3.9")) // false
```

The set is empty where no repository contains the build. Order it with the scheme's own precedence, never by string
comparison.

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
dockerTag := (resolvedVersion.value.value match
  case v: SemVer => s"${v.major.value}.${v.minor.value}.${v.patch.value}"
  case other     => sys.error(s"unexpected scheme: ${other.getClass.getSimpleName}")
)
```
