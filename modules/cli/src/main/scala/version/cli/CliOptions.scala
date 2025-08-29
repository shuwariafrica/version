package version.cli

import scopt.*

import version.cli.core.ResolutionError
import version.internal.BuildInfo

/** Top-level parsed CLI options (command + global flags). */
final case class CliOptions(
  repository: os.Path,
  basisCommit: String,
  prNumber: Option[Int],
  branchOverride: Option[String],
  shaLength: Int,
  verbose: Boolean,
  ci: Boolean,
  noColour: Boolean,
  command: CommandConfig
) derives CanEqual

// --- Commands ---
sealed trait CommandConfig derives CanEqual
final case class ResolveConfig(
  sinks: List[OutputSink],
  consoleStyle: ConsoleStyle, // user-selected or default placeholder
  consoleStyleExplicit: Boolean // track if user explicitly set it
) extends CommandConfig derives CanEqual

// Placeholder for future commands.
final case class ReleaseConfig(
  tagPrefix: Option[String],
  push: Boolean,
  annotate: Boolean
) extends CommandConfig derives CanEqual

// --- Output model ---

enum SinkKind derives CanEqual:
  case Console, Raw, Json, Yaml
object SinkKind:
  def parse(s: String): Either[ResolutionError, SinkKind] = s.toLowerCase match
    case "console" => Right(SinkKind.Console)
    case "raw"     => Right(SinkKind.Raw)
    case "json"    => Right(SinkKind.Json)
    case "yaml"    => Right(SinkKind.Yaml)
    case other     => Left(ResolutionError.InvalidSink(other))

enum ConsoleStyle derives CanEqual:
  case Pretty, Compact
object ConsoleStyle:
  def fromString(s: String): Either[ResolutionError, ConsoleStyle] = s.toLowerCase match
    case "pretty"  => Right(ConsoleStyle.Pretty)
    case "compact" => Right(ConsoleStyle.Compact)
    case other     => Left(ResolutionError.InvalidConsoleStyle(other))

final case class OutputSink(kind: SinkKind, destination: Option[os.Path]) derives CanEqual

