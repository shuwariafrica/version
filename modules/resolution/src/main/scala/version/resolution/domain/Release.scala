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

import version.Version

/** A released version: its parsed version, the tag name, the release time, and the commit the tag dereferences to.
  *
  * `releaseTime` is the annotated tag's tagger time (seconds since the Unix epoch) - when the release was tagged.
  * `commit.commitTime` is the committer time of the source commit it points to, which may predate the release. Used as
  * the anchoring release on [[version.resolution.ResolutionResult ResolutionResult]] and ordered by
  * [[Release.version version]].
  */
final case class Release[V <: Version](version: V, tag: String, releaseTime: Long, commit: RawCommit)

object Release:
  given [V <: Version](using CanEqual[V, V]): CanEqual[Release[V], Release[V]] = CanEqual.derived
  given [V <: Version: Ordering]: Ordering[Release[V]] = Ordering.by(_.version)
