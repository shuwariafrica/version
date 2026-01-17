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

/** [[Git]] implementation using os-lib and Git plumbing commands. */
final class GitProcess(repoPath: os.Path)(using logger: Logger, isVerbose: Boolean, reader: Version.Read[String]) extends Git:

  private def run(args: List[String], check: Boolean): Either[ResolutionError, os.CommandResult] =
    val cmd = List("git") ++ args
    logger.verbose(s"git ${args.mkString(" ")}", "Git")
    try
      val res = os.proc(cmd).call(cwd = repoPath, check = false, stderr = os.Pipe)
      if check && res.exitCode != 0 then Left(GitCommandFailed(args, res.exitCode, res.out.text(), res.err.text()))
      else Right(res)
    catch
      case t: Throwable =>
        Left(GitCommandFailed(args, -1, "", Option(t.getMessage).getOrElse(t.toString)))

  // Cache repository validation at construction time to eliminate redundant subprocess calls.
  // This is evaluated lazily on first access but memoised for all subsequent calls.
  private lazy val repoValidation: Either[ResolutionError, Unit] =
    if !os.exists(repoPath) then Left(NotAGitRepository(repoPath))
    else
      run(List("rev-parse", "--is-inside-work-tree"), check = false).flatMap { res =>
        val txt = res.out.text().trim.toLowerCase
        if res.exitCode == 0 && txt == "true" then Right(())
        else Left(NotAGitRepository(repoPath))
      }

  private def checkRepo(): Either[ResolutionError, Unit] = repoValidation

  def resolveRev(rev: String): Either[ResolutionError, CommitSha] =
    for
      _ <- checkRepo()
      res <- run(List("rev-parse", s"$rev^{commit}"), check = true)
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
      // Format: %(refname:short) %(objecttype) %(*objectname) - where *objectname is the dereferenced commit for annotated tags.
      // Only annotated tags (objecttype == "tag") are considered valid version tags per spec.
      // Lightweight tags are silently ignored.
      r <- run(
        List("for-each-ref", "--format=%(refname:short)%00%(objecttype)%00%(*objectname)", "refs/tags"),
        check = true
      )
      tags = r.out.text().linesIterator.toList.flatMap { line =>
        val parts = line.split("\u0000", -1).toList
        parts match
          case name :: objType :: derefCommit :: Nil if objType == "tag" && derefCommit.nonEmpty =>
            // Annotated tag: *objectname gives the dereferenced commit directly (no extra subprocess call)
            parseTag(name, derefCommit)
          case name :: objType :: _ :: Nil if objType != "tag" =>
            // Lightweight tag - silently ignored per spec
            logger.verbose(s"Ignoring lightweight tag: $name", "Git")
            None
          case _ => None
      }
      _ = logger.verbose(s"Found ${tags.size} parsed SemVer annotated tag(s)", "Git")
    yield tags

  def findReachableTags(from: CommitSha): Either[ResolutionError, List[Tag]] =
    for
      _ <- checkRepo()
      // Use --merged to filter tags reachable from `from` in a single subprocess call.
      r <- run(
        List("for-each-ref", s"--merged=${from.value}", "--format=%(refname:short)%00%(objecttype)%00%(*objectname)", "refs/tags"),
        check = true
      )
      tags = r.out.text().linesIterator.toList.flatMap { line =>
        val parts = line.split("\u0000", -1).toList
        parts match
          case name :: objType :: derefCommit :: Nil if objType == "tag" && derefCommit.nonEmpty =>
            val tagOpt = parseTag(name, derefCommit)
            tagOpt.foreach(t => logger.verbose(s"Tag ${t.name.value} reachable from ${from.value}", "Git"))
            tagOpt
          case name :: objType :: _ :: Nil if objType != "tag" =>
            logger.verbose(s"Ignoring lightweight tag: $name", "Git")
            None
          case _ => None
      }
      _ = logger.verbose(s"Found ${tags.size} reachable SemVer annotated tag(s) from ${from.value}", "Git")
    yield tags

  def isWorkingDirectoryClean(): Either[ResolutionError, Boolean] =
    for
      _ <- checkRepo()
      // diff-index detects staged/unstaged changes; exit 0 = clean, 1 = dirty
      diffRes <- run(List("diff-index", "--quiet", "HEAD", "--"), check = false)
      trackedClean = diffRes.exitCode == 0
      // ls-files --others detects untracked files not excluded by .gitignore
      untrackedRes <- run(List("ls-files", "--others", "--exclude-standard"), check = true)
      untrackedClean = untrackedRes.out.text().trim.isEmpty
      clean = trackedClean && untrackedClean
      _ = logger.verbose(s"Worktree clean=$clean (tracked=$trackedClean, untracked=$untrackedClean)", "Git")
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
      // Include parent count (%P gives space-separated parent SHAs) for batch merge detection.
      // Format: %H (SHA), %P (parents), %B (message body), then record separator 0x1E.
      fmt = "%H%n%P%n%B%x1e"
      args = fromExclusive match
        case Some(from) => List("rev-list", s"--format=$fmt", to.value, s"^${from.value}")
        case None       => List("rev-list", s"--format=$fmt", to.value)
      r <- run(args, check = true)
      blocks = r.out.text().split("\u001e").toList.map(_.trim).filter(_.nonEmpty)
      commits = blocks.flatMap { block =>
        val lines = block.linesIterator.toList
        lines match
          case Nil           => None
          case first :: rest =>
            // rev-list emits "commit <sha>" headers when --format is used; handle both cases robustly
            val (sha, remaining) =
              if first.startsWith("commit ") then
                rest match
                  case shaLine :: tail => (shaLine.trim, tail)
                  case Nil             => (first.stripPrefix("commit ").trim, Nil)
              else (first.trim, rest)
            // Next line is parents (space-separated); may be empty for root commit
            val (parentCount, msgLines) = remaining match
              case parentsLine :: tail =>
                val parents = parentsLine.trim.split("\\s+").filter(_.nonEmpty)
                (parents.length, tail)
              case Nil => (0, Nil)
            val msg = msgLines.mkString("\n").trim
            Some(Commit(CommitSha(sha), msg, parentCount))
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

  def isMergeCommit(sha: CommitSha): Either[ResolutionError, Boolean] =
    for
      _ <- checkRepo()
      r <- run(List("rev-parse", s"${sha.value}^2"), check = false)
    yield r.exitCode == 0

  def getMergedCommits(mergeSha: CommitSha): Either[ResolutionError, Set[CommitSha]] =
    for
      _ <- checkRepo()
      // Commits reachable from merge but not from first parent
      // git rev-list <merge>^2 --not <merge>^1
      // This will fail for non-merge commits (no ^2 parent), so we handle that gracefully
      r <- run(List("rev-list", s"${mergeSha.value}^2", "--not", s"${mergeSha.value}^1"), check = false)
      shas =
        if r.exitCode != 0 then Set.empty[CommitSha] // Not a merge commit
        else r.out.text().split('\n').map(_.trim.toLowerCase).filter(_.nonEmpty).toSet.map(CommitSha(_))
      _ = if shas.nonEmpty then logger.verbose(s"Merged commits from ${mergeSha.value}: ${shas.size}", "Git")
    yield shas

  private inline def parseTag(name: String, commit: String)(using reader: Version.Read[String]): Option[Tag] =
    val raw = if name.startsWith("v") || name.startsWith("V") then name.drop(1) else name
    reader.toVersion(raw).toOption.map { v =>
      Tag(TagName(name), CommitSha(commit.toLowerCase), v)
    }
end GitProcess
