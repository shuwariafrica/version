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
package version.resolution.logging

/** How much a log entry has to say for itself. */
enum LogLevel derives CanEqual:

  /** A failure the caller needs, emitted whatever the logger's verbosity. */
  case Error

  /** Detail about how a version was reached, emitted only by a logger that asked for it. */
  case Verbose

/** One thing worth saying, with the part of the engine that said it. */
final case class LogEntry(level: LogLevel, message: String, context: Option[String])

/** Provides instances for [[LogEntry]]. */
object LogEntry:
  given CanEqual[LogEntry, LogEntry] = CanEqual.derived

/** Where the resolution engine's diagnostics go.
  *
  * A consumer implements this against its own logging - sbt's, a build tool's, a service's - and answers
  * [[Logger.verboseEnabled verboseEnabled]] for whether it wants the detail. That answer is the only verbosity switch:
  * the engine asks the logger rather than being told separately.
  */
trait Logger:

  /** Emits `entry` wherever this logger writes. */
  def log(entry: LogEntry): Unit

  /** Whether this logger wants verbose entries. Asked before each one is built. */
  def verboseEnabled: Boolean

/** Provides the logging operations and the default instance for [[Logger]]. */
object Logger:

  /** Records nothing, so that resolution runs without a logger being wired at all. Override locally to see anything. */
  given Logger = NullLogger

  extension (logger: Logger)
    /** Records a failure, whatever this logger's verbosity. */
    inline def error(inline message: String, inline context: String): Unit =
      logger.log(LogEntry(LogLevel.Error, message, if context.nonEmpty then Some(context) else None))

    inline def error(inline message: String): Unit =
      logger.log(LogEntry(LogLevel.Error, message, None))

    /** Records detail, where this logger wants it. The message is built only then, so composing one costs nothing
      * when nobody is listening.
      */
    inline def verbose(inline message: String, inline context: String): Unit =
      if logger.verboseEnabled then logger.log(LogEntry(LogLevel.Verbose, message, if context.nonEmpty then Some(context) else None))

    inline def verbose(inline message: String): Unit =
      if logger.verboseEnabled then logger.log(LogEntry(LogLevel.Verbose, message, None))
  end extension
end Logger

/** Records nothing. */
object NullLogger extends Logger:
  def log(entry: LogEntry): Unit = ()
  def verboseEnabled: Boolean = false
