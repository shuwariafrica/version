---
title: Effect Integration
---

# Effect Integration

Version derivation as a described effect, for an application already built on cats-effect.

```scala
libraryDependencies += "africa.shuwari" %%% "version-eff" % "@VERSION@"
```

Available on JVM and Scala Native. This is the only module that brings a runtime with it: the core, the resolution
engine, the sbt plugin and the command-line tool stay direct-style, so the cost lands on the consumers who ask for it
and on nobody else.

On Scala Native, link with thread support - the cats-effect runtime builds its blocking pool from a cached thread
pool, and a binary linked without threads fails at link time rather than at run time:

```scala
nativeConfig ~= (_.withMultithreading(true))
```

Everything here is expressed with [Eff](https://github.com/shuwariafrica/boilerplate), the typed-error effect the
`africa.shuwari` libraries share. Resolution's own failures ride its typed channel natively, so a
`ResolutionError` is a value you observe or recover from rather than a throwable you catch.

## Resolving

```scala
import boilerplate.effect.Eff
import version.eff.Resolver
import version.resolution.{ResolutionConfig, ResolutionError, openRepository}
import version.semver.SemVer

val config = ResolutionConfig.default[SemVer](".")

val version: Eff[ResolutionError, SemVer] = Resolver.resolve(config, openRepository)
```

`resolve`, `resolveAll` and `releaseHistory` answer exactly as the direct engine does - the resolved version, the full
result with the target and how it was reached, or every release the repository carries. Each runs on the blocking
pool, because the work underneath is filesystem and Git.

Each comes in two shapes. Given the function that opens a repository, the operation opens the path `config` names and
closes it again - on success, on a typed failure, and on cancellation alike:

```scala
Resolver.resolveAll(config, openRepository)   // the path config names
Resolver.resolveAll(config, discoverRepository) // the nearest repository at or above it
```

Given a repository you hold, it reads and leaves the lifecycle to you:

```scala
Repository.open(".").use(repo => Resolver.resolveAll(config, repo))
```

## Holding a repository open

`Repository` yields a repository as a scoped resource, for an application that wants one repository across several
reads or wants it composed into a larger resource graph.

```scala
import version.eff.Repository

val releases = Repository
  .discover(".")
  .use(repo => Resolver.releaseHistory(config, repo))
```

Acquisition is the only step that can fail, and it fails with a `GitError`; a repository has nothing to report when
it closes. Where you would rather have one error type than two, the operations taking an opening function lift the
acquisition failure into `ResolutionError.GitFailure` for you.

## The typed channel

```scala
// Observe the failure as a value
val observed: Eff[Nothing, Either[ResolutionError, SemVer]] =
  Resolver.resolve(config, openRepository).either

// Recover from it
val recovered: Eff[Nothing, SemVer] =
  Resolver.resolve(config, openRepository).catchAll(_ => Eff.succeed(SemVer.parseUnsafe("0.0.0")))

// Or absolve the channel and let the error raise at the end of the world
val raising: cats.effect.IO[SemVer] = Resolver.resolve(config, openRepository).absolve
```

Retrying is yours to compose, and nothing is retried for you: a policy that suits a repository on a network
filesystem is not the one that suits a checkout on local disk.

```scala
import boilerplate.effect.RetryPolicy
import scala.concurrent.duration.*

Eff.retry(Resolver.resolve(config, openRepository), RetryPolicy.exponential(50.millis).withMaxAttempts(3))
```

A retry re-runs the whole operation, so the repository is re-opened and closed once per attempt.

## See Also

- [Automatic Versioning](../versioning/overview.md) - what derivation computes, and from what
- [Derivation Specification](../versioning/specification.md) - the normative algorithm
