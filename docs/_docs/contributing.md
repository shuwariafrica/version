---
title: Contributing
---

Contributions are welcome. This guide covers project conventions and workflow.

---

## Development Environment

### Prerequisites

- **JDK**: @JDK_VERSION@ or later
- **Clang / LLVM**: For Scala Native tests
- **cmake**: For building the vendored libgit2 (Native backend)

### Running Tests

```bash
sbt test                    # All tests
sbt version-jvm/test        # JVM modules
sbt version-native/test     # Native modules
```

### Formatting

```bash
sbt format      # Apply scalafmt + scalafix + license headers
sbt check       # Verify formatting compliance
```

---

## Architecture

### Module Structure

| Module | Scope | Dependencies |
|--------|-------|-------------|
| `version` | Version model | boilerplate |
| `version-resolution` | Version derivation | version, JGit (JVM), libgit2 (Native) |
| `version-cli` | CLI application | version-resolution, scopt |
| `sbt-version` | sbt plugin | version-resolution |
| `version-testkit` | Test utilities | (no external dependencies) |

### Version Resolution

The resolution engine in `version-resolution` follows the [Specification](versioning/specification.md). Key components:

- [[version.resolution.parsing.KeywordParser KeywordParser]] - extracts directives from commit messages
- [[version.resolution.TargetVersionCalculator TargetVersionCalculator]] - computes target version from keywords
- [[version.resolution.Resolver Resolver]] - orchestrates the full resolution workflow

Per-platform Git backends:

- **JVM**: `JvmGitRepository` wrapping JGit
- **Native**: `NativeGitRepository` wrapping libgit2 (statically linked, built via cmake)

---

## Pull Request Guidelines

1. **One concern per PR** - focused changes are easier to review
2. **Include tests** - coverage for new functionality and regressions
3. **Follow existing patterns** - consistency aids maintenance
4. **Update documentation** - keep docs in sync with changes
5. **Run static checks** - `sbt check` must pass

## Specification Changes

The [Specification](versioning/specification.md) is normative. If code contradicts the specification, the specification wins. Changes to version resolution behaviour require:

1. Specification update with rationale
2. Implementation changes
3. Test coverage
4. Documentation updates

---

## Licence

By contributing, you agree that your contributions will be licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
