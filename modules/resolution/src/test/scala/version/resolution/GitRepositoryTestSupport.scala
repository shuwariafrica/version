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

import version.testkit.TestRepoSupport

/** Shared test infrastructure for [[GitRepository]] tests.
  *
  * Platform-specific subtraits implement [[openTestRepository]] using the platform's backend.
  */
trait GitRepositoryTestSupport extends TestRepoSupport:

  /** Opens a [[GitRepository]] at the given path using the platform's backend. */
  def openTestRepository(path: os.Path): GitRepository
