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

import version.ResolvableScheme
import version.resolution.domain.*
import version.resolution.logging.Logger
import version.resolution.logging.Verbose
import version.resolution.parsing.KeywordParser

/** Version resolution engine.
  *
  * Implements the algorithm from the version resolution specification. Scheme-generic: parameterised by
  * `[V: ResolvableScheme]`.
  */
object Resolver:

  private def lift[A](r: Either[GitError, A]): Either[ResolutionError, A] =
    r.left.map(ResolutionError.GitFailure.apply)

  /** Resolves the repository version from Git state. */
  def resolve[V](
    config: ResolutionConfig[V],
    open: String => Either[GitError, GitRepository]
  )(using
    scheme: ResolvableScheme[V],
    logger: Logger,
    v: Verbose
  ): Either[ResolutionError, V] =
    given Ordering[V] = scheme.ordering
    logger.verbose(s"Begin resolution repoPath=${config.repoPath}, basisCommit=${config.basisCommit}", "Resolver")

    lift(open(config.repoPath)).flatMap: repo =>
      try
        doResolve(config, repo)
      finally
        repo.close()

  private def doResolve[V](
    config: ResolutionConfig[V],
    repo: GitRepository
  )(using
    scheme: ResolvableScheme[V],
    logger: Logger,
    verbose: Verbose,
    ord: Ordering[V]
  ): Either[ResolutionError, V] =
    lift(repo.head).flatMap:
      case None =>
        if config.basisCommit != "HEAD" then Left(ResolutionError.GitFailure(GitError.RevisionNotFound(config.basisCommit)))
        else
          logger.verbose("Empty repository - returning initial version", "Resolver")
          Right(scheme.initialVersion)

      case Some(headSha) =>
        val basisResult =
          if config.basisCommit == "HEAD" then Right(headSha)
          else lift(repo.resolve(config.basisCommit))

        basisResult.flatMap: basis =>
          (for
            branchName <- lift(repo.branch)
            isClean <- lift(repo.clean)
            rawTags <- lift(repo.tags)
          yield (branchName, isClean, rawTags)).flatMap: (branchName, isClean, rawTags) =>
            val versionTags: IArray[Tag[V]] = rawTags
              .filter(_.kind == TagKind.Annotated)
              .flatMap(rt => config.tagParser(rt.name).map(v => Tag(rt.name, rt.commit, v)))

            logger.verbose(s"Parsed ${versionTags.length} version tag(s)", "Resolver")

            lift(repo.reachableTags(basis, versionTags.map(_.commit).toSet)).flatMap: reachableCommits =>
              val reachableTags = versionTags.filter(t => reachableCommits.contains(t.commit))

              val tagsOnBasis = reachableTags.filter(_.commit == basis)
              val highestOnBasis = tagsOnBasis.sorted.lastOption
              if highestOnBasis.isDefined && isClean then
                logger.verbose(s"Mode 1: HEAD tagged with ${highestOnBasis.get.version.show} and clean", "Resolver")
                Right(highestOnBasis.get.version)
              else
                val isDirty = !isClean
                val baseTag = reachableTags.sorted.lastOption
                logger.verbose(s"Mode 2: development version (dirty=$isDirty, base=${baseTag.map(_.name)})", "Resolver")

                lift(repo.walkAll(basis, baseTag.map(_.commit))).flatMap: scanRange =>
                  computeMergeExclusions(scanRange, repo).flatMap: mergeExclusions =>
                    val keywords = extractKeywords(scanRange, mergeExclusions)
                    val targetCore = calculateTarget(keywords, baseTag, versionTags)

                    lift(repo.walkFirstParent(basis, baseTag.map(_.commit))).flatMap: fpCommits =>
                      val commitCount = fpCommits.count(!_.isMerge)

                      lift(repo.abbreviate(basis, config.shaLength)).map: abbreviatedSha =>
                        val devMeta = MetadataBuilder.assemble(
                          branchOverride = config.branchOverride,
                          branchDetected = branchName,
                          abbreviatedSha = abbreviatedSha,
                          commitCount = commitCount,
                          prNumber = config.prNumber,
                          isDirty = isDirty
                        )
                        logger.verbose(s"Metadata assembled: $devMeta", "Resolver")
                        scheme.developmentVersion(targetCore, devMeta)
              end if

  private def extractKeywords[V](
    commits: IArray[RawCommit],
    mergeExclusions: Set[CommitSha]
  )(using ResolvableScheme[V]): List[Keyword] =
    val parsed = commits.map(c => (c, KeywordParser.parse[V](c.message)))
    val directExclusions = buildDirectExclusions(parsed, commits)
    val allExclusions = directExclusions ++ mergeExclusions

    parsed
      .flatMap: (commit, keywords) =>
        val isExcluded = allExclusions.contains(commit.id)
        val hasSelfIgnore = keywords.exists:
          case Keyword.IgnoreSelf => true
          case _                  => false
        if isExcluded || hasSelfIgnore then Nil
        else
          keywords.filter:
            case _: Keyword.IgnoreDirective => false
            case _                          => true
      .toList

  private def buildDirectExclusions(
    parsed: IArray[(RawCommit, List[Keyword])],
    allCommits: IArray[RawCommit]
  ): Set[CommitSha] =
    val commitsByPrefix = allCommits.map(c => (c.id.value, c.id)).toMap

    parsed.foldLeft(Set.empty[CommitSha]):
      case (exclusions, (_, keywords)) =>
        keywords.foldLeft(exclusions):
          case (acc, Keyword.IgnoreCommits(shas)) =>
            shas.foldLeft(acc): (a, prefix) =>
              commitsByPrefix.keys
                .filter(_.startsWith(prefix))
                .foldLeft(a): (a2, fullSha) =>
                  a2 + CommitSha(fullSha)
          case (acc, Keyword.IgnoreRange(from, to)) =>
            val fromSha = commitsByPrefix.keys.find(_.startsWith(from))
            val toSha = commitsByPrefix.keys.find(_.startsWith(to))
            (fromSha, toSha) match
              case (Some(f), Some(t)) =>
                val fromIdx = allCommits.indexWhere(_.id.value == f)
                val toIdx = allCommits.indexWhere(_.id.value == t)
                if fromIdx != -1 && toIdx != -1 then
                  val (start, end) = if fromIdx <= toIdx then (fromIdx, toIdx) else (toIdx, fromIdx)
                  allCommits.slice(start, end + 1).foldLeft(acc)((a, c) => a + c.id)
                else acc
              case _ => acc
          case (acc, _) => acc
  end buildDirectExclusions

  private def computeMergeExclusions[V](
    commits: IArray[RawCommit],
    repo: GitRepository
  )(using ResolvableScheme[V]): Either[ResolutionError, Set[CommitSha]] =
    val mergeCommitsWithIgnore = commits.filter: c =>
      c.isMerge && KeywordParser
        .parse[V](c.message)
        .exists:
          case Keyword.IgnoreMerged => true
          case _                    => false

    mergeCommitsWithIgnore.foldLeft(Right(Set.empty[CommitSha]): Either[ResolutionError, Set[CommitSha]]): (acc, mc) =>
      acc.flatMap: exclusions =>
        mc.parentIds
          .drop(1)
          .foldLeft(Right(exclusions): Either[ResolutionError, Set[CommitSha]]): (innerAcc, parentId) =>
            innerAcc.flatMap: current =>
              lift(repo.walkAll(parentId, Some(mc.parentIds(0)))).map: walked =>
                current ++ walked.map(_.id).toSet

  private def calculateTarget[V](
    keywords: List[Keyword],
    baseTag: Option[Tag[V]],
    allTags: IArray[Tag[V]]
  )(using
    scheme: ResolvableScheme[V],
    logger: Logger,
    verbose: Verbose,
    ord: Ordering[V]
  ): V =
    val targetRaws = keywords.collect { case Keyword.TargetSet(raw) => raw }
    val targets = targetRaws.flatMap: raw =>
      val norm = if raw.startsWith("v") || raw.startsWith("V") then raw.drop(1) else raw
      scheme.parse(norm).toOption

    val allTagsList = allTags.toList
    val allRepoFinals = allTagsList.filter(_.version.isFinal)

    val validTarget = TargetVersionCalculator.selectValidTarget(
      targets = targets,
      highestReachable = baseTag,
      highestRepo = allTagsList.sorted.lastOption,
      allRepoFinals = allRepoFinals,
      isHeadOnFinalTag = false
    )

    validTarget.getOrElse:
      baseTag match
        case Some(bt) =>
          val derived = TargetVersionCalculator.fromKeywords(bt.version, keywords)
          logger.verbose(s"Derived target: ${derived.show}", "Resolver")
          derived
        case None =>
          val highestRepo = allTagsList.sorted.lastOption
          val fallback = highestRepo match
            case Some(h) => h.version.core.incrementComponent(0)
            case None    => scheme.initialVersion.core
          logger.verbose(s"Fallback target: ${fallback.show}", "Resolver")
          fallback
  end calculateTarget
end Resolver
