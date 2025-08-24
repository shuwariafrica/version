package version.cli.core

import version.Version
import version.cli.core.domain.*
import version.cli.core.git.GitProcess

/** Public API entry point for the version-cli-core library. */
object VersionCliCore:

  /** Resolve the semantic version for a Git repository based on the given configuration. */
  def resolve(config: CliConfig): Either[ResolutionError, Version] =
    // Instantiate the live Git implementation for the provided path.
    val git = new GitProcess(config.repo)
    Resolver.resolve(config, git)

  /** Convenience no-arg resolve using the default configuration. */
  def resolve(): Either[ResolutionError, Version] =
    resolve(CliConfig())
