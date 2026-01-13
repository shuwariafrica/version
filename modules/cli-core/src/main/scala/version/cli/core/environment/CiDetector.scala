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
package version.cli.core.environment

import version.cli.core.domain.*

/** Entry point for CI environment detection. */
object CiDetector:
  given CanEqual[CiDetector.type, CiDetector.type] = CanEqual.derived

  def detect(env: collection.Map[String, String]): Option[CiMetadata] =
    detectors.iterator
      .map(_.detect(env))
      .collectFirst { case Some(metadata) => metadata }

  def detectCurrent(): Option[CiMetadata] =
    detect(scala.sys.env)

  private val detectors: List[ProviderDetector] =
    List(
      ProviderDetector.GitHub,
      ProviderDetector.AzurePipelines,
      ProviderDetector.TeamCity,
      ProviderDetector.Bitbucket
    )

private trait ProviderDetector:
  def detect(env: collection.Map[String, String]): Option[CiMetadata]

private object ProviderDetector:

  object GitHub extends ProviderDetector:
    override def detect(env: collection.Map[String, String]): Option[CiMetadata] =
      val isActions = env.get("GITHUB_ACTIONS").exists(ValueHelpers.truthy)
      if !isActions then None
      else
        val prNumber =
          env
            .get("GITHUB_REF")
            .flatMap(ValueHelpers.extractPullNumber)
            .orElse(env.get("GITHUB_REF_NAME").flatMap(ValueHelpers.extractLeadingDigits))
        val sourceBranch = ValueHelpers.nonEmpty(env.get("GITHUB_HEAD_REF"))
        val targetBranch = ValueHelpers.nonEmpty(env.get("GITHUB_BASE_REF"))
        val branch =
          sourceBranch
            .orElse(env.get("GITHUB_REF_NAME").flatMap(ValueHelpers.normaliseRef))
            .orElse(env.get("GITHUB_REF").flatMap(ValueHelpers.normaliseRef))
        val repository =
          Some(
            RepositoryInfo(
              host = RepositoryHost.GitHub,
              owner = env.get("GITHUB_REPOSITORY_OWNER"),
              name = env.get("GITHUB_REPOSITORY").flatMap(ValueHelpers.repoNameComponent),
              slug = env.get("GITHUB_REPOSITORY")
            )
          )
        val build =
          CiBuildInfo(
            id = env.get("GITHUB_RUN_ID"),
            number = env.get("GITHUB_RUN_NUMBER"),
            url = ValueHelpers.combineGitHubUrl(env.get("GITHUB_SERVER_URL"), env.get("GITHUB_REPOSITORY"), env.get("GITHUB_RUN_ID"))
          )
        val prDetails =
          if prNumber.isDefined || sourceBranch.isDefined || targetBranch.isDefined then
            Some(
              PullRequestInfo(
                number = prNumber,
                sourceRef = sourceBranch,
                targetRef = targetBranch,
                mergeCommitSha = env.get("GITHUB_SHA")
              )
            )
          else None
        Some(
          CiMetadata(
            provider = CiProvider.GitHubActions,
            isCi = true,
            build = build,
            repository = repository,
            branch = branch,
            commitSha = env.get("GITHUB_SHA"),
            pullRequest = prDetails
          )
        )
      end if
    end detect
  end GitHub

  object AzurePipelines extends ProviderDetector:
    override def detect(env: collection.Map[String, String]): Option[CiMetadata] =
      val tfBuild = env.get("TF_BUILD").exists(ValueHelpers.truthy)
      val buildIdPresent = env.contains("BUILD_BUILDID")
      if !tfBuild && !buildIdPresent then None
      else
        val repositoryProvider = env.get("BUILD_REPOSITORY_PROVIDER")
        val repositoryHost =
          repositoryProvider match
            case Some(providerName) if providerName.equalsIgnoreCase("GitHub")    => Some(RepositoryHost.GitHub)
            case Some(providerName) if providerName.equalsIgnoreCase("TfsGit")    => Some(RepositoryHost.AzureRepos)
            case Some(providerName) if providerName.equalsIgnoreCase("Bitbucket") => Some(RepositoryHost.Bitbucket)
            case _                                                                => None
        val repoName = env.get("BUILD_REPOSITORY_NAME")
        val repository =
          repositoryHost.map { host =>
            val owner =
              host match
                case RepositoryHost.GitHub | RepositoryHost.Bitbucket => repoName.flatMap(ValueHelpers.repoOwnerComponent)
                case RepositoryHost.AzureRepos                        => env.get("SYSTEM_TEAMPROJECT")
            RepositoryInfo(
              host = host,
              owner = owner,
              name = repoName.flatMap(ValueHelpers.repoNameComponent),
              slug = repoName
            )
          }
        val prSource =
          env
            .get("SYSTEM_PULLREQUEST_SOURCEBRANCH")
            .orElse(env.get("SYSTEM_PULLREQUEST_SOURCEBRANCHNAME"))
            .flatMap(ValueHelpers.normaliseRef)
        val prTarget =
          env
            .get("SYSTEM_PULLREQUEST_TARGETBRANCH")
            .orElse(env.get("SYSTEM_PULLREQUEST_TARGETBRANCHNAME"))
            .flatMap(ValueHelpers.normaliseRef)
        val prNumber =
          env
            .get("SYSTEM_PULLREQUEST_PULLREQUESTNUMBER")
            .orElse(env.get("SYSTEM_PULLREQUEST_PULLREQUESTID"))
            .flatMap(ValueHelpers.toInt)
            .orElse(env.get("BUILD_SOURCEBRANCH").flatMap(ValueHelpers.extractPullNumber))
        val prDetails =
          if prNumber.isDefined || prSource.isDefined || prTarget.isDefined then
            Some(
              PullRequestInfo(
                number = prNumber,
                sourceRef = prSource,
                targetRef = prTarget,
                mergeCommitSha = env.get("SYSTEM_PULLREQUEST_SOURCECOMMITID")
              )
            )
          else None
        val branch =
          env
            .get("BUILD_SOURCEBRANCHNAME")
            .orElse(env.get("BUILD_SOURCEBRANCH").flatMap(ValueHelpers.normaliseRef))
        val buildInfo =
          CiBuildInfo(
            id = env.get("BUILD_BUILDID"),
            number = env.get("BUILD_BUILDNUMBER"),
            url = env.get("BUILD_BUILDURI")
          )
        Some(
          CiMetadata(
            provider = CiProvider.AzurePipelines,
            isCi = true,
            build = buildInfo,
            repository = repository,
            branch = branch,
            commitSha = env.get("BUILD_SOURCEVERSION"),
            pullRequest = prDetails
          )
        )
      end if
    end detect
  end AzurePipelines

  object TeamCity extends ProviderDetector:
    override def detect(env: collection.Map[String, String]): Option[CiMetadata] =
      if !env.contains("TEAMCITY_VERSION") then None
      else
        val buildInfo =
          CiBuildInfo(
            id = env.get("BUILD_ID").orElse(env.get("TEAMCITY_BUILD_ID")),
            number = env.get("BUILD_NUMBER"),
            url = env.get("BUILD_URL")
          )
        val sourceBranch = env.get("TEAMCITY_PULLREQUEST_SOURCE_BRANCH").orElse(env.get("TEAMCITY_BUILD_BRANCH"))
        val targetBranch = env.get("TEAMCITY_PULLREQUEST_TARGET_BRANCH")
        val prNumber = env.get("TEAMCITY_PULLREQUEST_NUMBER").flatMap(ValueHelpers.toInt)
        val prDetails =
          if prNumber.isDefined || sourceBranch.isDefined || targetBranch.isDefined then
            Some(
              PullRequestInfo(
                number = prNumber,
                sourceRef = sourceBranch,
                targetRef = targetBranch,
                mergeCommitSha = env.get("TEAMCITY_PULLREQUEST_MERGE_COMMIT")
              )
            )
          else None
        val branch = sourceBranch.orElse(env.get("TEAMCITY_BUILD_BRANCH")).orElse(env.get("BRANCH_NAME"))
        val commit = env.get("BUILD_VCS_NUMBER").orElse(env.get("BUILD_VCS_NUMBER_1"))
        Some(
          CiMetadata(
            provider = CiProvider.TeamCity,
            isCi = true,
            build = buildInfo,
            repository = None,
            branch = branch,
            commitSha = commit,
            pullRequest = prDetails
          )
        )
  end TeamCity

  object Bitbucket extends ProviderDetector:
    override def detect(env: collection.Map[String, String]): Option[CiMetadata] =
      if !env.contains("BITBUCKET_BUILD_NUMBER") then None
      else
        val buildInfo =
          CiBuildInfo(
            id = env.get("BITBUCKET_STEP_UUID").orElse(env.get("BITBUCKET_PIPELINE_UUID")),
            number = env.get("BITBUCKET_BUILD_NUMBER"),
            url = ValueHelpers.bitbucketBuildUrl(env.get("BITBUCKET_GIT_HTTP_ORIGIN"), env.get("BITBUCKET_BUILD_NUMBER"))
          )
        val repository =
          Some(
            RepositoryInfo(
              host = RepositoryHost.Bitbucket,
              owner = env.get("BITBUCKET_WORKSPACE"),
              name = env.get("BITBUCKET_REPO_SLUG"),
              slug = env.get("BITBUCKET_REPO_FULL_NAME")
            )
          )
        val prNumber = env.get("BITBUCKET_PR_ID").flatMap(ValueHelpers.toInt)
        val prDetails =
          if prNumber.isDefined || env.get("BITBUCKET_PR_DESTINATION_BRANCH").exists(_.nonEmpty) then
            Some(
              PullRequestInfo(
                number = prNumber,
                sourceRef = env.get("BITBUCKET_BRANCH"),
                targetRef = env.get("BITBUCKET_PR_DESTINATION_BRANCH"),
                mergeCommitSha = env.get("BITBUCKET_PR_DESTINATION_COMMIT")
              )
            )
          else None
        val isCi = env.get("CI").map(ValueHelpers.truthy).getOrElse(true)
        Some(
          CiMetadata(
            provider = CiProvider.BitbucketPipelines,
            isCi = isCi,
            build = buildInfo,
            repository = repository,
            branch = env.get("BITBUCKET_BRANCH"),
            commitSha = env.get("BITBUCKET_COMMIT"),
            pullRequest = prDetails
          )
        )
  end Bitbucket
