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
package version.cli

import scopt.*

import version.cli.CliError
import version.internal.BuildInfo

/** Top-level parsed CLI options: the selected command plus global flags. */
final case class CliOptions(
  repository: java.nio.file.Path,
  basisCommit: String,
  prNumber: Option[Int],
  branchOverride: Option[String],
  shaLength: Int,
  verbose: Boolean,
  ci: Boolean,
  noColour: Boolean,
  command: CommandConfig
) derives CanEqual

/** Which version a show command reports. */
enum ShowKind derives CanEqual:
  case Current, Target

sealed trait CommandConfig derives CanEqual

/** Show the resolved version (`Current`) or the resolution target (`Target`). */
final case class ShowConfig(
  what: ShowKind,
  sinks: List[OutputSink],
  consoleStyle: ConsoleStyle,
  consoleStyleExplicit: Boolean
) extends CommandConfig derives CanEqual

/** Record a target directive as an empty commit on HEAD: `--set` writes `target: <version>`, `--increment` writes
  * `version: <keyword>`. Exactly one of the two is present.
  */
final case class TargetConfig(set: Option[String], increment: Option[String], noSign: Boolean, dryRun: Boolean) extends CommandConfig
    derives CanEqual

/** Create an annotated tag at HEAD; `version` defaults to the target version when absent. */
final case class TagConfig(version: Option[String], message: Option[String], noSign: Boolean, dryRun: Boolean) extends CommandConfig
    derives CanEqual

/** List the release history (annotated version tags), newest first, with optional scheme-generic filters. */
final case class ListConfig(limit: Option[Int], finalOnly: Boolean, since: Option[String], until: Option[String], details: Boolean)
    extends CommandConfig derives CanEqual

enum SinkKind derives CanEqual:
  case Console, Raw, Json
object SinkKind:
  def parse(s: String): Either[CliError, SinkKind] = s.toLowerCase match
    case "console" => Right(SinkKind.Console)
    case "raw"     => Right(SinkKind.Raw)
    case "json"    => Right(SinkKind.Json)
    case other     => Left(CliError.InvalidSink(other))

enum ConsoleStyle derives CanEqual:
  case Pretty, Compact
object ConsoleStyle:
  def fromString(s: String): Either[CliError, ConsoleStyle] = s.toLowerCase match
    case "pretty"  => Right(ConsoleStyle.Pretty)
    case "compact" => Right(ConsoleStyle.Compact)
    case other     => Left(CliError.InvalidConsoleStyle(other))

final case class OutputSink(kind: SinkKind, destination: Option[java.nio.file.Path]) derives CanEqual

