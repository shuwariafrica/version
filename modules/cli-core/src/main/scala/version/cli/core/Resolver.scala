package version.cli.core

import version.*
import version.cli.core.domain.*
import version.cli.core.git.Git
import version.cli.core.parsing.KeywordParser
import version.operations.*

/** Core engine for resolving the semantic version from a Git repository. */
object Resolver:

  /** Resolve the repository version based on the provided configuration and Git interface. */
  def resolve(config: CliConfig, git: Git): Either[ResolutionError, Version] =

    for
      basisCommitSha <- git.resolveRev(config.basisCommit)
      allTags <- git.listAllTags()
      worktreeClean <- git.isWorkingDirectoryClean()

      tagsOnHead = allTags.filter(_.commitSha == basisCommitSha)
      highestTagOnHead = tagsOnHead.sorted.lastOption

      result <- highestTagOnHead match
        // Mode 1: HEAD is tagged and working directory clean → emit exact version
        case Some(tag) if worktreeClean =>
          Right(tag.version)

        // Mode 2: compute snapshot version with metadata
        case _ =>
          val isDirty = !worktreeClean
          calculateDevelopmentVersion(config, git, basisCommitSha, isDirty, allTags)
    yield result

  private def calculateDevelopmentVersion(
    config: CliConfig,
    git: Git,
    basisCommitSha: CommitSha,
    isDirty: Boolean,
    allTags: List[Tag]
  ): Either[ResolutionError, Version] =
    given PreRelease.Resolver = PreRelease.Resolver.default

    for
      reachable <- git.findReachableTags(basisCommitSha)
      baseOpt = reachable.sorted.lastOption
      // Step 2 — Target Version
      targetCore <- baseOpt match
        case Some(baseTag) =>
          for
            commits <- git.getCommitsSince(basisCommitSha, Some(baseTag.commitSha))
            keywords = commits.flatMap(c => KeywordParser.parse(c.message))
            targetOpt = TargetVersionCalculator.selectValidTarget(
              keywords.collect { case Keyword.TargetSet(v) => v },
              highestReachable = Some(baseTag),
              highestRepo = None,
              allRepoFinals = Nil,
              isHeadOnFinalTag = tagsOnHeadIsFinal(allTags, basisCommitSha)
            )
          yield targetOpt.getOrElse(TargetVersionCalculator.fromKeywords(baseTag.version, keywords))

        case None =>
          // No reachable base: repository-wide defaults
          for
            commits <- git.getCommitsSince(basisCommitSha, None)
            keywords = commits.flatMap(c => KeywordParser.parse(c.message))
            highestRepo = allTags.sorted.lastOption
            repoFinals = allTags.filter(_.version.isFinal)
            targetOpt = TargetVersionCalculator.selectValidTarget(
              keywords.collect { case Keyword.TargetSet(v) => v },
              highestReachable = None,
              highestRepo = highestRepo,
              allRepoFinals = repoFinals,
              isHeadOnFinalTag = tagsOnHeadIsFinal(allTags, basisCommitSha)
            )
          yield targetOpt.getOrElse {
            highestRepo match
              case Some(h) => Version(h.version.major.increment, MinorVersion.reset, PatchNumber.reset)
              case None    => Version(MajorVersion.unsafe(0), MinorVersion.unsafe(1), PatchNumber.unsafe(0))
          }

      // Step 3 — Snapshot pre-release + build metadata
      metadata <- BuildMetadataBuilder.assemble(config, git, basisCommitSha, baseOpt.map(_.commitSha), isDirty)

      // Step 4 — Assemble
      result = targetCore.copy(preRelease = Some(PreRelease.snapshot), buildMetadata = Some(metadata))
    yield result
    end for
  end calculateDevelopmentVersion

  private def tagsOnHeadIsFinal(all: List[Tag], head: CommitSha): Boolean =
    all.exists(t => t.commitSha == head && t.version.isFinal)
end Resolver
