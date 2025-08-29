package version.cli

import os.Path
import scopt.*

import version.cli.core.ResolutionError
import version.internal.BuildInfo

/** CLI options for version-cli. Pure data. */
final case class Options(
  workDir: os.Path,
  basisCommit: String,
  prNumber: Option[Int],
  branchOverride: Option[String],
  shaLength: Int,
  formats: List[Options.OutputFormat],
  verbose: Boolean,
  ci: Boolean
) derives CanEqual

object Options:
  given CanEqual[Options, Options] = CanEqual.derived

  /** Supported output formats. */
  enum OutputFormat:
    case Pretty, Compact, Json, Yaml
  export OutputFormat.*

  object OutputFormat:
    given CanEqual[OutputFormat, OutputFormat] = CanEqual.derived

  // scopt Reads for custom types
  given Read[os.Path] = Read.reads(os.Path(_))
  given Read[OutputFormat] = Read.reads { s =>
    s.trim.toLowerCase match
      case "pretty"  => OutputFormat.Pretty
      case "compact" => OutputFormat.Compact
      case "json"    => OutputFormat.Json
      case "yaml"    => OutputFormat.Yaml
      case other     => throw ResolutionError.InvalidOutputFormat(other) // scalafix:ok
  }

  /** Default options. */
  val default: Options =
    Options(
      workDir = os.pwd,
      basisCommit = "HEAD",
      prNumber = None,
      branchOverride = None,
      shaLength = 12,
      formats = List(OutputFormat.Pretty),
      verbose = false,
      ci = false
    )

  /** Build the scopt OParser for Options. */
  def parser: OParser[Unit, Options] =
    val b = OParser.builder[Options]
    import b.*
    OParser.sequence(
      programName("version-cli"),
      head("version", BuildInfo.version),
      opt[OutputFormat]('f', "format")
        .unbounded()
        .action((fmt, c) => c.copy(formats = c.formats :+ fmt))
        .text("Output format: pretty | compact | json | yaml. Can be specified multiple times."),
      opt[os.Path]('d', "work-dir")
        .action((p, c) => c.copy(workDir = p))
        .text("Working directory containing the Git repository (default: current directory)."),
      opt[String]('b', "basis-commit")
        .action((rev, c) => c.copy(basisCommit = rev))
        .text("Commit-ish to resolve against (default: HEAD)."),
      opt[Int]('p', "pr")
        .action((n, c) => c.copy(prNumber = Some(n)))
        .text("Pull request number to embed in build metadata (e.g., 42 -> +pr42)."),
      opt[String]('B', "branch-override")
        .action((s, c) => c.copy(branchOverride = Some(s)))
        .text("Override branch name for build metadata (e.g., from CI)."),
      opt[Int]('s', "sha-length")
        .action((n, c) => c.copy(shaLength = n))
        .validate { n =>
          if n >= 7 && n <= 40 then success
          else failure("sha-length must be within [7, 40]")
        }
        .text("Abbreviated SHA length in build metadata (default: 12). Range: 7..40."),
      opt[Unit]('v', "verbose")
        .action((_, c) => c.copy(verbose = true))
        .text("Enable verbose output (diagnostics to stderr)."),
      opt[Unit]("ci")
        .action((_, c) => c.copy(ci = true))
        .text("Enable CI mode (disable colours, compact output)."),
      help("help").text("Print this help message."),
      version("version").text("Show application version and exit."),
      checkConfig { c =>
        if c.shaLength < 7 || c.shaLength > 40 then failure("sha-length must be within [7, 40]")
        else success
      }
    )
  end parser
end Options
