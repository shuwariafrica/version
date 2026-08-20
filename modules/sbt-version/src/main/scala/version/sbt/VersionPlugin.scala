/****************************************************************************
 * Copyright 2023-2026 Shuwari Africa Ltd.                                  *
 *                                                                          *
 * Licensed under the Apache License, Version 2.0 (the "License");          *
 * you may not use this file except in compliance with the License.         *
 * You may obtain a copy of the License at                                  *
 *                                                                          *
 *     http://www.apache.org/licenses/LICENSE-2.0                           *
 *                                                                          *
 * Unless required by applicable law or agreed to in writing, software      *
 * distributed under the License is distributed on an "AS IS" BASIS,        *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. *
 * See the License for the specific language governing permissions and      *
 * limitations under the License.                                           *
 ****************************************************************************/
package version.sbt

import boilerplate.nullable.*
import sbt.*
import sbt.Keys.{version as _, *}
import sbt.util.Logger as SbtLogger

import version.DevelopmentMetadata
import version.Formatter
import version.ResolvableScheme
import version.VersionArithmetic
import version.VersionScheme
import version.Versioned
import version.resolution.GitError
import version.resolution.ResolutionConfig
import version.resolution.ResolutionError
import version.resolution.Resolver
import version.resolution.VersionResolver
import version.resolution.discoverRepository
import version.resolution.domain.CiMetadata
import version.resolution.environment.CiDetector
import version.resolution.logging.LogEntry
import version.resolution.logging.LogLevel
import version.resolution.logging.Logger as CoreLogger
import version.sbt.VersionPluginImports.*
import version.semver.SemVer

/** Sets a build's version from the state of the Git repository it lives in.
  *
  * The repository is discovered upwards from the build's root, so a build in a linked worktree or a subdirectory of a
  * monorepo resolves against the repository that actually contains it. Where no repository is found the plugin falls
  * back to the scheme's initial development version rather than failing the build.
  *
  * Sets `version` and `isSnapshot`, and adds `versionResolver`, `versionBranchOverride`, `resolvedVersion` and
  * `versionTarget`.
  *
  * @see
  *   [[VersionPluginImports$ VersionPluginImports]] for the settings and types this plugin auto-imports.
  */
