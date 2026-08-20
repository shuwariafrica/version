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

import version.resolution.domain.RawCommit
import version.resolution.domain.Release

/** How a resolved version was arrived at.
  *
  * Instances are compared via [[ResolutionMode$ ResolutionMode]].
  */
enum ResolutionMode:

  /** The basis commit carries a version tag and the working tree is clean, so the tag itself is the version. */
  case Concrete

  /** The basis is untagged or the working tree is modified, so the version is an in-development one built from the
    * target and the state of the repository.
    */
  case Development

/** Provides instances for [[ResolutionMode]]. */
object ResolutionMode:
  given CanEqual[ResolutionMode, ResolutionMode] = CanEqual.derived

/** What a resolution run arrived at.
  *
  * `resolved` is the version to render; `target` is the release it is working towards, which equals `resolved` under
  * [[ResolutionMode.Concrete]]. `basis` is the commit resolved at, absent only for an unborn repository. `base` is the
  * release the run is anchored to, absent when none is reachable. `repository` is the root that was actually read -
  * the working tree, or the Git directory of a bare repository - which discovery may have found above the path asked
  * for.
  *
  * Instances are produced by [[Resolver$ Resolver]].
  */
final case class ResolutionResult[V](
  resolved: V,
  target: V,
  mode: ResolutionMode,
  basis: Option[RawCommit],
  base: Option[Release[V]],
  repository: String
)

/** Provides equality for [[ResolutionResult]]. */
object ResolutionResult:
  given [V](using CanEqual[V, V]): CanEqual[ResolutionResult[V], ResolutionResult[V]] = CanEqual.derived
