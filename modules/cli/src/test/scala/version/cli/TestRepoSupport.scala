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
package version.cli

import version.resolution.logging.Logger
import version.resolution.logging.NullLogger
import version.resolution.logging.Verbose

/** Re-exports [[version.testkit.TestRepoSupport]] for cli tests.
  *
  * Provides default `given` instances for `Logger` (NullLogger) and `Verbose` (disabled) so that tests calling
  * `VersionCliCore.resolve(config)` resolve the contextual overload without additional ceremony.
  */
trait TestRepoSupport extends version.testkit.TestRepoSupport:
  given Logger = NullLogger
  given Verbose = Verbose.disabled
