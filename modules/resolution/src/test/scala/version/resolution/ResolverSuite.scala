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

import java.nio.file.Files
import java.nio.file.Paths

import scala.concurrent.duration.*

import version.resolution.logging.Logger
import version.resolution.logging.NullLogger
import version.resolution.logging.Verbose
import version.semver.*
import version.testkit.Filesystem

/** Shared resolver tests exercising the full resolution pipeline via [[GitRepository]]. */
abstract class ResolverSuite extends FunSuite, GitRepositoryTestSupport:

  override val munitTimeout: Duration = 120.seconds

  given Logger = NullLogger
  given Verbose = Verbose.disabled

  private def cfg(repoPath: String, pr: Option[Int]): ResolutionConfig[SemVer] =
    ResolutionConfig.default[SemVer](repoPath).copy(prNumber = pr)

  private def cfg(repoPath: String): ResolutionConfig[SemVer] = cfg(repoPath, None)

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
      Files.writeString(repo.resolve("dirty.txt"), "dirty"): Unit
      val res = VersionCliCore.resolve(cfg(repo.toString), path => openEither(path))
      assert(res.isRight, clues(res))
      val v = res.toOption.get
      assert(v.metadata.isDefined, "expected metadata")
      val full = SemVer.Formatter.Full.format(v)
      assert(full.contains("dirty"), s"expected 'dirty' in $full")

  test("Mode 2: metadata leads with a 12-digit UTC timestamp identifier"):
    withFreshRepo("mode2-timestamp"): repo =>
      checkout(repo, "v1.0.0")
      Files.writeString(repo.resolve("dirty.txt"), "dirty"): Unit
      val res = VersionCliCore.resolve(cfg(repo.toString), path => openEither(path))
      assert(res.isRight, clues(res))
      val ids = res.toOption.get.metadata.map(_.identifiers).getOrElse(Nil)
      val leading = ids.headOption.getOrElse(fail(s"metadata empty: $ids"))
      assert(leading.length == 12, s"leading identifier '$leading' is not 12 chars: $ids")
      assert(leading.forall(_.isDigit), s"leading identifier '$leading' is not all digits: $ids")

  test("No tags anywhere: default target is 0.1.0"):
    withFreshRepo("no-tags"): _ =>
      // Use the initMinimalRepo which has no tags
      val tmp = Files.createTempDirectory("version-no-tags-")
      try
        initMinimalRepo(tmp)
        val res = VersionCliCore.resolve(cfg(tmp.toString), path => openEither(path))
        assert(res.isRight, clues(res))
        val v = res.toOption.get
        assert(v.show.startsWith("0.1.0-SNAPSHOT"), s"Expected 0.1.0-SNAPSHOT, got ${v.show}")
      finally
        try Filesystem.removeRecursive(tmp)
        catch case _: Throwable => ()

  test("Pre-1.0 base: a breaking bump caps to a minor bump instead of forcing 1.0.0"):
    withTempRepo("pre1-breaking"): tmp =>
      initMinimalRepo(tmp)
      tag(tmp, "v0.93.9", "Release 0.93.9")
      commit(tmp, "version: major"): Unit
      val res = VersionCliCore.resolveAll(cfg(tmp.toString), path => openEither(path))
      assert(res.isRight, clues(res))
      val r = res.toOption.get
      assertEquals(r.target.show, "0.94.0")
      assert(r.resolved.show.startsWith("0.94.0-SNAPSHOT"), clue(r.resolved.show))

  test("Pre-1.0 base: an explicit major set still reaches 1.0.0"):
    withTempRepo("pre1-explicit"): tmp =>
      initMinimalRepo(tmp)
      tag(tmp, "v0.93.9", "Release 0.93.9")
      commit(tmp, "version: major: 1"): Unit
      val res = VersionCliCore.resolveAll(cfg(tmp.toString), path => openEither(path))
      assert(res.isRight, clues(res))
      assertEquals(res.toOption.get.target.show, "1.0.0")

  test("Lightweight tags are ignored during version resolution"):
    withFreshRepo("lightweight-ignored"): repo =>
      // v0.1.0 is a lightweight tag in the test repo - should be ignored
      checkout(repo, "v1.0.0")
      val res = VersionCliCore.resolve(cfg(repo.toString), path => openEither(path))
      assert(res.isRight, clues(res))
      assertEquals(res.toOption.get.show, "1.0.0")

  test("resolveAll Mode 1: concrete mode, target equals resolved"):
    withFreshRepo("detailed-mode1"): repo =>
      checkout(repo, "v1.0.0")
      val res = VersionCliCore.resolveAll(cfg(repo.toString), path => openEither(path))
      assert(res.isRight, clues(res))
      val r = res.toOption.get
      assertEquals(r.mode, ResolutionMode.Concrete)
      assertEquals(r.resolved, r.target)
      assertEquals(r.resolved.show, "1.0.0")
      assert(r.basis.isDefined, "basis commit should be present")
      val base = r.base.getOrElse(fail("base release should be present in Concrete mode"))
      assertEquals(base.version, r.resolved)
      assertEquals(base.commit.id, r.basis.get.id)

  test("resolveAll Mode 2: development mode exposes a clean target distinct from the snapshot"):
    withFreshRepo("detailed-mode2"): repo =>
      checkout(repo, "v1.0.0")
      Files.writeString(repo.resolve("dirty.txt"), "dirty"): Unit
      val res = VersionCliCore.resolveAll(cfg(repo.toString), path => openEither(path))
      assert(res.isRight, clues(res))
      val r = res.toOption.get
      assertEquals(r.mode, ResolutionMode.Development)
      assert(r.resolved.show.contains("-SNAPSHOT"), clue(r.resolved.show))
      assert(!r.target.show.contains("-SNAPSHOT"), clue(r.target.show))
      assertNotEquals(r.target, r.resolved)
      assert(r.basis.isDefined, "basis commit should be present")
      val base = r.base.getOrElse(fail("base release should be present (reachable v1.0.0)"))
      assertEquals(base.version.show, "1.0.0")
      assert(base.commit.commitTime > 0L, "source commit time should be positive")
      assert(base.releaseTime > 0L, "release (tagger) time should be positive")

  test("releaseHistory lists annotated version tags ordered ascending by version"):
    withFreshRepo("history"): repo =>
      val res = VersionCliCore.releaseHistory(cfg(repo.toString), path => openEither(path))
      assert(res.isRight, clues(res))
      val releases = res.toOption.get
      val shown = releases.map(_.version.show)
      assert(shown.contains("1.0.0"), s"expected 1.0.0 in $shown")
      assert(shown.contains("2.0.0"), s"expected 2.0.0 in $shown")
      assert(releases.forall(_.commit.commitTime > 0L), "every release should carry a source commit time")
      assert(releases.forall(_.releaseTime > 0L), "every release should carry a release (tagger) time")
      val i1 = shown.indexOf("1.0.0")
      val i2 = shown.indexOf("2.0.0")
      assert(i1 >= 0 && i2 >= 0 && i1 < i2, s"expected 1.0.0 before 2.0.0 (ascending): $shown")

  private def withTempRepo[A](name: String)(f: java.nio.file.Path => A): A =
    val tmp = Files.createTempDirectory(s"version-$name-")
    try f(tmp)
    finally
      try Filesystem.removeRecursive(tmp)
      catch case _: Throwable => ()

  /** Helper to open a GitRepository, wrapping in Either. */
  private def openEither(path: String): Either[GitError, GitRepository] =
    try Right(openTestRepository(Paths.get(path)))
    catch case e: Throwable => Left(GitError.BackendFailure(e.getMessage))
end ResolverSuite
