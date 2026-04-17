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
package version.cli

import scala.util.control.NoStackTrace

import version.resolution.ResolutionError

/** CLI-specific errors. Module-scoped sealed ADT. */
sealed trait CliError extends RuntimeException with NoStackTrace with Product with Serializable:
  def message: String
  final override def getMessage: String = message

object CliError:
  given CanEqual[CliError, CliError] = CanEqual.derived

  final case class ResolutionFailed(cause: ResolutionError) extends CliError:
    def message: String = cause.message

  final case class InvalidSink(value: String) extends CliError:
    def message: String = s"Unknown sink '$value' (expected console|raw|json)"

  final case class InvalidConsoleStyle(value: String) extends CliError:
    def message: String = s"Invalid console-style '$value' (expected pretty|compact)"

  final case class EmptyEmitPath(spec: String) extends CliError:
    def message: String = s"Empty path after '=' in --emit $spec"

  final case class InvalidOutputFormat(value: String) extends CliError:
    def message: String = s"Unknown output format: $value (allowed: pretty, compact, json, yaml)"
