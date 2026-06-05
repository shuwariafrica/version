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
package version.resolution

import version.Version
import version.resolution.domain.RawCommit
import version.resolution.domain.Release

/** Distinguishes how a resolved version was arrived at.
  *
  * Instances are compared via [[ResolutionMode$ ResolutionMode]].
  */
enum ResolutionMode:

  /** The basis commit carries a valid annotated tag and the worktree is clean; the resolved
    * version is that tag verbatim, so target and resolved coincide.
    */
  case Concrete

  /** No clean tagged basis; the resolved version is a development version constructed from the
    * computed target core and commit metadata.
    */
  case Development

object ResolutionMode:
  given CanEqual[ResolutionMode, ResolutionMode] = CanEqual.derived

/** Outcome of a resolution run.
  *
  * `resolved` is what consumers normally render; `target` is the release core the resolution computed - equal to
  * `resolved` under [[ResolutionMode.Concrete]], the core underlying the development version under
  * [[ResolutionMode.Development]]. `basis` is the commit the version was resolved at (its `commitTime` is the last
  * commit date); it is `None` only for an empty (unborn) repository. `base` is the release this resolution is anchored
  * to - the resolved release under [[ResolutionMode.Concrete]], the base release the development version builds from
  * under [[ResolutionMode.Development]], or `None` when no release is reachable; the time of its commit is the release
  * time. Instances are produced by [[Resolver$ Resolver]] (`resolveAll`).
  *
  * @tparam V
  *   The version type for the scheme in use.
  */
final case class ResolutionResult[V <: Version](
  resolved: V,
  target: V,
  mode: ResolutionMode,
  basis: Option[RawCommit],
  base: Option[Release[V]]
)

/** Provides equality for [[ResolutionResult]]. */
object ResolutionResult:
  given [V <: Version](using CanEqual[V, V]): CanEqual[ResolutionResult[V], ResolutionResult[V]] = CanEqual.derived
