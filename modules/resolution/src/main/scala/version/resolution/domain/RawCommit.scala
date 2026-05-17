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

/** A Git commit as returned by the backend walk methods. `commitTime` is seconds since the Unix epoch. */
final case class RawCommit(id: CommitSha, message: String, parentIds: IArray[CommitSha], commitTime: Long)

object RawCommit:
  given CanEqual[RawCommit, RawCommit] = CanEqual.derived

  extension (c: RawCommit)
    /** Whether this commit is a merge commit (has two or more parents). */
    inline def isMerge: Boolean = c.parentIds.length >= 2
