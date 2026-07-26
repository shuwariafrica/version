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

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

import scala.concurrent.duration.*
import scala.util.control.NonFatal

import version.testkit.Filesystem
import version.testkit.Process
import version.testkit.TestRepoSupport

class RepositoryDiscoverySuite extends FunSuite, TestRepoSupport:

  override val munitTimeout: Duration = 120.seconds

  private def withTempRoot[A](name: String)(f: Path => A): A =
    val tmp = Files.createTempDirectory(s"version-discovery-$name-")
    try f(tmp)
    finally
      try Filesystem.removeRecursive(tmp)
      catch case NonFatal(_) => ()

  private val IdentityMark = "discovery-identity"

  // A reported path is proved by what it leads to, never by its spelling: libgit2 resolves symlinks the JDK leaves
  // alone, and expands the 8.3 short names a Windows TEMP can carry, which Scala Native's javalib cannot expand back.
  private def markGitDirectory(gitDirectory: Path): Unit =
    Files.writeString(gitDirectory.resolve(IdentityMark), s"${gitDirectory.getFileName}-${System.nanoTime}"): Unit

  private def gitDirIdentity(gitDir: String): String = Files.readString(File(gitDir, IdentityMark).toPath)

  private def workTreeIdentity(workTree: String): String =
    val entry = File(workTree, ".git")
    // A linked worktree's `.git` is the redirect naming its own git directory, so its bytes already identify it.
    if entry.isDirectory then Files.readString(File(entry, IdentityMark).toPath) else Files.readString(entry.toPath)

  private def withRepository[A](opened: Either[GitError, GitRepository])(f: GitRepository => A): A =
    val repository = opened.fold(error => fail(s"expected an open repository, got $error"), identity)
    try f(repository)
    finally repository.close()

  private def assertNotFound(result: Either[GitError, GitRepository], start: Path): Unit =
    result match
      case Left(GitError.RepositoryNotFound(reported)) => assertEquals(reported, start.toString)
      case Left(other)                                 => fail(s"expected RepositoryNotFound, got $other")
      case Right(repository)                           =>
        val located = repository.workTree
        repository.close()
        fail(s"expected RepositoryNotFound, opened $located")

  test("openRepository opens a worktree root and reports its git directory and work tree"):
    withTempRoot("exact-root"): tmp =>
      val repo = tmp.resolve("repo")
      initMinimalRepo(repo)
      markGitDirectory(repo.resolve(".git"))
      withRepository(openRepository(repo.toString)): repository =>
        assert(!repository.isBare)
        assertEquals(repository.workTree.map(workTreeIdentity), Some(workTreeIdentity(repo.toString)))
        assertEquals(gitDirIdentity(repository.gitDir), gitDirIdentity(repo.resolve(".git").toString))

  test("openRepository resolves the gitdir redirect at a linked worktree root"):
    withTempRoot("exact-linked"): tmp =>
      val repo = tmp.resolve("repo")
      initMinimalRepo(repo)
      val linked = tmp.resolve("linked")
      git(repo, "worktree", "add", "-q", "-b", "linked-branch", linked.toString): Unit
      assert(Files.isRegularFile(linked.resolve(".git")), "fixture: a linked worktree's .git is a redirect file")
      val linkedGitDir = repo.resolve(".git").resolve("worktrees").resolve("linked")
      markGitDirectory(linkedGitDir)
      withRepository(openRepository(linked.toString)): repository =>
        assertEquals(repository.workTree.map(workTreeIdentity), Some(workTreeIdentity(linked.toString)))
        assertEquals(repository.branch, Right(Some("linked-branch")))
        assertEquals(gitDirIdentity(repository.gitDir), gitDirIdentity(linkedGitDir.toString))

  test("openRepository does not ascend out of a subdirectory of a repository"):
    withTempRoot("exact-no-ascent"): tmp =>
      val repo = tmp.resolve("repo")
      initMinimalRepo(repo)
      val nested = repo.resolve("services/app")
      Files.createDirectories(nested): Unit
      assertNotFound(openRepository(nested.toString), nested)

  test("openRepository opens a bare repository, reports no work tree, and reads its objects"):
    withTempRoot("exact-bare"): tmp =>
      val repo = tmp.resolve("repo")
      initMinimalRepo(repo)
      val bare = tmp.resolve("bare.git")
      Process.runChecked(Seq("git", "clone", "--bare", "--no-hardlinks", "-q", repo.toString, bare.toString), tmp): Unit
      markGitDirectory(bare)
      withRepository(openRepository(bare.toString)): repository =>
        assert(repository.isBare)
        assertEquals(repository.workTree, None)
        assertEquals(gitDirIdentity(repository.gitDir), gitDirIdentity(bare.toString))
        assert(repository.head.toOption.flatten.isDefined, "a bare repository still resolves HEAD")

  test("discoverRepository finds the repository root from a nested build directory"):
    withTempRoot("discover-monorepo"): tmp =>
      val repo = tmp.resolve("repo")
      initMinimalRepo(repo)
      markGitDirectory(repo.resolve(".git"))
      val nested = repo.resolve("services/app")
      Files.createDirectories(nested): Unit
      withRepository(discoverRepository(nested.toString)): repository =>
        assertEquals(repository.workTree.map(workTreeIdentity), Some(workTreeIdentity(repo.toString)))

  test("discoverRepository finds a linked worktree root from one of its subdirectories"):
    withTempRoot("discover-linked"): tmp =>
      val repo = tmp.resolve("repo")
      initMinimalRepo(repo)
      val linked = tmp.resolve("linked")
      git(repo, "worktree", "add", "-q", "-b", "linked-branch", linked.toString): Unit
      val nested = linked.resolve("sub/deep")
      Files.createDirectories(nested): Unit
      withRepository(discoverRepository(nested.toString)): repository =>
        assertEquals(repository.workTree.map(workTreeIdentity), Some(workTreeIdentity(linked.toString)))
        assertEquals(repository.branch, Right(Some("linked-branch")))

  test("discoverRepository never examines a ceiling directory"):
    withTempRoot("discover-ceiling-blocks"): tmp =>
      val repo = tmp.resolve("repo")
      initMinimalRepo(repo)
      val nested = repo.resolve("a/b")
      Files.createDirectories(nested): Unit
      assertNotFound(discoverRepository(nested.toString, Seq(repo.resolve("a").toString)), nested)

  test("discoverRepository examines the start directory even when its parent is a ceiling"):
    withTempRoot("discover-ceiling-start"): tmp =>
      val repo = tmp.resolve("repo")
      initMinimalRepo(repo)
      markGitDirectory(repo.resolve(".git"))
      withRepository(discoverRepository(repo.toString, Seq(tmp.toString))): repository =>
        assertEquals(repository.workTree.map(workTreeIdentity), Some(workTreeIdentity(repo.toString)))

  test("discoverRepository reports not found when no repository lies below the ceiling"):
    withTempRoot("discover-none"): tmp =>
      val outside = tmp.resolve("plain/a/b")
      Files.createDirectories(outside): Unit
      assertNotFound(discoverRepository(outside.toString, Seq(tmp.toString)), outside)

  test("a malformed gitdir redirect fails loudly rather than reading as an absent repository"):
    withTempRoot("malformed-gitdir"): tmp =>
      val broken = tmp.resolve("broken")
      Files.createDirectories(broken): Unit
      Files.writeString(broken.resolve(".git"), "this is not a gitdir reference\n"): Unit

      def assertLoud(result: Either[GitError, GitRepository], entryPoint: String): Unit =
        result match
          case Left(GitError.RepositoryNotFound(_)) => fail(s"$entryPoint read a malformed redirect as an absent repository")
          case Left(_)                              => ()
          case Right(repository)                    =>
            repository.close()
            fail(s"$entryPoint opened a malformed redirect")

      assertLoud(openRepository(broken.toString), "openRepository")
      assertLoud(discoverRepository(broken.toString), "discoverRepository")

end RepositoryDiscoverySuite
