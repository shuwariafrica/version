---
title: Settings
---

# Settings

Configuration settings for the sbt plugin.

## versionShaLength

SHA identifier truncation length.

```scala
versionShaLength := 12
```

|             |       |
|-------------|-------|
| **Type**    | `Int` |
| **Range**   | 7–40  |
| **Default** | `7`   |

Example output: `shaabc1234567890` vs `shaabc1234`

## versionPrNumber

Pull request number for build metadata.

```scala
versionPrNumber := Some(42)

// From environment
versionPrNumber := sys.env.get("PR_NUMBER").flatMap(_.toIntOption)
```

|             |               |
|-------------|---------------|
| **Type**    | `Option[Int]` |
| **Default** | `None`        |

Adds `pr42` to metadata when set.

## versionBranchOverride

Override detected branch name.

```scala
versionBranchOverride := Some("release")
```

|             |                        |
|-------------|------------------------|
| **Type**    | `Option[String]`       |
| **Default** | `None` (auto-detected) |

Use when CI checkouts don't preserve branch information.

## versionShow

The `Show` instance for rendering.

```scala
import version.Version

// Include build metadata
versionShow := Some(Version.Show.Extended)

// Default: exclude metadata (None uses Standard internally)
versionShow := None
```

|             |                        |
|-------------|------------------------|
| **Type**    | `Option[Version.Show]` |
| **Default** | `None`                 |

## versionResolutionResult

Task returning the full resolution result.

```scala
val result = (versionResolutionResult).value

result.version // Version
result.isTagged // Boolean
result.isDirty // Boolean
result.baseTag // Option[String]
result.commitCount // Int
```

|          |                          |
|----------|--------------------------|
| **Type** | `Task[ResolutionResult]` |

## Example Configuration

```scala
// build.sbt

import version.Version

versionShaLength := 10
versionPrNumber := sys.env.get("GITHUB_PR_NUMBER").flatMap(_.toIntOption)
versionBranchOverride := sys.env.get("GITHUB_REF_NAME")
versionShow := Some(Version.Show.Extended)
```
