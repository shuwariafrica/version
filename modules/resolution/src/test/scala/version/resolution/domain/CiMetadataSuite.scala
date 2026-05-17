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
package version.resolution.domain

import munit.FunSuite

/** Tests for [[CiMetadata]] extensions, especially the branch-override inference rules. */
class CiMetadataSuite extends FunSuite:

  private val emptyBuild = CiBuildInfo(id = None, number = None, url = None)

  private def base(branch: Option[String], pr: Option[PullRequestInfo]): CiMetadata =
    CiMetadata(
      provider = CiProvider.GitHubActions,
      isCi = true,
      build = emptyBuild,
      repository = None,
      branch = branch,
      commitSha = None,
      pullRequest = pr
    )

  test("inferBranchOverride: non-PR build returns the active branch"):
    val ci = base(branch = Some("feature/x"), pr = None)
    assertEquals(ci.inferBranchOverride, Some("feature/x"))

  test("inferBranchOverride: PR build prefers target branch over source branch"):
    val pr = PullRequestInfo(
      number = Some(42),
      sourceRef = Some("contributor/typo-fix"),
      targetRef = Some("main"),
      mergeCommitSha = None
    )
    val ci = base(branch = Some("contributor/typo-fix"), pr = Some(pr))
    assertEquals(ci.inferBranchOverride, Some("main"))

  test("inferBranchOverride: PR build with no target falls back to active branch"):
    val pr = PullRequestInfo(
      number = Some(42),
      sourceRef = Some("contributor/typo-fix"),
      targetRef = None,
      mergeCommitSha = None
    )
    val ci = base(branch = Some("contributor/typo-fix"), pr = Some(pr))
    assertEquals(ci.inferBranchOverride, Some("contributor/typo-fix"))

  test("inferBranchOverride: PR build with no target or active branch falls back to source"):
    val pr = PullRequestInfo(
      number = Some(42),
      sourceRef = Some("contributor/typo-fix"),
      targetRef = None,
      mergeCommitSha = None
    )
    val ci = base(branch = None, pr = Some(pr))
    assertEquals(ci.inferBranchOverride, Some("contributor/typo-fix"))

  test("inferBranchOverride: returns None when nothing is known"):
    val ci = base(branch = None, pr = None)
    assertEquals(ci.inferBranchOverride, None)

  test("inferPullRequestNumber: extracts from PR metadata"):
    val pr = PullRequestInfo(number = Some(42), sourceRef = None, targetRef = None, mergeCommitSha = None)
    val ci = base(branch = None, pr = Some(pr))
    assertEquals(ci.inferPullRequestNumber, Some(42))

  test("inferPullRequestNumber: returns None for non-PR builds"):
    val ci = base(branch = Some("main"), pr = None)
    assertEquals(ci.inferPullRequestNumber, None)
end CiMetadataSuite
