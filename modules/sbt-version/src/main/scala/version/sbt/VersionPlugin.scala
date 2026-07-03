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

import sbt.*
import sbt.Keys.{version as _, *}
import sbt.util.Logger as SbtLogger

import version.DevelopmentMetadata
import version.Formatter
import version.ResolvableScheme
import version.Version
import version.VersionResolver
import version.resolution.GitError
import version.resolution.ResolutionConfig
import version.resolution.ResolutionError
import version.resolution.ResolutionMode
import version.resolution.ResolutionResult
import version.resolution.VersionCliCore
import version.resolution.domain.CiMetadata
import version.resolution.environment.CiDetector
import version.resolution.logging.LogEntry
import version.resolution.logging.LogLevel
import version.resolution.logging.Logger as CoreLogger
import version.resolution.logging.Verbose
import version.resolution.openRepository
import version.sbt.VersionPluginImports.*
import version.semver.SemVer

/** sbt plugin for automatic version resolution from Git state.
  *
  * Provides the following keys:
  *   - `versionResolver`: bundled scheme + tag parser + formatter (default: SemVer)
  *   - `versionBranchOverride`: optional branch override for CI environments
  *   - `resolvedVersion`: resolved [[Version]] for the repository state
  *   - `versionTarget`: target release version the working tree is heading toward
  *   - `version` (sbt built-in): rendered version string for publishing
  *   - `isSnapshot` (sbt built-in): scheme-defined snapshot state
  *
  * @see
  *   [[VersionPluginImports$ VersionPluginImports]] for all available settings and types.
  */
