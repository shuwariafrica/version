/****************************************************************
 * Copyright © Shuwari Africa Ltd.                              *
 *                                                              *
 * This file is licensed to you under the terms of the Apache   *
 * License Version 2.0 (the "License"); you may not use this    *
 * file except in compliance with the License. You may obtain   *
 * a copy of the License at:                                    *
 *                                                              *
 *     https://www.apache.org/licenses/LICENSE-2.0              *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, *
 * either express or implied. See the License for the specific  *
 * language governing permissions and limitations under the     *
 * License.                                                     *
 ****************************************************************/
package version.cli.core

import scala.util.control.NoStackTrace

/** Base trait for all errors produced by the version-cli-core library. */
sealed trait ResolutionError extends RuntimeException with NoStackTrace:
  def message: String
  final override def getMessage: String = message

object ResolutionError:
  given CanEqual[ResolutionError, ResolutionError] = CanEqual.derived

  /** Represents an error when a required Git command fails. */
  final case class GitCommandFailed(cmd: List[String], exitCode: Int, out: String, err: String) extends ResolutionError:
    def message: String =
      s"Git command failed: '${(List("git") ++ cmd).mkString(" ")}', exit=$exitCode, stderr='${err.trim}', stdout='${out.trim}'"

  /** The specified path does not appear to be a Git repository (heuristic). */
  final case class NotAGitRepository(path: os.Path) extends ResolutionError:
    def message: String = s"The specified path does not appear to be a Git repository: $path"

  /** Provided SHA abbreviation length is outside the allowed bounds [7, 40]. */
  final case class InvalidShaLength(length: Int) extends ResolutionError:
    def message: String = s"shaLength must be within [7, 40]. Found: $length"

  /** Generic message wrapper for unexpected situations. */
  final case class Message(msg: String) extends ResolutionError:
    def message: String = msg

  /** Specific CLI validation error: unsupported --format value. */
  final case class InvalidOutputFormat(value: String) extends ResolutionError:
    def message: String =
      s"Unknown output format: $value (allowed: pretty, compact, json, yaml)"

  /** CLI: unknown emit sink value. */
  final case class InvalidSink(value: String) extends ResolutionError:
    def message: String = s"Unknown sink '$value' (expected console|raw|json|yaml)"

  /** CLI: invalid console style value. */
  final case class InvalidConsoleStyle(value: String) extends ResolutionError:
    def message: String = s"Invalid console-style '$value' (expected pretty|compact)"

  /** CLI: empty path after '=' in an --emit specification. */
  final case class EmptyEmitPath(spec: String) extends ResolutionError:
    def message: String = s"Empty path after '=' in --emit $spec"
end ResolutionError
