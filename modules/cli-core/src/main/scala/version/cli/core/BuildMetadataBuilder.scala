package version.cli.core

import version.BuildMetadata
import version.cli.core.domain.*
import version.cli.core.git.Git
import version.cli.core.logging.Logger

// scalafix:off
/** Constructs BuildMetadata for development versions according to the specification. */
object BuildMetadataBuilder:

  /** Assemble metadata for a snapshot. Always includes sha<hex>. May include pr<number>, branch<name>, commits<number>,
    * dirty. Canonical order: pr, branch, commits, sha, dirty, then extensions (none by default).
    */
  def assemble(
    config: CliConfig,
    git: Git,
    basisCommitSha: CommitSha,
    baseVersionCommitSha: Option[CommitSha],
    isDirty: Boolean
  )(using logger: Logger, isVerbose: Boolean): Either[ResolutionError, BuildMetadata] =
    for
      _ <- if config.shaLength < 7 || config.shaLength > 40 then Left(ResolutionError.InvalidShaLength(config.shaLength)) else Right(())
      prId = config.prNumber.map(n => s"pr${Math.max(0, n)}")
      branchOpt <- getNormalizedBranch(config, git).map(_.map(n => s"branch$n"))
      commitsId <- git.countCommitsSince(basisCommitSha, baseVersionCommitSha).map(n => s"commits$n")
      sha <- git.getAbbreviatedSha(basisCommitSha, config.shaLength).map(s => s"sha$s")
      dirtyId = if isDirty then Some("dirty") else None
      ids = List(prId, branchOpt, Some(commitsId), Some(sha), dirtyId).flatten
      _ = logger.verbose(s"Build metadata identifiers: ${ids.mkString(",")}", "Metadata")
      meta <- BuildMetadata.from(ids) match
        case Right(bm) => Right(bm)
        case Left(err) => Left(ResolutionError.Message(s"Invalid build metadata identifiers: ${err.message}"))
    yield meta

  private def getNormalizedBranch(config: CliConfig, git: Git): Either[ResolutionError, Option[String]] =
    config.branchOverride match
      case Some(branch) => Right(Some(normalize(branch)))
      case None         => git.getBranchName().map(_.map(normalize).orElse(Some("detached")))

  /** Branch normalisation as per spec. */
  private def normalize(name: String): String =
    val lower = name.toLowerCase
    val sb = new StringBuilder(lower.length)
    var prevHyphen = false
    lower.foreach { ch =>
      val ok = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '-'
      if ok then
        if ch == '-' then
          if !prevHyphen then
            sb.append('-'); prevHyphen = true
        else
          sb.append(ch); prevHyphen = false
      else if !prevHyphen then
        sb.append('-'); prevHyphen = true
    }
    var s = sb.result()
    s = s.dropWhile(_ == '-')
    s = s.reverse.dropWhile(_ == '-').reverse
    if s.isEmpty then "detached" else s
end BuildMetadataBuilder
// scalafix:on
