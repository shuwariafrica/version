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

/** Version resolution engine.
  *
  * Implements the algorithm defined in the version resolution specification. Consumers should use [[VersionCliCore]].
  */
object Resolver:
  given CanEqual[Resolver.type, Resolver.type] = CanEqual.derived

  /** Resolves the repository version from Git state.
    *
    * Requires contextual [[Logger]], verbosity flag, and [[Version.Read]] for tag parsing.
    */
  def resolve(config: CliConfig, git: Git)(using
    logger: Logger,
    isVerbose: Boolean,
    reader: Version.Read[String]
  ): Either[ResolutionError, Version] =
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
  )(using logger: Logger, isVerbose: Boolean, reader: Version.Read[String]): Either[ResolutionError, Version] =
    for
      reachable <- git.findReachableTags(basisCommitSha)
      _ = logger.verbose(s"Reachable tags: ${reachable.map(_.name.value).mkString(",")}", "Resolver")
      baseOpt = reachable.sorted.lastOption
      targetCore <- baseOpt match
        case Some(baseTag) =>
          for
            commits <- git.getCommitsSince(basisCommitSha, Some(baseTag.commitSha))
            _ = logger.verbose(s"Commits since base tag ${baseTag.name.value}: ${commits.size}", "Resolver")
            keywords <- extractKeywords(commits, git)
            _ = logger.verbose(s"Keywords extracted: ${keywords.size}", "Resolver")
            targetOpt = TargetVersionCalculator.selectValidTarget(
              targets = keywords.collect { case Keyword.TargetSet(v) => v },
              highestReachable = Some(baseTag),
              highestRepo = allTags.sorted.lastOption,
              allRepoFinals = allTags.filter(_.version.preRelease.isEmpty),
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
            keywords <- extractKeywords(commits, git)
            highestRepo = allTags.sorted.lastOption
            repoFinals = allTags.filter(_.version.preRelease.isEmpty)
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
      metadata <- MetadataBuilder.assemble(config, git, basisCommitSha, baseOpt.map(_.commitSha), isDirty)
      _ = logger.verbose(s"Build metadata assembled: ${metadata.show}", "Resolver")
      result = targetCore.copy(preRelease = Some(PreRelease.snapshot), metadata = Some(metadata))
      _ = logger.verbose(s"Final snapshot version: $result", "Resolver")
    yield result
    end for
  end calculateDevelopmentVersion

  /** Extracts keywords from commits, applying ignore directives.
    *
    * Ignore directive precedence:
    *   1. `IgnoreSelf` — excludes the commit containing it
    *   2. `IgnoreCommits(shas)` — excludes commits matching the SHA prefixes
    *   3. `IgnoreRange(from, to)` — excludes commits in the range (inclusive)
    *   4. `IgnoreMerged` — excludes all commits from merged branches (merge commit only)
    */
  private def extractKeywords(commits: List[Commit], git: Git)(using
    reader: Version.Read[String],
    logger: Logger,
    isVerbose: Boolean
  ): Either[ResolutionError, List[Keyword]] =
    // Phase 1: Parse all keywords and collect ignore directives
    val parsed = commits.map(c => (c, KeywordParser.parse(c.message)))

    // Phase 2: Build exclusion set from ignore directives
    val exclusionResult = buildExclusionSet(parsed, commits, git)

    exclusionResult.map { exclusions =>
      logger.verbose(s"Exclusion set: ${exclusions.map(_.value).mkString(",")}", "Resolver")

      // Phase 3: Filter commits and extract non-ignore keywords
      parsed.flatMap { case (commit, keywords) =>
        val isExcluded = exclusions.exists(ex => commit.sha.value.startsWith(ex.value))
        val hasSelfIgnore = keywords.exists {
          case Keyword.IgnoreSelf => true
          case _                  => false
        }
        if isExcluded || hasSelfIgnore then Nil
        else
          keywords.filter {
            case _: Keyword.IgnoreDirective => false
            case _                          => true
          }
      }
    }
  end extractKeywords

  /** Builds the set of commit SHAs to exclude based on ignore directives. */
  private def buildExclusionSet(
    parsed: List[(Commit, List[Keyword])],
    allCommits: List[Commit],
    git: Git
  ): Either[ResolutionError, Set[CommitSha]] =
    val commitsByPrefix = allCommits.map(c => (c.sha.value, c.sha)).toMap

    // Collect all ignore directives
    val ignoreDirectives = parsed.flatMap { case (commit, keywords) =>
      keywords.collect { case d: Keyword.IgnoreDirective => (commit, d) }
    }

    // Process IgnoreCommits and IgnoreRange directives (pure, no Git calls)
    val directExclusions = ignoreDirectives.foldLeft(Set.empty[CommitSha]) { case (exclusions, (_, directive)) =>
      directive match
        case Keyword.IgnoreCommits(shas) =>
          shas.foldLeft(exclusions) { (acc, prefix) =>
            commitsByPrefix.keys.filter(_.startsWith(prefix)).foldLeft(acc) { (a, fullSha) =>
              a + CommitSha(fullSha)
            }
          }
        case Keyword.IgnoreRange(from, to) =>
          val fromSha = commitsByPrefix.keys.find(_.startsWith(from))
          val toSha = commitsByPrefix.keys.find(_.startsWith(to))
          (fromSha, toSha) match
            case (Some(f), Some(t)) =>
              val fromIdx = allCommits.indexWhere(_.sha.value == f)
              val toIdx = allCommits.indexWhere(_.sha.value == t)
              if fromIdx != -1 && toIdx != -1 then
                val (start, end) = if fromIdx <= toIdx then (fromIdx, toIdx) else (toIdx, fromIdx)
                allCommits.slice(start, end + 1).foldLeft(exclusions)((acc, c) => acc + c.sha)
              else exclusions
            case _ => exclusions
        case _ => exclusions
    }

    // IgnoreMerged: get merged commits from merge commits (requires Git calls only for actual merges)
    // Use Commit.isMerge to skip non-merge commits without subprocess calls
    val mergeCommits = ignoreDirectives.collect { case (commit, Keyword.IgnoreMerged) if commit.isMerge => commit }
    val mergedResults = mergeCommits.map { mergeCommit =>
      git.getMergedCommits(mergeCommit.sha)
    }

    // Combine all merged commit results
    mergedResults.foldLeft(Right(directExclusions): Either[ResolutionError, Set[CommitSha]]) { (acc, result) =>
      for
        current <- acc
        merged <- result
      yield current ++ merged
    }
  end buildExclusionSet

end Resolver
