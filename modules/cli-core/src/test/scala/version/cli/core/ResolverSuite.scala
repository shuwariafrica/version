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

import version.*
import version.cli.core.domain.*

// scalafix:off
final class ResolverSuite extends FunSuite with TestRepoSupport:

  private def cfg(repo: os.Path, pr: Option[Int] = None, shaLen: Int = 12) =
    CliConfig(repo = repo, basisCommit = "HEAD", prNumber = pr, branchOverride = None, shaLength = shaLen, verbose = false)

  test("Mode 1: HEAD at tag and clean emits exact version") {
    withFreshRepo("mode1") { repo =>
      // Checkout exactly the v1.0.0 commit
      checkout(repo, "v1.0.0")
      val res = VersionCliCore.resolve(cfg(repo))
      assert(res.isRight, clues(res))
      val v = res.toOption.get
      assertEquals(v.toString, "1.0.0")
    }
  }

  test("Mode 1: when pre-release and final tags share a commit, choose the final") {
    withFreshRepo("mode1-multitag-final-wins") { repo =>
      // Checkout the commit that has both v2.0.0-rc.1 and v2.0.0; expect final chosen
      checkout(repo, "v2.0.0")
      val res = VersionCliCore.resolve(cfg(repo))
      assert(res.isRight, clues(res))
      val v = res.toOption.get
      assertEquals(v.toString, "2.0.0")
    }
  }

  test("Rule D: HEAD at final (dirty) -> equal target ignored; default patch bump") {
    withFreshRepo("rule-d") { repo =>
      // Move to v1.0.0 commit and dirty the worktree
      checkout(repo, "v1.0.0")
      os.write.append(repo / "README.md", "\ndir\n"): Unit
      // Commit message attempting to set target equal to final
      os.proc("git", "add", "README.md").call(cwd = repo, check = true): Unit
      os.proc("git", "commit", "--no-gpg-sign", "-m", "target: 1.0.0").call(cwd = repo, check = true): Unit
      val res = VersionCliCore.resolve(cfg(repo))
      assert(res.isRight)
      val v = res.toOption.get
      // Should be default patch bump from 1.0.0 => 1.0.1
      assertEquals((v.major.value, v.minor.value, v.patch.value), (1, 0, 1), clues(v.toString))
      assert(v.preRelease.exists(_.isSnapshot), clues(v.toString))
    }
  }

  test("Basis commit override: resolve for a specific commit and reflect its sha in metadata") {
    withFreshRepo("basis-override") { repo =>
      // Pick a historical commit (v1.0.0) and resolve using it as basis
      val full = os.proc("git", "rev-parse", "v1.0.0^{commit}").call(cwd = repo, check = true).out.text().trim.toLowerCase
      // Dirty to ensure Mode 2 regardless of tag cleanliness, then commit so HEAD is past v1.0.0
      os.write.append(repo / "README.md", "\nbasis test\n"): Unit
      os.proc("git", "add", "README.md").call(cwd = repo, check = true): Unit
      os.proc("git", "commit", "--no-gpg-sign", "-m", "housekeeping").call(cwd = repo, check = true): Unit
      // Dirty the worktree (untracked) so Mode 1 cannot emit a concrete tag
      os.write.append(repo / "UNTRACKED.txt", "dirty"): Unit
      val res = VersionCliCore.resolve(
        CliConfig(repo = repo, basisCommit = full, prNumber = None, branchOverride = None, shaLength = 12, verbose = false))
      assert(res.isRight)
      val v = res.toOption.get
      // Should include sha of the provided basis commit, abbreviated
      val abbrev12 = full.take(12)
      val meta = v.buildMetadata.map(_.show).getOrElse("")
      assert(meta.contains(s"+branch"), clues(meta))
      assert(meta.contains(s".sha$abbrev12"), clues(meta))
      assert(v.preRelease.exists(_.isSnapshot), clues(v.toString))
    }
  }

  test("Mode 2: dirty worktree forces snapshot with dirty metadata") {
    withFreshRepo("dirty") { repo =>
      // Ensure on a reachable tag commit and then dirty the worktree
      checkout(repo, "v1.0.0")
      os.write.append(repo / "README.md", "\nDIRTY\n")
      val res = VersionCliCore.resolve(cfg(repo))
      assert(res.isRight)
      val v = res.toOption.get
      assert(v.preRelease.exists(_.isSnapshot), s"version was: ${v.toString}")
      val meta = v.buildMetadata.map(_.show).getOrElse("")
      assert(meta.contains("dirty"), s"metadata was: $meta")
    }
  }

  test("No base: repo-wide highest final drives default (major+1).0.0") {
    withFreshRepo("nobase") { repo =>
      // Move HEAD to a branch with no reachable tags by resetting history artificially
      // Simplest: checkout an orphan branch with no tags reachable
      os.proc("git", "checkout", "--orphan", "orphan").call(cwd = repo, check = true): Unit
      os.write.over(repo / "ORPHAN.txt", "x")
      os.proc("git", "add", "ORPHAN.txt").call(cwd = repo, check = true): Unit
      os.proc("git", "commit", "--no-gpg-sign", "-m", "orphan commit").call(cwd = repo, check = true): Unit
      val res = VersionCliCore.resolve(cfg(repo))
      assert(res.isRight)
      val v = res.toOption.get
      // Repo-wide highest tag is v4.3.0 => default should be 5.0.0-snapshot+...
      assertEquals((v.major.value, v.minor.value, v.patch.value), (5, 0, 0))
      assert(v.preRelease.exists(_.isSnapshot), s"version was: ${v.toString}")
    }
  }

  test("No tags anywhere: default target is 0.1.0") {
    withFreshRepo("no-tags") { repo =>
      // Create a brand new repo with no tags by nuking and reinitialising here
      os.remove.all(repo)
      os.makeDir.all(repo)
      os.proc("git", "init", "-q").call(cwd = repo, check = true): Unit
      os.proc("git", "config", "advice.detachedHead", "false").call(cwd = repo, check = true): Unit
      os.proc("git", "config", "user.name", "Version CLI Test").call(cwd = repo, check = true): Unit
      os.proc("git", "config", "user.email", "test@example.com").call(cwd = repo, check = true): Unit
      os.write.over(repo / "README.md", "x")
      os.proc("git", "add", "README.md").call(cwd = repo, check = true): Unit
      os.proc("git", "commit", "--no-gpg-sign", "-m", "init").call(cwd = repo, check = true): Unit
      os.write.append(repo / "README.md", "\nchange\n")
      val res = VersionCliCore.resolve(cfg(repo))
      assert(res.isRight)
      val v = res.toOption.get
      assertEquals((v.major.value, v.minor.value, v.patch.value), (0, 1, 0), clues(v.toString))
      assert(v.preRelease.exists(_.isSnapshot), clues(v.toString))
    }
  }

  test("Repo highest is pre-release; no base: target equal to its core is accepted") {
    withFreshRepo("repo-pre-highest") { repo =>
      // Create a repo with only a pre-release tag; no final tags
      os.remove.all(repo)
      os.makeDir.all(repo)
      os.proc("git", "init", "-q").call(cwd = repo, check = true): Unit
      os.proc("git", "config", "advice.detachedHead", "false").call(cwd = repo, check = true): Unit
      os.proc("git", "config", "user.name", "Version CLI Test").call(cwd = repo, check = true): Unit
      os.proc("git", "config", "user.email", "test@example.com").call(cwd = repo, check = true): Unit
      os.write.over(repo / "README.md", "x")
      os.proc("git", "add", "README.md").call(cwd = repo, check = true): Unit
      os.proc("git", "commit", "--no-gpg-sign", "-m", "init").call(cwd = repo, check = true): Unit
      // Annotated pre-release tag, explicitly not signed
      os.proc("git", "tag", "-a", "--no-sign", "-m", "pre", "v2.0.0-rc.1").call(cwd = repo, check = true): Unit
      // Add a commit with target equal to 2.0.0 (the core of the pre-release)
      os.write.append(repo / "README.md", "\nchange\n")
      os.proc("git", "add", "README.md").call(cwd = repo, check = true): Unit
      os.proc("git", "commit", "--no-gpg-sign", "-m", "target: 2.0.0").call(cwd = repo, check = true): Unit
      val res = VersionCliCore.resolve(cfg(repo))
      assert(res.isRight)
      val v = res.toOption.get
      assertEquals((v.major.value, v.minor.value, v.patch.value), (2, 0, 0), clues(v.toString))
      assert(v.preRelease.exists(_.isSnapshot), clues(v.toString))
    }
  }

  test("Reachable pre-release allows equal core target") {
    withFreshRepo("pre-equal") { repo =>
      // point HEAD to the pre-release branch tip (which has v1.1.0-rc.1 reachable)
      checkout(repo, "pre-from-1.0")
      // Make worktree dirty to force snapshot path rather than exact tag
      os.write.append(repo / "README.md", "\nchange\n"): Unit
      // Add a commit with an explicit target equal to the pre-release core
      os.proc("git", "add", "README.md").call(cwd = repo, check = true): Unit
      os.proc("git", "commit", "--no-gpg-sign", "-m", "target: 1.1.0").call(cwd = repo, check = true): Unit
      val res = VersionCliCore.resolve(cfg(repo))
      assert(res.isRight)
      val v = res.toOption.get
      // Should target the same core as the reachable pre-release (1.1.0) but be a snapshot
      assertEquals((v.major.value, v.minor.value, v.patch.value), (1, 1, 0), clues(v.toString))
      assert(v.preRelease.exists(_.isSnapshot), s"version was: ${v.toString}")
    }
  }

  test("Lightweight tags are ignored during version resolution") {
    withFreshRepo("lightweight-ignored-resolver") { repo =>
      // v0.1.0 is a lightweight tag on the initial commit
      // Checkout the initial commit where v0.1.0 (lightweight) was created
      val c1 = os.proc("git", "rev-list", "--max-parents=0", "HEAD").call(cwd = repo, check = true).out.text().trim
      checkout(repo, c1)
      // Worktree is clean, but v0.1.0 is lightweight so Mode 1 should NOT apply
      // Should result in Mode 2 using repo-wide highest (v4.3.0 annotated) => 5.0.0-snapshot
      val res = VersionCliCore.resolve(cfg(repo))
      assert(res.isRight, clues(res))
      val v = res.toOption.get
      // Lightweight v0.1.0 is ignored; repo-wide highest annotated is v4.3.0 => 5.0.0
      assertEquals((v.major.value, v.minor.value, v.patch.value), (5, 0, 0), clues(v.toString))
      assert(v.preRelease.exists(_.isSnapshot), s"version was: ${v.toString}")
    }
  }

  test("Mode 1: annotated tag at HEAD with clean worktree returns exact version") {
    withFreshRepo("mode1-annotated") { repo =>
      // v1.0.0 is annotated; checkout it
      checkout(repo, "v1.0.0")
      val res = VersionCliCore.resolve(cfg(repo))
      assert(res.isRight, clues(res))
      val v = res.toOption.get
      assertEquals(v.toString, "1.0.0")
      assert(v.preRelease.isEmpty, "should be a concrete version, not a pre-release")
    }
  }

end ResolverSuite
// scalafix:on
