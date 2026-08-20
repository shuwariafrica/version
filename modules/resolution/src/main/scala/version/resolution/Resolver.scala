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

import boilerplate.nullable.*

import scala.annotation.targetName
import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

import version.Derivation
import version.Directive
import version.ResolvableScheme
import version.VersionArithmetic
import version.VersionScheme
import version.resolution.domain.*
import version.resolution.logging.Logger

/** Answers what version a repository is at, for any scheme that supplies the capabilities to read one.
  *
  * This is the library's entry point. Each operation comes in two shapes: one reading a repository the caller holds,
  * which it never closes, and one taking a function to open the path `config` names, which it closes again before
  * returning. Diagnostics are opt-in: with no [[version.resolution.logging.Logger Logger]] in scope nothing is
  * recorded, and the operations taking one explicitly serve callers holding a logger as a value.
  */
object Resolver:

  /** Resolves the version `repo` is at, together with the target it is working towards and how it was arrived at. */
  @targetName("resolveAllRepository")
  def resolveAll[V](
    config: ResolutionConfig[V],
    repo: GitRepository
  )(using
    scheme: VersionScheme[V],
    arithmetic: VersionArithmetic[V],
    workflow: ResolvableScheme[V],
    logger: Logger
  ): Either[ResolutionError, ResolutionResult[V]] =
    logger.verbose(s"Begin resolution repoPath=${config.repoPath}, basisCommit=${config.basisCommit}", "Resolver")
    doResolve(config, repo)

  /** Resolves the version `repo` is at, recording diagnostics to `logger`. */
  @targetName("resolveAllRepositoryLogged")
  def resolveAll[V](
    config: ResolutionConfig[V],
    repo: GitRepository,
    logger: Logger
  )(using VersionScheme[V], VersionArithmetic[V], ResolvableScheme[V]): Either[ResolutionError, ResolutionResult[V]] =
    given Logger = logger
    resolveAll(config, repo)

  /** Resolves the version of the repository `open` yields for the path `config` names. */
  @targetName("resolveAllOpen")
  def resolveAll[V](
    config: ResolutionConfig[V],
    open: String => Either[GitError, GitRepository]
  )(using
    VersionScheme[V],
    VersionArithmetic[V],
    ResolvableScheme[V],
    Logger
  ): Either[ResolutionError, ResolutionResult[V]] =
    bracket(config, open)(resolveAll(config, _))

  /** Resolves the version of the repository `open` yields, recording diagnostics to `logger`. */
  @targetName("resolveAllOpenLogged")
  def resolveAll[V](
    config: ResolutionConfig[V],
    open: String => Either[GitError, GitRepository],
    logger: Logger
  )(using VersionScheme[V], VersionArithmetic[V], ResolvableScheme[V]): Either[ResolutionError, ResolutionResult[V]] =
    given Logger = logger
    resolveAll(config, open)

  /** The resolved version alone, for a caller with no use for the rest of [[resolveAll]]. */
  @targetName("resolveRepository")
  def resolve[V](
    config: ResolutionConfig[V],
    repo: GitRepository
  )(using VersionScheme[V], VersionArithmetic[V], ResolvableScheme[V], Logger): Either[ResolutionError, V] =
    resolveAll(config, repo).map(_.resolved)

  /** The resolved version `repo` is at, recording diagnostics to `logger`. */
  @targetName("resolveRepositoryLogged")
  def resolve[V](
    config: ResolutionConfig[V],
    repo: GitRepository,
    logger: Logger
  )(using VersionScheme[V], VersionArithmetic[V], ResolvableScheme[V]): Either[ResolutionError, V] =
    given Logger = logger
    resolve(config, repo)

  /** The resolved version of the repository `open` yields for the path `config` names. */
  @targetName("resolveOpen")
  def resolve[V](
    config: ResolutionConfig[V],
    open: String => Either[GitError, GitRepository]
  )(using VersionScheme[V], VersionArithmetic[V], ResolvableScheme[V], Logger): Either[ResolutionError, V] =
    resolveAll(config, open).map(_.resolved)

  /** The resolved version of the repository `open` yields, recording diagnostics to `logger`. */
  @targetName("resolveOpenLogged")
  def resolve[V](
    config: ResolutionConfig[V],
    open: String => Either[GitError, GitRepository],
    logger: Logger
  )(using VersionScheme[V], VersionArithmetic[V], ResolvableScheme[V]): Either[ResolutionError, V] =
    given Logger = logger
    resolve(config, open)

  /** Every release `repo` carries - each annotated tag `config.tagParser` reads as a version, paired with the commit
    * it names - ordered ascending by the scheme's precedence.
    */
  @targetName("releaseHistoryRepository")
  def releaseHistory[V](
    config: ResolutionConfig[V],
    repo: GitRepository
  )(using scheme: VersionScheme[V], logger: Logger): Either[ResolutionError, List[Release[V]]] =
    logger.verbose(s"Listing release history repoPath=${config.repoPath}", "Resolver")
    given Ordering[V] = scheme.precedence
    doReleaseHistory(config, repo)

  /** Every release `repo` carries, recording diagnostics to `logger`. */
  @targetName("releaseHistoryRepositoryLogged")
  def releaseHistory[V](
    config: ResolutionConfig[V],
    repo: GitRepository,
    logger: Logger
  )(using VersionScheme[V]): Either[ResolutionError, List[Release[V]]] =
    given Logger = logger
    releaseHistory(config, repo)

  /** Every release the repository `open` yields for the path `config` names carries. */
  @targetName("releaseHistoryOpen")
  def releaseHistory[V](
    config: ResolutionConfig[V],
    open: String => Either[GitError, GitRepository]
  )(using VersionScheme[V], Logger): Either[ResolutionError, List[Release[V]]] =
    bracket(config, open)(releaseHistory(config, _))

  /** Every release the repository `open` yields carries, recording diagnostics to `logger`. */
  @targetName("releaseHistoryOpenLogged")
  def releaseHistory[V](
    config: ResolutionConfig[V],
    open: String => Either[GitError, GitRepository],
    logger: Logger
  )(using VersionScheme[V]): Either[ResolutionError, List[Release[V]]] =
    given Logger = logger
    releaseHistory(config, open)

  private inline def lift[A](r: Either[GitError, A]): Either[ResolutionError, A] =
    r.left.map(ResolutionError.GitFailure.apply)

  private def bracket[V, A](
    config: ResolutionConfig[V],
    open: String => Either[GitError, GitRepository]
  )(read: GitRepository => Either[ResolutionError, A]): Either[ResolutionError, A] =
    lift(open(config.repoPath)).flatMap: repo =>
      try read(repo)
      finally repo.close()

  private def doResolve[V](
    config: ResolutionConfig[V],
    repo: GitRepository
  )(using
    scheme: VersionScheme[V],
    arithmetic: VersionArithmetic[V],
    workflow: ResolvableScheme[V],
    logger: Logger
  ): Either[ResolutionError, ResolutionResult[V]] =
    given Ordering[V] = scheme.precedence
    boundary:
      def ok[A](e: Either[ResolutionError, A]): A = e match
        case Right(value) => value
        case Left(error)  => break(Left(error))

      val root = repo.workTree.getOrElse(repo.gitDir)

      ok(lift(repo.head)) match
        case None =>
          config.basisCommit.foreach(rev => break(Left(ResolutionError.GitFailure(GitError.RevisionNotFound(rev)))))
          logger.verbose("Empty repository - returning initial version", "Resolver")
          val initial = workflow.initialVersion
          Right(ResolutionResult(initial, initial, ResolutionMode.Concrete, None, None, root))

        case Some(headSha) =>
          val basis = config.basisCommit match
            case Some(rev) => ok(lift(repo.resolve(rev)))
            case None      => headSha

          val branchName = ok(lift(repo.branch))
          val isClean = ok(lift(repo.clean))
          val rawTags = ok(lift(repo.tags))

          val versionTags: IArray[Tag[V]] = rawTags
            .filter(_.kind == TagKind.Annotated)
            .flatMap(rt => config.tagParser(rt.name).map(v => Tag(rt.name, rt.commit, v)))

          logger.verbose(s"Parsed ${versionTags.length} version tag(s)", "Resolver")

          val reachableCommits = ok(lift(repo.reachableTags(basis, versionTags.map(_.commit).toSet)))
          val reachableTags = versionTags.filter(t => reachableCommits.contains(t.commit))

          reachableTags.filter(_.commit == basis).sorted.lastOption.filter(_ => isClean) match
            case Some(taggedTag) =>
              val tagged = taggedTag.version
              val basisCommit = ok(lift(repo.loadCommit(basis)))
              logger.verbose(s"Basis is tagged ${scheme.show(tagged)} and the working tree is clean", "Resolver")
              val release = Release(tagged, taggedTag.name, ok(lift(repo.loadTagger(taggedTag.name))), basisCommit)
              Right(ResolutionResult(tagged, tagged, ResolutionMode.Concrete, Some(basisCommit), Some(release), root))

            case None =>
              val isDirty = !isClean
              val baseTag = reachableTags.sorted.lastOption
              logger.verbose(s"Development version (dirty=$isDirty, base=${baseTag.map(_.name)})", "Resolver")

              val scanRange = ok(lift(repo.walkAll(basis, baseTag.map(_.commit))))
              val mergeExclusions = ok(computeMergeExclusions(scanRange, repo))
              val directives = extractDirectives(scanRange, mergeExclusions)
              val derivation = derive(directives, baseTag, versionTags)
              derivation.discarded.foreach(e => logger.verbose(s"Directive discarded: ${e.getMessage.unsafe}", "Resolver"))
              logger.verbose(s"Target: ${scheme.show(derivation.target)}", "Resolver")

              val fpCommits = ok(lift(repo.walkFirstParent(basis, baseTag.map(_.commit))))
              val basisCommit = ok(lift(repo.loadCommit(basis)))
              val devMeta = MetadataBuilder.assemble(
                branchOverride = config.branchOverride,
                branchDetected = branchName,
                commitSha = Some(basis.value),
                commitCount = fpCommits.count(!_.isMerge),
                commitTime = Some(basisCommit.commitTime),
                prNumber = config.prNumber,
                isDirty = isDirty
              )
              logger.verbose(s"Metadata assembled: $devMeta", "Resolver")

              val resolved = workflow.developmentVersion(derivation.target, devMeta)
              val base = baseTag.map: bt =>
                Release(bt.version, bt.name, ok(lift(repo.loadTagger(bt.name))), ok(lift(repo.loadCommit(bt.commit))))
              Right(ResolutionResult(resolved, derivation.target, ResolutionMode.Development, Some(basisCommit), base, root))
          end match
      end match
  end doResolve

  private def doReleaseHistory[V](
    config: ResolutionConfig[V],
    repo: GitRepository
  )(using Ordering[V]): Either[ResolutionError, List[Release[V]]] =
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

  // A project with no release behind the basis has nothing to advance from, so the scheme's own starting version is
  // its first target - which a version named outright may still raise.
  private def derive[V](
    directives: List[Directive],
    baseTag: Option[Tag[V]],
    allTags: IArray[Tag[V]]
  )(using VersionScheme[V], VersionArithmetic[V], ResolvableScheme[V]): Derivation[V] =
    val repository = allTags.toList.map(_.version)
    baseTag match
      case Some(tag) => Derivation.target(tag.version, directives, Some(tag.version), repository)
      case None      => Derivation.target(directives, repository)

  private def extractDirectives[V](
    commits: IArray[RawCommit],
    mergeExclusions: Set[CommitSha]
  )(using ResolvableScheme[V]): List[Directive] =
    val parsed = commits.map(c => (c, Directive.parse[V](c.message)))
    val exclusions = buildDirectExclusions(parsed, commits) ++ mergeExclusions

    parsed
      .flatMap: (commit, directives) =>
        val ignoresSelf = directives.exists:
          case Directive.IgnoreSelf => true
          case _                    => false
        if exclusions.contains(commit.id) || ignoresSelf then Nil
        else
          directives.filter:
            case Directive.IgnoreSelf | Directive.IgnoreMerged | Directive.IgnoreCommits(_) | Directive.IgnoreRange(_, _) => false
            case Directive.Emit(_) | Directive.Target(_)                                                                  => true
      .toList

  private def buildDirectExclusions(
    parsed: IArray[(RawCommit, List[Directive])],
    allCommits: IArray[RawCommit]
  ): Set[CommitSha] =
    val commitsByPrefix = allCommits.map(c => (c.id.value, c.id)).toMap

    parsed.foldLeft(Set.empty[CommitSha]):
      case (exclusions, (_, directives)) =>
        directives.foldLeft(exclusions):
          case (acc, Directive.IgnoreCommits(ids)) =>
            ids.foldLeft(acc): (a, prefix) =>
              commitsByPrefix.keys
                .filter(_.startsWith(prefix))
                .foldLeft(a): (a2, fullSha) =>
                  a2 + CommitSha(fullSha)
          case (acc, Directive.IgnoreRange(from, to)) =>
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
  private def computeMergeExclusions[V](
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

  private inline def hasIgnoreMerged[V](mc: RawCommit)(using ResolvableScheme[V]): Boolean =
    Directive
      .parse[V](mc.message)
      .exists:
        case Directive.IgnoreMerged => true
        case _                      => false

end Resolver
