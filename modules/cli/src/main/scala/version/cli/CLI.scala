package version.cli

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import org.virtuslab.yaml.*
import scopt.OParser

import version.Version
import version.cli.CliOptions.given
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
    val parser = CliOptions.parser
    OParser.parse(parser, args, CliOptions.default) match
      case Some(opts0) =>
        // Possibly inject default console sink already applied by checkConfig; ensure style override for CI if needed.
        val resolvedOpts = applyPostParseDefaults(opts0)

        val logConfig = LogConfig(isVerbose = resolvedOpts.verbose, isCI = resolvedOpts.ci)
        val colourCfg =
          if resolvedOpts.ci || resolvedOpts.noColour then ColourConfig(enableColours = false, isCI = resolvedOpts.ci)
          else ColourConfig.fromEnvironment(resolvedOpts.ci)
        val logger = StandardLogger(logConfig, colourCfg)

        given Boolean = resolvedOpts.verbose

        logger.verbose(
          s"Resolving in ${resolvedOpts.repository} at ${resolvedOpts.basisCommit}; sinks=${renderSinkSummary(resolvedOpts)}; ci=${resolvedOpts.ci}",
          "CLI")

        resolvedOpts.command match
          case rc: ResolveConfig =>
            val cfg = CliConfig(
              repo = resolvedOpts.repository,
              basisCommit = resolvedOpts.basisCommit,
              prNumber = resolvedOpts.prNumber,
              branchOverride = resolvedOpts.branchOverride,
              shaLength = resolvedOpts.shaLength,
              verbose = resolvedOpts.verbose
            )
            Core.resolve(cfg, logger, resolvedOpts.verbose) match
              case Left(err) =>
                logger.error(renderError(err))
                sys.exit(1)
              case Right(version) =>
                val (consoleOutputs, fileWrites) = render(version, rc, logger)(using resolvedOpts.verbose)
                val failed = fileWrites.collect { case Left(m) => m }
                if failed.nonEmpty then
                  failed.foreach(logger.error(_))
                  consoleOutputs.foreach(println)
                  sys.exit(1)
                consoleOutputs.foreach(println)
                sys.exit(0)
          case _: ReleaseConfig =>
            logger.error("The 'release' command is not yet implemented.")
            sys.exit(2)
        end match
      case None => sys.exit(2)
    end match
  end main

  private def applyPostParseDefaults(o: CliOptions): CliOptions =
    o.command match
      case rc: ResolveConfig =>
        val style =
          if rc.consoleStyleExplicit then rc.consoleStyle
          else if o.ci then ConsoleStyle.Compact
          else rc.consoleStyle
        val sinks1 = if rc.sinks.isEmpty then List(OutputSink(SinkKind.Console, None)) else rc.sinks
        o.copy(command = rc.copy(sinks = dedupeSinks(sinks1), consoleStyle = style))
      case _ => o

  private def dedupeSinks(sinks: List[OutputSink]): List[OutputSink] =
    // Allow duplicates when destinations differ; dedupe identical (kind, None)
    sinks.foldLeft(List.empty[OutputSink]) { (acc, s) =>
      if s.destination.isEmpty && acc.exists(o => o.kind == s.kind && o.destination.isEmpty) then acc else acc :+ s
    }

  private def render(version: Version, rc: ResolveConfig, logger: Logger)(using Boolean): (List[String], List[Either[String, Unit]]) =
    val consoleBuf = scala.collection.mutable.ListBuffer.empty[String]
    val fileResults = scala.collection.mutable.ListBuffer.empty[Either[String, Unit]]
    rc.sinks.foreach { sink =>
      val content = sink.kind match
        case SinkKind.Console => renderConsole(version, rc.consoleStyle)
        case SinkKind.Raw     => version.toString
        case SinkKind.Json    => writeToString(version)
        case SinkKind.Yaml    => version.asYaml
      sink.destination match
        case Some(path) =>
          try
            // ensure parent directories exist
            os.makeDir.all(path / os.up)
            os.write.over(path, content)
            logger.verbose(s"Wrote ${sink.kind.toString.toLowerCase} output to $path", "CLI")
            fileResults += Right(())
          catch case t: Throwable => fileResults += Left(s"Failed to write $path: ${t.getMessage}")
        case None => consoleBuf += content
    }
    (consoleBuf.toList, fileResults.toList)

  private def renderConsole(v: Version, style: ConsoleStyle): String = style match
    case ConsoleStyle.Pretty  => renderConsolePretty(v)
    case ConsoleStyle.Compact => v.toString

  private def renderConsolePretty(v: Version): String =
    val b = new StringBuilder
    b.append("Version:\n")
    b.append(s"  full      : ${v.toString}\n")
    b.append(s"  core      : ${v.major.value}.${v.minor.value}.${v.patch.value}\n")
    b.append(s"  preRelease: ${v.preRelease.fold("none")(_.toString)}\n")
    b.append(s"  metadata  : ${v.buildMetadata.map(_.render).getOrElse("none")}\n")
    b.result()

  private def renderSinkSummary(o: CliOptions): String = o.command match
    case rc: ResolveConfig =>
      rc.sinks
        .map {
          case OutputSink(k, Some(p)) => s"${k.toString.toLowerCase}=$p"
          case OutputSink(k, None)    => k.toString.toLowerCase
        }
        .mkString(",")
    case _ => "<non-resolve>"

  private def renderError(e: ResolutionError): String = s"ERROR: ${e.message}"
end CLI
