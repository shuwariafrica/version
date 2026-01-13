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
package version.cli.core.domain

import version.Version

/** An abstraction for a Git commit SHA. Backed by a lowercased string. */
opaque type CommitSha = String

object CommitSha:
  /** Wraps a string as a [[CommitSha]], normalising to lowercase for consistency. */
  inline def apply(sha: String): CommitSha = sha.toLowerCase

  /** Unwraps the [[CommitSha]] to its string value. */
  extension (sha: CommitSha) inline def value: String = sha

  given CanEqual[CommitSha, CommitSha] = CanEqual.derived

/** An abstraction for a Git tag name (as-is, not normalised). */
opaque type TagName = String

object TagName:
  inline def apply(name: String): TagName = name
  extension (name: TagName) inline def value: String = name
  given CanEqual[TagName, TagName] = CanEqual.derived

/** Represents a Git commit (SHA + full message). Pure data. */
final case class Commit(sha: CommitSha, message: String) derives CanEqual
object Commit:
  given CanEqual[Commit, Commit] = CanEqual.derived

/** Represents a parsed and validated SemVer Git tag. Pure data. */
final case class Tag(name: TagName, commitSha: CommitSha, version: Version) derives CanEqual
object Tag:
  given CanEqual[Tag, Tag] = CanEqual.derived
  given Ordering[Tag] = Ordering.by(_.version)
