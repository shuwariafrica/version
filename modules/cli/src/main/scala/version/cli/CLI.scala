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

import boilerplate.nullable.*
import scopt.OParser

import version.Formatter
import version.ResolvableScheme
import version.Utc
import version.Version
import version.VersionResolver
import version.cli.logging.ColourConfig
import version.cli.logging.LogConfig
import version.cli.logging.StandardLogger
import version.resolution.GitError
import version.resolution.GitRepository
import version.resolution.ResolutionConfig
import version.resolution.ResolutionError
import version.resolution.ResolutionResult
import version.resolution.VersionCliCore as Core
import version.resolution.domain.CiMetadata
import version.resolution.domain.Release
import version.resolution.environment.CiDetector
import version.resolution.logging.Logger
import version.resolution.logging.Verbose
import version.resolution.openRepository
import version.semver.SemVer

/** Command-line entry point for version-cli.
  *
  * Resolves a [[Version]] from a repository's Git state and either reports it (the resolved or `target` version) or records it
  * (`bump`, `target <version>`, `tag`). Resolution is scheme-generic via [[VersionResolver]]; today the only registered
  * scheme is SemVer, so the SemVer renderers and the JSON sink apply.
  */
object CLI:

  /** Pairs a resolution result with the scheme that produced it, for downstream rendering. */
  final private case class TypedResult[V <: Version](scheme: ResolvableScheme[V], result: ResolutionResult[V])

  def main(args: Array[String]): Unit =
    // End-of-the-world boundary: turn any escaped exception into a meaningful message and a non-zero exit rather than a
    // stack trace. `run` returns error values; this only guards against unexpected failures.
    val code =
      try run(args)
      catch
        case scala.util.control.NonFatal(e) =>
          Console.err.println(s"ERROR: ${e.getMessage.getOrElse("unexpected failure")}")
          1
    sys.exit(code)

  /** Runs the CLI and returns the process exit code without calling `sys.exit`, keeping the flow testable. */
  private[cli] def run(args: Array[String]): Int =
    val metadata = CiDetector.detectCurrent()
    OParser.parse(CliOptions.parser, args, CliOptions.default) match
      case Some(opts0) =>
        val opts = applyPostParseDefaults(opts0, metadata)
        val logConfig = LogConfig(isVerbose = opts.verbose, isCI = opts.ci)
        val colourCfg =
          if opts.ci || opts.noColour then ColourConfig(enableColours = false, isCI = opts.ci)
          else ColourConfig.fromEnvironment(opts.ci)
        val logger = StandardLogger(logConfig, colourCfg)
        given Verbose = Verbose(opts.verbose)

        val resolver = buildResolver()
        opts.command match
          case sc: ShowConfig      => runShow(resolver, sc, opts, metadata, logger)
          case ts: TargetSetConfig => runTargetSet(resolver, opts, logger, ts)
          case bc: BumpConfig      => runBump(resolver, opts, logger, bc)
          case tc: TagConfig       => runTag(resolver, opts, metadata, logger, tc)
          case lc: ListConfig      => runList(resolver, opts, metadata, logger, lc)
      case None => 2
  end run

  private def applyPostParseDefaults(o: CliOptions, metadata: Option[CiMetadata]): CliOptions =
    val inferredPr = o.prNumber.orElse(metadata.flatMap(_.inferPullRequestNumber))
    val inferredBranch = o.branchOverride.orElse(metadata.flatMap(_.inferBranchOverride))
    val inferredCi = if o.ci then true else metadata.exists(_.isCi)
    val base = o.copy(prNumber = inferredPr, branchOverride = inferredBranch, ci = inferredCi)
    base.command match
      case sc: ShowConfig =>
        val style =
          if sc.consoleStyleExplicit then sc.consoleStyle
          else if base.ci then ConsoleStyle.Compact
          else sc.consoleStyle
        val sinks = if sc.sinks.isEmpty then List(OutputSink(SinkKind.Console, None)) else dedupeSinks(sc.sinks)
        base.copy(command = sc.copy(sinks = sinks, consoleStyle = style))
      case _ => base

  private def dedupeSinks(sinks: List[OutputSink]): List[OutputSink] =
    sinks.foldLeft(List.empty[OutputSink]) { (acc, s) =>
      if s.destination.isEmpty && acc.exists(o => o.kind == s.kind && o.destination.isEmpty) then acc else acc :+ s
    }

  /** SemVer is the only registered scheme today. */
  private def buildResolver(): VersionResolver[? <: Version] = VersionResolver.withDefaults[SemVer]

  /** SemVer `Full` formatter parameterised by `--sha-length` (40 leaves the SHA verbatim). */
  private def buildSemVerFullFormatter(opts: CliOptions): Formatter[SemVer] =
    if opts.shaLength == 40 then SemVer.Formatter.Full
    else SemVer.Formatter.Full.withShaLength(opts.shaLength)

  private def buildConfig[V <: Version](
    r: VersionResolver[V],
    opts: CliOptions,
    metadata: Option[CiMetadata]
  ): ResolutionConfig[V] =
    given ResolvableScheme[V] = r.scheme
    ResolutionConfig
      .default[V](opts.repository.toString)
      .copy(
        basisCommit = opts.basisCommit,
        prNumber = opts.prNumber,
        branchOverride = opts.branchOverride,
        verbose = opts.verbose,
        tagParser = r.tagParser
      )
      .mergeWith(metadata)

  private def runShow(
    resolver: VersionResolver[? <: Version],
    sc: ShowConfig,
    opts: CliOptions,
    metadata: Option[CiMetadata],
    logger: Logger
  )(using Verbose): Int = resolver match
    case r: VersionResolver[v] =>
      given ResolvableScheme[v] = r.scheme
      Core.resolveAll(buildConfig(r, opts, metadata), openRepository, logger, Verbose(opts.verbose)) match
        case Left(e) =>
          logger.error(renderError(e))
          1
        case Right(result) =>
          val typed = TypedResult(r.scheme, result)
          val (consoleOutputs, fileWrites) = render(typed, sc, buildSemVerFullFormatter(opts), logger)
          val failed = fileWrites.collect { case Left(m) => m }
          consoleOutputs.foreach(println)
          if failed.nonEmpty then
            failed.foreach(logger.error)
            1
          else 0

  private def render[V <: Version](
    typed: TypedResult[V],
    sc: ShowConfig,
    semverFull: Formatter[SemVer],
    logger: Logger
  )(using Verbose): (List[String], List[Either[String, Unit]]) =
    val shown = sc.what match
      case ShowKind.Current => typed.result.resolved
      case ShowKind.Target  => typed.result.target
    val consoleBuf = scala.collection.mutable.ListBuffer.empty[String]
    val fileResults = scala.collection.mutable.ListBuffer.empty[Either[String, Unit]]
    sc.sinks.foreach { sink =>
      val content = sink.kind match
        case SinkKind.Console => renderConsole(shown, typed.result, sc, semverFull)
        case SinkKind.Raw     => renderCanonical(shown)
        case SinkKind.Json    =>
          shown match
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
  end render

  private def renderCanonical[V <: Version](v: V): String = v match
    case s: SemVer => SemVer.Formatter.Standard.format(s)
    case other     => other.show

  private def renderConsole[V <: Version](
    shown: V,
    result: ResolutionResult[V],
    sc: ShowConfig,
    semverFull: Formatter[SemVer]
  ): String = sc.consoleStyle match
    case ConsoleStyle.Compact => renderCanonical(shown)
    case ConsoleStyle.Pretty  => renderPretty(shown, result, sc, semverFull)

  private def renderPretty[V <: Version](
    shown: V,
    result: ResolutionResult[V],
    sc: ShowConfig,
    semverFull: Formatter[SemVer]
  ): String =
    val sep = System.lineSeparator()
    val b = new StringBuilder
    val label = sc.what match
      case ShowKind.Current => "version"
      case ShowKind.Target  => "target "
    b.append(s"Version:$sep")
    b.append(s"  $label   : ${renderCanonical(shown)}$sep")
    shown match
      case s: SemVer => b.append(s"  full      : ${semverFull.format(s)}$sep")
      case _         => ()
    b.append(s"  target    : ${renderCanonical(result.target)}$sep")
    b.append(s"  mode      : ${result.mode}$sep")
    result.basis.foreach(c => b.append(s"  commit    : ${c.id.value} (${Utc.dateTime(c.commitTime)} UTC)$sep"))
    result.base.foreach(rel =>
      b.append(s"  base      : ${renderCanonical(rel.version)} (${rel.tag}, released ${Utc.dateTime(rel.releaseTime)} UTC)$sep"))
    b.result()

  private def runList(
    resolver: VersionResolver[? <: Version],
    opts: CliOptions,
    metadata: Option[CiMetadata],
    logger: Logger,
    lc: ListConfig
  )(using Verbose): Int = resolver match
    case r: VersionResolver[v] =>
      given ResolvableScheme[v] = r.scheme
      // Validate the optional version bounds up front and report a meaningful error rather than silently ignoring an
      // unparseable --since / --until.
      val bounds =
        for
          since <- parseBound(r.scheme, lc.since, "since")
          until <- parseBound(r.scheme, lc.until, "until")
        yield (since, until)
      bounds match
        case Left(message) =>
          logger.error(message)
          1
        case Right((since, until)) =>
          Core.releaseHistory(buildConfig(r, opts, metadata), openRepository, logger, Verbose(opts.verbose)) match
            case Left(e) =>
              logger.error(renderError(e))
              1
            case Right(releases) =>
              filterReleases(releases, lc, since, until, r.scheme).foreach(rel => println(renderRelease(rel, lc.details)))
              0

  /** Parses an optional version bound, returning a CLI message when it is present but unparseable. */
  private def parseBound[V <: Version](scheme: ResolvableScheme[V], raw: Option[String], flag: String): Either[String, Option[V]] =
    raw match
      case None    => Right(None)
      case Some(s) =>
        scheme.parse(stripVPrefix(s)) match
          case Right(value) => Right(Some(value))
          case Left(_)      => Left(s"invalid --$flag version '$s' for scheme '${scheme.name}'")

  /** Applies the scheme-generic filters and orders newest-first. Filtering uses only `scheme.ordering` and the
    * `isFinal` predicate, so it holds for any scheme.
    */
  private def filterReleases[V <: Version](
    releases: List[Release[V]],
    lc: ListConfig,
    since: Option[V],
    until: Option[V],
    scheme: ResolvableScheme[V]
  ): List[Release[V]] =
    given ResolvableScheme[V] = scheme
    val ord = scheme.ordering
    val byFinal = if lc.finalOnly then releases.filter(_.version.isFinal) else releases
    val bySince = since.fold(byFinal)(bound => byFinal.filter(rel => ord.gteq(rel.version, bound)))
    val byUntil = until.fold(bySince)(bound => bySince.filter(rel => ord.lteq(rel.version, bound)))
    // releaseHistory yields ascending by version; present newest first.
    val newestFirst = byUntil.reverse
    lc.limit.fold(newestFirst)(n => newestFirst.take(Math.max(0, n)))

  /** Default: `<version>  <release date>`. With `--details`: `<version>  <release date>  <tag>  <source-commit date>`,
    * where the release date is the tag's tagger time and the commit date is the committer time of the commit it points
    * to.
    */
  private def renderRelease[V <: Version](rel: Release[V], details: Boolean): String =
    val version = renderCanonical(rel.version)
    val releaseDate = s"${Utc.dateTime(rel.releaseTime)} UTC"
    if details then s"$version  $releaseDate  ${rel.tag}  ${Utc.dateTime(rel.commit.commitTime)} UTC"
    else s"$version  $releaseDate"

  private def stripVPrefix(s: String): String =
    if s.startsWith("v") || s.startsWith("V") then s.drop(1) else s

  private def runBump(
    resolver: VersionResolver[? <: Version],
    opts: CliOptions,
    logger: Logger,
    bc: BumpConfig
  ): Int = resolver match
    case r: VersionResolver[v] =>
      val scheme = r.scheme
      if scheme.keywordAliases.contains(bc.keyword.toLowerCase) then commit(opts, logger, s"version: ${bc.keyword}", bc.noSign, bc.dryRun)
      else
        val accepted = scheme.keywordAliases.keys.toList.sorted.mkString(", ")
        logger.error(s"unknown bump keyword '${bc.keyword}' for scheme '${scheme.name}'; accepted: $accepted")
        1

  private def runTargetSet(
    resolver: VersionResolver[? <: Version],
    opts: CliOptions,
    logger: Logger,
    ts: TargetSetConfig
  ): Int = resolver match
    case r: VersionResolver[v] =>
      // Pre-validate for fail-fast UX; the resolver remains authoritative at next resolution.
      val normalised = stripVPrefix(ts.versionString)
      r.scheme.parse(normalised) match
        case Right(_) => commit(opts, logger, s"target: ${ts.versionString}", ts.noSign, ts.dryRun)
        case Left(_)  =>
          logger.error(s"invalid target version '${ts.versionString}' for scheme '${r.scheme.name}'")
          1

  private def commit(opts: CliOptions, logger: Logger, message: String, noSign: Boolean, dryRun: Boolean): Int =
    openRepository(opts.repository.toString) match
      case Left(e) =>
        logger.error(renderGitError(e))
        1
      case Right(repo) =>
        try
          resolveSign(repo, noSign) match
            case Left(msg) =>
              logger.error(s"ERROR: $msg")
              1
            case Right(sign) =>
              if dryRun then
                println(s"[dry-run] empty commit (sign=$sign): $message")
                0
              else
                val outcome =
                  for
                    author <- repo.defaultSignature
                    sha <- repo.createCommit(message, author, sign)
                  yield sha
                outcome match
                  case Right(sha) =>
                    println(s"${sha.value}  $message")
                    0
                  case Left(e) =>
                    logger.error(renderGitError(e))
                    1
        finally repo.close()

  private def runTag(
    resolver: VersionResolver[? <: Version],
    opts: CliOptions,
    metadata: Option[CiMetadata],
    logger: Logger,
    tc: TagConfig
  )(using Verbose): Int = resolver match
    case r: VersionResolver[v] =>
      given ResolvableScheme[v] = r.scheme
      val versionStr: Either[Int, String] = tc.version match
        case Some(explicit) =>
          val normalised = stripVPrefix(explicit)
          r.scheme.parse(normalised) match
            case Right(_) => Right(explicit)
            case Left(_)  =>
              logger.error(s"invalid tag version '$explicit' for scheme '${r.scheme.name}'")
              Left(1)
        case None =>
          Core.resolveAll(buildConfig(r, opts, metadata), openRepository, logger, Verbose(opts.verbose)) match
            case Right(result) => Right(renderCanonical(result.target))
            case Left(e)       =>
              logger.error(renderError(e))
              Left(1)
      versionStr match
        case Left(code)     => code
        case Right(version) => doTag(opts, logger, version, tc)

  private def doTag(opts: CliOptions, logger: Logger, version: String, tc: TagConfig): Int =
    val message = tc.message.getOrElse(s"Release $version")
    openRepository(opts.repository.toString) match
      case Left(e) =>
        logger.error(renderGitError(e))
        1
      case Right(repo) =>
        try
          resolveSign(repo, tc.noSign) match
            case Left(msg) =>
              logger.error(s"ERROR: $msg")
              1
            case Right(sign) =>
              val outcome =
                for
                  headOpt <- repo.head
                  target <- headOpt.toRight(GitError.RevisionNotFound("HEAD"))
                  tagger <- repo.defaultSignature
                  _ <- if tc.dryRun then Right(()) else repo.createTag(version, target, message, tagger, sign)
                yield target
              outcome match
                case Right(target) =>
                  if tc.dryRun then println(s"[dry-run] annotated tag '$version' at ${target.value} (sign=$sign): $message")
                  else println(s"Tagged '$version' at ${target.value}: $message")
                  0
                case Left(e) =>
                  logger.error(renderGitError(e))
                  1
        finally repo.close()
    end match
  end doTag

  /** Signed by default: sign whenever a signing key is configured. Refuse to create an unsigned object unless the user
    * explicitly opts in with `--no-sign`.
    */
  private def resolveSign(repo: GitRepository, noSign: Boolean): Either[String, Boolean] =
    if noSign then Right(false)
    else
      repo.signingKey match
        case Right(Some(_)) => Right(true)
        case Right(None)    =>
          Left("no signing key configured (set user.signingkey, or pass --no-sign to create an unsigned object)")
        case Left(e) => Left(e.message)

  private def renderError(e: ResolutionError): String = s"ERROR: ${e.message}"

  private def renderGitError(e: GitError): String = e match
    case GitError.SigningFailure(detail) =>
      s"ERROR: $detail. Ensure gpg is installed and the signing key is available, or pass --no-sign to create an unsigned object."
    case other => s"ERROR: ${other.message}"
end CLI
