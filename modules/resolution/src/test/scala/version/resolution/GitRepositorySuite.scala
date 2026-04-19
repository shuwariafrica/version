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
import scala.util.control.NonFatal

import version.resolution.domain.*

/** Shared tests for [[GitRepository]] trait, run against each platform's backend. */
abstract class GitRepositorySuite extends FunSuite, GitRepositoryTestSupport:

  override val munitTimeout: Duration = 120.seconds

  /** Creates a minimal test repo and runs the test with it. */
  private def withMinimalRepo[A](name: String)(f: os.Path => A): A =
    val tmp = os.temp.dir(prefix = s"version-gr-$name-")
    try
      initMinimalRepo(tmp)
      f(tmp)
    finally
      try os.remove.all(tmp)
      catch case NonFatal(_) => ()

  test("head returns Some(sha) for repository with commits"):
    withMinimalRepo("head-some"): repo =>
      val gr = openTestRepository(repo)
      try
        val result = gr.head
        assert(result.isRight, s"Expected Right, got $result")
        val headOpt = result.toOption.get
        assert(headOpt.isDefined)
        assert(headOpt.get.value.nonEmpty)
        assert(headOpt.get.value.length == 40)
      finally gr.close()

  test("resolve returns full SHA for HEAD"):
    withMinimalRepo("resolve-head"): repo =>
      val gr = openTestRepository(repo)
      try
        val result = gr.resolve("HEAD")
        assert(result.isRight, s"Expected Right, got $result")
        assertEquals(result.toOption.get.value.length, 40)
      finally gr.close()

  test("resolve returns RevisionNotFound for nonexistent ref"):
    withMinimalRepo("resolve-missing"): repo =>
      val gr = openTestRepository(repo)
      try
        val result = gr.resolve("nonexistent-ref-xyz")
        assert(result.isLeft)
        result.left.toOption.get match
          case GitError.RevisionNotFound(_) => ()
          case other                        => fail(s"Expected RevisionNotFound, got $other")
      finally gr.close()

  test("branch returns Some(main) on non-detached HEAD"):
    withMinimalRepo("branch-main"): repo =>
      val gr = openTestRepository(repo)
      try
        val result = gr.branch
        assert(result.isRight, s"Expected Right, got $result")
        assertEquals(result.toOption.get, Some("main"))
      finally gr.close()

  test("branch returns None on detached HEAD"):
    withMinimalRepo("branch-detached"): repo =>
      val sha = git(repo, "rev-parse", "HEAD").trim
      checkout(repo, sha)
      val gr = openTestRepository(repo)
      try
        val result = gr.branch
        assert(result.isRight, s"Expected Right, got $result")
        assertEquals(result.toOption.get, None)
      finally gr.close()

  test("isBare returns false for normal repository"):
    withMinimalRepo("isBare-false"): repo =>
      val gr = openTestRepository(repo)
      try assert(!gr.isBare)
      finally gr.close()

  test("clean returns true for unmodified repository"):
    withMinimalRepo("clean-true"): repo =>
      val gr = openTestRepository(repo)
      try
        val result = gr.clean
        assert(result.isRight, s"Expected Right, got $result")
        assertEquals(result.toOption.get, true)
      finally gr.close()

  test("clean returns false when untracked file exists"):
    withMinimalRepo("clean-untracked"): repo =>
      os.write(repo / "untracked.txt", "hello")
      val gr = openTestRepository(repo)
      try
        val result = gr.clean
        assert(result.isRight, s"Expected Right, got $result")
        assertEquals(result.toOption.get, false)
      finally gr.close()

  test("tags returns annotated and lightweight tags with correct kind"):
    withMinimalRepo("tags-kinds"): repo =>
      tag(repo, "v1.0.0", "Release 1.0.0")
      os.proc("git", "tag", "lightweight-tag").call(cwd = repo, check = true): Unit
      val gr = openTestRepository(repo)
      try
        val result = gr.tags
        assert(result.isRight, s"Expected Right, got $result")
        val allTags = result.toOption.get
        val annotated = allTags.filter(_.kind == TagKind.Annotated)
        val lightweight = allTags.filter(_.kind == TagKind.Lightweight)
        assert(annotated.exists(_.name == "v1.0.0"), s"Expected annotated v1.0.0, got: ${annotated.map(_.name).mkString}")
        assert(lightweight.exists(_.name == "lightweight-tag"), s"Expected lightweight-tag, got: ${lightweight.map(_.name).mkString}")
      finally gr.close()

  test("isAncestorOf returns true for ancestor commit"):
    withMinimalRepo("ancestor-true"): repo =>
      val sha1 = git(repo, "rev-parse", "HEAD").trim.toLowerCase
      commit(repo, "second commit"): Unit
      val sha2 = git(repo, "rev-parse", "HEAD").trim.toLowerCase
      val gr = openTestRepository(repo)
      try
        val result = gr.isAncestorOf(CommitSha(sha1), CommitSha(sha2))
        assert(result.isRight, s"Expected Right, got $result")
        assertEquals(result.toOption.get, true)
      finally gr.close()

  test("isAncestorOf returns true for same commit"):
    withMinimalRepo("ancestor-same"): repo =>
      val sha = git(repo, "rev-parse", "HEAD").trim.toLowerCase
      val gr = openTestRepository(repo)
      try
        val result = gr.isAncestorOf(CommitSha(sha), CommitSha(sha))
        assert(result.isRight, s"Expected Right, got $result")
        assertEquals(result.toOption.get, true)
      finally gr.close()

  test("isAncestorOf returns false for non-ancestor"):
    withMinimalRepo("ancestor-false"): repo =>
      commit(repo, "second commit"): Unit
      val sha2 = git(repo, "rev-parse", "HEAD").trim.toLowerCase
      os.proc("git", "checkout", "-q", "-b", "other", "HEAD~1").call(cwd = repo, check = true): Unit
      val branchSha = commit(repo, "branch commit")
      val gr = openTestRepository(repo)
      try
        val result = gr.isAncestorOf(CommitSha(sha2), CommitSha(branchSha))
        assert(result.isRight, s"Expected Right, got $result")
        assertEquals(result.toOption.get, false)
      finally gr.close()

  test("reachableTags finds tags reachable from commit"):
    withMinimalRepo("reachable-tags"): repo =>
      val sha1 = git(repo, "rev-parse", "HEAD").trim.toLowerCase
      tag(repo, "v1.0.0", "Release 1.0.0")
      commit(repo, "after tag"): Unit
      val sha2 = git(repo, "rev-parse", "HEAD").trim.toLowerCase
      val gr = openTestRepository(repo)
      try
        val result = gr.reachableTags(CommitSha(sha2), Set(CommitSha(sha1)))
        assert(result.isRight, s"Expected Right, got $result")
        assertEquals(result.toOption.get, Set(CommitSha(sha1)))
      finally gr.close()

  test("walkAll returns commits in time-descending order"):
    withMinimalRepo("walkAll-order"): repo =>
      val sha1 = git(repo, "rev-parse", "HEAD").trim.toLowerCase
      val sha2 = commit(repo, "second")
      val sha3 = commit(repo, "third")
      val gr = openTestRepository(repo)
      try
        val result = gr.walkAll(CommitSha(sha3), Some(CommitSha(sha1)))
        assert(result.isRight, s"Expected Right, got $result")
        val commits = result.toOption.get
        assertEquals(commits.length, 2)
        assertEquals(commits(0).id.value, sha3)
        assertEquals(commits(1).id.value, sha2)
      finally gr.close()

  test("walkAll includes merge parents"):
    withMinimalRepo("walkAll-merges"): repo =>
      val initSha = git(repo, "rev-parse", "HEAD").trim.toLowerCase
      os.proc("git", "checkout", "-q", "-b", "feat").call(cwd = repo, check = true): Unit
      val featureSha = commit(repo, "feature work")
      checkoutMain(repo)
      os.proc("git", "merge", "--no-ff", "--no-gpg-sign", "-m", "Merge feat", "feat").call(cwd = repo, check = true): Unit
      val mergeSha = git(repo, "rev-parse", "HEAD").trim.toLowerCase
      val gr = openTestRepository(repo)
      try
        val result = gr.walkAll(CommitSha(mergeSha), Some(CommitSha(initSha)))
        assert(result.isRight, s"Expected Right, got $result")
        val commits = result.toOption.get
        val shas = commits.map(_.id.value).toSet
        assert(shas.contains(featureSha), s"Feature commit $featureSha should be in walkAll result")
        assert(shas.contains(mergeSha), s"Merge commit $mergeSha should be in walkAll result")
      finally gr.close()

  test("walkFirstParent follows only first-parent links"):
    withMinimalRepo("walkFirstParent"): repo =>
      val initSha = git(repo, "rev-parse", "HEAD").trim.toLowerCase
      os.proc("git", "checkout", "-q", "-b", "feat").call(cwd = repo, check = true): Unit
      val featureSha = commit(repo, "feature work")
      checkoutMain(repo)
      os.proc("git", "merge", "--no-ff", "--no-gpg-sign", "-m", "Merge feat", "feat").call(cwd = repo, check = true): Unit
      val mergeSha = git(repo, "rev-parse", "HEAD").trim.toLowerCase
      val gr = openTestRepository(repo)
      try
        val result = gr.walkFirstParent(CommitSha(mergeSha), Some(CommitSha(initSha)))
        assert(result.isRight, s"Expected Right, got $result")
        val commits = result.toOption.get
        val shas = commits.map(_.id.value).toSet
        assert(shas.contains(mergeSha), "Merge commit should be in walkFirstParent result")
        assert(!shas.contains(featureSha), "Feature commit should NOT be in walkFirstParent result")
      finally gr.close()

  test("abbreviate truncates SHA to requested length"):
    withMinimalRepo("abbreviate"): repo =>
      val sha = git(repo, "rev-parse", "HEAD").trim.toLowerCase
      val gr = openTestRepository(repo)
      try
        val result = gr.abbreviate(CommitSha(sha), 12)
        assert(result.isRight, s"Expected Right, got $result")
        assertEquals(result.toOption.get.length, 12)
        assert(sha.startsWith(result.toOption.get))
      finally gr.close()

  test("RawCommit.isMerge returns true for merge commits"):
    withMinimalRepo("rawcommit-merge"): repo =>
      os.proc("git", "checkout", "-q", "-b", "feat").call(cwd = repo, check = true): Unit
      commit(repo, "feature work"): Unit
      checkoutMain(repo)
      os.proc("git", "merge", "--no-ff", "--no-gpg-sign", "-m", "Merge feat", "feat").call(cwd = repo, check = true): Unit
      val mergeSha = git(repo, "rev-parse", "HEAD").trim.toLowerCase
      val gr = openTestRepository(repo)
      try
        val result = gr.walkAll(CommitSha(mergeSha), None)
        assert(result.isRight, s"Expected Right, got $result")
        val mergeCommit = result.toOption.get.find(_.id.value == mergeSha)
        assert(mergeCommit.isDefined)
        assert(mergeCommit.get.isMerge)
        assert(mergeCommit.get.parentIds.length == 2)
      finally gr.close()
end GitRepositorySuite
