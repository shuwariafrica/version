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

/** Errors produced during version resolution.
  *
  * Every public method in the resolution module returns `Either[ResolutionError, ...]`. Git backend errors are wrapped
  * in [[ResolutionError.GitFailure]] at the boundary. Module-scoped sealed ADT - does not extend
  * [[version.errors.VersionError]].
  */
sealed trait ResolutionError extends RuntimeException with NoStackTrace with Product with Serializable:
  def message: String
  final override def getMessage: String = message

object ResolutionError:
  given CanEqual[ResolutionError, ResolutionError] = CanEqual.derived

  /** Wraps a [[GitError]] at the resolution boundary. */
  final case class GitFailure(cause: GitError) extends ResolutionError:
    def message: String = cause.message

  /** SHA abbreviation length is outside the allowed bounds. */
  final case class InvalidShaLength(length: Int) extends ResolutionError:
    def message: String =
      s"SHA length must be within [${ResolutionConfig.MinShaLength}, ${ResolutionConfig.MaxShaLength}]. Found: $length"

  /** Basis commit must not be empty. */
  final case class InvalidBasisCommit(value: String) extends ResolutionError:
    def message: String = s"Basis commit must not be empty. Found: '$value'"

  /** Invalid commit SHA - must be non-empty and contain only hexadecimal characters. */
  final case class InvalidCommitSha(value: String) extends ResolutionError:
    def message: String =
      s"Invalid commit SHA: '$value'. Must be non-empty and contain only hexadecimal characters [0-9a-fA-F]."
end ResolutionError
