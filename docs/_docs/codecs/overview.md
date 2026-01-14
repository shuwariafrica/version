---
title: Serialisation
---

# Serialisation

Codec modules provide JSON and YAML serialisation for version types.

## Available Codecs

| Module                        | Library        |
|-------------------------------|----------------|
| [jsoniter-scala](jsoniter.md) | jsoniter-scala |
| [ZIO JSON](zio-json.md)       | zio-json       |
| [scala-yaml](yaml.md)         | scala-yaml     |

## Installation

Choose your preferred library:

```scala
// jsoniter-scala
libraryDependencies += "africa.shuwari" %% /* or `%%%` */ "version-codecs-jsoniter" % "@VERSION@"

// ZIO JSON
libraryDependencies += "africa.shuwari" %% /* or `%%%` */ "version-codecs-zio" % "@VERSION@"

// scala-yaml
libraryDependencies += "africa.shuwari" %% /* or `%%%` */ "version-codecs-yaml" % "@VERSION@"
```

## Usage Pattern

All modules follow the same pattern:

```scala
// Import the codec package

import version.codecs.jsoniter.given // or zio, yaml

// Codecs are automatically available
```

## Encoded Types

All modules encode these types:

| Type                   | JSON Representation       |
|------------------------|---------------------------|
| `Version`              | String: `"1.2.3-alpha.1"` |
| `MajorVersion`         | Number: `1`               |
| `MinorVersion`         | Number: `2`               |
| `PatchNumber`          | Number: `3`               |
| `PreReleaseNumber`     | Number: `1`               |
| `PreReleaseClassifier` | String: `"alpha"`         |
| `PreRelease`           | String: `"alpha.1"`       |
| `BuildMetadata`        | Array: `["build", "456"]` |

## Platform Support

All codec modules are cross-platform:

| Platform     | Status |
|--------------|:------:|
| JVM          |   ✅    |
| Scala.js     |   ✅    |
| Scala Native |   ✅    |
