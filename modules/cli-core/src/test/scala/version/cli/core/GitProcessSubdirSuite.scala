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
package version.cli.core

import munit.FunSuite

import version.cli.core.git.GitProcess
import version.cli.core.logging.Logger
import version.cli.core.logging.NullLogger
import version.{*, given}

/** Tests for executing GitProcess from a subdirectory within a repository. */
final class GitProcessSubdirSuite extends FunSuite with TestRepoSupport:
  given Logger = NullLogger
  given Boolean = false

  test("GitProcess functions when initialised at nested subdirectory path") {
    withFreshRepo("subdir-support") { repo =>
      // Create nested subdirectories several levels deep
      val nested = repo / "a" / "b" / "c"
      os.makeDir.all(nested)

      // Instantiate GitProcess with nested path
      val git = new GitProcess(nested)

      // Basic operations should still work
      val head = git.resolveRev("HEAD").fold(e => fail(e.message), identity)
      assert(head.value.matches("[0-9a-f]{40}"))

      val tags = git.listAllTags().fold(e => fail(e.message), identity)
      assert(tags.nonEmpty, "expected some tags parsed from repo when running in subdir")

      val clean1 = git.isWorkingDirectoryClean().fold(e => fail(e.message), identity)
      assertEquals(clean1, true, "expected clean worktree before adding untracked files")

      // Now create an untracked file to ensure dirty detection still functions from subdir
      os.write(nested / "note.txt", "hello from nested")
      val clean2 = git.isWorkingDirectoryClean().fold(e => fail(e.message), identity)
      assertEquals(clean2, false, "expected dirty worktree after creating untracked file in subdir")

      val branch = git.getBranchName().fold(e => fail(e.message), identity)
      assert(branch.contains("main"), s"expected to be on main branch, got $branch")
    }
  }
end GitProcessSubdirSuite
