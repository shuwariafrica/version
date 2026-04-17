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

/** A parsed version tag - the result of applying `config.tagParser` to a [[RawTag]].
  *
  * Parameterised by `V` (the scheme's version type). Produced by the resolver after parsing
  * raw tag names. Ordered by the version component.
  */
final case class Tag[V](name: String, commit: CommitSha, version: V)

object Tag:
  given [V](using CanEqual[V, V]): CanEqual[Tag[V], Tag[V]] = CanEqual.derived
  given [V: Ordering]: Ordering[Tag[V]] = Ordering.by(_.version)
