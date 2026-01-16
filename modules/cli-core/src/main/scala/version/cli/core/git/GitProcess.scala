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
package version.cli.core.git

import version.*
import version.cli.core.ResolutionError
import version.cli.core.ResolutionError.GitCommandFailed
import version.cli.core.ResolutionError.InvalidShaLength
import version.cli.core.ResolutionError.NotAGitRepository
import version.cli.core.domain.*
import version.cli.core.logging.Logger

/** Git implementation using os-lib and plumbing commands.
  *
  * Supports repositories where the working directory is a subdirectory of the repository root.
  */
final class GitProcess(repoPath: os.Path)(using logger: Logger, isVerbose: Boolean, reader: Version.Read[String]) extends Git:
  import GitProcess.*

  private def run(args: List[String], check: Boolean): Either[ResolutionError, os.CommandResult] =
    val cmd = List("git") ++ args
    logger.verbose(s"git ${args.mkString(" ")}", "Git")
    try
      val res = os.proc(cmd).call(cwd = repoPath, check = false)
      if check && res.exitCode != 0 then Left(GitCommandFailed(args, res.exitCode, res.out.text(), res.err.text()))
      else Right(res)
    catch
      case t: Throwable =>
        Left(GitCommandFailed(args, -1, "", Option(t.getMessage).getOrElse(t.toString)))

  private def ok(args: List[String]): Either[ResolutionError, Boolean] = run(args, check = false).map(_.exitCode == 0)

  // Basic repository presence heuristic; commands will still fail with details if not a repo
  private def checkRepo(): Either[ResolutionError, Unit] =
    // Use git plumbing so that any subdirectory within a repository is supported.
    // "rev-parse --is-inside-work-tree" prints "true" when inside a work tree and exits 0.
    if !os.exists(repoPath) then Left(NotAGitRepository(repoPath))
    else
      run(List("rev-parse", "--is-inside-work-tree"), check = false).flatMap { res =>
        val txt = res.out.text().trim.toLowerCase
        if res.exitCode == 0 && txt == "true" then Right(())
        else Left(NotAGitRepository(repoPath))
      }

  def resolveRev(rev: String): Either[ResolutionError, CommitSha] =
    for
      _ <- checkRepo()
      res <- run(List("rev-parse", s"${rev}^{commit}"), check = true)
      sha = CommitSha(res.out.text().trim)
      _ = logger.verbose(s"Resolved $rev -> ${sha.value}", "Git")
    yield sha

  def getAbbreviatedSha(sha: CommitSha, length: Int): Either[ResolutionError, String] =
    for
      _ <- checkRepo()
      _ <- if length < 7 || length > 40 then Left(InvalidShaLength(length)) else Right(())
      r <- run(List("rev-parse", s"--short=$length", sha.value), check = true)
      abbr = r.out.text().trim.toLowerCase
      _ = logger.verbose(s"Abbreviated ${sha.value} ($length) -> $abbr", "Git")
    yield abbr

  def listAllTags(): Either[ResolutionError, List[Tag]] =
    for
      _ <- checkRepo()
      // Use null separators for robust parsing and include object type for cross-version support.
      // Only annotated tags (objecttype == "tag") are considered valid version tags per spec.
      // Lightweight tags are silently ignored.
      r <- run(
        List("for-each-ref", "--format=%(refname:short)%00%(objecttype)%00%(objectname)", "refs/tags"),
        check = true
      )
      tagsE = r.out.text().linesIterator.toList.foldLeft(Right(List.empty): Either[ResolutionError, List[Tag]]) { (accE, line) =>
        val parts = line.split("\u0000", -1).toList
        parts match
          case name :: objType :: obj :: Nil =>
            // Only process annotated tags (objecttype == "tag"); lightweight tags have objecttype == "commit"
            if objType == "tag" then
              val commitE = run(List("rev-parse", s"${name}^{commit}"), check = true).map(_.out.text().trim)
              for
                acc <- accE
                commit <- commitE
              yield parseTag(name, commit).fold(acc)(t => acc :+ t)
            else
              // Lightweight tag - silently ignored per spec
              logger.verbose(s"Ignoring lightweight tag: $name", "Git")
              accE
          case _ => accE
      }
      tags <- tagsE
      _ = logger.verbose(s"Found ${tags.size} parsed SemVer annotated tag(s)", "Git")
    yield tags

  def findReachableTags(from: CommitSha): Either[ResolutionError, List[Tag]] =
    listAllTags().flatMap { tags =>
      tags.foldLeft(Right(List.empty): Either[ResolutionError, List[Tag]]) { (accE, t) =>
        for
          acc <- accE
          r <- ok(List("merge-base", "--is-ancestor", t.commitSha.value, from.value))
        yield
          if r then
            logger.verbose(s"Tag ${t.name.value} reachable from ${from.value}", "Git")
            acc :+ t
          else acc
      }
    }

  def isWorkingDirectoryClean(): Either[ResolutionError, Boolean] =
    for
      _ <- checkRepo()
      trackedClean <- run(List("diff-index", "--quiet", "HEAD", "--"), check = false).map(_.exitCode == 0)
      untrackedEmpty <- run(List("ls-files", "--others", "--exclude-standard"), check = false).map(_.out.text().trim.isEmpty)
      clean = trackedClean && untrackedEmpty
      _ = logger.verbose(s"Worktree clean=$clean (tracked=$trackedClean, untrackedEmpty=$untrackedEmpty)", "Git")
    yield clean

  def getBranchName(): Either[ResolutionError, Option[String]] =
    for
      _ <- checkRepo()
      r <- run(List("symbolic-ref", "--quiet", "--short", "HEAD"), check = false)
      branch = if r.exitCode == 0 then Some(r.out.text().trim) else None
      _ = logger.verbose(s"Branch resolved: ${branch.getOrElse("<detached>")}", "Git")
    yield branch

  def getCommitsSince(to: CommitSha, fromExclusive: Option[CommitSha]): Either[ResolutionError, List[Commit]] =
    for
      _ <- checkRepo()
      // Use %H for the SHA, %B for subject+body, and a record separator 0x1E for robust splitting
      fmt = "%H%n%B%x1e"
      args = fromExclusive match
        case Some(from) => List("rev-list", s"--format=$fmt", to.value, s"^${from.value}")
        case None       => List("rev-list", s"--format=$fmt", to.value)
      r <- run(args, check = true)
      blocks = r.out.text().split("\u001e").toList.map(_.trim).filter(_.nonEmpty)
      commits = blocks.flatMap { block =>
        val it = block.linesIterator
        if !it.hasNext then None
        else
          val first = it.next().trim
          // rev-list emits "commit <sha>" headers when --format is used; handle both cases robustly
          val (sha, restIt) =
            if first.startsWith("commit ") then if it.hasNext then (it.next().trim, it) else (first.stripPrefix("commit ").trim, it)
            else (first, it)
          val msg = restIt.mkString("\n").trim
          Some(Commit(CommitSha(sha), msg))
      }
      _ = logger.verbose(s"Collected ${commits.size} commit(s) since ${fromExclusive.fold("<root>")(_.value)}", "Git")
    yield commits

  def countCommitsSince(to: CommitSha, fromExclusive: Option[CommitSha]): Either[ResolutionError, Int] =
    for
      _ <- checkRepo()
      r <- run(
        fromExclusive match
          case Some(from) => List("rev-list", "--first-parent", "--no-merges", "--count", to.value, s"^${from.value}")
          case None       => List("rev-list", "--first-parent", "--no-merges", "--count", to.value),
        check = true
      )
      nLong = scala.util.Try(r.out.text().trim.toLong).getOrElse(Long.MaxValue)
      n = if nLong >= Int.MaxValue then Int.MaxValue else nLong.toInt
      _ = logger.verbose(s"Counted $n commit(s) since ${fromExclusive.fold("<root>")(_.value)}", "Git")
    yield n
end GitProcess

private[git] object GitProcess:

  def parseTag(name: String, commit: String)(using reader: Version.Read[String]): Option[Tag] =
    val raw = if name.startsWith("v") || name.startsWith("V") then name.drop(1) else name
    reader.toVersion(raw).toOption.map { v =>
      Tag(TagName(name), CommitSha(commit.toLowerCase), v)
    }
