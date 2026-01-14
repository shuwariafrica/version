---
title: Contributing
---
# Contributing

Contributions to `version` are welcome. This guide covers the project conventions and workflow.

## Development Environment

### Prerequisites

- **JDK**: @JDK_VERSION@ or later
- **Scala**: @SCALA3_VERSION@
- **Git**: For version resolution tests
- **NodeJS**: For JS Platform tests
- **Clang / LLVM**: For Native Platform tests

### Project Setup

```bash
git clone https://github.com/shuwariafrica/version.git
cd version
sbt compile
```

### Running Tests

```bash
sbt test                    # All tests
sbt version-jvm/test        # All JVM Platform Modules
sbt version-native/test     # All Native Platform Modules
sbt version-js/test         # All JS Platform Modules
```

Tests run on JVM, Scala.js, and Scala Native platforms.

## Code Style

### Formatting and Linting

```bash
sbt format      # Apply scalafmt + scalafix + license headers
sbt check       # Verify formatting compliance
```

## Architecture

### Module Structure

| Module | Scope | Dependencies |
|--------|-------|--------------|
| `version` | Core SemVer model | boilerplate |
| `version-cli-core` | Resolution engine | version, os-lib |
| `version-cli` | CLI application | version-cli-core, scopt |
| `sbt-version` | sbt plugin | version-cli-core |
| `version-codecs-*` | Serialisation | version, codec library |
| `version-zio-prelude` | Type classes | version, zio-prelude |
| `version-testkit` | Test utilities | version, munit |

### Version Resolution

The resolution engine in `version-cli-core` follows the [Technical Specification](specification.md). Key components:

- **`KeywordParser`** — extracts version directives from commit messages
- **`CommitResolver`** — processes commit history to compute target version
- **`Resolver`** — orchestrates the full resolution workflow

## Pull Request Guidelines

1. **One concern per PR** — focused changes are easier to review
2. **Include tests** — coverage for new functionality and regressions
3. **Follow existing patterns** — consistency aids maintenance
4. **Update documentation** — keep README and docs in sync with changes
5. **Run static checks** — `sbt check` must pass

## Specification Changes

The [Technical Specification](specification.md) is normative. If code contradicts the specification, the specification wins. Changes to version resolution behaviour require:

1. Specification update with rationale
2. Implementation changes
3. Comprehensive test coverage
4. Documentation updates

## Licence

By contributing, you agree that your contributions will be licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
