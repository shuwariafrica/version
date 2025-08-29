package version.cli.core.domain

/** Pure configuration for a version resolution run.
  *
  * @param repo
  *   Path anywhere within the Git repository.
  * @param basisCommit
  *   Commit-ish to use as basis (default HEAD).
  * @param prNumber
  *   Optional pull request number (emits 'pr<N>' build metadata).
  * @param branchOverride
  *   Optional branch name override for metadata; when absent we detect via symbolic-ref.
  * @param shaLength
  *   Abbreviated SHA length for metadata. Must be in [7, 40]. Default 12.
  * @param verbose
  *   Enable verbose debug logging throughout resolution process.
  */
final case class CliConfig(
  repo: os.Path,
  basisCommit: String,
  prNumber: Option[Int],
  branchOverride: Option[String],
  shaLength: Int,
  verbose: Boolean
) derives CanEqual

object CliConfig:
  given CanEqual[CliConfig, CliConfig] = CanEqual.derived

  def apply(): CliConfig =
    new CliConfig(
      repo = os.pwd,
      basisCommit = "HEAD",
      prNumber = None,
      branchOverride = None,
      shaLength = 12,
      verbose = false
    )
