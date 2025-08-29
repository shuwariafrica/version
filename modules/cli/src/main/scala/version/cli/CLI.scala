package version.cli

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import org.virtuslab.yaml.*
import scopt.OParser

import version.Version
import version.cli.Options.OutputFormat
import version.cli.core.ResolutionError
import version.cli.core.VersionCliCore as Core
import version.cli.core.domain.CliConfig
import version.cli.core.logging.LogConfig
import version.cli.core.logging.Logger
import version.cli.logging.ColourConfig
import version.cli.logging.StandardLogger
import version.codecs.jsoniter.given
import version.codecs.yaml.given

/** Command-line entry point for version-cli.
  *
  * This CLI:
  *   - Parses flags using scopt
  *   - Invokes version-cli-core to resolve a Version for a repository
  *   - Prints the result in one or more formats: pretty | compact | json | yaml
  *
  * Effects are confined to main; underlying core remains pure.
  */
object CLI:

  def main(args: Array[String]): Unit =
    val parser = Options.parser
    OParser.parse(parser, args, Options.default) match
      case Some(opts) =>
        // Set up logging configuration
        val logConfig = LogConfig(isVerbose = opts.verbose, isCI = opts.ci)
        // In CI we disable colours explicitly
        val colourConfig = if opts.ci then ColourConfig(enableColours = false, isCI = true) else ColourConfig.fromEnvironment(false)
        val logger = StandardLogger(logConfig, colourConfig)

        given Boolean = opts.verbose

        logger.verbose(
          s"Resolving in ${opts.workDir} at ${opts.basisCommit}; rawFormats=${opts.formats.mkString(",")}; ci=${opts.ci}",
          "CLI")

        val cfg = CliConfig(
          repo = opts.workDir,
          basisCommit = opts.basisCommit,
          prNumber = opts.prNumber,
          branchOverride = opts.branchOverride,
          shaLength = opts.shaLength,
          verbose = opts.verbose
        )

        // Determine effective formats: in CI force compact unless user explicitly asked for others.
        val effectiveFormats =
          if opts.ci && (opts.formats.isEmpty || opts.formats == List(OutputFormat.Pretty)) then List(OutputFormat.Compact)
          else if opts.formats.isEmpty then List(OutputFormat.Pretty)
          else opts.formats

        Core.resolve(cfg, logger, opts.verbose) match
          case Left(err) =>
            logger.error(renderError(err))
            sys.exit(1)
          case Right(v) =>
            val outputs = renderAll(v, effectiveFormats, opts.ci)
            outputs.foreach(println)
            sys.exit(0)
      case None =>
        // scopt already printed errors/help.
        sys.exit(2)
    end match
  end main

  // --- Rendering ---

  private def renderAll(v: Version, formats: List[OutputFormat], isCI: Boolean): List[String] =
    formats.distinct.map {
      case OutputFormat.Pretty  => renderPretty(v, isCI)
      case OutputFormat.Compact => renderCompact(v)
      case OutputFormat.Json    => renderJson(v)
      case OutputFormat.Yaml    => renderYaml(v)
    }

  private def renderPretty(v: Version, isCI: Boolean): String =
    // Human-readable multi-line summary. Currently we don't vary output by CI, but keep the param to allow future adjustments.
    val _ = isCI // mark as used to satisfy -Werror unused parameter policy
    val b = new StringBuilder
    b.append("Version:\n")
    b.append(s"  full      : ${v.toString}\n")
    b.append(s"  core      : ${v.major.value}.${v.minor.value}.${v.patch.value}\n")
    b.append(s"  preRelease: ${v.preRelease.fold("none")(_.toString)}\n")
    b.append(s"  metadata  : ${v.buildMetadata.map(_.render).getOrElse("none")}\n")
    b.result()

  private def renderCompact(v: Version): String =
    // SemVer as a single line
    v.toString

  private def renderJson(v: Version): String =
    // JSON via jsoniter-scala codecs from version.codecs.jsoniter
    writeToString(v)

  private def renderYaml(v: Version): String =
    // YAML via scala-yaml codecs from version.codecs.yaml; .asYaml provided by the library
    v.asYaml

  private def renderError(e: ResolutionError): String =
    s"ERROR: ${e.message}"
end CLI
