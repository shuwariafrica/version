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
import version.VersionArithmetic
import version.VersionScheme
import version.Versioned
import version.cli.logging.ColourConfig
import version.cli.logging.LogConfig
import version.cli.logging.StandardLogger
import version.resolution.GitError
import version.resolution.GitRepository
import version.resolution.ResolutionConfig
import version.resolution.ResolutionError
import version.resolution.ResolutionResult
import version.resolution.Resolver
import version.resolution.VersionResolver
import version.resolution.discoverRepository
import version.resolution.domain.CiMetadata
import version.resolution.domain.CommitSha
import version.resolution.domain.Release
import version.resolution.environment.CiDetector
import version.resolution.logging.Logger
import version.semver.SemVer

/** Command-line entry point.
  *
  * Reports the version a repository is at (`show`, `target`, `list`) or records one (`target --set` /
  * `target --increment`, `tag`). Every command discovers the repository from `--repository` or the working directory
  * upwards, exactly as `git` does. Resolution runs through the [[version.resolution.VersionResolver VersionResolver]]
  * the CLI builds; SemVer is the only scheme it registers, so the SemVer renderers and the JSON sink apply.
  */
object CLI:

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
      case Some(parsed) =>
        val opts = applyPostParseDefaults(parsed, metadata)
        val colours =
          if opts.ci || opts.noColour then ColourConfig(enableColours = false, isCI = opts.ci)
          else ColourConfig.fromEnvironment(opts.ci)
        val logger = StandardLogger(LogConfig(isVerbose = opts.verbose, isCI = opts.ci), colours)
        val resolver = buildResolver()
        opts.command match
          case sc: ShowConfig   => runShow(resolver, sc, opts, metadata, logger)
          case tc: TargetConfig => runTarget(resolver, opts, logger, tc)
          case tg: TagConfig    => runTag(resolver, opts, metadata, logger, tg)
          case lc: ListConfig   => runList(resolver, opts, metadata, logger, lc)
      case None => 2
  end run

  // CI-detected pull request and branch values are merged into the resolution configuration itself, so only the
  // presentation defaults are settled here.
  private def applyPostParseDefaults(o: CliOptions, metadata: Option[CiMetadata]): CliOptions =
    val base = o.copy(ci = o.ci || metadata.exists(_.isCi))
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
  private def buildResolver(): VersionResolver[?] = VersionResolver.withDefaults[SemVer]

  /** SemVer `Full` formatter parameterised by `--sha-length` (40 leaves the SHA verbatim). */
  private def buildSemVerFullFormatter(opts: CliOptions): Formatter[SemVer] =
    if opts.shaLength == 40 then SemVer.Formatter.Full
    else SemVer.Formatter.Full.withShaLength(opts.shaLength)

  private def buildConfig[V](
    r: VersionResolver[V],
    opts: CliOptions,
    metadata: Option[CiMetadata]
  ): Either[ResolutionError, ResolutionConfig[V]] =
    ResolutionConfig
      .from[V](opts.repository.toString, opts.basisCommit, opts.prNumber, opts.branchOverride, r.tagParser)
      .map(_.mergeWith(metadata))

  private def runShow(
    resolver: VersionResolver[?],
    sc: ShowConfig,
    opts: CliOptions,
    metadata: Option[CiMetadata],
    logger: Logger
  ): Int = resolver match
    case r: VersionResolver[v] =>
      given VersionScheme[v] = r.scheme
      given VersionArithmetic[v] = r.arithmetic
      given ResolvableScheme[v] = r.workflow
      buildConfig(r, opts, metadata).flatMap(c => Resolver.resolveAll(c, path => discoverRepository(path), logger)) match
        case Left(e) =>
          logger.error(renderError(e))
          1
        case Right(result) =>
          val (consoleOutputs, fileWrites) = render(result, sc, buildSemVerFullFormatter(opts), logger)
          val failed = fileWrites.collect { case Left(m) => m }
          consoleOutputs.foreach(println)
          if failed.nonEmpty then
            failed.foreach(logger.error)
            1
          else 0

  private def render[V](
    result: ResolutionResult[V],
    sc: ShowConfig,
    semverFull: Formatter[SemVer],
    logger: Logger
  )(using VersionScheme[V]): (List[String], List[Either[String, Unit]]) =
    val shown = Versioned(sc.what match
      case ShowKind.Current => result.resolved
      case ShowKind.Target  => result.target)
    val consoleBuf = scala.collection.mutable.ListBuffer.empty[String]
    val fileResults = scala.collection.mutable.ListBuffer.empty[Either[String, Unit]]
    sc.sinks.foreach { sink =>
      val content = sink.kind match
        case SinkKind.Console => renderConsole(shown, result, sc, semverFull)
        case SinkKind.Raw     => renderCanonical(shown)
        case SinkKind.Json    =>
          shown.value match
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

  // SemVer's build metadata belongs to the `full` rendering alone, so the canonical line stays the three numbers and
  // any pre-release. Every other scheme writes itself.
  private def renderCanonical(v: Versioned): String = v.value match
    case s: SemVer => SemVer.Formatter.Standard.format(s)
    case _         => v.show

  private def renderConsole[V](
    shown: Versioned,
    result: ResolutionResult[V],
    sc: ShowConfig,
    semverFull: Formatter[SemVer]
  )(using VersionScheme[V]): String = sc.consoleStyle match
    case ConsoleStyle.Compact => renderCanonical(shown)
    case ConsoleStyle.Pretty  => renderPretty(shown, result, sc, semverFull)

  private def renderPretty[V](
    shown: Versioned,
    result: ResolutionResult[V],
    sc: ShowConfig,
    semverFull: Formatter[SemVer]
  )(using VersionScheme[V]): String =
    val sep = System.lineSeparator()
    val b = new StringBuilder
    val label = sc.what match
      case ShowKind.Current => "version"
      case ShowKind.Target  => "target "
    b.append(s"Version:$sep")
    b.append(s"  $label   : ${renderCanonical(shown)}$sep")
    shown.value match
      case s: SemVer => b.append(s"  full      : ${semverFull.format(s)}$sep")
      case _         => ()
    b.append(s"  target    : ${renderCanonical(Versioned(result.target))}$sep")
    b.append(s"  mode      : ${result.mode}$sep")
    b.append(s"  repository: ${result.repository}$sep")
    result.basis.foreach(c => b.append(s"  commit    : ${c.id.value} (${Utc.dateTime(c.commitTime)} UTC)$sep"))
    result.base.foreach(rel =>
      b.append(s"  base      : ${renderCanonical(Versioned(rel.version))} (${rel.tag}, released ${Utc.dateTime(rel.releaseTime)} UTC)$sep"))
    b.result()

  private def runList(
    resolver: VersionResolver[?],
    opts: CliOptions,
    metadata: Option[CiMetadata],
    logger: Logger,
    lc: ListConfig
  ): Int = resolver match
    case r: VersionResolver[v] =>
      given VersionScheme[v] = r.scheme
      // Bounds are validated before the repository is read, so an unusable --since / --until is reported rather than
      // silently ignored.
      val positions = r.scheme.numbers(r.workflow.initialVersion).length
      val bounds =
        for
          since <- parseBound(r.scheme, positions, lc.since, "since")
          until <- parseBound(r.scheme, positions, lc.until, "until")
        yield (since, until)
      bounds match
        case Left(message) =>
          logger.error(message)
          1
        case Right((since, until)) =>
          buildConfig(r, opts, metadata).flatMap(c => Resolver.releaseHistory(c, path => discoverRepository(path), logger)) match
            case Left(e) =>
              logger.error(renderError(e))
              1
            case Right(releases) =>
              filterReleases(releases, lc, since, until).foreach(rel => println(renderRelease(rel, lc.details)))
              0

  /** A bound the release list is filtered by: a version the scheme reads outright, or the leading numbers of a line. */
  private enum VersionBound[V]:
    case Exact(value: V)
    case Line(leading: List[Long])

  private def parseBound[V](
    scheme: VersionScheme[V],
    positions: Int,
    raw: Option[String],
    flag: String
  ): Either[String, Option[VersionBound[V]]] =
    raw match
      case None        => Right(None)
      case Some(bound) =>
        val text = stripVPrefix(bound)
        scheme.parse(text) match
          case Right(value) => Right(Some(VersionBound.Exact(value)))
          case Left(_)      =>
            lineOf(text, positions)
              .map(leading => Right(Some(VersionBound.Line(leading))))
              .getOrElse(Left(s"invalid --$flag version '$bound' for scheme '${scheme.name}'"))

  // A line is a dotted prefix of the scheme's numeric positions, optionally closed by `x` (`1`, `1.x`, `1.2`). A
  // scheme with no numeric positions names no lines, so its bounds must be full versions.
  private def lineOf(text: String, positions: Int): Option[List[Long]] =
    val parts = text.split('.').toList
    val digits = if parts.lastOption.exists(_.equalsIgnoreCase("x")) then parts.init else parts
    Option.when(digits.nonEmpty && digits.length <= positions && digits.forall(decimal))(digits.map(_.toLong))

  // 18 digits is the widest decimal that cannot overflow a Long.
  private def decimal(s: String): Boolean = s.nonEmpty && s.length <= 18 && s.forall(c => c >= '0' && c <= '9')

  private def filterReleases[V](
    releases: List[Release[V]],
    lc: ListConfig,
    since: Option[VersionBound[V]],
    until: Option[VersionBound[V]]
  )(using scheme: VersionScheme[V]): List[Release[V]] =
    val byStable = if lc.finalOnly then releases.filter(rel => scheme.stable(rel.version)) else releases
    val bounded = byStable.filter: rel =>
      since.forall(b => rank(b, rel.version) <= 0) && until.forall(b => rank(b, rel.version) >= 0)
    // releaseHistory yields ascending by version; present newest first.
    val newestFirst = bounded.reverse
    lc.limit.fold(newestFirst)(n => newestFirst.take(Math.max(0, n)))

  // How `bound` ranks against `version`: negative where the bound is the lower of the two. A line is ranked on the
  // positions it names alone, so `1.2` ranks `1.2.0-rc.1` and `1.2.9` alike and neither falls on the 2 line's side.
  private def rank[V](bound: VersionBound[V], version: V)(using scheme: VersionScheme[V]): Int = bound match
    case VersionBound.Exact(value)  => scheme.precedence.compare(value, version)
    case VersionBound.Line(leading) =>
      val numbers = scheme.numbers(version)
      leading.zipWithIndex
        .map((position, i) => java.lang.Long.compare(position, if i < numbers.length then numbers(i) else 0L))
        .find(_ != 0)
        .getOrElse(0)

  /** Default: `<version>  <release date>`. With `--details`: `<version>  <release date>  <tag>  <source-commit date>`,
    * where the release date is the tag's tagger time and the commit date is the committer time of the commit it points
    * to.
    */
  private def renderRelease[V](rel: Release[V], details: Boolean)(using VersionScheme[V]): String =
    val version = renderCanonical(Versioned(rel.version))
    val releaseDate = s"${Utc.dateTime(rel.releaseTime)} UTC"
    if details then s"$version  $releaseDate  ${rel.tag}  ${Utc.dateTime(rel.commit.commitTime)} UTC"
    else s"$version  $releaseDate"

  private def stripVPrefix(s: String): String =
    if s.startsWith("v") || s.startsWith("V") then s.drop(1) else s

  private def runTarget(
    resolver: VersionResolver[?],
    opts: CliOptions,
    logger: Logger,
    tc: TargetConfig
  ): Int = resolver match
    case r: VersionResolver[v] =>
      val scheme = r.scheme
      (tc.set, tc.increment) match
        case (Some(named), _) =>
          // Pre-validate for fail-fast UX; the resolver remains authoritative at next resolution.
          scheme.parse(stripVPrefix(named)) match
            case Right(_) => record(opts, logger, s"target: $named", tc.noSign, tc.dryRun)
            case Left(_)  =>
              logger.error(s"invalid target version '$named' for scheme '${scheme.name}'")
              1
        case (_, Some(keyword)) =>
          if r.workflow.directives.contains(keyword.toLowerCase) then record(opts, logger, s"version: $keyword", tc.noSign, tc.dryRun)
          else
            val accepted = r.workflow.directives.keys.toList.sorted.mkString(", ")
            logger.error(s"unknown increment keyword '$keyword' for scheme '${scheme.name}'; accepted: $accepted")
            1
        case (None, None) =>
          logger.error("target: provide --set <version> or --increment <keyword> to record a directive")
          1

  // Both writing commands run against one discovered session: the repository is opened once, the signing decision is
  // taken from it, and it stays open until the command is finished with it.
  private def withSession(opts: CliOptions, logger: Logger, noSign: Boolean)(act: (GitRepository, Boolean) => Int): Int =
    discoverRepository(opts.repository.toString) match
      case Left(e) =>
        logger.error(renderGitError(e))
        1
      case Right(repo) =>
        try
          resolveSign(repo, noSign) match
            case Left(message) =>
              logger.error(s"ERROR: $message")
              1
            case Right(sign) => act(repo, sign)
        finally repo.close()

  private def record(opts: CliOptions, logger: Logger, message: String, noSign: Boolean, dryRun: Boolean): Int =
    withSession(opts, logger, noSign): (repo, sign) =>
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

  private def runTag(
    resolver: VersionResolver[?],
    opts: CliOptions,
    metadata: Option[CiMetadata],
    logger: Logger,
    tc: TagConfig
  ): Int = resolver match
    case r: VersionResolver[v] =>
      given VersionScheme[v] = r.scheme
      given VersionArithmetic[v] = r.arithmetic
      given ResolvableScheme[v] = r.workflow
      withSession(opts, logger, tc.noSign): (repo, sign) =>
        plan(r, repo, opts, metadata, logger, tc) match
          case Left(message) =>
            logger.error(s"ERROR: $message")
            1
          case Right((name, target)) =>
            val message = tc.message.getOrElse(s"Release $name")
            if tc.dryRun then
              println(s"[dry-run] annotated tag '$name' at ${target.value} (sign=$sign): $message")
              0
            else
              repo.defaultSignature.flatMap(tagger => repo.createTag(name, target, message, tagger, sign)) match
                case Right(_) =>
                  println(s"Tagged '$name' at ${target.value}: $message")
                  0
                case Left(e) =>
                  logger.error(renderGitError(e))
                  1

  // An explicit version is written as the user spelled it, at HEAD. A derived one is written at the commit resolution
  // actually read, so a HEAD that moves mid-command cannot receive a version computed for another commit.
  private def plan[V](
    r: VersionResolver[V],
    repo: GitRepository,
    opts: CliOptions,
    metadata: Option[CiMetadata],
    logger: Logger,
    tc: TagConfig
  )(using VersionScheme[V], VersionArithmetic[V], ResolvableScheme[V]): Either[String, (String, CommitSha)] =
    tc.version match
      case Some(explicit) =>
        r.scheme.parse(stripVPrefix(explicit)) match
          case Right(_) => head(repo).map(target => (explicit, target))
          case Left(_)  => Left(s"invalid tag version '$explicit' for scheme '${r.scheme.name}'")
      case None =>
        buildConfig(r, opts, metadata)
          .flatMap(config => Resolver.resolveAll(config, repo, logger))
          .left
          .map(_.getMessage.unsafe)
          .flatMap: result =>
            val name = tc.prefix + renderCanonical(Versioned(result.target))
            result.basis.map(basis => (name, basis.id)).toRight("cannot tag an empty repository")

  private def head(repo: GitRepository): Either[String, CommitSha] =
    repo.head.left.map(_.getMessage.unsafe).flatMap(_.toRight("cannot tag an unborn HEAD"))

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
        case Left(e) => Left(e.getMessage.unsafe)

  private def renderError(e: ResolutionError): String = s"ERROR: ${e.getMessage.unsafe}"

  private def renderGitError(e: GitError): String = e match
    case GitError.SigningFailure(detail, _) =>
      s"ERROR: $detail. Ensure gpg is installed and the signing key is available, or pass --no-sign to create an unsigned object."
    case other => s"ERROR: ${other.getMessage.unsafe}"
end CLI
