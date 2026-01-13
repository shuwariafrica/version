/****************************************************************
 * Copyright © Shuwari Africa Ltd.                              *
 *                                                              *
 * This file is licensed to you under the terms of the Apache   *
 * License Version 2.0 (the "License"); you may not use this    *
 * file except in compliance with the License. You may obtain   *
 * a copy of the License at:                                    *
 *                                                              *
 *     https://www.apache.org/licenses/LICENSE-2.0              *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, *
 * either express or implied. See the License for the specific  *
 * language governing permissions and limitations under the     *
 * License.                                                     *
 ****************************************************************/
package version.cli.logging

import version.Version
import version.cli.core.logging.LogConfig
import version.cli.core.logging.LogEntry
import version.cli.core.logging.LogLevel
import version.cli.core.logging.Logger
import version.cli.logging.AnsiColours.colorize

/** ANSI colour codes for terminal output. */
object AnsiColours:
  val Reset = "\u001b[0m"
  val Green = "\u001b[32m" // Final releases
  val Yellow = "\u001b[33m" // Pre-releases
  val Red = "\u001b[31m" // Snapshots
  val Gray = "\u001b[90m" // Debug/verbose output
  val Bold = "\u001b[1m"

  extension (colour: String)
    /** Apply colour to text with automatic reset. */
    inline def colorize(text: String): String = s"$colour$text$Reset"

/** Configuration for coloured output behavior. */
final case class ColourConfig(enableColours: Boolean, isCI: Boolean)

object ColourConfig:

  def apply(enableColours: Boolean): ColourConfig = ColourConfig(enableColours, isCI = false)

  given CanEqual[ColourConfig, ColourConfig] = CanEqual.derived

  /** Create colour configuration based on environment. */
  def fromEnvironment(isCI: Boolean): ColourConfig =
    // We avoid java.io.Console (not available on Scala Native). Rules:
    //  - Disable colours in CI (unless FORCE_COLOR explicitly set)
    //  - Honour NO_COLOR to forcibly disable
    //  - Honour FORCE_COLOR to forcibly enable
    //  - Otherwise enable when TERM is set and not "dumb"
    val env = scala.sys.env
    val forceDisable = env.contains("NO_COLOR")
    val forceEnable = env.get("FORCE_COLOR").exists(_.nonEmpty)
    val termOk = env.get("TERM").exists(t => t.nonEmpty && t != "dumb")
    val enable = !isCI && !forceDisable && (forceEnable || termOk)
    ColourConfig(enableColours = enable, isCI = isCI)

  extension (config: ColourConfig)
    /** Apply colour if enabled, otherwise return plain text. */
    inline def colourize(text: String, colour: String): String =
      if config.enableColours then colour.colorize(text) else text
end ColourConfig

/** Shared log routing to avoid duplication in concrete loggers. */
abstract class BaseLogger(protected val logConfig: LogConfig) extends Logger:
  protected def formatEntry(entry: LogEntry): String

  final inline def log(entry: LogEntry): Unit =
    entry.level match
      case LogLevel.Error =>
        System.err.println(formatEntry(entry))
      case LogLevel.Verbose if logConfig.isVerbose =>
        System.err.println(formatEntry(entry))
      case _ => () // Skip if verbose not enabled

/** Standard implementation of Logger that outputs to stdout/stderr with optional colours.
  *
  * This implementation follows the specification:
  *   - Error logs go to stderr
  *   - Version output goes to stdout with appropriate colours
  *   - Debug/verbose logs go to stderr in gray
  */
final class StandardLogger(
  logConfig: LogConfig,
  colourConfig: ColourConfig
) extends BaseLogger(logConfig):

  /** Format a version with appropriate colours based on its type. */
  private inline def formatVersion(version: Version): String =
    val versionStr = version.toString
    if version.isFinal then colourConfig.colourize(versionStr, AnsiColours.Green)
    else if version.isPreRelease then colourConfig.colourize(versionStr, AnsiColours.Yellow)
    else colourConfig.colourize(versionStr, AnsiColours.Red)

  /** Output a version to stdout with appropriate formatting. */
  def outputVersion(version: Version): Unit =
    val formatted = if colourConfig.isCI then version.toString else formatVersion(version)
    println(formatted)

  private inline def levelPrefix(level: LogLevel): String = level match
    case LogLevel.Error   => "ERROR: "
    case LogLevel.Verbose => "DEBUG: "

  private inline def levelColour(level: LogLevel): String = level match
    case LogLevel.Error   => AnsiColours.Red
    case LogLevel.Verbose => AnsiColours.Gray

  override protected def formatEntry(entry: LogEntry): String =
    val timestamp = if logConfig.isVerbose then s"${java.time.LocalTime.now} " else ""
    val contextStr = entry.context.fold("")(ctx => s"[$ctx] ")
    val prefix = levelPrefix(entry.level)
    val fullMessage = s"$timestamp$contextStr${entry.message}"
    colourConfig.colourize(prefix + fullMessage, levelColour(entry.level))
end StandardLogger

object StandardLogger:
  /** Create a standard logger with the given configurations. */
  def apply(logConfig: LogConfig, colourConfig: ColourConfig): StandardLogger =
    new StandardLogger(logConfig, colourConfig)

  /** Create a standard logger with default colour detection. */
  def apply(logConfig: LogConfig): StandardLogger =
    new StandardLogger(logConfig, ColourConfig.fromEnvironment(logConfig.isCI))

/** Null logger that discards all output. Useful for testing and library usage. */
object NullLogger extends Logger:
  def log(entry: LogEntry): Unit = ()

/** Simple logger that outputs plain text without colours. Useful for testing. */
final class PlainLogger(logConfig: LogConfig) extends BaseLogger(logConfig):
  override protected def formatEntry(entry: LogEntry): String =
    val contextStr = entry.context.fold("")(ctx => s"[$ctx] ")
    entry.level match
      case LogLevel.Error   => s"ERROR: $contextStr${entry.message}"
      case LogLevel.Verbose => s"DEBUG: $contextStr${entry.message}"

object PlainLogger:
  def apply(logConfig: LogConfig): PlainLogger = new PlainLogger(logConfig)
