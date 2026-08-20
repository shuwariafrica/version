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

import scala.annotation.targetName

import version.VersionScheme
import version.resolution.domain.CiMetadata

/** What a resolution run is given before it opens anything.
  *
  * `repoPath` is platform-neutral; the `open` function passed alongside it performs the platform's own path
  * resolution. `basisCommit` names the revision to resolve at, and is `None` for HEAD - the only setting an
  * unborn repository tolerates. `tagParser` decides which tag names carry a version, and what version each carries.
  *
  * Instances may be constructed via [[ResolutionConfig$ ResolutionConfig]].
  */
final case class ResolutionConfig[V] private[version] (
  repoPath: String,
  basisCommit: Option[String],
  prNumber: Option[Int],
  branchOverride: Option[String],
  tagParser: String => Option[V]
)

/** Provides factory methods and extensions for [[ResolutionConfig]]. */
object ResolutionConfig:
  given [V](using CanEqual[V, V]): CanEqual[ResolutionConfig[V], ResolutionConfig[V]] = CanEqual.derived

  /** Validated construction, reading `"HEAD"` as the unpinned basis and rejecting an empty one. */
  def from[V](
    repoPath: String,
    basisCommit: String,
    prNumber: Option[Int],
    branchOverride: Option[String],
    tagParser: String => Option[V]
  ): Either[ResolutionError, ResolutionConfig[V]] =
    if basisCommit.isEmpty then Left(ResolutionError.InvalidBasisCommit(basisCommit))
    else Right(new ResolutionConfig(repoPath, Option.unless(basisCommit == "HEAD")(basisCommit), prNumber, branchOverride, tagParser))

  /** Configuration resolving `repoPath` at HEAD, reading tags with [[VersionResolver.defaultTagParser]]. */
  def default[V](repoPath: String)(using VersionScheme[V]): ResolutionConfig[V] =
    ResolutionConfig(
      repoPath = repoPath,
      basisCommit = None,
      prNumber = None,
      branchOverride = None,
      tagParser = VersionResolver.defaultTagParser[V]
    )

  /** Companion alias for the multi-parameter [[mergeWith]] extension. */
  inline def mergeWith[V](config: ResolutionConfig[V], metadata: Option[CiMetadata]): ResolutionConfig[V] =
    config.mergeWith(metadata)

  extension [V](config: ResolutionConfig[V])
    /** Fills in the pull-request number and branch override from `metadata` where this configuration states neither. */
    @targetName("ext_mergeWith")
    inline def mergeWith(metadata: Option[CiMetadata]): ResolutionConfig[V] =
      metadata match
        case Some(ci) =>
          val pr = config.prNumber.orElse(ci.inferPullRequestNumber)
          val branch = config.branchOverride.orElse(ci.inferBranchOverride)
          config.copy(prNumber = pr, branchOverride = branch)
        case None => config
end ResolutionConfig