object VersionPlugin extends AutoPlugin:

  override def requires: Plugins = plugins.IvyPlugin
  override def trigger: PluginTrigger = allRequirements

  val autoImport: VersionPluginImports.type = VersionPluginImports

  /** Every released version parsed from the repository's annotated version tags, as a `Def.Initialize[Set[Version]]`
    * to splice into a setting with `.value`. It lives on the plugin object rather than in [[autoImport]] because
    * evaluating it walks the Git tags; keeping it off the default import surface means only builds that ask for it pay
    * that cost. The plugin object is already in scope in a `build.sbt`, so deriving `mimaPreviousArtifacts` needs no
    * import:
    *
    * {{{
    * mimaPreviousArtifacts := VersionPlugin.versionHistory.value.collect {
    *   case v: SemVer if v.isFinal => organization.value %% moduleName.value % v.show
    * }
    * }}}
    *
    * Empty when the base directory is not a Git repository. Filter and order with the scheme's own API
    * (`isFinal`, `Ordering`).
    */
  val versionHistory: Def.Initialize[Set[Version]] = Def.setting {
    internal.history(
      versionResolver.value,
      (LocalRootProject / baseDirectory).value.getAbsolutePath,
      sLog.value
    )
  }

  // Private graph-node backing for the resolution result
  private val resolvedTyped: SettingKey[internal.VersionResult[? <: Version]] =
    settingKey[internal.VersionResult[? <: Version]]("(internal) typed resolution result")

  override def buildSettings: Seq[Setting[?]] =
    Seq(
      versionBranchOverride := sys.env.get("VERSION_BRANCH"),
      versionResolver := VersionResolver.withDefaults[SemVer],
      resolvedTyped := internal.resolve(
        versionResolver.value,
        versionBranchOverride.value,
        (LocalRootProject / baseDirectory).value.getAbsolutePath,
        sLog.value
      ),
      resolvedVersion := (
        resolvedTyped.value match
          case r: internal.VersionResult[v] => r.value: Version
      ),
      versionTarget := (
        resolvedTyped.value match
          case r: internal.VersionResult[v] => r.target: Version
      ),
      Keys.version := (
        resolvedTyped.value match
          case r: internal.VersionResult[v] => internal.render(r)
      ),
      isSnapshot := (
        resolvedTyped.value match
          case r: internal.VersionResult[v] =>
            given ResolvableScheme[v] = r.scheme
            r.value.isSnapshot
      )
    )

  override def projectSettings: Seq[Setting[?]] = Seq.empty

  /** Adapts sbt's [[SbtLogger]] to the resolution module's [[CoreLogger]] interface. */
  final private class SbtCoreLogger(underlying: SbtLogger) extends CoreLogger:
    override def log(entry: LogEntry): Unit =
      val prefix = entry.context.fold("")(ctx => s"[$ctx] ")
      entry.level match
        case LogLevel.Error   => underlying.error(prefix + entry.message)
        case LogLevel.Verbose => underlying.info(prefix + entry.message)

  private[sbt] object internal:

    final case class VersionResult[V <: Version](
      scheme: ResolvableScheme[V],
      formatter: Option[Formatter[V]],
      value: V,
      target: V
    )

    def render[V <: Version](r: VersionResult[V]): String =
      r.formatter.fold(r.value.show)(_.format(r.value))

    def detectCiMetadata(env: collection.Map[String, String]): Option[CiMetadata] =
      CiDetector.detect(env)

    def defaultVerbose(env: collection.Map[String, String]): Boolean =
      env.get("VERSION_VERBOSE").exists(_.toBooleanOption.getOrElse(true))

    def resolve(
      resolver: VersionResolver[? <: Version],
      branchOverride: Option[String],
      repoPath: String,
      sbtLog: SbtLogger
    ): VersionResult[? <: Version] = resolver match
      case r: VersionResolver[v] =>
        given ResolvableScheme[v] = r.scheme
        val env = sys.env
        val metadata = detectCiMetadata(env)
        val base = ResolutionConfig
          .default[v](repoPath)
          .copy(
            branchOverride = branchOverride,
            verbose = defaultVerbose(env),
            tagParser = r.tagParser
          )
        val cfg = base.mergeWith(metadata)
        val result = resolveResult(cfg, sbtLog, r.scheme)
        VersionResult(r.scheme, r.formatter, result.resolved, result.target)

    def history(
      resolver: VersionResolver[? <: Version],
      repoPath: String,
      sbtLog: SbtLogger
    ): Set[Version] = resolver match
      case r: VersionResolver[v] =>
        given ResolvableScheme[v] = r.scheme
        val cfg = ResolutionConfig.default[v](repoPath).copy(verbose = defaultVerbose(sys.env), tagParser = r.tagParser)
        val logger = new SbtCoreLogger(sbtLog)
        VersionCliCore.releaseHistory(cfg, openRepository, logger, Verbose(cfg.verbose)) match
          case Right(releases)                                                  => releases.map(_.version: Version).toSet
          case Left(ResolutionError.GitFailure(GitError.RepositoryNotFound(_))) => Set.empty
          case Left(err) => throw new MessageOnlyException(s"sbt-version: ${err.message}") // scalafix:ok

    private[sbt] def resolveResult[V <: Version](
      cfg: ResolutionConfig[V],
      sbtLog: SbtLogger,
      scheme: ResolvableScheme[V]
    ): ResolutionResult[V] =
      given ResolvableScheme[V] = scheme
      val logger = new SbtCoreLogger(sbtLog)
      sbtLog.info(s"sbt-version: resolving version from ${cfg.repoPath}")
      VersionCliCore.resolveAll(cfg, openRepository, logger, Verbose(cfg.verbose)) match
        case Left(ResolutionError.GitFailure(GitError.RepositoryNotFound(path))) =>
          val fallback = scheme.developmentVersion(
            scheme.initialVersion,
            DevelopmentMetadata(None, None, None, None, None, false)
          )
          sbtLog.info(s"sbt-version: Not a Git repository at $path, using fallback ${fallback.show}")
          ResolutionResult(fallback, scheme.initialVersion, ResolutionMode.Development, None, None)
        case Left(err) =>
          sbtLog.info(s"sbt-version: Resolution error: ${err.getClass.getName} - ${err.message}")
          throw new MessageOnlyException(s"sbt-version: ${err.message}") // scalafix:ok
        case Right(result) =>
          sbtLog.info(s"sbt-version: Resolved version: ${result.resolved.show}")
          result
  end internal

end VersionPlugin
