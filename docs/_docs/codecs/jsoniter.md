---
title: jsoniter-scala
---
# jsoniter-scala

Codecs for [jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala).

```scala
libraryDependencies += "africa.shuwari" %%  /* or `%%%` */ "version-codecs-jsoniter" % "@VERSION@"
```

## Usage

```scala
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import version.*
import version.codecs.jsoniter.given

// Encode
val v = "1.2.3-alpha.1".toVersionUnsafe
val json = writeToString(v) // "1.2.3-alpha.1"

// Decode
val decoded = readFromString[Version](json)
// Version(1, 2, 3, Some(alpha.1), None)
```

## In Data Structures

```scala
case class Package(name: String, version: Version)

object Package:
  given JsonValueCodec[Package] = JsonCodecMaker.make

val pkg = Package("my-lib", "1.0.0".toVersionUnsafe)
writeToString(pkg)
// {"name":"my-lib","version":"1.0.0"}
```

## Provided Codecs

```scala
given JsonValueCodec[Version]
given JsonValueCodec[MajorVersion]
given JsonValueCodec[MinorVersion]
given JsonValueCodec[PatchNumber]
given JsonValueCodec[PreReleaseNumber]
given JsonValueCodec[PreReleaseClassifier]
given JsonValueCodec[PreRelease]
given JsonValueCodec[Metadata]
```

## Error Handling

```scala
import scala.util.Try

Try(readFromString[Version]("invalid"))
// Failure(JsonReaderException: ...expected '"'...)

Try(readFromString[Version]("\"not-a-version\""))
// Failure(JsonReaderException: ...invalid version...)
```
