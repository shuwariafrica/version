/****************************************************************************
 * Copyright 2023 Shuwari Africa Ltd.                                       *
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

import version.cli.core.ResolutionError
import version.cli.core.VersionCliCore
import version.cli.core.domain.CiMetadata
import version.cli.core.domain.CliConfig
import version.cli.core.environment.CiDetector
import version.cli.core.logging.LogEntry
import version.cli.core.logging.LogLevel
import version.cli.core.logging.Logger as CoreLogger
import version.sbt.VersionPluginImports.*
import version.{*, given}

/** sbt plugin for automatic semantic version resolution from Git state.
  *
  * Provides the following keys:
  *   - `resolvedVersion`: The full [[version.Version Version]] object with 40-character SHA
  *   - `version`: Standard SemVer string for publishing (excludes build metadata by default)
  *   - `isSnapshot`: `true` if the resolved version is a snapshot
  *   - `versionRead`: Customisable [[version.Version.Read Version.Read]] instance for parsing version tags
  *   - `versionShow`: Optional [[version.Version.Show Version.Show]] instance for rendering
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
      versionRead := Version.Read.ReadString,
      versionResolver := PreRelease.Resolver.given_Resolver,
      versionShow := None,
      resolvedVersion :=
        {
          val log = sLog.value
          val reader = versionRead.value
          val resolver = versionResolver.value
          val repo = os.Path((LocalRootProject / baseDirectory).value.toPath)
          log.debug(s"version-sbt: repo path = $repo, .git exists = ${os.exists(repo / ".git")}")
          val env = sys.env
          val metadata = internal.detectCiMetadata(env)
          val base =
            CliConfig(
              repo = repo,
              basisCommit = "HEAD",
              prNumber = None,
              branchOverride = versionBranchOverride.value,
              shaLength = 40,
              verbose = internal.defaultVerbose(env)
            )
          val cfg = CliConfig.mergeWithCiMetadata(base, metadata)
          internal.resolveVersion(cfg, log, reader, resolver)
        },
      Keys.version := {
        val v = resolvedVersion.value
        versionShow.value match
          case Some(s) => s.show(v)
          case None    => Version.Show.Standard.show(v)
      },
      isSnapshot := resolvedVersion.value.snapshot
    )

  override def projectSettings: Seq[Setting[?]] = Seq.empty

  /** Adapts sbt's [[SbtLogger]] to the `version-cli-core` [[CoreLogger]] interface. */
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
    val fallbackVersion: Version = "0.1.0-SNAPSHOT".toVersionUnsafe

    def resolveVersion(
      cfg: CliConfig,
      sbtLog: SbtLogger,
      reader: Version.Read[String],
      resolver: PreRelease.Resolver
    ): Version =
      val logger = new SbtCoreLogger(sbtLog)
      sbtLog.info(s"version-sbt: resolving version from ${cfg.repo}")
      VersionCliCore.resolve(cfg, logger, cfg.verbose, reader, resolver) match
        case scala.util.Left(ResolutionError.NotAGitRepository(path)) =>
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
