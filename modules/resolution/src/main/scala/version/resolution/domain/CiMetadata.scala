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

/** Known CI providers supported by the resolution module. */
enum CiProvider derives CanEqual:
  case GitHubActions
  case AzurePipelines
  case TeamCity
  case BitbucketPipelines

/** Known repository hosting platforms referenced by CI providers. */
enum RepositoryHost derives CanEqual:
  case GitHub
  case AzureRepos
  case Bitbucket

/** Repository identification details extracted from CI environment metadata. */
final case class RepositoryInfo(
  host: RepositoryHost,
  owner: Option[String],
  name: Option[String],
  slug: Option[String]
)

object RepositoryInfo:
  given CanEqual[RepositoryInfo, RepositoryInfo] = CanEqual.derived

/** Build level metadata common across CI providers. */
final case class CiBuildInfo(
  id: Option[String],
  number: Option[String],
  url: Option[String]
)

object CiBuildInfo:
  given CanEqual[CiBuildInfo, CiBuildInfo] = CanEqual.derived

/** Pull request metadata available for CI runs triggered by PRs. */
final case class PullRequestInfo(
  number: Option[Int],
  sourceRef: Option[String],
  targetRef: Option[String],
  mergeCommitSha: Option[String]
)

object PullRequestInfo:
  given CanEqual[PullRequestInfo, PullRequestInfo] = CanEqual.derived

/** Aggregated metadata describing the active CI environment. */
final case class CiMetadata(
  provider: CiProvider,
  isCi: Boolean,
  build: CiBuildInfo,
  repository: Option[RepositoryInfo],
  branch: Option[String],
  commitSha: Option[String],
  pullRequest: Option[PullRequestInfo]
)

/** Provides instances and extensions for [[CiMetadata]]. */
object CiMetadata:
  given CanEqual[CiMetadata, CiMetadata] = CanEqual.derived

  private def selectBranchOverride(ci: CiMetadata): Option[String] =
    // PR builds: the target branch (where the merge lands) is the stable, informative identifier.
    // The PR's source branch is often noisy (`copilot/...`, `dependabot/...`) and is communicated
    // separately via [[inferPullRequestNumber]].
    ci.pullRequest
      .flatMap(pr => pr.targetRef.orElse(ci.branch).orElse(pr.sourceRef))
      .orElse(ci.branch)

  extension (ci: CiMetadata)
    /** Infer the most appropriate branch name from CI metadata. */
    def inferBranchOverride: Option[String] = selectBranchOverride(ci)

    /** Extract the pull request number, if available. */
    def inferPullRequestNumber: Option[Int] =
      ci.pullRequest.flatMap(_.number)
