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
package version.resolution.domain

/** ADT of recognised keywords extracted from commit messages.
  *
  * Generic over version schemes: `ComponentBump` and `ComponentSet` use positional indices
  * into the scheme's component layout rather than SemVer-specific types.
  *
  * Instances may be constructed via [[Keyword$ Keyword]].
  */
sealed trait Keyword

/** Provides factory methods, subtypes, and instances for [[Keyword]]. */
object Keyword:
  given CanEqual[Keyword, Keyword] = CanEqual.derived

  /** Directives that exclude commits from version calculation. */
  sealed trait IgnoreDirective extends Keyword

  /** Excludes the commit containing this directive from version calculation. */
  case object IgnoreSelf extends IgnoreDirective

  /** Excludes specific commits by SHA prefix (7+ characters). */
  final case class IgnoreCommits(shas: Set[String]) extends IgnoreDirective

  /** Excludes a positional range of commits (inclusive). */
  final case class IgnoreRange(from: String, to: String) extends IgnoreDirective

  /** Excludes all commits from merged branches (only meaningful in merge commits). */
  case object IgnoreMerged extends IgnoreDirective

  /** Relative increment of the component at the given index. */
  final case class ComponentBump(index: Int) extends Keyword

  /** Absolute set of the component at the given index to the given value. */
  final case class ComponentSet(index: Int, value: Int) extends Keyword

  /** Target version directive carrying the raw unparsed string. */
  final case class TargetSet(raw: String) extends Keyword
end Keyword
