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
package version.cli.core

import scala.annotation.targetName

import version.PreRelease
import version.Version
import version.cli.core.domain.*
import version.cli.core.git.GitProcess
import version.cli.core.logging.Logger
import version.cli.core.logging.NullLogger

/** Public API entry point for version resolution.
  *
  * Provides overloaded `resolve` methods for deriving semantic versions from Git repository state. All methods return
  * `Either[ResolutionError, Version]`.
  *
  * Library consumers should use contextual parameters:
  * {{{
  * import version.{given, *}
  * import version.cli.core.VersionCliCore
  *
  * val result = VersionCliCore.resolve(config)
  * }}}
  *
  * Application code may use explicit parameters for full control:
  * {{{
  * VersionCliCore.resolve(config, logger, verbose, Version.Read.ReadString, summon[PreRelease.Resolver])
  * }}}
  *
  * @see [[version.cli.core.domain.CliConfig]] for configuration options.
  */
object VersionCliCore:
  given CanEqual[VersionCliCore.type, VersionCliCore.type] = CanEqual.derived

  // Internal shared implementation
  private def resolveImpl(
    config: CliConfig
  )(using Logger, Boolean, Version.Read[String], PreRelease.Resolver): Either[ResolutionError, Version] =
    summon[Logger].verbose(s"Initialising Git interface at ${config.repo}", "Core")
    val git = new GitProcess(config.repo)
    Resolver.resolve(config, git)

  // --- Public API ---

  /** Resolves the version using contextual parameters.
    *
    * Preferred for Scala library usage where `Logger`, verbosity, `Read[String]`, and `Resolver` are in scope.
    */
  @targetName("resolveImplicit")
  def resolveImplicit(using
    Logger,
    Boolean,
    Version.Read[String],
    PreRelease.Resolver
  )(config: CliConfig): Either[ResolutionError, Version] =
    resolveImpl(config)

  /** Resolves the version with all parameters explicit. */
  @targetName("resolveExplicitAll")
  def resolve(
    config: CliConfig,
    logger: Logger,
    verbose: Boolean,
    reader: Version.Read[String],
    resolver: PreRelease.Resolver
  ): Either[ResolutionError, Version] =
    given Logger = logger
    given Boolean = verbose
    given Version.Read[String] = reader
    given PreRelease.Resolver = resolver
    resolveImpl(config)

  /** Resolves the version with explicit logger and verbosity, using contextual reader and resolver. */
  @targetName("resolveExplicitLogger")
  def resolve(
    config: CliConfig,
    logger: Logger,
    verbose: Boolean
  )(using Version.Read[String], PreRelease.Resolver): Either[ResolutionError, Version] =
    given Logger = logger
    given Boolean = verbose
    resolveImpl(config)

  /** Resolves the version with configuration only, using `NullLogger` and contextual reader/resolver. */
  @targetName("resolveConfigOnly")
  def resolve(config: CliConfig)(using Version.Read[String], PreRelease.Resolver): Either[ResolutionError, Version] =
    given Logger = NullLogger
    given Boolean = config.verbose
    resolveImpl(config)

  /** Resolves the version with default configuration, using `NullLogger` and contextual reader/resolver. */
  @targetName("resolveDefault")
  def resolve()(using Version.Read[String], PreRelease.Resolver): Either[ResolutionError, Version] =
    resolve(CliConfig())
end VersionCliCore