object CliOptions:

  given Read[java.nio.file.Path] = Read.reads(s => java.nio.file.Path.of(s))

  private val defaultShow =
    ShowConfig(ShowKind.Current, sinks = Nil, consoleStyle = ConsoleStyle.Pretty, consoleStyleExplicit = false)

  val default: CliOptions = CliOptions(
    repository = java.nio.file.Path.of(System.getProperty("user.dir")),
    basisCommit = "HEAD",
    prNumber = None,
    branchOverride = None,
    shaLength = 40,
    verbose = false,
    ci = false,
    noColour = false,
    command = defaultShow
  )

  /** Builds the scopt parser. Root options are global: the resolution flags plus `--emit` / `--console-style` shaping
    * the default `show` output. Each explicit subcommand carries its own options as scopt children - `--set` /
    * `--increment` / `--dry-run` / `--no-sign` on `target`, `--message` on `tag`, and the filters (`--limit`, `--final`,
    * `--since`, `--until`, `--details`) on `list`. A subcommand must precede its options (scopt keeps root options
    * matchable after the subcommand, but a command after a root option is not recognised).
    */
  final class CliParser(builder: OParserBuilder[CliOptions]):
    import builder.*

    private def updateShow(c: CliOptions)(f: ShowConfig => ShowConfig): CliOptions =
      c.command match
        case s: ShowConfig => c.copy(command = f(s))
        case _             => c

    private def updateList(c: CliOptions)(f: ListConfig => ListConfig): CliOptions =
      c.command match
        case l: ListConfig => c.copy(command = f(l))
        case _             => c

    private val optRepository = opt[java.nio.file.Path]('r', "repository")
      .valueName("<path>")
      .action((p, c) => c.copy(repository = p))
      .text("Repository directory (default: current directory).")

    private val optBasisCommit = opt[String]('b', "basis-commit")
      .action((rev, c) => c.copy(basisCommit = rev))
      .text("Commit-ish to resolve against (default: HEAD).")

    private val optPrNumber = opt[Int]("pr")
      .action((n, c) => c.copy(prNumber = Some(n)))
      .text("Pull request number to embed in build metadata.")

    private val optBranchOverride = opt[String]("branch-override")
      .action((s, c) => c.copy(branchOverride = Some(s)))
      .text("Override branch name for build metadata.")

    private val optShaLength = opt[Int]("sha-length")
      .action((n, c) => c.copy(shaLength = n))
      .validate(n => if n >= 7 && n <= 64 then success else failure("sha-length must be within [7, 64]"))
      .text("SHA length for the 'full' renderer (default: 40). Range: 7..64.")

    private val optVerbose = opt[Unit]('v', "verbose")
      .action((_, c) => c.copy(verbose = true))
      .text("Enable verbose diagnostics to stderr.")

    private val optCi = opt[Unit]("ci")
      .action((_, c) => c.copy(ci = true))
      .text("CI mode (forces compact console style unless overridden; disables colours).")

    private val optNoColour = opt[Unit]("no-colour")
      .abbr("no-color")
      .action((_, c) => c.copy(noColour = true))
      .text("Disable ANSI colours.")

    private val optEmit = opt[String]('e', "emit")
      .unbounded()
      .valueName("sink[=path]")
      .validate(spec => parseEmit(spec).left.map(_.message).map(_ => ()))
      .action { (spec, c) =>
        parseEmit(spec) match
          case Right(snk) => updateShow(c)(s => s.copy(sinks = s.sinks :+ snk))
          case Left(_)    => c
      }
      .text("Show output sink: console|raw|json optionally =<file>. Repeatable.")

    private val optConsoleStyle = opt[String]("console-style")
      .valueName("pretty|compact")
      .validate(s => ConsoleStyle.fromString(s).left.map(_.message).map(_ => ()))
      .action { (s, c) =>
        ConsoleStyle.fromString(s) match
          case Right(sty) => updateShow(c)(sc => sc.copy(consoleStyle = sty, consoleStyleExplicit = true))
          case Left(_)    => c
      }
      .text("Console rendering style for show commands (pretty|compact).")

    // Lift the pending command into a TargetConfig, preserving any flags already accumulated on a bare `target`.
    private def toTarget(c: CliOptions)(f: TargetConfig => TargetConfig): CliOptions =
      val base = c.command match
        case t: TargetConfig => t
        case _               => TargetConfig(None, None, noSign = false, dryRun = false)
      c.copy(command = f(base))

    private def optSet = opt[String]('s', "set")
      .valueName("<version>")
      .action((v, c) => toTarget(c)(_.copy(set = Some(v))))
      .text("Record `target: <version>` as an empty commit.")

    private def optIncrement = opt[String]('i', "increment")
      .valueName("<keyword>")
      .action((k, c) => toTarget(c)(_.copy(increment = Some(k))))
      .text("Record `version: <keyword>` as an empty commit; the keyword is validated against the active scheme.")

    // Shared by the mutating commands as scoped children, so a fresh fragment is produced per command.
    private def optDryRun = opt[Unit]("dry-run")
      .action { (_, c) =>
        c.copy(command = c.command match
          case t: TargetConfig => t.copy(dryRun = true)
          case g: TagConfig    => g.copy(dryRun = true)
          case other           => other)
      }
      .text("Preview the command without performing it.")

    private def optNoSign = opt[Unit]("no-sign")
      .action { (_, c) =>
        c.copy(command = c.command match
          case t: TargetConfig => t.copy(noSign = true)
          case g: TagConfig    => g.copy(noSign = true)
          case other           => other)
      }
      .text("Create the object unsigned, even when a signing key is configured.")

    private def optMessage = opt[String]('m', "message")
      .action { (msg, c) =>
        c.copy(command = c.command match
          case g: TagConfig => g.copy(message = Some(msg))
          case other        => other)
      }
      .text("Tag message (default: \"Release <version>\").")

    private def optLimit = opt[Int]('n', "limit")
      .valueName("<count>")
      .action((n, c) => updateList(c)(_.copy(limit = Some(n))))
      .text("Limit the list to the <count> newest entries.")

    private def optFinal = opt[Unit]("final")
      .action((_, c) => updateList(c)(_.copy(finalOnly = true)))
      .text("List only final releases, excluding pre-releases.")

    private def optSince = opt[String]("since")
      .valueName("<version>")
      .action((v, c) => updateList(c)(_.copy(since = Some(v))))
      .text("List only releases at or above <version>.")

    private def optUntil = opt[String]("until")
      .valueName("<version>")
      .action((v, c) => updateList(c)(_.copy(until = Some(v))))
      .text("List only releases at or below <version>.")

    private def optDetails = opt[Unit]("details")
      .action((_, c) => updateList(c)(_.copy(details = true)))
      .text("Show extended details: the tag and the source-commit date.")

    private val cmdList = cmd("list")
      .action((_, c) => c.copy(command = ListConfig(None, finalOnly = false, None, None, details = false)))
      .text("List the release history (annotated version tags), newest first.")
      .children(optLimit, optFinal, optSince, optUntil, optDetails)

    private val cmdTarget = cmd("target")
      .action { (_, c) =>
        c.command match
          case s: ShowConfig => c.copy(command = s.copy(what = ShowKind.Target))
          case _             => c.copy(command = defaultShow.copy(what = ShowKind.Target))
      }
      .text("Show the resolution target; with --set or --increment, record a directive.")
      .children(optSet, optIncrement, optDryRun, optNoSign)

    private val checkTarget = checkConfig {
      case CliOptions(_, _, _, _, _, _, _, _, TargetConfig(Some(_), Some(_), _, _)) =>
        failure("target: use only one of --set and --increment, not both")
      case _ => success
    }

    private val cmdTag = cmd("tag")
      .action((_, c) => c.copy(command = TagConfig(None, None, noSign = false, dryRun = false)))
      .text("Create an annotated tag at HEAD using the target (or given) version.")
      .children(
        arg[String]("<version>")
          .optional()
          .action { (v, c) =>
            c.copy(command = c.command match
              case g: TagConfig => g.copy(version = Some(v))
              case _            => TagConfig(Some(v), None, noSign = false, dryRun = false))
          }
          .text("Explicit tag version (default: the resolved version)."),
        optMessage,
        optNoSign,
        optDryRun
      )

    private val helpFlag = help("help").text("Print this help message.")
    private val versionFlag = version("version").text("Show application version and exit.")

    val parser: OParser[Unit, CliOptions] = OParser.sequence(
      programName("version"),
      head("version", BuildInfo.version),
      optRepository,
      optBasisCommit,
      optPrNumber,
      optBranchOverride,
      optShaLength,
      optVerbose,
      optCi,
      optNoColour,
      optEmit,
      optConsoleStyle,
      cmdTarget,
      cmdTag,
      cmdList,
      helpFlag,
      versionFlag,
      checkTarget
    )
  end CliParser

  def parser: OParser[Unit, CliOptions] =
    val b = OParser.builder[CliOptions]
    new CliParser(b).parser

  private def parseEmit(spec: String): Either[CliError, OutputSink] =
    val split = spec.split("=", 2)
    val (left, rightOptOrErr): (String, Either[CliError, Option[String]]) = split match
      case Array(l, r) if r.nonEmpty => (l, Right(Some(r)))
      case Array(l, r) if r.isEmpty  => (l, Left(CliError.EmptyEmitPath(spec)))
      case Array(l)                  => (l, Right(None))
    rightOptOrErr match
      case Left(err)       => Left(err)
      case Right(rightOpt) => SinkKind.parse(left).map(kind => OutputSink(kind, rightOpt.map(java.nio.file.Path.of(_))))
end CliOptions
