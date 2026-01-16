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

import version.Version
import version.cli.core.domain.*
import version.cli.core.git.GitProcess
import version.cli.core.logging.Logger
import version.cli.core.logging.NullLogger

/** Public API entry point for the version-cli-core library. */
object VersionCliCore:
  given CanEqual[VersionCliCore.type, VersionCliCore.type] = CanEqual.derived

  // --- Internal shared implementation ---
  private def resolveImpl(config: CliConfig)(using Logger, Boolean): Either[ResolutionError, Version] =
    summon[Logger].verbose(s"Initialising Git interface at ${config.repo}", "Core")
    val git = new GitProcess(config.repo)
    Resolver.resolve(config, git)

  // --- Public API Overloads (Scala) ---

  /** Resolve with implicit logger + verbosity (preferred in Scala usage). Scala name distinct to avoid overload
    * ambiguity.
    */
  @targetName("resolveImplicit")
  def resolveImplicit(using logger: Logger, verbose: Boolean)(config: CliConfig): Either[ResolutionError, Version] =
    resolveImpl(config)

  /** Resolve with explicit logger & verbosity (interop friendly). */
  @targetName("resolveExplicitLogger")
  def resolve(config: CliConfig, logger: Logger, verbose: Boolean): Either[ResolutionError, Version] =
    given Logger = logger
    given Boolean = verbose
    resolveImpl(config)

  /** Resolve with config only (NullLogger fallback). */
  @targetName("resolveConfigOnly")
  def resolve(config: CliConfig): Either[ResolutionError, Version] =
    given Logger = NullLogger
    given Boolean = config.verbose
    resolveImpl(config)

  /** Resolve with default configuration (NullLogger). */
  @targetName("resolveDefault")
  def resolve(): Either[ResolutionError, Version] =
    resolve(CliConfig())
end VersionCliCore
