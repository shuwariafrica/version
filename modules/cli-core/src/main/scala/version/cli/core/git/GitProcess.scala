package version.cli.core.git
import version.PreRelease
import version.cli.core.ResolutionError
import version.cli.core.ResolutionError.GitCommandFailed
import version.cli.core.ResolutionError.InvalidShaLength
import version.cli.core.ResolutionError.NotAGitRepository
import version.cli.core.domain.*
import version.parser.VersionParser

/** Git implementation using os-lib and plumbing commands only. */
final class GitProcess(repoPath: os.Path) extends Git:
  import GitProcess.*

  private def run(args: List[String], check: Boolean): Either[ResolutionError, os.CommandResult] =
    val cmd = List("git") ++ args
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
    if os.exists(repoPath / ".git") || os.exists(repoPath / "HEAD") then Right(())
    else Left(NotAGitRepository(repoPath))

  def resolveRev(rev: String): Either[ResolutionError, CommitSha] =
    for
      _ <- checkRepo()
      res <- run(List("rev-parse", s"${rev}^{commit}"), check = true)
    yield CommitSha(res.out.text().trim)

  def getAbbreviatedSha(sha: CommitSha, length: Int): Either[ResolutionError, String] =
    for
      _ <- checkRepo()
      _ <- if length < 7 || length > 40 then Left(InvalidShaLength(length)) else Right(())
      r <- run(List("rev-parse", s"--short=$length", sha.value), check = true)
    yield r.out.text().trim.toLowerCase

  def listAllTags(): Either[ResolutionError, List[Tag]] =
    for
      _ <- checkRepo()
      // Use null separators for robust parsing and include object type for cross-version support.
      // Older git may not support %(peeled); resolve annotated tags via rev-parse ^{commit} instead.
      r <- run(
        List("for-each-ref", "--format=%(refname:short)%00%(objecttype)%00%(objectname)", "refs/tags"),
        check = true
      )
      tagsE = r.out.text().linesIterator.toList.foldLeft(Right(List.empty): Either[ResolutionError, List[Tag]]) { (accE, line) =>
        val parts = line.split("\u0000", -1).toList
        parts match
          case name :: objType :: obj :: Nil =>
            val commitE =
              if objType == "tag" then run(List("rev-parse", s"${name}^{commit}"), check = true).map(_.out.text().trim)
              else Right(obj)
            for
              acc <- accE
              commit <- commitE
            yield parseTag(name, commit).fold(acc)(t => acc :+ t)
          case _ => accE
      }
      tags <- tagsE
    yield tags

  def findReachableTags(from: CommitSha): Either[ResolutionError, List[Tag]] =
    listAllTags().flatMap { tags =>
      tags.foldLeft(Right(List.empty): Either[ResolutionError, List[Tag]]) { (accE, t) =>
        for
          acc <- accE
          r <- ok(List("merge-base", "--is-ancestor", t.commitSha.value, from.value))
        yield if r then acc :+ t else acc
      }
    }

  def isWorkingDirectoryClean(): Either[ResolutionError, Boolean] =
    for
      _ <- checkRepo()
      trackedClean <- run(List("diff-index", "--quiet", "HEAD", "--"), check = false).map(_.exitCode == 0)
      untrackedEmpty <- run(List("ls-files", "--others", "--exclude-standard"), check = false).map(_.out.text().trim.isEmpty)
    yield trackedClean && untrackedEmpty

  def getBranchName(): Either[ResolutionError, Option[String]] =
    for
      _ <- checkRepo()
      r <- run(List("symbolic-ref", "--quiet", "--short", "HEAD"), check = false)
    yield if r.exitCode == 0 then Some(r.out.text().trim) else None

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
    yield if nLong >= Int.MaxValue then Int.MaxValue else nLong.toInt
end GitProcess

private object GitProcess:

  def parseTag(name: String, commit: String): Option[Tag] =
    val raw = if name.startsWith("v") || name.startsWith("V") then name.drop(1) else name
    VersionParser.parse(raw).toOption.map { v =>
      Tag(TagName(name), CommitSha(commit.toLowerCase), v)
    }
