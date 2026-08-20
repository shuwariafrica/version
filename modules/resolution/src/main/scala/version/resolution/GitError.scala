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

import boilerplate.TypedError

/** A failure reported by the Git backend while reading or writing a repository.
  *
  * Instances may be constructed via [[GitError$ GitError]].
  */
sealed abstract class GitError(message: String, cause: Option[Throwable]) extends TypedError(message, cause)

/** Provides the failure cases for [[GitError]]. */
object GitError:

  /** The specified path does not contain a Git repository. */
  final case class RepositoryNotFound(path: String) extends GitError(s"Not a Git repository: $path", None)

  /** A revision spec could not be resolved to a commit. */
  final case class RevisionNotFound(rev: String) extends GitError(s"Revision not found: $rev", None)

  /** A short object ID matched multiple objects. */
  final case class AmbiguousRevision(rev: String) extends GitError(s"Ambiguous revision: $rev", None)

  /** A specific object could not be found in the repository. */
  final case class ObjectNotFound(id: String) extends GitError(s"Object not found: $id", None)

  /** GPG signing of a commit or tag failed, carrying whatever the signer raised where it raised anything. */
  final case class SigningFailure(detail: String, cause: Option[Throwable]) extends GitError(detail, cause)

  /** The backend failed for a reason it does not classify, carrying what it raised where it raised anything. */
  final case class BackendFailure(detail: String, cause: Option[Throwable]) extends GitError(detail, cause)

end GitError
