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
package version.cli.core.domain

import version.MajorVersion
import version.MinorVersion
import version.PatchNumber
import version.Version

/** ADT of recognised keywords extracted from commit messages.
  *
  * This sealed trait and its subtypes represent version control directives parsed from Git commit messages.
  * Instances may be constructed via [[Keyword$ Keyword]].
  */
sealed trait Keyword

/** Provides factory methods, subtypes, and instances for [[Keyword]]. */
object Keyword:
  given CanEqual[Keyword, Keyword] = CanEqual.derived

  // Ignore Directives - exclude commits from version calculation
  sealed trait IgnoreDirective extends Keyword

  /** Excludes the commit containing this directive from version calculation. */
  case object IgnoreSelf extends IgnoreDirective

  /** Excludes specific commits by SHA prefix (7+ characters). */
  final case class IgnoreCommits(shas: Set[String]) extends IgnoreDirective

  /** Excludes a range of commits (inclusive). */
  final case class IgnoreRange(from: String, to: String) extends IgnoreDirective

  /** Excludes all commits being merged (only meaningful in merge commits). */
  case object IgnoreMerged extends IgnoreDirective

  // Relative Change Keywords (coalesced to at-most one increment per component)
  // Note: PatchChange is not needed as patch increment is the default behaviour
  sealed trait Relative extends Keyword
  case object MajorChange extends Relative
  case object MinorChange extends Relative

  // Absolute Version Set Keywords (highest wins per component)
  sealed trait Absolute extends Keyword
  final case class MajorSet(value: MajorVersion) extends Absolute
  final case class MinorSet(value: MinorVersion) extends Absolute
  final case class PatchSet(value: PatchNumber) extends Absolute

  // Target Version Set Keyword (full SemVer parsed; only core used by selection stage)
  final case class TargetSet(value: Version) extends Keyword
end Keyword
