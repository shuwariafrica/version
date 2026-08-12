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
import boilerplate.nullable.*

/** A failure that stopped a resolution run.
  *
  * Every public operation in this module answers with one of these on its left; a [[GitError]] raised by the backend
  * arrives wrapped in [[ResolutionError.GitFailure GitFailure]].
  *
  * Instances may be constructed via [[ResolutionError$ ResolutionError]].
  */
sealed abstract class ResolutionError(message: String, cause: Option[Throwable]) extends TypedError(message, cause)

/** Provides the failure cases for [[ResolutionError]]. */
object ResolutionError:

  /** The Git backend failed, and says why. */
  final case class GitFailure(cause: GitError) extends ResolutionError(cause.getMessage.unsafe, Some(cause))

  /** Basis commit must not be empty. */
  final case class InvalidBasisCommit(value: String) extends ResolutionError(s"Basis commit must not be empty. Found: '$value'", None)

  /** Invalid commit SHA - must be non-empty and contain only hexadecimal characters. */
  final case class InvalidCommitSha(value: String)
      extends ResolutionError(
        s"Invalid commit SHA: '$value'. Must be non-empty and contain only hexadecimal characters [0-9a-fA-F].",
        None
      )

end ResolutionError
