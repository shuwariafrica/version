/****************************************************************
 * Copyright © Shuwari Africa Ltd.                              *
 *                                                              *
 * This file is licensed to you under the terms of the Apache   *
 * License Version 2.0 (the "License"); you may not use this    *
 * file except in compliance with the License. You may obtain   *
 * a copy of the License at:                                    *
 *                                                              *
 *     https://www.apache.org/licenses/LICENSE-2.0              *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, *
 * either express or implied. See the License for the specific  *
 * language governing permissions and limitations under the     *
 * License.                                                     *
 ****************************************************************/
package version.sbt

import _root_.version.cli.core.VersionCliCore
import _root_.version.cli.core.domain.CiMetadata
import _root_.version.cli.core.domain.CliConfig
import _root_.version.cli.core.environment.CiDetector
import _root_.version.cli.core.logging.LogEntry
import _root_.version.cli.core.logging.LogLevel
import _root_.version.cli.core.logging.Logger as CoreLogger
import sbt.*
import sbt.Keys.{version as _, *}
import sbt.util.Logger as SbtLogger

import version.sbt.VersionPluginImports.*

/** sbt plugin that wires the `version-cli-core` resolver into the build lifecycle.
  *
  * Provides three keys:
  *   - `resolvedVersion`: The full [[Version]] object (with 40-char SHA for maximum flexibility)
  *   - `version`: Standard SemVer string (without build metadata) for publishing
  *   - `isSnapshot`: `true` if the resolved version is a snapshot
  */
object VersionPlugin extends AutoPlugin:

  override def requires: Plugins = plugins.IvyPlugin
  override def trigger: PluginTrigger = allRequirements

  val autoImport: VersionPluginImports.type = VersionPluginImports

  override def buildSettings: Seq[Setting[?]] =
    Seq(
      versionBranchOverride := sys.env.get("VERSION_BRANCH"),
      resolvedVersion :=
        {
          val log = sLog.value
          val repo = os.Path((LocalRootProject / baseDirectory).value.toPath)
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
          internal.resolveVersion(cfg, log)
        },
      Keys.version := Version.Show.Standard.show(resolvedVersion.value),
      isSnapshot := resolvedVersion.value.isSnapshot
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

    def resolveVersion(cfg: CliConfig, sbtLog: SbtLogger): Version =
      val logger = new SbtCoreLogger(sbtLog)
      VersionCliCore.resolve(cfg, logger, cfg.verbose) match
        case scala.util.Left(err)  => throw new MessageOnlyException(s"version-sbt: ${err.message}") // scalafix:ok
        case scala.util.Right(ver) => ver

end VersionPlugin
