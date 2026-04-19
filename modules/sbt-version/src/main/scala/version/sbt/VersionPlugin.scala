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

import version.resolution.GitError
import version.resolution.ResolutionConfig
import version.resolution.ResolutionError
import version.resolution.VersionCliCore
import version.resolution.domain.CiMetadata
import version.resolution.environment.CiDetector
import version.resolution.logging.LogEntry
import version.resolution.logging.LogLevel
import version.resolution.logging.Logger as CoreLogger
import version.resolution.logging.Verbose
import version.resolution.openRepository
import version.sbt.VersionPluginImports.*
import version.semver.*

/** sbt plugin for automatic semantic version resolution from Git state.
  *
  * Provides the following keys:
  *   - `resolvedVersion`: The full [[version.semver.SemVer SemVer]] object with 40-character SHA
  *   - `version`: Standard SemVer string for publishing (excludes build metadata by default)
  *   - `isSnapshot`: `true` if the resolved version is a snapshot
  *   - `versionFormatter`: Optional [[version.semver.SemVer.Formatter SemVer.Formatter]] for rendering
  *   - `versionBranchOverride`: Optional branch name override for CI environments
  *
  * @see [[VersionPluginImports$ VersionPluginImports]] for all available settings and types.
  */
object VersionPlugin extends AutoPlugin:

  override def requires: Plugins = plugins.IvyPlugin
  override def trigger: PluginTrigger = allRequirements

  val autoImport: VersionPluginImports.type = VersionPluginImports

  override def buildSettings: Seq[Setting[?]] =
    Seq(
      versionBranchOverride := sys.env.get("VERSION_BRANCH"),
      versionTagParser := ResolutionConfig.default[SemVer]("").tagParser,
      versionFormatter := None,
      resolvedVersion :=
        {
          val log = sLog.value
          val tagParser = versionTagParser.value
          val repo = (LocalRootProject / baseDirectory).value.getAbsolutePath
          log.debug(s"version-sbt: repo path = $repo")
          val env = sys.env
          val metadata = internal.detectCiMetadata(env)
          val base = ResolutionConfig
            .default[SemVer](repo)
            .copy(
              branchOverride = versionBranchOverride.value,
              shaLength = 40,
              verbose = internal.defaultVerbose(env),
              tagParser = tagParser
            )
          val cfg = base.mergeWith(metadata)
          internal.resolveVersion(cfg, log)
        },
      Keys.version := {
        val v = resolvedVersion.value
        versionFormatter.value match
          case Some(f) => f.format(v)
          case None    => v.show
      },
      isSnapshot := resolvedVersion.value.snapshot
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

    def detectCiMetadata(env: collection.Map[String, String]): Option[CiMetadata] =
      CiDetector.detect(env)

    def defaultVerbose(env: collection.Map[String, String]): Boolean =
      env.get("VERSION_VERBOSE").exists(_.toBooleanOption.getOrElse(true))

    /** Fallback version used when not in a Git repository.
      *
      * Per specification, `0.1.0` is the target when no tags exist. For non-repository contexts, we provide
      * `0.1.0-SNAPSHOT` to indicate development state.
      */
    val fallbackVersion: SemVer = SemVer.parseUnsafe("0.1.0-SNAPSHOT")

    def resolveVersion(
      cfg: ResolutionConfig[SemVer],
      sbtLog: SbtLogger
    ): SemVer =
      val logger = new SbtCoreLogger(sbtLog)
      sbtLog.info(s"version-sbt: resolving version from ${cfg.repoPath}")
      VersionCliCore.resolve(cfg, openRepository, logger, Verbose(cfg.verbose)) match
        case scala.util.Left(ResolutionError.GitFailure(GitError.RepositoryNotFound(path))) =>
          sbtLog.info(s"version-sbt: Not a Git repository at $path, using fallback version ${fallbackVersion.show}")
          fallbackVersion
        case scala.util.Left(err) =>
          sbtLog.info(s"version-sbt: Resolution error: ${err.getClass.getName} - ${err.message}")
          throw new MessageOnlyException(s"version-sbt: ${err.message}") // scalafix:ok
        case scala.util.Right(ver) =>
          sbtLog.info(s"version-sbt: Resolved version: ${ver.show}")
          ver
  end internal

end VersionPlugin
