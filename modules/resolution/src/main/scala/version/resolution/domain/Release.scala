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

/** A version that was released, and when.
  *
  * `releaseTime` is the annotated tag's tagger time in seconds since the Unix epoch - when the release was cut, which
  * may postdate `commit.commitTime`, the time the source commit was written.
  *
  * Instances are compared and ordered via [[Release$ Release]].
  */
final case class Release[V](version: V, tag: String, releaseTime: Long, commit: RawCommit)

/** Provides instances for [[Release]]. */
object Release:
  given [V](using CanEqual[V, V]): CanEqual[Release[V], Release[V]] = CanEqual.derived
  given [V: Ordering]: Ordering[Release[V]] = Ordering.by(_.version)
