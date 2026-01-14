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
package version.cli.core.domain

/** Known CI providers supported by version-cli-core. */
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
) derives CanEqual

object RepositoryInfo:
  given CanEqual[RepositoryInfo, RepositoryInfo] = CanEqual.derived

  def empty(host: RepositoryHost): RepositoryInfo =
    RepositoryInfo(host = host, owner = None, name = None, slug = None)

/** Build level metadata common across CI providers. */
final case class CiBuildInfo(
  id: Option[String],
  number: Option[String],
  url: Option[String]
) derives CanEqual

object CiBuildInfo:
  given CanEqual[CiBuildInfo, CiBuildInfo] = CanEqual.derived

  val empty: CiBuildInfo = CiBuildInfo(id = None, number = None, url = None)

/** Pull request metadata available for CI runs triggered by PRs. */
final case class PullRequestInfo(
  number: Option[Int],
  sourceRef: Option[String],
  targetRef: Option[String],
  mergeCommitSha: Option[String]
) derives CanEqual

object PullRequestInfo:
  given CanEqual[PullRequestInfo, PullRequestInfo] = CanEqual.derived

  val empty: PullRequestInfo =
    PullRequestInfo(number = None, sourceRef = None, targetRef = None, mergeCommitSha = None)

/** Aggregated metadata describing the active CI environment. */
final case class CiMetadata(
  provider: CiProvider,
  isCi: Boolean,
  build: CiBuildInfo,
  repository: Option[RepositoryInfo],
  branch: Option[String],
  commitSha: Option[String],
  pullRequest: Option[PullRequestInfo]
) derives CanEqual

object CiMetadata:
  given CanEqual[CiMetadata, CiMetadata] = CanEqual.derived

  def apply(provider: CiProvider): CiMetadata =
    CiMetadata(
      provider = provider,
      isCi = true,
      build = CiBuildInfo.empty,
      repository = None,
      branch = None,
      commitSha = None,
      pullRequest = None
    )

  def withPullRequest(provider: CiProvider, pr: PullRequestInfo): CiMetadata =
    CiMetadata(
      provider = provider,
      isCi = true,
      build = CiBuildInfo.empty,
      repository = None,
      branch = None,
      commitSha = None,
      pullRequest = Some(pr)
    )

  def nonCi(provider: CiProvider): CiMetadata =
    CiMetadata(
      provider = provider,
      isCi = false,
      build = CiBuildInfo.empty,
      repository = None,
      branch = None,
      commitSha = None,
      pullRequest = None
    )

  def empty(provider: CiProvider): CiMetadata = apply(provider)

  def isPullRequest(metadata: CiMetadata): Boolean = metadata.pullRequest.exists(_.number.isDefined)
end CiMetadata
