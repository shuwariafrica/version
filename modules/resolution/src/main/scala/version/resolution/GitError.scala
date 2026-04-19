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

import scala.util.control.NoStackTrace

/** Errors produced by Git backend operations.
  *
  * Each case maps to a specific failure mode in the JGit or libgit2 backends.
  * Module-scoped sealed ADT - does not extend [[version.errors.VersionError]].
  */
sealed trait GitError extends RuntimeException with NoStackTrace with Product with Serializable:
  def message: String
  final override def getMessage: String = message

object GitError:
  given CanEqual[GitError, GitError] = CanEqual.derived

  /** The specified path does not contain a Git repository. */
  final case class RepositoryNotFound(path: String) extends GitError:
    def message: String = s"Not a Git repository: $path"

  /** A revision spec could not be resolved to a commit. */
  final case class RevisionNotFound(rev: String) extends GitError:
    def message: String = s"Revision not found: $rev"

  /** A short object ID matched multiple objects. */
  final case class AmbiguousRevision(rev: String) extends GitError:
    def message: String = s"Ambiguous revision: $rev"

  /** A specific object could not be found in the repository. */
  final case class ObjectNotFound(id: String) extends GitError:
    def message: String = s"Object not found: $id"

  /** Catch-all for unexpected backend failures. */
  final case class BackendFailure(detail: String) extends GitError:
    def message: String = detail
