/****************************************************************
 * Copyright © Shuwari Africa Ltd.                              *
 *                                                              *
 * This file is licensed to you under the terms of the Apache   *
 * License Version 2.0 (the "License"); you may not use this    *
 * file except in compliance with the License. You may obtain   *
 * a copy of the License at:                                    *
 *                                                              *
 *     https://www.apache.org/licenses/LICENSE-2.0              *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, *
 * either express or implied. See the License for the specific  *
 * language governing permissions and limitations under the     *
 * License.                                                     *
 ****************************************************************/
package version.cli.core

import munit.FunSuite

import version.cli.core.domain.*

class CliConfigSuite extends FunSuite:

  private val repo = os.pwd

  private val metadata = CiMetadata(
    provider = CiProvider.GitHubActions,
    isCi = true,
    build = CiBuildInfo(id = Some("1"), number = Some("42"), url = None),
    repository = None,
    branch = Some("ci-default"),
    commitSha = Some("abcdef1234567890"),
    pullRequest = Some(
      PullRequestInfo(
        number = Some(99),
        sourceRef = Some("feature/ci"),
        targetRef = Some("main"),
        mergeCommitSha = None
      )
    )
  )

  test("mergeWithCiMetadata prefers existing base values for branch override and pr number") {
    val base =
      CliConfig(repo = repo, basisCommit = "HEAD", prNumber = Some(7), branchOverride = Some("manual"), shaLength = 40, verbose = false)
    val merged = CliConfig.mergeWithCiMetadata(base, Some(metadata))

    assertEquals(merged.prNumber, Some(7))
    assertEquals(merged.branchOverride, Some("manual"))
  }

  test("mergeWithCiMetadata hydrates missing values from CI metadata") {
    val base = CliConfig(repo = repo, basisCommit = "HEAD", prNumber = None, branchOverride = None, shaLength = 40, verbose = false)
    val merged = CliConfig.mergeWithCiMetadata(base, Some(metadata))

    assertEquals(merged.prNumber, Some(99))
    assertEquals(merged.branchOverride, Some("feature/ci"))
  }

  test("inferBranchOverride falls back through pull request and metadata branch") {
    val prOnly = metadata.copy(branch = None)
    assertEquals(CliConfig.inferBranchOverride(Some(prOnly)), Some("feature/ci"))

    val branchOnly = metadata.copy(pullRequest = None, branch = Some("ci-branch"))
    assertEquals(CliConfig.inferBranchOverride(Some(branchOnly)), Some("ci-branch"))

    assertEquals(CliConfig.inferBranchOverride(None), None)
  }

  test("inferBranchOverride falls back to pull request target when source and metadata missing") {
    val targetFallback = metadata.copy(
      branch = None,
      pullRequest = metadata.pullRequest.map(_.copy(sourceRef = None, targetRef = Some("fallback-target")))
    )

    assertEquals(CliConfig.inferBranchOverride(Some(targetFallback)), Some("fallback-target"))
  }

  test("inferPullRequestNumber unwraps nested metadata values") {
    assertEquals(CliConfig.inferPullRequestNumber(Some(metadata)), Some(99))
    assertEquals(CliConfig.inferPullRequestNumber(None), None)
  }

end CliConfigSuite
