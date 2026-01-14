---
title: ZIO JSON
---
# ZIO JSON

Codecs for [ZIO JSON](https://github.com/zio/zio-json).

```scala
libraryDependencies += "africa.shuwari" %%  /* or `%%%` */ "version-codecs-zio" % "@VERSION@"
```

## Usage

```scala
import zio.json.*
import version.*
import version.codecs.zio.given

// Encode
val v = Version.parseUnsafe("1.2.3-alpha.1")
val json = v.toJson // "1.2.3-alpha.1"

// Decode
val decoded = json.fromJson[Version]
// Right(Version(1, 2, 3, Some(alpha.1), None))
```

## In Data Structures

```scala
import zio.json.*

case class Package(name: String, version: Version) derives JsonCodec

val pkg = Package("my-lib", Version.parseUnsafe("1.0.0"))
pkg.toJson
// {"name":"my-lib","version":"1.0.0"}
```

## Provided Codecs

```scala
given JsonCodec[Version]
given JsonCodec[MajorVersion]
given JsonCodec[MinorVersion]
given JsonCodec[PatchNumber]
given JsonCodec[PreReleaseNumber]
given JsonCodec[PreReleaseClassifier]
given JsonCodec[PreRelease]
given JsonCodec[BuildMetadata]
```

## Error Handling

```scala
"invalid".fromJson[Version]
// Left("Expected '\"' but found 'i'")

"\"not-a-version\"".fromJson[Version]
// Left("Invalid version format: not-a-version")
```
