package version

object errors:

  sealed abstract class VersionError(message: String, cause: Option[Throwable])
      extends RuntimeException(message, cause.orNull)

  final case class RuntimeInitialisationError(message: String, cause: Option[Throwable])
      extends VersionError(message, cause)

  final case class RuntimeError(message: String, cause: Option[Throwable]) extends VersionError(message, cause)
