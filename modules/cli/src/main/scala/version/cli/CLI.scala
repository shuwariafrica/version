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

import scopt.OParser

import version.Formatter
import version.ResolvableScheme
import version.Version
import version.VersionResolver
import version.cli.logging.ColourConfig
import version.cli.logging.LogConfig
import version.cli.logging.StandardLogger
import version.resolution.ResolutionConfig
import version.resolution.ResolutionError
import version.resolution.VersionCliCore as Core
import version.resolution.domain.CiMetadata
import version.resolution.environment.CiDetector
import version.resolution.logging.Logger
import version.resolution.logging.Verbose
import version.resolution.openRepository
import version.semver.SemVer

/** Command-line entry point for version-cli.
  *
  * Resolves a [[Version]] from a repository's Git state and renders it via configured sinks.
  * Resolution is scheme-generic via [[VersionResolver]]; today the only registered scheme is SemVer, so the
  * SemVer-specific renderers and the JSON sink apply. Other schemes added in future supply their own renderers.
  */
object CLI:

  /** Internal bundle pairing the resolved value with the resolver's scheme for downstream rendering. */
  final private case class TypedResult[V <: Version](
    scheme: ResolvableScheme[V],
    value: V
  )

  def main(args: Array[String]): Unit =
    val metadata = CiDetector.detectCurrent()
    val parser = CliOptions.parser
    OParser.parse(parser, args, CliOptions.default) match
      case Some(opts0) =>
        val resolvedOpts = applyPostParseDefaults(opts0, metadata)

        val logConfig = LogConfig(isVerbose = resolvedOpts.verbose, isCI = resolvedOpts.ci)
        val colourCfg =
          if resolvedOpts.ci || resolvedOpts.noColour then ColourConfig(enableColours = false, isCI = resolvedOpts.ci)
          else ColourConfig.fromEnvironment(resolvedOpts.ci)
        val logger = StandardLogger(logConfig, colourCfg)

        given Verbose = Verbose(resolvedOpts.verbose)

        logger.verbose(
          s"Resolving in ${resolvedOpts.repository} at ${resolvedOpts.basisCommit}; sinks=${renderSinkSummary(resolvedOpts)}; ci=${resolvedOpts.ci}",
          "CLI")

        resolvedOpts.command match
          case rc: ResolveConfig =>
            val resolver = buildResolver()
            val semverFullFormatter = buildSemVerFullFormatter(resolvedOpts)
            resolve(resolver, resolvedOpts, metadata, logger) match
              case Left(e) =>
                logger.error(renderError(e))
                sys.exit(1)
              case Right(typed) =>
                val (consoleOutputs, fileWrites) = render(typed, rc, semverFullFormatter, logger)
                val failed = fileWrites.collect { case Left(m) => m }
                if failed.nonEmpty then
                  failed.foreach(logger.error)
                  consoleOutputs.foreach(println)
                  sys.exit(1)
                consoleOutputs.foreach(println)
                sys.exit(0)
          case _: ReleaseConfig =>
            logger.error("The 'release' command is not yet implemented.")
            sys.exit(2)
        end match
      case None =>
        sys.exit(2)
    end match
  end main

  private def applyPostParseDefaults(o: CliOptions, metadata: Option[CiMetadata]): CliOptions =
    val inferredPr = o.prNumber.orElse(metadata.flatMap(_.inferPullRequestNumber))
    val inferredBranch = o.branchOverride.orElse(metadata.flatMap(_.inferBranchOverride))
    val inferredCi = if o.ci then true else metadata.exists(_.isCi)
    val base = o.copy(prNumber = inferredPr, branchOverride = inferredBranch, ci = inferredCi)
    base.command match
      case rc: ResolveConfig =>
        val style =
          if rc.consoleStyleExplicit then rc.consoleStyle
          else if base.ci then ConsoleStyle.Compact
          else rc.consoleStyle
        val sinks1 = if rc.sinks.isEmpty then List(OutputSink(SinkKind.Console, None)) else rc.sinks
        base.copy(command = rc.copy(sinks = dedupeSinks(sinks1), consoleStyle = style))
      case _ => base

  private def dedupeSinks(sinks: List[OutputSink]): List[OutputSink] =
    // Allow duplicates when destinations differ; dedupe identical (kind, None)
    sinks.foldLeft(List.empty[OutputSink]) { (acc, s) =>
      if s.destination.isEmpty && acc.exists(o => o.kind == s.kind && o.destination.isEmpty) then acc else acc :+ s
    }

  /** SemVer is the only registered scheme today. */
  private def buildResolver(): VersionResolver[? <: Version] =
    VersionResolver.withDefaults[SemVer]

  /** SemVer `Full` formatter parameterised by `--sha-length` (40 leaves the SHA verbatim). */
  private def buildSemVerFullFormatter(opts: CliOptions): Formatter[SemVer] =
    if opts.shaLength == 40 then SemVer.Formatter.Full
    else SemVer.Formatter.Full.withShaLength(opts.shaLength)

  /** Canonical-string rendering with explicit formatter selection where one is registered for the scheme. */
  private def renderCanonical[V <: Version](v: V): String = v match
    case s: SemVer => SemVer.Formatter.Standard.format(s)
    case other     => other.show

  private def resolve(
    resolver: VersionResolver[? <: Version],
    opts: CliOptions,
    metadata: Option[CiMetadata],
    logger: Logger
  )(using Verbose): Either[ResolutionError, TypedResult[? <: Version]] = resolver match
    case r: VersionResolver[v] =>
      given ResolvableScheme[v] = r.scheme
      val cfg = ResolutionConfig
        .default[v](opts.repository.toString)
        .copy(
          basisCommit = opts.basisCommit,
          prNumber = opts.prNumber,
          branchOverride = opts.branchOverride,
          verbose = opts.verbose,
          tagParser = r.tagParser
        )
        .mergeWith(metadata)
      Core.resolve(cfg, openRepository, logger, Verbose(opts.verbose)).map(value => TypedResult(r.scheme, value))

  private def render(
    typed: TypedResult[? <: Version],
    rc: ResolveConfig,
    semverFullFormatter: Formatter[SemVer],
    logger: Logger
  )(using Verbose): (List[String], List[Either[String, Unit]]) = typed match
    case t: TypedResult[v] =>
      val consoleBuf = scala.collection.mutable.ListBuffer.empty[String]
      val fileResults = scala.collection.mutable.ListBuffer.empty[Either[String, Unit]]
      rc.sinks.foreach { sink =>
        val content = sink.kind match
          case SinkKind.Console => renderConsole(t, rc.consoleStyle, semverFullFormatter)
          case SinkKind.Raw     => renderCanonical(t.value)
          case SinkKind.Json    =>
            t.value match
              case s: SemVer => SemVerJson.toJson(s)
              case other     =>
                fileResults += Left(s"json output is not supported for ${other.getClass.getSimpleName}")
                ""
        sink.destination match
          case Some(path) =>
            try
              java.nio.file.Files.createDirectories(path.getParent)
              java.nio.file.Files.writeString(path, content)
              logger.verbose(s"Wrote ${sink.kind.toString.toLowerCase} output to $path", "CLI")
              fileResults += Right(())
            catch case th: Throwable => fileResults += Left(s"Failed to write $path: ${th.getMessage}")
          case None => consoleBuf += content
      }
      (consoleBuf.toList, fileResults.toList)

  private def renderConsole[V <: Version](
    t: TypedResult[V],
    style: ConsoleStyle,
    semverFull: Formatter[SemVer]
  ): String = style match
    case ConsoleStyle.Pretty  => renderConsolePretty(t, semverFull)
    case ConsoleStyle.Compact => renderCanonical(t.value)

  private def renderConsolePretty[V <: Version](t: TypedResult[V], semverFull: Formatter[SemVer]): String =
    t.value match
      case s: SemVer => renderSemVerPretty(s, semverFull)
      case other     =>
        // Schemes other than SemVer: canonical form only until a scheme-specific renderer is registered.
        val sep = System.lineSeparator()
        s"Version:$sep  version : ${renderCanonical(other)}$sep"

  private def renderSemVerPretty(v: SemVer, full: Formatter[SemVer]): String =
    val b = new StringBuilder
    val sep = System.lineSeparator()
    b.append(s"Version:$sep")
    b.append(s"  version   : ${SemVer.Formatter.Standard.format(v)}$sep")
    b.append(s"  full      : ${full.format(v)}$sep")
    b.append(s"  preRelease: ${v.preRelease.fold("none")(_.show)}$sep")
    b.append(s"  metadata  : ${v.metadata.map(_.show).getOrElse("none")}$sep")
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
