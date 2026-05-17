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

import version.DevelopmentMetadata

/** Assembles [[DevelopmentMetadata]] from pre-gathered resolution values.
  *
  * Pure function - no Git dependency. The resolver gathers the values via [[GitRepository]], then passes them here for
  * assembly. The branch name is preserved verbatim (no sanitisation) so consumers see the actual Git ref; rendering
  * adjustments belong to the chosen [[version.VersionScheme VersionScheme]].
  */
object MetadataBuilder:

  /** Assemble development metadata from pre-gathered values.
    *
    * `branchOverride` takes precedence over `branchDetected`. `None` for both means HEAD is detached or the branch was
    * otherwise unavailable; the scheme decides how to render that.
    *
    * `commitTime` is seconds since the Unix epoch (UTC). Pass `None` when no basis commit is available (e.g., an
    * unborn HEAD), otherwise pass the basis commit's committer time.
    */
  def assemble(
    branchOverride: Option[String],
    branchDetected: Option[String],
    abbreviatedSha: String,
    commitCount: Int,
    commitTime: Option[Long],
    prNumber: Option[Int],
    isDirty: Boolean
  ): DevelopmentMetadata =
    DevelopmentMetadata(
      branch = branchOverride.orElse(branchDetected),
      commitSha = Some(abbreviatedSha),
      commitCount = Some(commitCount),
      commitTime = commitTime,
      prNumber = prNumber,
      isDirty = isDirty
    )

end MetadataBuilder
