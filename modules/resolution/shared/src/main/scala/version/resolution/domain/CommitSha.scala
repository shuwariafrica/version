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
package version.resolution.domain

import boilerplate.OpaqueType

import version.resolution.ResolutionError

/** A Git commit SHA. Normalised to lowercase on construction.
  *
  * Instances may be constructed via [[CommitSha$ CommitSha]].
  */
opaque type CommitSha = String

/** Provides factory methods and operations for [[CommitSha]]. */
object CommitSha extends OpaqueType[CommitSha, String], OpaqueType.Eq[CommitSha]:
  type Error = ResolutionError.InvalidCommitSha

  def wrap(sha: String): CommitSha = sha.toLowerCase
  def unwrap(sha: CommitSha): String = sha

  protected inline def validate(value: String): Option[Error] =
    if value.nonEmpty && value.forall(c => (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))
    then None
    else Some(ResolutionError.InvalidCommitSha(value))

  inline def apply(inline value: String): CommitSha = fromUnsafe(value)

  extension (sha: CommitSha)
    /** The lowercase hex string value. */
    inline def value: String = unwrap(sha)
