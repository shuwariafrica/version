---
title: scala-yaml
---
## scala-yaml

Codecs for [scala-yaml](https://github.com/VirtusLab/scala-yaml).

```scala
libraryDependencies += "africa.shuwari" %%  /* or `%%%` */ "version-codecs-yaml" % "@VERSION@"
```

---

### Usage

```scala
import org.virtuslab.yaml.*
import version.*
import version.codecs.yaml.given

// Encode
val v = "1.2.3-alpha.1".toVersionUnsafe
val yaml = v.asYaml // 1.2.3-alpha.1

// Decode
val decoded = yaml.as[Version]
// Right(Version(1, 2, 3, Some(alpha.1), None))
```

### In Data Structures

```scala
import org.virtuslab.yaml.*

case class Package(name: String, version: Version) derives YamlCodec

val pkg = Package("my-lib", "1.0.0".toVersionUnsafe)
pkg.asYaml
// name: my-lib
// version: 1.0.0
```

### Provided Codecs

```scala
given YamlCodec[Version]
given YamlCodec[MajorVersion]
given YamlCodec[MinorVersion]
given YamlCodec[PatchNumber]
given YamlCodec[PreReleaseNumber]
given YamlCodec[PreReleaseClassifier]
given YamlCodec[PreRelease]
given YamlCodec[Metadata]
```

### YAML Examples

```yaml
# Simple version
version: 1.2.3

# Pre-release
version: 1.2.3-alpha.1

# With build metadata
version: 1.2.3+build.456

# Full form
version: 1.2.3-rc.1+sha.abc123
```

### Error Handling

```scala
"not-a-version".as[Version]
// Left(YamlError: Invalid version format: not-a-version)
```