end ProviderDetector

private object ValueHelpers:
  inline def truthy(value: String | Null): Boolean =
    inline value match
      case v: String =>
        val lower = v.trim.toLowerCase
        lower == "true" || lower == "1" || lower == "yes" || lower == "y" || lower == "on"
      case _ => false

  def toInt(value: String): Option[Int] =
    val trimmed = value.trim
    if trimmed.isEmpty then None else trimmed.toIntOption

  def extractLeadingDigits(value: String): Option[Int] =
    val digits = value.takeWhile(_.isDigit)
    if digits.nonEmpty then digits.toIntOption else None

  def extractPullNumber(ref: String): Option[Int] =
    val prefix = "refs/pull/"
    val startIdx = ref.indexOf(prefix)
    if startIdx < 0 then None
    else
      val afterPrefix = ref.substring(startIdx + prefix.length)
      val endIdx = afterPrefix.indexOf('/') match
        case -1 => afterPrefix.length
        case n  => n
      val numberPart = afterPrefix.substring(0, endIdx)
      toInt(numberPart)

  def nonEmpty(opt: Option[String]): Option[String] =
    opt.flatMap { value =>
      val trimmed = value.trim
      if trimmed.nonEmpty then Some(trimmed) else None
    }

  def normaliseRef(ref: String): Option[String] =
    val trimmed = ref.trim
    if trimmed.isEmpty then None
    else if trimmed.startsWith("refs/heads/") then Some(trimmed.stripPrefix("refs/heads/"))
    else if trimmed.startsWith("refs/tags/") then Some(trimmed.stripPrefix("refs/tags/"))
    else if trimmed.startsWith("refs/heads") then lastPathSegmentOpt(trimmed)
    else if trimmed.startsWith("refs/pull/") then None
    else Some(trimmed)

  def repoNameComponent(repo: String): Option[String] =
    val segments = repo.split("/", 2)
    if segments.length == 2 then Some(segments(1)) else Some(repo)

  def repoOwnerComponent(repo: String): Option[String] =
    val segments = repo.split("/", 2)
    if segments.length == 2 then Some(segments(0)) else None

  def lastPathSegmentOpt(value: String): Option[String] =
    val trimmed = value.trim
    if trimmed.isEmpty then None
    else
      val idx = trimmed.lastIndexOf('/')
      if idx >= 0 && idx < trimmed.length - 1 then Some(trimmed.substring(idx + 1)) else Some(trimmed)

  def combineGitHubUrl(server: Option[String], repository: Option[String], runId: Option[String]): Option[String] =
    (server, repository, runId) match
      case (Some(base), Some(repo), Some(id)) =>
        val baseTrimmed = if base.endsWith("/") then base.dropRight(1) else base
        Some(s"$baseTrimmed/$repo/actions/runs/$id")
      case _ => None

  def bitbucketBuildUrl(origin: Option[String], buildNumber: Option[String]): Option[String] =
    (origin, buildNumber) match
      case (Some(url), Some(number)) =>
        val trimmed = if url.endsWith(".git") then url.dropRight(4) else url
        Some(s"$trimmed/addon/pipelines/home#!/results/$number")
      case _ => None
end ValueHelpers
