package africa.shuwari.version.git

import java.nio.file.Path
import java.time.OffsetDateTime

import africa.shuwari.version.Version

/** Represents the status of a Git repository.
  *
  * @param sha
  *   The sha of this ref
  * @param branch
  *   The current branch of the repository (if not in a detached HEAD state).
  * @param tags
  *   The list of [[VersionTag VersionTags]] that are currently matched by this ref. Always empty if status is not
  *   clean.
  * @param upstream
  *   The upstream branch, if any, the current branch is tracking.
  * @param aheadBy
  *   The number of commits the local branch is ahead of the upstream branch, if applicable.
  * @param behindBy
  *   The number of commits the local branch is behind the upstream branch, if applicable.
  * @param changes
  *   A summary of the changes present in the repository.
  */
final case class GitStatus(
  sha: String,
  branch: Option[String],
  tags: Set[VersionTag],
  upstream: Option[String],
  aheadBy: Option[Int],
  behindBy: Option[Int],
  changes: ChangesSummary):

  final val clean =
    changes.untracked.isEmpty && changes.modified.isEmpty && changes.added.isEmpty && changes.deleted.isEmpty && changes.renamed.isEmpty

/** Summarises the types of changes present in the Git repository.
  *
  * @param untracked
  *   The set of files that are untracked by Git.
  * @param modified
  *   The set of files that have been modified.
  * @param added
  *   The set of files that have been added to the staging area.
  * @param deleted
  *   The set of files that have been deleted.
  * @param renamed
  *   The set of files that have been renamed, represented as a collection of old and new [[Path Paths]].
  */
final case class ChangesSummary(untracked: Set[Path], modified: Set[Path], added: Set[Path], deleted: Set[Path], renamed: Set[(Path, Path)])

/** Represents an annotated tag with a name that can be parsed into a valid version.
  *
  * @param tagSha
  *   The sha of this annotated tag ref
  * @param name
  *   The name of the tag, which can be parsed into a valid `Version`.
  * @param version
  *   The parsed version associated with the tag.
  * @param tagDate
  *   The date and time when the tag was created, stored as an `OffsetDateTime`.
  */
final case class VersionTag(tagSha: String, name: String, version: Version, tagDate: OffsetDateTime)
