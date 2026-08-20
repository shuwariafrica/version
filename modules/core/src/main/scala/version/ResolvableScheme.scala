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
package version

/** Repository state at the commit a development version is built from.
  *
  * @param commitTime
  *   the commit's own time, in seconds since the Unix epoch, UTC.
  */
final case class DevelopmentMetadata(
  branch: Option[String],
  commitSha: Option[String],
  commitCount: Option[Int],
  commitTime: Option[Long],
  prNumber: Option[Int],
  isDirty: Boolean
)

/** Provides instances for [[DevelopmentMetadata]]. */
object DevelopmentMetadata:
  given CanEqual[DevelopmentMetadata, DevelopmentMetadata] = CanEqual.derived

/** What a scheme must answer for a repository-driven pipeline to release it: where a project starts, how an
  * in-development build is spelled, and which words its commit messages are read for.
  *
  * Composed with [[VersionScheme]] and [[VersionArithmetic]] through `using` rather than by extending them, so a
  * scheme that orders and advances values but prescribes no release workflow simply supplies no instance.
  */
trait ResolvableScheme[V]:

  /** The version a project carries before it has released anything. */
  def initialVersion: V

  /** The in-development version for `release`, with `metadata` encoded in the scheme's own vocabulary. */
  def developmentVersion(release: V, metadata: DevelopmentMetadata): V

  /** The target for `base` when no directive applies to it. */
  def defaultTarget(base: V): V

  /** The words this scheme recognises in a commit message, and the request each stands for. */
  def directives: Map[String, Request]

  extension (v: V)
    /** Whether the value names an in-development build rather than a release. */
    def snapshot: Boolean
