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
package version.eff

import boilerplate.effect.Eff
import boilerplate.effect.EffResource

import version.resolution.GitError
import version.resolution.GitRepository
import version.resolution.discoverRepository
import version.resolution.openRepository

/** Provides [[version.resolution.GitRepository GitRepository]] as a scoped resource, so that a repository composes
  * into a larger resource graph and is closed on success, on a typed failure and on cancellation alike.
  *
  * Acquisition is the only failing step: a repository has nothing to say when it closes.
  */
object Repository:

  /** The repository at `path`, examining no parent directory. */
  def open(path: String): EffResource[GitError, GitRepository] =
    EffResource.make(Eff.blocking(openRepository(path)))(release)

  /** The nearest repository at or above `start`, stopping where the walk would leave `start`'s filesystem. */
  def discover(start: String): EffResource[GitError, GitRepository] =
    EffResource.make(Eff.blocking(discoverRepository(start)))(release)

  /** The nearest repository at or above `start`, examining neither a ceiling directory nor anything above one. */
  def discover(start: String, ceilings: Seq[String]): EffResource[GitError, GitRepository] =
    EffResource.make(Eff.blocking(discoverRepository(start, ceilings)))(release)

  private[eff] def release(repo: GitRepository): Eff[Nothing, Unit] = Eff.suspendBlocking(repo.close())

end Repository
