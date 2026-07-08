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

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

import version.ResolvableScheme
import version.Version
import version.resolution.domain.*
import version.resolution.logging.Logger
import version.resolution.logging.Verbose
import version.resolution.parsing.KeywordParser

/** Version resolution engine.
  *
  * Scheme-generic across `[V <: Version : ResolvableScheme]`: drives the standard resolution algorithm against any
  * version type for which a [[version.ResolvableScheme ResolvableScheme]] instance exists.
  */
object Resolver:

  private inline def lift[A](r: Either[GitError, A]): Either[ResolutionError, A] =
    r.left.map(ResolutionError.GitFailure.apply)

  /** Resolves the repository version from Git state, returning the resolved version together with
    * the target it was derived from and the resolution mode.
    */
  def resolveAll[V <: Version](
    config: ResolutionConfig[V],
    open: String => Either[GitError, GitRepository]
  )(using
    scheme: ResolvableScheme[V],
    logger: Logger,
    v: Verbose
  ): Either[ResolutionError, ResolutionResult[V]] =
    given Ordering[V] = scheme.ordering
    logger.verbose(s"Begin resolution repoPath=${config.repoPath}, basisCommit=${config.basisCommit}", "Resolver")

    lift(open(config.repoPath)).flatMap: repo =>
      try
        doResolve(config, repo)
      finally
        repo.close()

  /** Resolves the repository version from Git state. Thin wrapper over [[resolveAll]]. */
  def resolve[V <: Version](
    config: ResolutionConfig[V],
    open: String => Either[GitError, GitRepository]
  )(using
    ResolvableScheme[V],
    Logger,
    Verbose
  ): Either[ResolutionError, V] =
    resolveAll(config, open).map(_.resolved)

  /** Lists the full release history: every annotated version tag the scheme parses, paired with the commit it points
    * to, ordered ascending by version.
    */
  def releaseHistory[V <: Version](
    config: ResolutionConfig[V],
    open: String => Either[GitError, GitRepository]
  )(using
    scheme: ResolvableScheme[V],
    logger: Logger,
    v: Verbose
  ): Either[ResolutionError, List[Release[V]]] =
    given Ordering[V] = scheme.ordering
    logger.verbose(s"Listing release history repoPath=${config.repoPath}", "Resolver")
    lift(open(config.repoPath)).flatMap: repo =>
      try
        doReleaseHistory(config, repo)
      finally
        repo.close()

  private def doResolve[V <: Version](
    config: ResolutionConfig[V],
    repo: GitRepository
  )(using
    scheme: ResolvableScheme[V],
    logger: Logger,
    verbose: Verbose,
    ord: Ordering[V]
  ): Either[ResolutionError, ResolutionResult[V]] =
    boundary:
      def ok[A](e: Either[ResolutionError, A]): A = e match
        case Right(v)    => v
        case Left(error) => break(Left(error))

      ok(lift(repo.head)) match
        case None =>
          if config.basisCommit != "HEAD" then break(Left(ResolutionError.GitFailure(GitError.RevisionNotFound(config.basisCommit))))
          logger.verbose("Empty repository - returning initial version", "Resolver")
          val initial = scheme.initialVersion
          Right(ResolutionResult(initial, initial, ResolutionMode.Concrete, None, None))

        case Some(headSha) =>
          val basis =
            if config.basisCommit == "HEAD" then headSha
            else ok(lift(repo.resolve(config.basisCommit)))

          val branchName = ok(lift(repo.branch))
          val isClean = ok(lift(repo.clean))
          val rawTags = ok(lift(repo.tags))

          val versionTags: IArray[Tag[V]] = rawTags
            .filter(_.kind == TagKind.Annotated)
            .flatMap(rt => config.tagParser(rt.name).map(v => Tag(rt.name, rt.commit, v)))

          logger.verbose(s"Parsed ${versionTags.length} version tag(s)", "Resolver")

          val reachableCommits = ok(lift(repo.reachableTags(basis, versionTags.map(_.commit).toSet)))
          val reachableTags = versionTags.filter(t => reachableCommits.contains(t.commit))

          val tagsOnBasis = reachableTags.filter(_.commit == basis)
          val highestOnBasis = tagsOnBasis.sorted.lastOption
          if highestOnBasis.isDefined && isClean then
            val taggedTag = highestOnBasis.get
            val tagged = taggedTag.version
            val basisCommit = ok(lift(repo.loadCommit(basis)))
            logger.verbose(s"Mode 1: HEAD tagged with ${tagged.show} and clean", "Resolver")
            val release = Release(tagged, taggedTag.name, ok(lift(repo.loadTagger(taggedTag.name))), basisCommit)
            Right(ResolutionResult(tagged, tagged, ResolutionMode.Concrete, Some(basisCommit), Some(release)))
          else
            val isDirty = !isClean
            val baseTag = reachableTags.sorted.lastOption
            logger.verbose(s"Mode 2: development version (dirty=$isDirty, base=${baseTag.map(_.name)})", "Resolver")

            val scanRange = ok(lift(repo.walkAll(basis, baseTag.map(_.commit))))
            val mergeExclusions = ok(computeMergeExclusions(scanRange, repo))
            val keywords = extractKeywords(scanRange, mergeExclusions)
            val targetCore = calculateTarget(keywords, baseTag, versionTags)
            val fpCommits = ok(lift(repo.walkFirstParent(basis, baseTag.map(_.commit))))
            val commitCount = fpCommits.count(!_.isMerge)
            val basisCommit = ok(lift(repo.loadCommit(basis)))
            val devMeta = MetadataBuilder.assemble(
              branchOverride = config.branchOverride,
              branchDetected = branchName,
              commitSha = Some(basis.value),
              commitCount = commitCount,
              commitTime = Some(basisCommit.commitTime),
              prNumber = config.prNumber,
              isDirty = isDirty
            )
            logger.verbose(s"Metadata assembled: $devMeta", "Resolver")
            val resolved = scheme.developmentVersion(targetCore, devMeta)
            val base =
              baseTag.map(bt => Release(bt.version, bt.name, ok(lift(repo.loadTagger(bt.name))), ok(lift(repo.loadCommit(bt.commit)))))
            Right(ResolutionResult(resolved, targetCore, ResolutionMode.Development, Some(basisCommit), base))
          end if
      end match
  end doResolve

  private def doReleaseHistory[V <: Version](
    config: ResolutionConfig[V],
    repo: GitRepository
  )(using ord: Ordering[V]): Either[ResolutionError, List[Release[V]]] =
    boundary:
      def ok[A](e: Either[ResolutionError, A]): A = e match
        case Right(value) => value
        case Left(error)  => break(Left(error))

      val releases = ok(lift(repo.tags)).toList
        .filter(_.kind == TagKind.Annotated)
        .flatMap(rt =>
          config
            .tagParser(rt.name)
            .map(version => Release(version, rt.name, ok(lift(repo.loadTagger(rt.name))), ok(lift(repo.loadCommit(rt.commit))))))
        .sorted
      Right(releases)
  end doReleaseHistory

  private def extractKeywords[V <: Version](
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

  // scalafix:off
  private def computeMergeExclusions[V <: Version](
    commits: IArray[RawCommit],
    repo: GitRepository
  )(using ResolvableScheme[V]): Either[ResolutionError, Set[CommitSha]] =
    // Use a mutable HashSet then freeze: avoids the persistent-Set tree-node allocation a `++=` chain would incur per commit.
    boundary:
      val exclusions = mutable.HashSet.empty[CommitSha]
      var i = 0
      while i < commits.length do
        val mc = commits(i)
        if mc.isMerge && hasIgnoreMerged[V](mc) then
          val firstParent = mc.parentIds(0)
          var p = 1
          while p < mc.parentIds.length do
            lift(repo.walkAll(mc.parentIds(p), Some(firstParent))) match
              case Left(err)     => break(Left(err))
              case Right(walked) =>
                var w = 0
                while w < walked.length do
                  exclusions += walked(w).id
                  w += 1
            p += 1
        i += 1
      Right(exclusions.toSet)
  end computeMergeExclusions
  // scalafix:on

  private inline def hasIgnoreMerged[V <: Version](mc: RawCommit)(using ResolvableScheme[V]): Boolean =
    KeywordParser
      .parse[V](mc.message)
      .exists:
        case Keyword.IgnoreMerged => true
        case _                    => false

  private def calculateTarget[V <: Version](
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
    val highestRepo = allTagsList.sorted.lastOption

    val validTarget = TargetVersionCalculator.selectValidTarget(
      targets = targets,
      highestReachable = baseTag,
      highestRepo = highestRepo,
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
          val fallback = highestRepo match
            case Some(h) => h.version.core.keywordBump(0)
            case None    => scheme.initialVersion.core
          logger.verbose(s"Fallback target: ${fallback.show}", "Resolver")
          fallback
  end calculateTarget
end Resolver
