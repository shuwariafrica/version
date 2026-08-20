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
package version.cli.logging

import version.resolution.logging.LogEntry
import version.resolution.logging.LogLevel
import version.resolution.logging.Logger

final case class LogConfig(
  isVerbose: Boolean,
  isCI: Boolean
)

object LogConfig:
  given CanEqual[LogConfig, LogConfig] = CanEqual.derived

object AnsiColours:
  val Reset = "\u001b[0m"
  val Red = "\u001b[31m"
  val Gray = "\u001b[90m"

final case class ColourConfig(enableColours: Boolean, isCI: Boolean)

object ColourConfig:

  def apply(enableColours: Boolean): ColourConfig = ColourConfig(enableColours, isCI = false)

  given CanEqual[ColourConfig, ColourConfig] = CanEqual.derived

  def fromEnvironment(isCI: Boolean): ColourConfig =
    // Terminal detection reads the environment because java.io.Console is absent from Scala Native's javalib.
    // NO_COLOR and FORCE_COLOR are the cross-tool conventions for overriding whatever it concludes.
    val env = scala.sys.env
    val forceDisable = env.contains("NO_COLOR")
    val forceEnable = env.get("FORCE_COLOR").exists(_.nonEmpty)
    val termOk = env.get("TERM").exists(t => t.nonEmpty && t != "dumb")
    val enable = !isCI && !forceDisable && (forceEnable || termOk)
    ColourConfig(enableColours = enable, isCI = isCI)

  extension (config: ColourConfig)
    inline def colourise(text: String, colour: String): String =
      if config.enableColours then s"$colour$text${AnsiColours.Reset}" else text
end ColourConfig

abstract class BaseLogger(protected val logConfig: LogConfig) extends Logger:
  protected def formatEntry(entry: LogEntry): String

  final def verboseEnabled: Boolean = logConfig.isVerbose

  final inline def log(entry: LogEntry): Unit =
    entry.level match
      case LogLevel.Error =>
        System.err.println(formatEntry(entry))
      case LogLevel.Verbose if verboseEnabled =>
        System.err.println(formatEntry(entry))
      case _ => ()

final class StandardLogger(
  logConfig: LogConfig,
  colourConfig: ColourConfig
) extends BaseLogger(logConfig):

  private inline def levelPrefix(level: LogLevel): String = level match
    case LogLevel.Error   => "ERROR: "
    case LogLevel.Verbose => "DEBUG: "

  private inline def levelColour(level: LogLevel): String = level match
    case LogLevel.Error   => AnsiColours.Red
    case LogLevel.Verbose => AnsiColours.Gray

  override protected def formatEntry(entry: LogEntry): String =
    val timestamp = if logConfig.isVerbose then s"${StandardLogger.utcTimeOfDay()} " else ""
    val contextStr = entry.context.fold("")(ctx => s"[$ctx] ")
    val prefix = levelPrefix(entry.level)
    val fullMessage = s"$timestamp$contextStr${entry.message}"
    colourConfig.colourise(prefix + fullMessage, levelColour(entry.level))
end StandardLogger

object StandardLogger:
  def apply(logConfig: LogConfig, colourConfig: ColourConfig): StandardLogger =
    new StandardLogger(logConfig, colourConfig)

  // Portable HH:mm:ss.SSS UTC time-of-day: java.time.LocalTime is not available
  // in Scala Native's javalib, and java.util.Calendar/TimeZone are absent too.
  // UTC is deliberate for reproducibility across hosts in verbose diagnostic logs.
  private[logging] def utcTimeOfDay(): String =
    val millis = System.currentTimeMillis()
    val secs = millis / 1000L
    val hour = ((secs / 3600L) % 24L).toInt
    val min = ((secs / 60L) % 60L).toInt
    val sec = (secs % 60L).toInt
    val ms = (millis % 1000L).toInt
    f"$hour%02d:$min%02d:$sec%02d.$ms%03d"

  def apply(logConfig: LogConfig): StandardLogger =
    new StandardLogger(logConfig, ColourConfig.fromEnvironment(logConfig.isCI))
