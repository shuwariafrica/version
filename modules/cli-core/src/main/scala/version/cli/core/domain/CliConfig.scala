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
package version.cli.core.domain

/** Pure configuration for a version resolution run.
  *
  * @param repo
  *   Path anywhere within the Git repository.
  * @param basisCommit
  *   Commit-ish to use as basis (default HEAD).
  * @param prNumber
  *   Optional pull request number (emits 'pr<N>' build metadata).
  * @param branchOverride
  *   Optional branch name override for metadata; when absent we detect via symbolic-ref.
  * @param shaLength
  *   Abbreviated SHA length for metadata. Must be in [7, 40]. Default 12.
  * @param verbose
  *   Enable verbose debug logging throughout resolution process.
  */
final case class CliConfig(
  repo: os.Path,
  basisCommit: String,
  prNumber: Option[Int],
  branchOverride: Option[String],
  shaLength: Int,
  verbose: Boolean
)

object CliConfig:
  given CanEqual[CliConfig, CliConfig] = CanEqual.derived

  def apply(): CliConfig =
    new CliConfig(
      repo = os.pwd,
      basisCommit = "HEAD",
      prNumber = None,
      branchOverride = None,
      shaLength = 12,
      verbose = false
    )

  def mergeWithCiMetadata(base: CliConfig, metadata: Option[CiMetadata]): CliConfig =
    metadata match
      case Some(ci) =>
        val pr = base.prNumber.orElse(ci.pullRequest.flatMap(_.number))
        val branch = base.branchOverride.orElse(selectBranchOverride(ci))
        base.copy(prNumber = pr, branchOverride = branch)
      case None => base

  def inferBranchOverride(metadata: Option[CiMetadata]): Option[String] =
    metadata.flatMap(selectBranchOverride)

  def inferPullRequestNumber(metadata: Option[CiMetadata]): Option[Int] =
    metadata.flatMap(_.pullRequest.flatMap(_.number))

  private def selectBranchOverride(ci: CiMetadata): Option[String] =
    ci.pullRequest
      .flatMap(pr => pr.sourceRef.orElse(ci.branch).orElse(pr.targetRef))
      .orElse(ci.branch)
end CliConfig
