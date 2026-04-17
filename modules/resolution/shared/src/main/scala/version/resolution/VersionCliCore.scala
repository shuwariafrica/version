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
package version.resolution

import scala.annotation.targetName

import version.ResolvableScheme
import version.resolution.logging.Logger
import version.resolution.logging.Verbose

/** Public API entry point for version resolution.
  *
  * Scheme-generic: parameterised by `[V: ResolvableScheme]`.
  */
object VersionCliCore:

  /** Resolves the version using contextual parameters and an explicit repository open function. */
  @targetName("resolveContextual")
  def resolve[V](
    config: ResolutionConfig[V],
    open: String => Either[GitError, GitRepository]
  )(using
    ResolvableScheme[V],
    Logger,
    Verbose
  ): Either[ResolutionError, V] =
    Resolver.resolve(config, open)

  /** Resolves the version with all parameters explicit. */
  @targetName("resolveExplicitAll")
  def resolve[V](
    config: ResolutionConfig[V],
    open: String => Either[GitError, GitRepository],
    logger: Logger,
    verbose: Verbose
  )(using ResolvableScheme[V]): Either[ResolutionError, V] =
    given Logger = logger
    given Verbose = verbose
    Resolver.resolve(config, open)
end VersionCliCore
