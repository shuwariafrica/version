package version.cli.core

import scala.util.control.NoStackTrace

/** Base trait for all errors produced by the version-cli-core library. */
sealed trait ResolutionError extends RuntimeException with NoStackTrace:
  def message: String
  final override def getMessage: String = message

object ResolutionError:
  given CanEqual[ResolutionError, ResolutionError] = CanEqual.derived

  /** Represents an error when a required Git command fails. */
  final case class GitCommandFailed(cmd: List[String], exitCode: Int, out: String, err: String) extends ResolutionError:
    def message: String =
      s"Git command failed: '${(List("git") ++ cmd).mkString(" ")}', exit=$exitCode, stderr='${err.trim}', stdout='${out.trim}'"

  /** The specified path does not appear to be a Git repository (heuristic). */
  final case class NotAGitRepository(path: os.Path) extends ResolutionError:
    def message: String = s"The specified path does not appear to be a Git repository: $path"

  /** Provided SHA abbreviation length is outside the allowed bounds [7, 40]. */
  final case class InvalidShaLength(length: Int) extends ResolutionError:
    def message: String = s"shaLength must be within [7, 40]. Found: $length"

  /** Generic message wrapper for unexpected situations. */
  final case class Message(msg: String) extends ResolutionError:
    def message: String = msg

  /** Specific CLI validation error: unsupported --format value. */
  final case class InvalidOutputFormat(value: String) extends ResolutionError:
    def message: String =
      s"Unknown output format: $value (allowed: pretty, compact, json, yaml)"
end ResolutionError
