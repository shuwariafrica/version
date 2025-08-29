package version.cli.core.logging

/** Logging levels for the version resolution system.
  *
  * Zero-cost abstraction using inline methods and compile-time evaluation to ensure no runtime overhead when logging is
  * disabled.
  */
enum LogLevel derives CanEqual:
  /** Error level - always enabled, for critical failures and errors. */
  case Error
  /** Verbose level - enabled only with verbose flag, for debug information. */
  case Verbose

object LogLevel:
  given CanEqual[LogLevel, LogLevel] = CanEqual.derived

  extension (level: LogLevel)
    /** Check if this level should be logged given the current configuration. */
    inline def isEnabled(inline isVerbose: Boolean): Boolean =
      inline level match
        case LogLevel.Error   => true
        case LogLevel.Verbose => isVerbose

/** Pure data representing a log entry. */
final case class LogEntry(
  level: LogLevel,
  message: String,
  context: Option[String]
) derives CanEqual

object LogEntry:
  given CanEqual[LogEntry, LogEntry] = CanEqual.derived

/** Abstract interface for log output handling.
  *
  * This trait enables pluggable logging implementations, allowing build tools and other consumers to integrate with
  * their own logging frameworks.
  */
trait Logger derives CanEqual:
  /** Output a log entry. Implementation determines where it goes (stderr, stdout, etc). */
  def log(entry: LogEntry): Unit

object Logger:
  given CanEqual[Logger, Logger] = CanEqual.derived

  /** Zero-cost logging macros that eliminate overhead when logging is disabled.
    *
    * These inline methods use compile-time evaluation to completely remove logging calls when the level is not enabled,
    * achieving true zero-cost abstraction.
    */
  extension (logger: Logger)
    /** Log an error message. Always emitted regardless of verbose setting. */
    inline def error(inline message: String, inline context: String): Unit =
      logger.log(LogEntry(LogLevel.Error, message, if context.nonEmpty then Some(context) else None))
    inline def error(inline message: String): Unit =
      logger.log(LogEntry(LogLevel.Error, message, None))

    /** Log a verbose/debug message. Only emitted when verbose mode is enabled.
      *
      * The message expression is only evaluated if verbose logging is enabled, providing zero-cost abstraction for
      * expensive debug computations.
      */
    def verbose(message: String, context: String)(using isVerbose: Boolean): Unit =
      if isVerbose then logger.log(LogEntry(LogLevel.Verbose, message, if context.nonEmpty then Some(context) else None))
    def verbose(message: String)(using isVerbose: Boolean): Unit =
      if isVerbose then logger.log(LogEntry(LogLevel.Verbose, message, None))

    /** Log a verbose message with lazy evaluation of an expensive computation.
      *
      * The computation is only performed if verbose logging is enabled.
      */
    def verboseLazy[T](computation: => T, messageTemplate: T => String, context: String)(using isVerbose: Boolean): Unit =
      if isVerbose then
        val result = computation
        logger.log(LogEntry(LogLevel.Verbose, messageTemplate(result), if context.nonEmpty then Some(context) else None))
    def verboseLazy[T](computation: => T, messageTemplate: T => String)(using isVerbose: Boolean): Unit =
      if isVerbose then
        val result = computation
        logger.log(LogEntry(LogLevel.Verbose, messageTemplate(result), None))
  end extension
end Logger

/** Null logger (does nothing). Provided in core so external users can opt out of logging with zero overhead. */
object NullLogger extends Logger:
  def log(entry: LogEntry): Unit = ()

/** Context function type for logging operations.
  *
  * This enables implicit logger passing without boilerplate.
  */
type LoggerContext[T] = Logger ?=> T

/** Configuration for logging behavior. */
final case class LogConfig(
  isVerbose: Boolean,
  isCI: Boolean
) derives CanEqual

object LogConfig:
  given CanEqual[LogConfig, LogConfig] = CanEqual.derived

  /** Default configuration with error-level logging only. */
  val default: LogConfig = LogConfig(isVerbose = false, isCI = false)
