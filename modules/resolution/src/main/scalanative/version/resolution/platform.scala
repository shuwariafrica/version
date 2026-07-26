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
package version.resolution

import version.resolution.native.NativeGitRepository

/** Opens the repository at `path`, examining no parent directory.
  *
  * `path` is a worktree root, whose `.git` may be a directory or a `gitdir:` redirect, or a Git directory itself,
  * bare or otherwise.
  */
def openRepository(path: String): Either[GitError, GitRepository] =
  NativeGitRepository.open(path)

/** Opens the nearest repository at or above `start`, stopping where the walk would leave `start`'s filesystem.
  *
  * Discovery is a function of `start` and the filesystem alone.
  */
def discoverRepository(start: String): Either[GitError, GitRepository] =
  NativeGitRepository.discover(start)

/** Opens the nearest repository at or above `start`, examining neither a ceiling directory nor anything above one.
  *
  * `start` is examined even when its own parent is a ceiling.
  */
def discoverRepository(start: String, ceilings: Seq[String]): Either[GitError, GitRepository] =
  NativeGitRepository.discover(start, ceilings)
