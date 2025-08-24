package version.cli

import scopt.OParser
import version.Version
import version.cli.core.{VersionCliCore => Core}
import version.cli.core.domain.CliConfig
import version.cli.core.ResolutionError
import version.cli.Options.OutputFormat
import version.codecs.jsoniter.given
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import org.virtuslab.yaml.*
import version.codecs.yaml.given

/** Command-line entry point for version-cli.
 *
 * This CLI:
 *  - Parses flags using scopt
 *  - Invokes version-cli-core to resolve a Version for a repository
 *  - Prints the result in one or more formats: pretty | compact | json | yaml
 *
 * Effects are confined to main; underlying core remains pure.
 */
object CLI:

  def main(args: Array[String]): Unit =
    val parser = Options.parser
    OParser.parse(parser, args, Options.default) match
      case Some(opts) =>
        if opts.verbose then
          Console.err.println(s"[version-cli] resolving in ${opts.workDir} at ${opts.basisCommit}; formats=${opts.formats.mkString(",")}")

        val cfg = CliConfig(
          repo = opts.workDir,
          basisCommit = opts.basisCommit,
          prNumber = opts.prNumber,
          branchOverride = opts.branchOverride,
          shaLength = opts.shaLength
        )

        Core.resolve(cfg) match
          case Left(err) =>
            Console.err.println(renderError(err))
            sys.exit(1)
          case Right(v) =>
            val outputs = renderAll(v, opts.formats)
            outputs.foreach(println)
            sys.exit(0)

      case None =>
        // scopt already printed errors/help.
        sys.exit(2)

  // --- Rendering ---

  private def renderAll(v: Version, formats: List[OutputFormat]): List[String] =
    formats.distinct.map {
      case OutputFormat.Pretty  => renderPretty(v)
      case OutputFormat.Compact => renderCompact(v)
      case OutputFormat.Json    => renderJson(v)
      case OutputFormat.Yaml    => renderYaml(v)
    }

  private def renderPretty(v: Version): String =
    // Human-readable multi-line summary.
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