object VersionPlugin extends AutoPlugin:

  override def requires: Plugins = plugins.IvyPlugin
  override def trigger: PluginTrigger = allRequirements

  val autoImport: VersionPluginImports.type = VersionPluginImports

  /** Every released version the repository's annotated version tags name, as a `Def.Initialize` to splice into a
    * setting with `.value`. It lives on the plugin object rather than in [[autoImport]] because evaluating it walks
    * the Git tags; keeping it off the default import surface means only builds that ask for it pay that cost. The
    * plugin object is already in scope in a `build.sbt`, so deriving `mimaPreviousArtifacts` needs no import:
    *
    * {{{
    * mimaPreviousArtifacts := VersionPlugin.versionHistory.value.collect {
    *   case v if v.stable => organization.value %% moduleName.value % v.show
    * }
    * }}}
    *
    * Empty where no repository contains the build.
    */
  val versionHistory: Def.Initialize[Set[Versioned]] = Def.setting {
    internal.history(
      versionResolver.value,
      (LocalRootProject / baseDirectory).value.getAbsolutePath,
      sLog.value
    )
  }

  // One resolution per build, shared by every key that needs part of it.
  private val resolvedTyped: SettingKey[internal.VersionResult[?]] =
    settingKey[internal.VersionResult[?]]("(internal) typed resolution result")

  override def buildSettings: Seq[Setting[?]] =
    Seq(
      versionBranchOverride := sys.env.get("VERSION_BRANCH"),
      versionResolver := internal.defaultResolver,
      resolvedTyped := internal.resolve(
        versionResolver.value,
        versionBranchOverride.value,
        (LocalRootProject / baseDirectory).value.getAbsolutePath,
        sLog.value
      ),
      resolvedVersion := (
        resolvedTyped.value match
          case r: internal.VersionResult[v] => Versioned.of(r.value, r.scheme)
      ),
      versionTarget := (
        resolvedTyped.value match
          case r: internal.VersionResult[v] => Versioned.of(r.target, r.scheme)
      ),
      Keys.version := (
        resolvedTyped.value match
          case r: internal.VersionResult[v] => internal.render(r)
      ),
      isSnapshot := (
        resolvedTyped.value match
          case r: internal.VersionResult[v] => r.workflow.snapshot(r.value)
      )
    )

  override def projectSettings: Seq[Setting[?]] = Seq.empty

  final private class SbtCoreLogger(underlying: SbtLogger, val verboseEnabled: Boolean) extends CoreLogger:
    override def log(entry: LogEntry): Unit =
      val prefix = entry.context.fold("")(ctx => s"[$ctx] ")
      entry.level match
        case LogLevel.Error   => underlying.error(prefix + entry.message)
        case LogLevel.Verbose => underlying.info(prefix + entry.message)

  private[sbt] object internal:

    // A published version must stay stable for the life of its snapshot line: a dependent build pins
    // `1.0.1-SNAPSHOT` and expects the next publish to replace it, which a metadata-unique string never would.
    // Telling individual snapshots apart belongs to the repository, and naming a pre-release is what classifiers are
    // for, so the published string carries no build metadata and the canonical form stays on `resolvedVersion`.
    val defaultResolver: VersionResolver[SemVer] =
      VersionResolver.withDefaults[SemVer].withFormatter(SemVer.Formatter.Standard)

    final case class VersionResult[V](
      scheme: VersionScheme[V],
      workflow: ResolvableScheme[V],
      formatter: Option[Formatter[V]],
      value: V,
      target: V
    )

    def render[V](r: VersionResult[V]): String =
      r.formatter.fold(r.scheme.show(r.value))(_.format(r.value))

    def detectCiMetadata(env: collection.Map[String, String]): Option[CiMetadata] =
      CiDetector.detect(env)

    def defaultVerbose(env: collection.Map[String, String]): Boolean =
      env.get("VERSION_VERBOSE").exists(_.toBooleanOption.getOrElse(true))

    def resolve(
      resolver: VersionResolver[?],
      branchOverride: Option[String],
      repoPath: String,
      sbtLog: SbtLogger
    ): VersionResult[?] = resolver match
      case r: VersionResolver[v] =>
        given VersionScheme[v] = r.scheme
        given VersionArithmetic[v] = r.arithmetic
        given ResolvableScheme[v] = r.workflow
        val env = sys.env
        val config = ResolutionConfig
          .default[v](repoPath)
          .copy(branchOverride = branchOverride, tagParser = r.tagParser)
          .mergeWith(detectCiMetadata(env))
        sbtLog.info(s"sbt-version: resolving version from $repoPath")
        Resolver.resolveAll(config, path => discoverRepository(path), new SbtCoreLogger(sbtLog, defaultVerbose(env))) match
          case Right(result) =>
            sbtLog.info(s"sbt-version: Resolved version: ${r.scheme.show(result.resolved)} from ${result.repository}")
            VersionResult(r.scheme, r.workflow, r.formatter, result.resolved, result.target)
          // No repository is a build state, not a resolution outcome: the plugin's own fallback is reported as such.
          case Left(ResolutionError.GitFailure(GitError.RepositoryNotFound(path))) =>
            val fallback = r.workflow.developmentVersion(
              r.workflow.initialVersion,
              DevelopmentMetadata(None, None, None, None, None, false)
            )
            sbtLog.info(s"sbt-version: Not a Git repository at $path, using fallback ${r.scheme.show(fallback)}")
            VersionResult(r.scheme, r.workflow, r.formatter, fallback, r.workflow.initialVersion)
          case Left(err) =>
            sbtLog.info(s"sbt-version: Resolution error: ${err.getClass.getName} - ${err.getMessage.unsafe}")
            throw new MessageOnlyException(s"sbt-version: ${err.getMessage.unsafe}") // scalafix:ok

    def history(
      resolver: VersionResolver[?],
      repoPath: String,
      sbtLog: SbtLogger
    ): Set[Versioned] = resolver match
      case r: VersionResolver[v] =>
        given VersionScheme[v] = r.scheme
        val config = ResolutionConfig.default[v](repoPath).copy(tagParser = r.tagParser)
        val logger = new SbtCoreLogger(sbtLog, defaultVerbose(sys.env))
        Resolver.releaseHistory(config, path => discoverRepository(path), logger) match
          case Right(releases) => releases.map(rel => Versioned.of(rel.version, r.scheme)).toSet
          case Left(ResolutionError.GitFailure(GitError.RepositoryNotFound(_))) => Set.empty
          case Left(err) => throw new MessageOnlyException(s"sbt-version: ${err.getMessage.unsafe}") // scalafix:ok
  end internal

end VersionPlugin
