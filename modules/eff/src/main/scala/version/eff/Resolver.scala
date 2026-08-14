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
package version.eff

import boilerplate.effect.Eff
import boilerplate.effect.EffResource

import version.ResolvableScheme
import version.VersionArithmetic
import version.VersionScheme
import version.resolution.GitError
import version.resolution.GitRepository
import version.resolution.ResolutionConfig
import version.resolution.ResolutionError
import version.resolution.ResolutionResult
import version.resolution.Resolver as Engine
import version.resolution.domain.Release
import version.resolution.logging.Logger

/** Answers what version a repository is at, as a described effect rather than as a value computed on the calling
  * thread.
  *
  * Each operation comes in two shapes: one reading a repository the caller holds and never closes, and one taking the
  * function that opens the path `config` names, whose repository is closed again on every path. Both suspend the
  * engine's work on the blocking pool, and both answer on the typed channel - a
  * [[version.resolution.ResolutionError ResolutionError]] is observed with `either`, recovered with `catchAll`, and
  * raised only where a caller absolves the channel.
  */
object Resolver:

  /** Resolves the version `repo` is at, together with the target it is working towards and how it was arrived at. */
  def resolveAll[V](config: ResolutionConfig[V], repo: GitRepository)(using
    VersionScheme[V],
    VersionArithmetic[V],
    ResolvableScheme[V],
    Logger
  ): Eff[ResolutionError, ResolutionResult[V]] =
    Eff.blocking(Engine.resolveAll(config, repo))

  /** Resolves the version of the repository `open` yields for the path `config` names. */
  def resolveAll[V](config: ResolutionConfig[V], open: String => Either[GitError, GitRepository])(using
    VersionScheme[V],
    VersionArithmetic[V],
    ResolvableScheme[V],
    Logger
  ): Eff[ResolutionError, ResolutionResult[V]] =
    scoped(config, open)(resolveAll(config, _))

  /** The resolved version alone, for a caller with no use for the rest of [[resolveAll]]. */
  def resolve[V](config: ResolutionConfig[V], repo: GitRepository)(using
    VersionScheme[V],
    VersionArithmetic[V],
    ResolvableScheme[V],
    Logger
  ): Eff[ResolutionError, V] =
    Eff.blocking(Engine.resolve(config, repo))

  /** The resolved version of the repository `open` yields for the path `config` names. */
  def resolve[V](config: ResolutionConfig[V], open: String => Either[GitError, GitRepository])(using
    VersionScheme[V],
    VersionArithmetic[V],
    ResolvableScheme[V],
    Logger
  ): Eff[ResolutionError, V] =
    scoped(config, open)(resolve(config, _))

  /** Every release `repo` carries, ordered ascending by the scheme's precedence. */
  def releaseHistory[V](config: ResolutionConfig[V], repo: GitRepository)(using
    VersionScheme[V],
    Logger
  ): Eff[ResolutionError, List[Release[V]]] =
    Eff.blocking(Engine.releaseHistory(config, repo))

  /** Every release the repository `open` yields for the path `config` names carries. */
  def releaseHistory[V](config: ResolutionConfig[V], open: String => Either[GitError, GitRepository])(using
    VersionScheme[V],
    Logger
  ): Eff[ResolutionError, List[Release[V]]] =
    scoped(config, open)(releaseHistory(config, _))

  // The acquisition failure is lifted into the channel the read itself fails into, so that opening and resolving are
  // one error type rather than the join of two.
  private def scoped[V, A](
    config: ResolutionConfig[V],
    open: String => Either[GitError, GitRepository]
  )(read: GitRepository => Eff[ResolutionError, A]): Eff[ResolutionError, A] =
    EffResource
      .make(Eff.blocking(open(config.repoPath).left.map(ResolutionError.GitFailure.apply)))(Repository.release)
      .use(read)

end Resolver
