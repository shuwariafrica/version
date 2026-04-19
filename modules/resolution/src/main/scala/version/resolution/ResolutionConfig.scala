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

import version.ResolvableScheme
import version.resolution.domain.CiMetadata

/** Pure configuration for a version resolution run.
  *
  * `repoPath` is a platform-neutral string; each backend's `open` factory performs platform-appropriate
  * path resolution. `tagParser` converts raw Git tag names to version values.
  *
  * @tparam V
  *   The version type for the scheme in use.
  */
final case class ResolutionConfig[V] private[version] (
  repoPath: String,
  basisCommit: String,
  prNumber: Option[Int],
  branchOverride: Option[String],
  shaLength: Int,
  verbose: Boolean,
  tagParser: String => Option[V]
)

/** Provides factory methods and extensions for [[ResolutionConfig]]. */
object ResolutionConfig:
  given [V](using CanEqual[V, V]): CanEqual[ResolutionConfig[V], ResolutionConfig[V]] = CanEqual.derived

  val MinShaLength: Int = 7
  val MaxShaLength: Int = 40

  /** Validated construction. Returns `Left` if `shaLength` is outside [7, 40] or `basisCommit` is empty. */
  def from[V](
    repoPath: String,
    basisCommit: String,
    prNumber: Option[Int],
    branchOverride: Option[String],
    shaLength: Int,
    verbose: Boolean,
    tagParser: String => Option[V]
  ): Either[ResolutionError, ResolutionConfig[V]] =
    if basisCommit.isEmpty then Left(ResolutionError.InvalidBasisCommit(basisCommit))
    else if shaLength < MinShaLength || shaLength > MaxShaLength then Left(ResolutionError.InvalidShaLength(shaLength))
    else Right(new ResolutionConfig(repoPath, basisCommit, prNumber, branchOverride, shaLength, verbose, tagParser))

  /** Default configuration for the given repository path, using the scheme's parser with `v`/`V` prefix stripping. */
  def default[V](repoPath: String)(using scheme: ResolvableScheme[V]): ResolutionConfig[V] =
    ResolutionConfig(
      repoPath = repoPath,
      basisCommit = "HEAD",
      prNumber = None,
      branchOverride = None,
      shaLength = 12,
      verbose = false,
      tagParser = name =>
        val raw = if name.startsWith("v") || name.startsWith("V") then name.drop(1) else name
        scheme.parse(raw).toOption
    )

  /** Companion alias for the multi-parameter [[mergeWith]] extension. */
  inline def mergeWith[V](config: ResolutionConfig[V], metadata: Option[CiMetadata]): ResolutionConfig[V] =
    config.mergeWith(metadata)

  extension [V](config: ResolutionConfig[V])
    /** Merge CI-detected metadata into this configuration, filling in PR number and branch override where absent. */
    @targetName("ext_mergeWith")
    inline def mergeWith(metadata: Option[CiMetadata]): ResolutionConfig[V] =
      metadata match
        case Some(ci) =>
          val pr = config.prNumber.orElse(ci.inferPullRequestNumber)
          val branch = config.branchOverride.orElse(ci.inferBranchOverride)
          config.copy(prNumber = pr, branchOverride = branch)
        case None => config
end ResolutionConfig
