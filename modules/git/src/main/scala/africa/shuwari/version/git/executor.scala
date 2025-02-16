package africa.shuwari.version.git

import zio.prelude._

import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import scala.sys.process._
import scala.util.Try

import africa.shuwari.version.codecs.VersionReader

object executor:

  private final case class Result(exitCode: Int, stdOut: String, stdErr: String)

  private def execute(workDir: Option[Path], env: List[(String, String)], commands: String*): Either[Throwable, Result] =
    scribe.debug(s"Executing command: ${commands.mkString(" ")} in workDir: $workDir with env: $env")

    val (stdout, stderr) = (new StringBuilder, new StringBuilder)

    def append(builder: StringBuilder)(line: String): Unit =
      val l = line + System.lineSeparator
      builder.append(l)
      scribe.debug(s"Process output: $l")

    val result =
      Try {
        val process =
          Process(commands, workDir.map(_.toFile.nn), env*)
            .run(ProcessLogger(append(stdout), append(stderr)))
        Result(process.exitValue(), stdout.toString, stderr.toString)
      }.toEither

    result match
      case Right(value) =>
        scribe.debug(
          s"Command execution completed. Exit code: ${value.exitCode}, stdout: ${value.stdOut}, stderr: ${value.stdErr}")
      case Left(error) =>
        scribe.error(s"Command execution failed. Error: ${error.getMessage}", error)
    result

  end execute

  def readVersion(repository: Path, gitExecutable: Option[Path]): Either[Throwable, GitStatus] =
    scribe.info(s"Reading Git version for repository '$repository'${gitExecutable.fold("")(exe => s" using provided git executable $exe")}.")

    val git: String = gitExecutable.map(_.nn.toString.nn).getOrElse("git").nn

    val result = for
      branchOrSha <- executeGit(git, repository, "rev-parse", "--abbrev-ref", "HEAD")
        .flatMap(validateOutput("Branch/SHA"))
      sha <- executeGit(git, repository, "rev-parse", "HEAD")
        .flatMap(validateOutput("commit sha"))
      upstreamBranch <- getUpstreamBranch(repository, git)
      aheadBehind <- calculateAheadBehind(repository, git, upstreamBranch)
      untracked <- getFiles(git, repository, "--others", "--exclude-standard")
      modified <- getFiles(git, repository, "--diff-filter=M", "--name-only")
      added <- getFiles(git, repository, "--diff-filter=A", "--name-only")
      deleted <- getFiles(git, repository, "--diff-filter=D", "--name-only")
      renamed <- getRenamedFiles(git, repository)
      tags <- getAnnotatedTags(git, repository, (untracked ++ modified ++ added ++ deleted ++ renamed).nonEmpty)
    yield GitStatus(
      sha = sha,
      branch = if branchOrSha === "HEAD" then None else Some(branchOrSha.nn),
      tags = tags,
      upstream = upstreamBranch.map(_.nn),
      aheadBy = aheadBehind._1,
      behindBy = aheadBehind._2,
      changes = ChangesSummary(
        untracked = untracked,
        modified = modified,
        added = added,
        deleted = deleted,
        renamed = renamed
      )
    )

    result match
      case Right(status) =>
        scribe.info(s"Successfully read Git version: $status")
      case Left(error) =>
        scribe.error(s"Failed to read Git version. Error: ${error.getMessage}", error)

    result
  end readVersion

  private def executeGit(git: String, repository: Path, args: String*): Either[Throwable, String] =
    val cmd = (git +: args).mkString(" ")
    scribe.info(s"Executing Git command: $cmd in repository: $repository")

    executor.execute(Some(repository.nn), List.empty, cmd).flatMap { result =>
      if result.stdErr.isEmpty then
        scribe.debug(s"Git command '$cmd' succeeded with output: ${result.stdOut.trim}")
        Right(result.stdOut.nn.trim.nn)
      else
        scribe.error(s"Git command '$cmd' execution error. Exit code: ${result.exitCode}, stderr: ${result.stdErr}")
        Left(GitVersionError(s"Git command failed: ${result.stdErr.nn}"))
    }

  private def validateOutput(context: String)(output: String): Either[Throwable, String] =
    if output.nonEmpty then Right(output)
    else Left(GitVersionError(s"Failed to get $context: Empty output"))

  private def getUpstreamBranch(repository: Path, git: String): Either[Throwable, Option[String]] =
    executeGit(git, repository, "rev-parse", "--symbolic-full-name", "@{upstream}")
      .flatMap {
        case output if output.nonEmpty => Right(Some(output.nn))
        case _                         => Right(None) // No upstream branch
      }

  private def calculateAheadBehind(
    repository: Path,
    git: String,
    upstreamBranch: Option[String]
  ): Either[Throwable, (Option[Int], Option[Int])] =
    upstreamBranch match
      case Some(upstream) =>
        executeGit(git, repository, "rev-list", "--left-right", "--count", s"HEAD...$upstream")
          .flatMap { result =>
            // Apply null-safety explicitly
            val counts = Option(result)
              // scalafix:off
              .map(
                _.nn.split("\\s+").nn.iterator.collect { case s if s != null => s.nn.toIntOption }.toSeq
              )
              // Safely map to IntOption
              // scalafix:on
              .getOrElse(Seq.empty) // Fallback to empty if null

            if counts.length === 2 then Right((counts.head, counts(1)))
            else Left(GitVersionError(s"Unexpected ahead/behind output: $result"))
          }
      case None =>
        Right((None, None))

  private def getFiles(
    git: String,
    repository: Path,
    filters: String*
  ): Either[Throwable, Set[Path]] =
    executeGit(git, repository, "ls-files" +: filters: _*)
      .map {
        _.linesIterator.map(p => Path.of(p.nn).nn).toSet
      }

  private def getRenamedFiles(git: String, repository: Path): Either[Throwable, Set[(Path, Path)]] =
    executeGit(git, repository, "diff", "--name-status")
      .map {
        _.linesIterator
          .collect {
            case line if line.startsWith("R") =>
              val parts = line.nn
                .split("\\s+")
                .nn
                .map(_.nn)

              if parts.length === 3 then Some((Path.of(parts(1)).nn, Path.of(parts(2)).nn)) else None
          }
          .flatten
          .toSet
      }

  private def getAnnotatedTags(
    git: String,
    repository: Path,
    changes: Boolean
  ): Either[Throwable, Set[VersionTag]] =
    if changes then Right(Set.empty)
    else
      for
        showRef <- executeGit(git, repository, "show-ref", "--tags", "-d")
        headSha <- executeGit(git, repository, "rev-parse", "HEAD")
        tags <- parseAnnotatedTags(git, repository, showRef.nn, headSha.nn)
      yield tags

  private def parseAnnotatedTags(
    git: String,
    repository: Path,
    refs: String,
    sha: String
  ): Either[Throwable, Set[VersionTag]] =
    // Wrap `refs` and safely extract its linesIterator
    val tags = Option(refs)
      .map(_.nn.linesIterator.toSeq) // Convert lines to a sequence
      .getOrElse(Seq.empty)
      .collect {
        case line if line.endsWith("^{}") && line.contains(sha.nn) =>
          // Safely process parts and prevent possible null entries
          val parts = Option(line)
            .map(_.nn.split("\\s+")) // split returns Array[String | Null]
            // scalafix:off
            .map(_.nn.iterator.collect { case s if s != null => s.nn }.toSeq)
            // Filter nulls and transform to Seq[String]
            // scalafix: on
            .getOrElse(Seq.empty)
          if parts.length === 2 then Some((parts(0), parts(1).stripPrefix("refs/tags/").stripSuffix("^{}")))
          else None
      }
      .flatten

    // Map `tags` to `VersionTag` objects
    val parsedTags = tags.flatMap { (tagSha, tagName) =>
      VersionReader.default(tagName).flatMap { version =>
        getTagDate(git, repository, tagName).map { date =>
          VersionTag(tagSha, tagName, version, date)
        }
      }
    }
    Right(parsedTags.toSet)
  end parseAnnotatedTags

  private def getTagDate(git: String, repository: Path, tagName: String): Option[OffsetDateTime] =
    executeGit(git, repository, "for-each-ref", "--format=%(taggerdate:iso8601)", s"refs/tags/$tagName").toOption
      .flatMap { result =>
        if result.nonEmpty then Some(OffsetDateTime.parse(result.trim.nn, DateTimeFormatter.ISO_OFFSET_DATE_TIME).nn)
        else None
      }

end executor
