package africa.shuwari.version.git

case class GitVersionError(message: String, cause: Option[Throwable]) extends RuntimeException(message, cause.orNull)

object GitVersionError:
  def apply(message: String): GitVersionError = new GitVersionError(message, None)
