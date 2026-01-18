---
title: Serialisation
---

## Serialisation

Codec modules provide JSON and YAML serialisation for version types.

## Available Codecs

| Module                        | Library        |
|-------------------------------|----------------|
| [jsoniter-scala](jsoniter.md) | jsoniter-scala |
| [ZIO JSON](zio-json.md)       | zio-json       |
| [scala-yaml](yaml.md)         | scala-yaml     |

### Dependencies

Choose your preferred library:

```scala
// jsoniter-scala
libraryDependencies += "africa.shuwari" %% /* or `%%%` */ "version-codecs-jsoniter" % "@VERSION@"

// ZIO JSON
libraryDependencies += "africa.shuwari" %% /* or `%%%` */ "version-codecs-zio" % "@VERSION@"

// scala-yaml
libraryDependencies += "africa.shuwari" %% /* or `%%%` */ "version-codecs-yaml" % "@VERSION@"
```

---

### Usage Pattern

All modules follow the same pattern:

```scala
// Import the codec package

import version.codecs.jsoniter.given // or zio, yaml

// Codecs are automatically available
```

### Encoded Types

All modules encode these types:

| Type                             | JSON Representation       |
|----------------------------------|---------------------------|
| [[version.Version]]              | String: `"1.2.3-alpha.1"` |
| [[version.MajorVersion]]         | Number: `1`               |
| [[version.MinorVersion]]         | Number: `2`               |
| [[version.PatchNumber]]          | Number: `3`               |
| [[version.PreReleaseNumber]]     | Number: `1`               |
| [[version.PreReleaseClassifier]] | String: `"alpha"`         |
| [[version.PreRelease]]           | String: `"alpha.1"`       |
| [[version.Metadata]]             | Array: `["build", "456"]` |

---

### Platform Support

All codec modules are cross-platform:

| Platform     | Status |
|--------------|:------:|
| JVM          |   ✅    |
| Scala.js     |   ✅    |
| Scala Native |   ✅    |
