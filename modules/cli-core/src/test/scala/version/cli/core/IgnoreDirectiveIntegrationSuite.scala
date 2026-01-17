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

import version.cli.core.domain.*
import version.{*, given}

// scalafix:off
/** Integration tests for ignore directive handling in version resolution.
  *
  * Tests specification sections 4.4 (Ignore Directives) and examples 11.16-11.21.
  */
final class IgnoreDirectiveIntegrationSuite extends FunSuite with TestRepoSupport:

  private def cfg(repo: os.Path, shaLen: Int = 12) =
    CliConfig(repo = repo, basisCommit = "HEAD", prNumber = None, branchOverride = None, shaLength = shaLen, verbose = false)

  /** Creates a minimal repo with base tag for ignore directive testing. */
  private def createIgnoreTestRepo(repoDir: os.Path): Unit =
    initMinimalRepo(repoDir)
    tag(repoDir, "v1.0.0")

  test("version: ignore excludes containing commit's keywords") {
    val tmp = os.temp.dir(prefix = "ignore-self-")
    try
      createIgnoreTestRepo(tmp)
      // Commit with major bump + ignore => should be excluded
      commit(tmp, "breaking: API change\n\nversion: ignore"): Unit
      // Commit without ignore => default patch
      commit(tmp, "housekeeping"): Unit
      val res = VersionCliCore.resolve(cfg(tmp))
      assert(res.isRight, clues(res))
      val v = res.toOption.get
      // The breaking change was ignored, so default patch from 1.0.0 => 1.0.1
      assertEquals((v.major.value, v.minor.value, v.patch.value), (1, 0, 1), clues(v.toString))
    finally os.remove.all(tmp)
  }

  test("version: ignore: <sha> excludes specific commit by SHA prefix") {
    val tmp = os.temp.dir(prefix = "ignore-sha-")
    try
      createIgnoreTestRepo(tmp)
      // Commit A with major bump
      val shaA = commit(tmp, "breaking: API redesign")
      // Commit B that ignores A
      commit(tmp, s"version: ignore: ${shaA.take(7)}"): Unit
      val res = VersionCliCore.resolve(cfg(tmp))
      assert(res.isRight, clues(res))
      val v = res.toOption.get
      // A was ignored, default patch => 1.0.1
      assertEquals((v.major.value, v.minor.value, v.patch.value), (1, 0, 1), clues(v.toString))
    finally os.remove.all(tmp)
  }

  test("version: ignore: <sha>, <sha> excludes multiple commits") {
    val tmp = os.temp.dir(prefix = "ignore-multi-")
    try
      createIgnoreTestRepo(tmp)
      // Commit A with major bump
      val shaA = commit(tmp, "version: major")
      // Commit B with minor bump
      val shaB = commit(tmp, "version: minor")
      // Commit C that ignores both A and B
      commit(tmp, s"version: ignore: ${shaA.take(7)}, ${shaB.take(7)}"): Unit
      val res = VersionCliCore.resolve(cfg(tmp))
      assert(res.isRight, clues(res))
      val v = res.toOption.get
      // Both A and B ignored, default patch => 1.0.1
      assertEquals((v.major.value, v.minor.value, v.patch.value), (1, 0, 1), clues(v.toString))
    finally os.remove.all(tmp)
  }

  test("version: ignore: <sha>..<sha> excludes commit range") {
    val tmp = os.temp.dir(prefix = "ignore-range-")
    try
      createIgnoreTestRepo(tmp)
      // Commits A, B, C with various bumps
      val shaA = commit(tmp, "version: major")
      commit(tmp, "version: minor"): Unit
      val shaC = commit(tmp, "feat: feature work")
      // Commit D that ignores range A..C
      commit(tmp, s"version: ignore: ${shaA.take(7)}..${shaC.take(7)}"): Unit
      val res = VersionCliCore.resolve(cfg(tmp))
      assert(res.isRight, clues(res))
      val v = res.toOption.get
      // All commits in range ignored, default patch => 1.0.1
      assertEquals((v.major.value, v.minor.value, v.patch.value), (1, 0, 1), clues(v.toString))
    finally os.remove.all(tmp)
  }

  test("version: ignore-merged excludes all merged branch commits") {
    val tmp = os.temp.dir(prefix = "ignore-merged-")
    try
      createIgnoreTestRepo(tmp)
      // Create feature branch from v1.0.0
      os.proc("git", "checkout", "-b", "feature/test").call(cwd = tmp, check = true): Unit
      // Commits on feature branch with bumps (use separate file to avoid conflict)
      os.write(tmp / "feature.txt", "feature work\n")
      os.proc("git", "add", "feature.txt").call(cwd = tmp, check = true): Unit
      os.proc("git", "commit", "--no-gpg-sign", "-m", "version: major").call(cwd = tmp, check = true): Unit
      os.write.append(tmp / "feature.txt", "more feature work\n")
      os.proc("git", "add", "feature.txt").call(cwd = tmp, check = true): Unit
      os.proc("git", "commit", "--no-gpg-sign", "-m", "version: minor").call(cwd = tmp, check = true): Unit
      // Return to main and add a commit (different file)
      os.proc("git", "checkout", "-q", "main").call(cwd = tmp, check = true): Unit
      os.write(tmp / "main.txt", "main work\n")
      os.proc("git", "add", "main.txt").call(cwd = tmp, check = true): Unit
      os.proc("git", "commit", "--no-gpg-sign", "-m", "housekeeping on main").call(cwd = tmp, check = true): Unit
      // Merge feature branch with ignore-merged + own directive
      os.proc(
        "git",
        "merge",
        "--no-ff",
        "--no-gpg-sign",
        "-m",
        "Merge feature\n\nversion: ignore-merged\nfeat: consolidated feature",
        "feature/test")
        .call(cwd = tmp, check = true): Unit
      val res = VersionCliCore.resolve(cfg(tmp))
      assert(res.isRight, clues(res))
      val v = res.toOption.get
      // Feature branch commits ignored; merge commit's minor applies => 1.1.0
      assertEquals((v.major.value, v.minor.value, v.patch.value), (1, 1, 0), clues(v.toString))
    finally os.remove.all(tmp)
    end try
  }

  test("version: ignore-merged on non-merge commit is silently ignored") {
    val tmp = os.temp.dir(prefix = "ignore-merged-nonmerge-")
    try
      createIgnoreTestRepo(tmp)
      // Regular commit with ignore-merged (not a merge commit)
      commit(tmp, "version: ignore-merged\nversion: minor"): Unit
      val res = VersionCliCore.resolve(cfg(tmp))
      assert(res.isRight, clues(res))
      val v = res.toOption.get
      // ignore-merged does nothing on non-merge; minor applies => 1.1.0
      assertEquals((v.major.value, v.minor.value, v.patch.value), (1, 1, 0), clues(v.toString))
    finally os.remove.all(tmp)
  }

  test("ignore directive with invalid SHA prefix is silently ignored") {
    val tmp = os.temp.dir(prefix = "ignore-invalid-sha-")
    try
      createIgnoreTestRepo(tmp)
      // Commit with major bump
      commit(tmp, "version: major"): Unit
      // Commit that tries to ignore with invalid SHA (too short)
      commit(tmp, "version: ignore: abc"): Unit
      val res = VersionCliCore.resolve(cfg(tmp))
      assert(res.isRight, clues(res))
      val v = res.toOption.get
      // Invalid ignore is silently dropped; major applies => 2.0.0
      assertEquals((v.major.value, v.minor.value, v.patch.value), (2, 0, 0), clues(v.toString))
    finally os.remove.all(tmp)
  }

  test("ignore directive does not affect commit count metadata") {
    val tmp = os.temp.dir(prefix = "ignore-commit-count-")
    try
      createIgnoreTestRepo(tmp)
      // Three commits: first two with ignore, third without
      commit(tmp, "version: ignore\nbreaking: ignored change"): Unit
      commit(tmp, "version: ignore\nfeat: ignored feature"): Unit
      commit(tmp, "housekeeping"): Unit
      // Dirty the worktree to force snapshot
      os.write.append(tmp / "README.md", "\ndirty\n")
      val res = VersionCliCore.resolve(cfg(tmp))
      assert(res.isRight, clues(res))
      val v = res.toOption.get
      // Default patch (all bumps ignored) => 1.0.1
      assertEquals((v.major.value, v.minor.value, v.patch.value), (1, 0, 1), clues(v.toString))
      // Commit count should still include all 3 commits (ignore affects keywords, not count)
      val meta = v.metadata.map(_.show).getOrElse("")
      assert(meta.contains("commits3"), clues(meta))
    finally os.remove.all(tmp)
  }

end IgnoreDirectiveIntegrationSuite
// scalafix:on
