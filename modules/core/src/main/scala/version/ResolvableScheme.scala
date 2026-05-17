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

/** Resolution context describing the state of the repository for development version construction.
  *
  * This is a pure data aggregate carrying information from the resolution engine to a scheme's
  * [[ResolvableScheme.developmentVersion]] implementation. The scheme uses this to construct a development version in
  * its own format (e.g., SemVer uses `-SNAPSHOT+metadata`).
  *
  * `commitTime` carries the basis commit's committer time in seconds since the Unix epoch (UTC). The scheme may render
  * this in a sortable form so that snapshots of the same base sort chronologically as raw strings.
  */
final case class DevelopmentMetadata(
  branch: Option[String],
  commitSha: Option[String],
  commitCount: Option[Int],
  commitTime: Option[Long],
  prNumber: Option[Int],
  isDirty: Boolean
)

object DevelopmentMetadata:
  given CanEqual[DevelopmentMetadata, DevelopmentMetadata] = CanEqual.derived

/** Full Git-based version resolution contract for schemes that support the complete resolution workflow.
  *
  * Extends [[VersionArithmetic]] because resolution requires component manipulation (incrementing after keyword
  * parsing). A single `given ResolvableScheme[V]` in a scheme's companion satisfies `VersionScheme[V]`,
  * `VersionArithmetic[V]`, and `ResolvableScheme[V]` via subtyping.
  *
  * @tparam V
  *   The version type.
  */
trait ResolvableScheme[V] extends VersionArithmetic[V]:

  /** Keyword aliases mapping to component indices (e.g., `"breaking" -> 0`).
    *
    * Built from [[layout]] descriptor names plus role-based aliases and scheme-specific extras.
    */
  def keywordAliases: Map[String, Int]

  /** Initial version when no tags exist (e.g., `0.1.0` for SemVer). */
  def initialVersion: V

  /** Construct a development version from a target core and resolution metadata.
    *
    * How metadata is encoded is scheme-specific: SemVer uses `-SNAPSHOT+metadata`, .NET CrossPlatform uses
    * `buildNumber=0`.
    */
  def developmentVersion(targetCore: V, metadata: DevelopmentMetadata): V

  /** Default advancement when base is a final release and no directives apply. */
  extension (v: V) def defaultBump: V

  /** Promote a development version to its release core. */
  extension (v: V) def promoteToRelease: V
end ResolvableScheme

object ResolvableScheme:
  /** Summons the contextual [[ResolvableScheme]] instance. */
  inline def apply[V](using rs: ResolvableScheme[V]): ResolvableScheme[V] = rs