object CliOptions:
  // only non-typeclass givens (e.g., scopt Reads) remain here

  given Read[os.Path] = Read.reads(os.Path(_))

  private val defaultResolve = ResolveConfig(
    sinks = Nil, // inject default later if empty
    consoleStyle = ConsoleStyle.Pretty,
    consoleStyleExplicit = false
  )

  val default: CliOptions = CliOptions(
    repository = os.pwd,
    basisCommit = "HEAD",
    prNumber = None,
    branchOverride = None,
    shaLength = 12,
    verbose = false,
    ci = false,
    noColour = false,
    command = defaultResolve
  )

  /** Structured parser pieces for easier debugging and potential reuse. */
  final class CliParser(builder: OParserBuilder[CliOptions]):
    import builder.*

    private def updateResolve(c: CliOptions)(f: ResolveConfig => ResolveConfig): CliOptions =
      c.command match
        case rc: ResolveConfig => c.copy(command = f(rc))
        case _                 => c

    // Global options
    private val optRepository = opt[os.Path]('r', "repository")
      .valueName("<path>")
      .action((p, c) => c.copy(repository = p))
      .text("Repository directory (default: current directory).")

    private val optBasisCommit = opt[String]('b', "basis-commit")
      .action((rev, c) => c.copy(basisCommit = rev))
      .text("Commit-ish to resolve against (default: HEAD).")

    private val optPrNumber = opt[Int]("pr")
      .action((n, c) => c.copy(prNumber = Some(n)))
      .text("Pull request number to embed in build metadata (e.g., 42 -> +pr42).")

    private val optBranchOverride = opt[String]("branch-override")
      .action((s, c) => c.copy(branchOverride = Some(s)))
      .text("Override branch name for build metadata.")

    private val optShaLength = opt[Int]("sha-length")
      .action((n, c) => c.copy(shaLength = n))
      .validate(n => if n >= 7 && n <= 40 then success else failure("sha-length must be within [7, 40]"))
      .text("Abbreviated SHA length in build metadata (default: 12). Range: 7..40.")

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

    // Resolve command specific (default)
    private val optEmit = opt[String]('e', "emit")
      .unbounded()
      .valueName("sink[=path]")
      .validate(spec => parseEmit(spec).left.map(_.message).map(_ => ()))
      .action { (spec, c) =>
        parseEmit(spec) match
          case Right(snk) => updateResolve(c)(rc => rc.copy(sinks = rc.sinks :+ snk))
          case Left(_)    => c
      }
      .text("Add an output sink: console|raw|json|yaml optionally =<file>. Repeatable.")

    private val optConsoleStyle = opt[String]("console-style")
      .valueName("pretty|compact")
      .validate(s => ConsoleStyle.fromString(s).left.map(_.message).map(_ => ()))
      .action { (s, c) =>
        ConsoleStyle.fromString(s) match
          case Right(sty) => updateResolve(c)(rc => rc.copy(consoleStyle = sty, consoleStyleExplicit = true))
          case Left(_)    => c
      }
      .text("Console rendering style (pretty|compact). Applies if a console sink is emitted.")

    // Release command placeholder
    private val relTagPrefix = opt[String]("tag-prefix")
      .action { (p, c) =>
        c.copy(
          command = c.command match
            case r: ReleaseConfig => r.copy(tagPrefix = Some(p))
            case other            => other
        )
      }
      .text("Tag prefix to use when creating release tags (e.g., 'v').")

    private val relPush = opt[Unit]("push")
      .action { (_, c) =>
        c.copy(
          command = c.command match
            case r: ReleaseConfig => r.copy(push = true)
            case other            => other
        )
      }
      .text("Push created tag to remote (release command).")

    private val relAnnotate = opt[Unit]("annotate")
      .action { (_, c) =>
        c.copy(
          command = c.command match
            case r: ReleaseConfig => r.copy(annotate = true)
            case other            => other
        )
      }
      .text("Create an annotated tag (release command).")

    private val cmdRelease = cmd("release")
      .hidden()
      .action((_, c) => c.copy(command = ReleaseConfig(None, push = false, annotate = false)))
      .children(relTagPrefix, relPush, relAnnotate)

    private val helpFlag = help("help").text("Print this help message.")
    private val versionFlag = version("version").text("Show application version and exit.")

    private val checkCfg = checkConfig { c =>
      val c2 = c.command match
        case rc: ResolveConfig if rc.sinks.isEmpty =>
          c.copy(command = rc.copy(sinks = List(OutputSink(SinkKind.Console, None))))
        case _ => c
      val validatePaths = c2.command match
        case rc: ResolveConfig =>
          val invalid = rc.sinks.collect { case s @ OutputSink(_, Some(p)) if p.toString.trim.isEmpty => s }
          if invalid.nonEmpty then failure("Empty path supplied for emit sink") else success
        case _ => success
      validatePaths
    }

    val parser: OParser[Unit, CliOptions] = OParser.sequence(
      programName("version-cli"),
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
      cmdRelease,
      helpFlag,
      versionFlag,
      checkCfg
    )
  end CliParser

  def parser: OParser[Unit, CliOptions] =
    val b = OParser.builder[CliOptions]
    val p = new CliParser(b)
    p.parser

  private def parseEmit(spec: String): Either[ResolutionError, OutputSink] =
    val split = spec.split("=", 2)
    val (left, rightOptOrErr): (String, Either[ResolutionError, Option[String]]) = split match
      case Array(l, r) if r.nonEmpty => (l, Right(Some(r)))
      case Array(l, r) if r.isEmpty  => (l, Left(ResolutionError.EmptyEmitPath(spec)))
      case Array(l)                  => (l, Right(None))
    rightOptOrErr match
      case Left(err)       => Left(err)
      case Right(rightOpt) =>
        for
          kind <- SinkKind.parse(left)
          sink <- Right(OutputSink(kind, rightOpt.map(os.Path(_))))
        yield sink
end CliOptions
