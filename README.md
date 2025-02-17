# version

This project is a version management library implemented in Scala. It provides functionality for handling semantic
versioning, including major, minor, patch, and pre-release versions. The library also includes codecs for JSON
serialization and deserialization using different libraries.

[![CI Build](https://github.com/shuwariafrica/version/actions/workflows/build.yml/badge.svg)](https://github.com/shuwariafrica/version/actions/workflows)
[![Maven Central](https://img.shields.io/maven-central/v/africa.shuwari/version_3.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:africa.shuwari%20AND%20a:version)

**Note: This project is currently under development and is not yet stable.**

_________________________

## Features

- **Version Management**: Create and manipulate versions with major, minor, patch, and pre-release components.
- **Ordering and Comparison**: Compare versions and pre-release components.
- **JSON Codecs**: Serialize and deserialize version objects using `jsoniter-scala` and `zio-json`.
- **ZIO Prelude Integration**: Provides instances for `Equal`, `Ord`, and `Commutative` type classes from ZIO Prelude.

## Modules

### Core

The core module provides the main functionality for version management.

- **Version**: Represents a version with major, minor, patch, and optional pre-release components.
- **VersionParser**: Parses version strings into `Version` objects.
- **VersionNumberField**: Represents version number fields (major, minor, patch, pre-release).
- **PreRelease**: Represents pre-release version information.
- **PreReleaseClassifier**: Enum for supported pre-release classifiers.
- **Errors**: Defines errors related to version management.

### Codecs

The codecs module provides JSON serialization and deserialization for version objects.

#### jsoniter

- **Jsoniter Codecs**: Uses `jsoniter-scala` for JSON serialization and deserialization.

#### zio

- **ZIO JSON Codecs**: Uses `zio-json` for JSON serialization and deserialization.

### ZIO Prelude

The ZIO Prelude module provides instances for `Equal`, `Ord`, and `Commutative` type classes from ZIO Prelude.

_________________________

## Usage

### Creating Versions

```scala
import version._

val version1 = Version(MajorVersion(1), MinorVersion(0), PatchNumber(0))
val version2 = Version(MajorVersion(1), MinorVersion(1), PatchNumber(0), PreRelease.alpha(PreReleaseNumber(1)))
```

### Parsing Versions

```scala
import version.VersionParser

val parsedVersion = VersionParser.version("1.0.0-alpha1")
```

### JSON Serialization

#### jsoniter

```scala
import com.github.plokhotnyuk.jsoniter_scala.core._
import version.codecs.jsoniter._

val json = writeToString(version1)
val deserializedVersion = readFromString[Version](json)
```

#### zio

```scala
import zio.json._
import version.codecs.zio._

val json = version1.toJson
val deserializedVersion = json.fromJson[Version]
```

### ZIO Prelude Integration

```scala
import zio.prelude._
import version.zio.prelude._

val isEqual = Equal[Version].equal(version1, version2)
val comparison = Ord[Version].compare(version1, version2)
```

## Pre-Release Classifiers

The supported pre-release classifiers are:

- Milestone (`m`, `milestone`)
- Alpha (`alpha`, `a`)
- Beta (`beta`, `b`)
- Release Candidate (`rc`, `cr`)
- Snapshot (`snapshot`)
- Unclassified (`unclassified`)

Any non-supported classifier will be represented by `PreReleaseClassifier.Unclassified`.

### Ordering of Classifiers

The classifiers are ordered as follows:

1. Unclassified
2. Milestone
3. Alpha
4. Beta
5. Release Candidate
6. Snapshot

_________________________

## License

This project is licensed under the Apache License, Version 2.0. See
the [LICENSE](https://www.apache.org/licenses/LICENSE-2.0) file for details.
