---
title: Custom Rendering
---

# Custom Rendering

Customise how versions are rendered to strings.

## Built-in Options

### Standard (Default)

Excludes build metadata:

```scala
import version.Version

versionShow := Some(Version.Show.Standard)
```

```
1.2.3-SNAPSHOT
```

### Extended

Includes metadata with truncated SHAs:

```scala
versionShow := Some(Version.Show.Extended)
```

```
1.2.3-SNAPSHOT+branchmain.commits5.sha1234567
```

## Custom Show Instance

Implement `Version.Show` for full control:

```scala
object MyShow extends Version.Show:

  import version.*

  extension (v: Version)
    def show: String =
      val core = s"${v.major.value}.${v.minor.value}.${v.patch.value}"
      v.preRelease match
        case Some(pr) if pr.isSnapshot =>
          // Extract SHA from metadata
          val sha = v.metadata
            .flatMap(_.identifiers.find(_.startsWith("sha")))
            .map(_.drop(3).take(7))
            .getOrElse("")
          if sha.nonEmpty then s"$core-SNAPSHOT-$sha"
          else s"$core-SNAPSHOT"
        case Some(pr) =>
          s"$core-${pr.show}"
        case None =>
          core

ThisBuild / versionShow := Some(MyShow)
```

Output: `1.2.3-SNAPSHOT-abc1234`

## Common Patterns

### CI Build Numbers

```scala
object CIShow extends Version.Show:
  extension (v: Version)
    def show: String =
      val core = s"${v.major.value}.${v.minor.value}.${v.patch.value}"
      val build = sys.env.getOrElse("BUILD_NUMBER", "local")
      if v.isSnapshot then s"$core-dev.$build"
      else v.preRelease.fold(core)(pr => s"$core-${pr.show}")
```

Output: `1.2.3-dev.456`

### Minimal Development

```scala
object MinimalShow extends Version.Show:
  extension (v: Version)
    def show: String =
      val core = s"${v.major.value}.${v.minor.value}.${v.patch.value}"
      if v.isFinal then core else s"$core-dev"
```

Output: `1.2.3-dev`

## Accessing Raw Data

The full `Version` object is always available:

```scala
val result = versionResolutionResult.value
```
