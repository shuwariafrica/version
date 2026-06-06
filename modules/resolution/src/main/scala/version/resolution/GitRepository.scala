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

/** Abstraction over Git repository operations.
  *
  * Implementations are platform-specific: JGit on JVM, libgit2 on Scala Native. Instances are not thread-safe -
  * consumers must not share across threads.
  *
  * Construction is platform-specific via companion `open` factories on the implementations. The resolver receives an
  * `open: String => Either[GitError, GitRepository]` parameter.
  */
trait GitRepository extends AutoCloseable:

  /** Returns the commit SHA at HEAD, or `None` if HEAD is unborn (post-init, no commits). */
  def head: Either[GitError, Option[CommitSha]]

  /** Resolves a revision spec to a full commit SHA. */
  def resolve(rev: String): Either[GitError, CommitSha]

  /** Returns the current branch short name, or `None` if HEAD is detached. */
  def branch: Either[GitError, Option[String]]

  /** Returns true if the repository has no working tree. */
  def isBare: Boolean

  /** Returns true if no tracked modifications and no untracked files. Bare repos return true. */
  def clean: Either[GitError, Boolean]

  /** Returns all tags under refs/tags/ with their kind and dereferenced commit. */
  def tags: Either[GitError, IArray[RawTag]]

  /** Returns true if `ancestor` is an ancestor of or equal to `commit`. */
  def isAncestorOf(ancestor: CommitSha, commit: CommitSha): Either[GitError, Boolean]

  /** Returns the subset of `tagCommits` reachable from `from` by walking ancestors. */
  def reachableTags(from: CommitSha, tagCommits: Set[CommitSha]): Either[GitError, Set[CommitSha]]

  /** Walks commits from `from` (inclusive) back to `until` (exclusive), traversing all parents. */
  def walkAll(from: CommitSha, until: Option[CommitSha]): Either[GitError, IArray[RawCommit]]

  /** Walks commits from `from` (inclusive) back to `until` (exclusive), following first-parent only. */
  def walkFirstParent(from: CommitSha, until: Option[CommitSha]): Either[GitError, IArray[RawCommit]]

  /** Looks up a single commit by its SHA and returns it as a [[RawCommit]]. */
  def loadCommit(sha: CommitSha): Either[GitError, RawCommit]

  /** Returns the tagger time (seconds since the Unix epoch) of the annotated tag named `name` - when the release was
    * tagged, as distinct from the time of the commit it points to.
    */
  def loadTagger(name: String): Either[GitError, Long]

  /** The configured `user.signingkey`, or `None` when unset or empty. */
  def signingKey: Either[GitError, Option[String]]

  /** The repository's default author identity (`user.name` / `user.email`) stamped at the current time. */
  def defaultSignature: Either[GitError, AuthorSignature]

  /** Creates an empty commit on HEAD (reusing HEAD's tree), advancing the current branch.
    *
    * `author` is used for both author and committer. When `sign` is true the commit is GPG-signed with the configured
    * signing key. Returns the new commit's SHA.
    */
  def createCommit(
    message: String,
    author: AuthorSignature,
    sign: Boolean
  ): Either[GitError, CommitSha]

  /** Creates an annotated tag named `name` at `target`, tagged by `tagger`.
    *
    * When `sign` is true the tag is GPG-signed with the configured signing key.
    */
  def createTag(
    name: String,
    target: CommitSha,
    message: String,
    tagger: AuthorSignature,
    sign: Boolean
  ): Either[GitError, Unit]
end GitRepository
