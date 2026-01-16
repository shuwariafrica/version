/****************************************************************************
 * Copyright 2023 Shuwari Africa Ltd.                                       *
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
package version.cli.core.git

import version.cli.core.ResolutionError
import version.cli.core.domain.*

/** Interface for interacting with a Git repository using plumbing commands only. */
trait Git:

  /** Resolve a revision (e.g., HEAD) to a full commit SHA. */
  def resolveRev(rev: String): Either[ResolutionError, CommitSha]

  /** Abbreviate a commit SHA to the requested length. */
  def getAbbreviatedSha(sha: CommitSha, length: Int): Either[ResolutionError, String]

  /** List all valid SemVer tags (annotated and lightweight), parsed to versions. */
  def listAllTags(): Either[ResolutionError, List[Tag]]

  /** Tags reachable from the given commit (ancestor check). */
  def findReachableTags(from: CommitSha): Either[ResolutionError, List[Tag]]

  /** Is the working directory clean relative to HEAD (tracked and untracked)? */
  def isWorkingDirectoryClean(): Either[ResolutionError, Boolean]

  /** Current branch short name, if not detached. */
  def getBranchName(): Either[ResolutionError, Option[String]]

  /** Commit messages from (exclusive) base to (inclusive) to across all parents (merges traversed). */
  def getCommitsSince(to: CommitSha, fromExclusive: Option[CommitSha]): Either[ResolutionError, List[Commit]]

  /** Count non-merge commits on first-parent from 'to' back to (exclusive) 'from'; root when None. */
  def countCommitsSince(to: CommitSha, fromExclusive: Option[CommitSha]): Either[ResolutionError, Int]
end Git

object Git:
  given CanEqual[Git, Git] = CanEqual.derived
