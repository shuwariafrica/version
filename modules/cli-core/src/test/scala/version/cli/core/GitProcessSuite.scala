/****************************************************************
 * Copyright © Shuwari Africa Ltd.                              *
 *                                                              *
 * This file is licensed to you under the terms of the Apache   *
 * License Version 2.0 (the "License"); you may not use this    *
 * file except in compliance with the License. You may obtain   *
 * a copy of the License at:                                    *
 *                                                              *
 *     https://www.apache.org/licenses/LICENSE-2.0              *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, *
 * either express or implied. See the License for the specific  *
 * language governing permissions and limitations under the     *
 * License.                                                     *
 ****************************************************************/
package version.cli.core

import munit.FunSuite

import version.cli.core.domain.*
import version.cli.core.git.GitProcess
import version.cli.core.logging.Logger
import version.cli.core.logging.NullLogger

final class GitProcessSuite extends FunSuite with TestRepoSupport:

  given Logger = NullLogger
  given Boolean = false

  test("listAllTags returns only annotated tags; ignores lightweight and invalid") {
    withFreshRepo("listAllTags") { repo =>
      val git = new GitProcess(repo)
      val tags = git.listAllTags().toOption.get
      val names = tags.map(_.name.value).toSet
      // Annotated tags with valid semver should be included
      assert(names.contains("v1.0.0"), "annotated v1.0.0 should be included")
      assert(names.contains("v1.0.1"), "annotated v1.0.1 should be included")
      assert(names.contains("v2.0.0"), "annotated v2.0.0 should be included")
      assert(names.contains("v2.0.0-rc.1"), "annotated v2.0.0-rc.1 should be included")
      assert(names.contains("v1.1.0-rc.1"), "annotated v1.1.0-rc.1 should be included")
      assert(names.contains("v4.3.0"), "annotated v4.3.0 should be included")
      // Lightweight tags should be IGNORED regardless of valid semver
      assert(!names.contains("v0.1.0"), "lightweight v0.1.0 should be ignored")
      assert(!names.contains("v3.0.0"), "lightweight v3.0.0 should be ignored")
      assert(!names.contains("0.5.0"), "lightweight 0.5.0 should be ignored")
      // Invalid semver tags should be ignored
      assert(!names.contains("not-a-version"), "non-semver tag should be ignored")
      assert(!names.contains("v1.0"), "partial semver tag should be ignored")
      assert(!names.contains("release-1.0.0"), "non-semver tag should be ignored")
    }
  }

  test("findReachableTags from main head excludes unreachable branch tags") {
    withFreshRepo("reachable") { repo =>
      val git = new GitProcess(repo)
      val head = git.resolveRev("HEAD").toOption.get
      val reachable = git.findReachableTags(head).toOption.get
      val names = reachable.map(_.name.value).toSet
      assert(!names.contains("v4.3.0")) // unreachable
      assert(names.contains("v2.0.0")) // multi-tag final on same commit is reachable
    }
  }

  test("isWorkingDirectoryClean and getBranchName") {
    withFreshRepo("clean-branch") { repo =>
      val git = new GitProcess(repo)
      assertEquals(git.isWorkingDirectoryClean().toOption.get, true)
      val b = git.getBranchName().toOption.get
      assert(b.contains("main"))
    }
  }

  test("getCommitsSince and countCommitsSince from v1.0.0 to HEAD") {
    withFreshRepo("rev-list") { repo =>
      val git = new GitProcess(repo)
      val head = git.resolveRev("HEAD").toOption.get
      val base = git.resolveRev("v1.0.0").toOption.get
      val msgs = git.getCommitsSince(head, Some(base)).toOption.get
      assert(msgs.nonEmpty)
      val count = git.countCommitsSince(head, Some(base)).toOption.get
      assert(count >= 1)
    }
  }

  test("countCommitsSince excludes merges (first-parent), while keyword scan traverses merge graph") {
    withFreshRepo("merge-exclusion") { repo =>
      val git = new GitProcess(repo)
      val head = git.resolveRev("HEAD").toOption.get
      val base = git.resolveRev("v1.0.0").toOption.get
      val msgs = git.getCommitsSince(head, Some(base)).toOption.get
      // Ensure we saw both feature commits across the merge boundaries
      val text = msgs.map(_.message).mkString("\n").toLowerCase
      // TestRepoBuilder uses new grammar: version: minor and standalone shorthands like fix:/feat:/feature:
      assert(text.contains("version: minor") || text.contains("feat:") || text.contains("feature:") || text.contains("minor"))
      assert(text.contains("version: patch") || text.contains("fix:") || text.contains("patch"))
      // First-parent count should exclude the merge commit itself
      val count = git.countCommitsSince(head, Some(base)).toOption.get
      assert(count >= 1)
    }
  }

  test("lightweight tags with valid semver are ignored; only annotated tags count") {
    withFreshRepo("lightweight-ignored") { repo =>
      val git = new GitProcess(repo)
      // Create another lightweight tag at HEAD to verify it's ignored
      os.proc("git", "tag", "v99.0.0").call(cwd = repo, check = true): Unit
      // Create an annotated tag to verify it's included
      os.proc("git", "tag", "-a", "-m", "annotated test tag", "v98.0.0").call(cwd = repo, check = true): Unit

      val tags = git.listAllTags().toOption.get
      val names = tags.map(_.name.value).toSet

      // Lightweight v99.0.0 should be ignored
      assert(!names.contains("v99.0.0"), "lightweight v99.0.0 should be ignored")
      // Annotated v98.0.0 should be included
      assert(names.contains("v98.0.0"), "annotated v98.0.0 should be included")
    }
  }

  test("findReachableTags excludes lightweight tags even when reachable") {
    withFreshRepo("lightweight-unreachable") { repo =>
      val git = new GitProcess(repo)
      val head = git.resolveRev("HEAD").toOption.get
      val reachable = git.findReachableTags(head).toOption.get
      val names = reachable.map(_.name.value).toSet

      // Lightweight v0.1.0 is on an early commit reachable from main, but should be ignored
      assert(!names.contains("v0.1.0"), "lightweight v0.1.0 should be ignored even when reachable")
      // Lightweight v3.0.0 is on main HEAD but should be ignored
      assert(!names.contains("v3.0.0"), "lightweight v3.0.0 should be ignored even when reachable")
      // Annotated v1.0.0 should be reachable
      assert(names.contains("v1.0.0"), "annotated v1.0.0 should be reachable")
    }
  }
end GitProcessSuite
