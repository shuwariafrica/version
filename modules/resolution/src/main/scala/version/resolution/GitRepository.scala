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
package version.resolution

import version.resolution.domain.*

/** Read and write access to one Git repository, backed by JGit on the JVM and libgit2 on Scala Native.
  *
  * Instances are not thread-safe. Obtain one from `openRepository` or `discoverRepository`.
  */
trait GitRepository extends AutoCloseable:

  /** The commit at HEAD, or `None` when HEAD is unborn. */
  def head: Either[GitError, Option[CommitSha]]

  /** Resolves a revision specification to the full SHA of the commit it names. */
  def resolve(rev: String): Either[GitError, CommitSha]

  /** The current branch as a short name, or `None` when HEAD is detached. */
  def branch: Either[GitError, Option[String]]

  def isBare: Boolean

  /** Absolute path of the Git directory - a linked worktree's own directory rather than the shared one. */
  def gitDir: String

  /** Absolute path of the working-tree root, or `None` when the repository is bare. */
  def workTree: Option[String]

  /** True when the working tree holds no modification and no untracked file; bare repositories are always clean. */
  def clean: Either[GitError, Boolean]

  /** Every tag under `refs/tags/`, each peeled to the commit it ultimately names. */
  def tags: Either[GitError, IArray[RawTag]]

  /** True when `ancestor` precedes `commit` in history, or is `commit`. */
  def isAncestorOf(ancestor: CommitSha, commit: CommitSha): Either[GitError, Boolean]

  /** The subset of `tagCommits` reachable from `from` through its ancestry. */
  def reachableTags(from: CommitSha, tagCommits: Set[CommitSha]): Either[GitError, Set[CommitSha]]

  /** Commits from `from` inclusive back to `until` exclusive, across all parents. */
  def walkAll(from: CommitSha, until: Option[CommitSha]): Either[GitError, IArray[RawCommit]]

  /** Commits from `from` inclusive back to `until` exclusive, following first parents only. */
  def walkFirstParent(from: CommitSha, until: Option[CommitSha]): Either[GitError, IArray[RawCommit]]

  def loadCommit(sha: CommitSha): Either[GitError, RawCommit]

  /** When the annotated tag `name` was created, in seconds since the Unix epoch - the tagger time, not the time of
    * the commit it points at.
    */
  def loadTagger(name: String): Either[GitError, Long]

  /** The configured `user.signingkey`, or `None` when unset or empty. */
  def signingKey: Either[GitError, Option[String]]

  /** The repository's default author identity (`user.name` / `user.email`) stamped at the current time. */
  def defaultSignature: Either[GitError, AuthorSignature]

  /** Commits HEAD's tree again on the current branch, changing no file.
    *
    * `author` becomes both author and committer. `sign` GPG-signs the commit with the configured `user.signingkey`,
    * failing when none is set.
    */
  def createCommit(
    message: String,
    author: AuthorSignature,
    sign: Boolean
  ): Either[GitError, CommitSha]

  /** Creates an annotated tag at `target`.
    *
    * `sign` GPG-signs the tag with the configured `user.signingkey`, failing when none is set.
    */
  def createTag(
    name: String,
    target: CommitSha,
    message: String,
    tagger: AuthorSignature,
    sign: Boolean
  ): Either[GitError, Unit]
end GitRepository
