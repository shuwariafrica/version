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
package version.cli.core

import version.*
import version.cli.core.domain.*
import version.cli.core.git.Git
import version.cli.core.logging.Logger
import version.cli.core.parsing.KeywordParser

/** Core engine for resolving the semantic version from a Git repository. */
object Resolver:
  given CanEqual[Resolver.type, Resolver.type] = CanEqual.derived

  /** Resolve the repository version based on the provided configuration and Git interface. */
  def resolve(config: CliConfig, git: Git)(using logger: Logger, isVerbose: Boolean): Either[ResolutionError, Version] =
    logger.verbose(s"Begin resolution basisCommit=${config.basisCommit}", "Resolver")
    for
      basisCommitSha <- git.resolveRev(config.basisCommit)
      allTags <- git.listAllTags()
      worktreeClean <- git.isWorkingDirectoryClean()
      tagsOnHead = allTags.filter(_.commitSha == basisCommitSha)
      highestTagOnHead = tagsOnHead.sorted.lastOption
      result <- highestTagOnHead match
        case Some(tag) if worktreeClean =>
          logger.verbose(s"HEAD tagged with ${tag.version} and worktree clean – emitting final version", "Resolver")
          Right(tag.version)
        case _ =>
          val isDirty = !worktreeClean
          logger.verbose(s"Entering snapshot path (dirty=$isDirty, tagsOnHead=${tagsOnHead.map(_.name.value).mkString(",")})", "Resolver")
          calculateDevelopmentVersion(config, git, basisCommitSha, isDirty, allTags)
    yield result

  private def calculateDevelopmentVersion(
    config: CliConfig,
    git: Git,
    basisCommitSha: CommitSha,
    isDirty: Boolean,
    allTags: List[Tag]
  )(using logger: Logger, isVerbose: Boolean): Either[ResolutionError, Version] =
    for
      reachable <- git.findReachableTags(basisCommitSha)
      _ = logger.verbose(s"Reachable tags: ${reachable.map(_.name.value).mkString(",")}", "Resolver")
      baseOpt = reachable.sorted.lastOption
      targetCore <- baseOpt match
        case Some(baseTag) =>
          for
            commits <- git.getCommitsSince(basisCommitSha, Some(baseTag.commitSha))
            _ = logger.verbose(s"Commits since base tag ${baseTag.name.value}: ${commits.size}", "Resolver")
            keywords = extractKeywords(commits)
            _ = logger.verbose(s"Keywords extracted: ${keywords.size}", "Resolver")
            targetOpt = TargetVersionCalculator.selectValidTarget(
              targets = keywords.collect { case Keyword.TargetSet(v) => v },
              highestReachable = Some(baseTag),
              highestRepo = allTags.sorted.lastOption,
              allRepoFinals = allTags.filter(_.version.isFinal),
              isHeadOnFinalTag = false
            )
          yield targetOpt.getOrElse {
            val derived = TargetVersionCalculator.fromKeywords(baseTag.version, keywords)
            logger.verbose(s"Derived target version: $derived", "Resolver")
            derived
          }
        case None =>
          for
            commits <- git.getCommitsSince(basisCommitSha, None)
            _ = logger.verbose(s"Commits (no base tag): ${commits.size}", "Resolver")
            keywords = extractKeywords(commits)
            highestRepo = allTags.sorted.lastOption
            repoFinals = allTags.filter(_.version.isFinal)
            targetOpt = TargetVersionCalculator.selectValidTarget(
              targets = keywords.collect { case Keyword.TargetSet(v) => v },
              highestReachable = None,
              highestRepo = highestRepo,
              allRepoFinals = repoFinals,
              isHeadOnFinalTag = false
            )
          yield targetOpt.getOrElse {
            val fallback = highestRepo match
              case Some(h) => Version(h.version.major.increment, MinorVersion.reset, PatchNumber.reset)
              case None    => Version(MajorVersion.fromUnsafe(0), MinorVersion.fromUnsafe(1), PatchNumber.fromUnsafe(0))
            logger.verbose(s"Fallback target version: $fallback", "Resolver")
            fallback
          }
      metadata <- BuildMetadataBuilder.assemble(config, git, basisCommitSha, baseOpt.map(_.commitSha), isDirty)
      _ = logger.verbose(s"Build metadata assembled: ${metadata.show}", "Resolver")
      result = targetCore.copy(preRelease = Some(PreRelease.snapshot), buildMetadata = Some(metadata))
      _ = logger.verbose(s"Final snapshot version: $result", "Resolver")
    yield result
    end for
  end calculateDevelopmentVersion

  /** Extracts keywords from commits, excluding any commit that contains `version: ignore`. */
  private def extractKeywords(commits: List[Commit]): List[Keyword] =
    commits.flatMap { c =>
      val keywords = KeywordParser.parse(c.message)
      // If the commit contains an Ignore directive, exclude ALL its keywords
      if keywords.contains(Keyword.Ignore) then Nil
      else keywords
    }

end Resolver
