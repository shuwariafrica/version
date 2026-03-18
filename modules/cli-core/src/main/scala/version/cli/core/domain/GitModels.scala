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

import boilerplate.OpaqueType

import version.Version
import version.cli.core.ResolutionError

/** A Git commit SHA. Normalised to lowercase on construction.
  *
  * Instances may be constructed via [[CommitSha$ CommitSha]].
  */
opaque type CommitSha = String

/** Provides factory methods and operations for [[CommitSha]]. */
object CommitSha extends OpaqueType[CommitSha]:
  type Type = String
  type Error = ResolutionError.InvalidCommitSha

  inline def wrap(sha: String): CommitSha = sha.toLowerCase
  inline def unwrap(sha: CommitSha): String = sha

  protected inline def validate(value: String): Option[Error] =
    if value.nonEmpty && value.forall(c => (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))
    then None
    else Some(ResolutionError.InvalidCommitSha(value))

  inline def apply(inline value: String): CommitSha = fromUnsafe(value)

  extension (sha: CommitSha)
    /** The lowercase hex string value. */
    inline def value: String = unwrap(sha)

/** A Git tag name (preserved as-is).
  *
  * Instances may be constructed via [[TagName$ TagName]].
  */
opaque type TagName = String

/** Provides factory methods and operations for [[TagName]]. */
object TagName extends OpaqueType[TagName]:
  type Type = String
  type Error = ResolutionError.InvalidTagName

  inline def wrap(name: String): TagName = name
  inline def unwrap(name: TagName): String = name

  protected inline def validate(value: String): Option[Error] =
    if value.nonEmpty then None
    else Some(ResolutionError.InvalidTagName(value))

  inline def apply(inline value: String): TagName = fromUnsafe(value)

  extension (name: TagName)
    /** The tag name string. */
    inline def value: String = unwrap(name)

/** Represents a Git commit (SHA + full message + parent info for merge detection).
  *
  * Instances may be constructed via [[Commit$ Commit]].
  */
final case class Commit(sha: CommitSha, message: String, parentCount: Int)

/** Provides instances and operations for [[Commit]]. */
object Commit:
  given CanEqual[Commit, Commit] = CanEqual.derived

  extension (c: Commit)
    /** Whether this commit is a merge commit (has multiple parents). */
    inline def isMerge: Boolean = c.parentCount >= 2

/** Represents a parsed and validated version tag.
  *
  * Instances may be constructed via [[Tag$ Tag]].
  */
final case class Tag(name: TagName, commitSha: CommitSha, version: Version)

/** Provides instances for [[Tag]]. */
object Tag:
  given CanEqual[Tag, Tag] = CanEqual.derived
  given Ordering[Tag] = Ordering.by(_.version)
