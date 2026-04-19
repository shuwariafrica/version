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

import munit.FunSuite

import scala.concurrent.duration.*

import version.resolution.logging.Logger
import version.resolution.logging.NullLogger
import version.resolution.logging.Verbose
import version.semver.*

/** Shared resolver tests exercising the full resolution pipeline via [[GitRepository]]. */
abstract class ResolverSuite extends FunSuite, GitRepositoryTestSupport:

  override val munitTimeout: Duration = 120.seconds

  given Logger = NullLogger
  given Verbose = Verbose.disabled

  private def cfg(repoPath: String, pr: Option[Int], shaLen: Int): ResolutionConfig[SemVer] =
    ResolutionConfig.default[SemVer](repoPath).copy(prNumber = pr, shaLength = shaLen)

  private def cfg(repoPath: String): ResolutionConfig[SemVer] = cfg(repoPath, None, 12)

  test("Mode 1: HEAD at tag and clean emits exact version"):
    withFreshRepo("mode1"): repo =>
      checkout(repo, "v1.0.0")
      val res = VersionCliCore.resolve(cfg(repo.toString), path => openEither(path))
      assert(res.isRight, clues(res))
      assertEquals(res.toOption.get.show, "1.0.0")

  test("Mode 1: when pre-release and final tags share a commit, choose the final"):
    withFreshRepo("mode1-multitag"): repo =>
      checkout(repo, "v2.0.0")
      val res = VersionCliCore.resolve(cfg(repo.toString), path => openEither(path))
      assert(res.isRight, clues(res))
      assertEquals(res.toOption.get.show, "2.0.0")

  test("Mode 2: dirty worktree forces snapshot with dirty metadata"):
    withFreshRepo("mode2-dirty"): repo =>
      checkout(repo, "v1.0.0")
      os.write(repo / "dirty.txt", "dirty")
      val res = VersionCliCore.resolve(cfg(repo.toString), path => openEither(path))
      assert(res.isRight, clues(res))
      val v = res.toOption.get
      assert(v.metadata.isDefined, "expected metadata")
      val full = SemVer.Formatter.full.format(v)
      assert(full.contains("dirty"), s"expected 'dirty' in $full")

  test("No tags anywhere: default target is 0.1.0"):
    withFreshRepo("no-tags"): _ =>
      // Use the initMinimalRepo which has no tags
      val tmp = os.temp.dir(prefix = "version-no-tags-")
      try
        initMinimalRepo(tmp)
        val res = VersionCliCore.resolve(cfg(tmp.toString), path => openEither(path))
        assert(res.isRight, clues(res))
        val v = res.toOption.get
        assert(v.show.startsWith("0.1.0-SNAPSHOT"), s"Expected 0.1.0-SNAPSHOT, got ${v.show}")
      finally
        try os.remove.all(tmp)
        catch case _: Throwable => ()

  test("Lightweight tags are ignored during version resolution"):
    withFreshRepo("lightweight-ignored"): repo =>
      // v0.1.0 is a lightweight tag in the test repo - should be ignored
      checkout(repo, "v1.0.0")
      val res = VersionCliCore.resolve(cfg(repo.toString), path => openEither(path))
      assert(res.isRight, clues(res))
      assertEquals(res.toOption.get.show, "1.0.0")

  /** Helper to open a GitRepository, wrapping in Either. */
  private def openEither(path: String): Either[GitError, GitRepository] =
    try Right(openTestRepository(os.Path(path)))
    catch case e: Throwable => Left(GitError.BackendFailure(e.getMessage))
end ResolverSuite
