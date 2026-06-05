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
import java.nio.file.Path

import scala.concurrent.duration.*
import scala.util.control.NonFatal

import version.resolution.domain.*
import version.testkit.Filesystem
import version.testkit.Process

/** Shared tests for [[GitRepository]] trait, run against each platform's backend. */
abstract class GitRepositorySuite extends FunSuite, GitRepositoryTestSupport:

  override val munitTimeout: Duration = 120.seconds

  /** Creates a minimal test repo and runs the test with it. */
  private def withMinimalRepo[A](name: String)(f: Path => A): A =
    val tmp = Files.createTempDirectory(s"version-gr-$name-")
    try
      initMinimalRepo(tmp)
      f(tmp)
    finally
      try Filesystem.removeRecursive(tmp)
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
      Files.writeString(repo.resolve("untracked.txt"), "hello"): Unit
      val gr = openTestRepository(repo)
      try
        val result = gr.clean
        assert(result.isRight, s"Expected Right, got $result")
        assertEquals(result.toOption.get, false)
      finally gr.close()

  test("tags returns annotated and lightweight tags with correct kind"):
    withMinimalRepo("tags-kinds"): repo =>
      tag(repo, "v1.0.0", "Release 1.0.0")
      Process.runChecked(Seq("git", "tag", "lightweight-tag"), repo): Unit
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
      Process.runChecked(Seq("git", "checkout", "-q", "-b", "other", "HEAD~1"), repo): Unit
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
      Process.runChecked(Seq("git", "checkout", "-q", "-b", "feat"), repo): Unit
      val featureSha = commit(repo, "feature work")
      checkoutMain(repo)
      Process.runChecked(Seq("git", "merge", "--no-ff", "--no-gpg-sign", "-m", "Merge feat", "feat"), repo): Unit
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
      Process.runChecked(Seq("git", "checkout", "-q", "-b", "feat"), repo): Unit
      val featureSha = commit(repo, "feature work")
      checkoutMain(repo)
      Process.runChecked(Seq("git", "merge", "--no-ff", "--no-gpg-sign", "-m", "Merge feat", "feat"), repo): Unit
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

  test("loadCommit returns RawCommit for an existing sha with non-zero commitTime"):
    withMinimalRepo("loadCommit-ok"): repo =>
      val sha = git(repo, "rev-parse", "HEAD").trim.toLowerCase
      val gr = openTestRepository(repo)
      try
        val result = gr.loadCommit(CommitSha(sha))
        assert(result.isRight, s"Expected Right, got $result")
        val rc = result.toOption.get
        assertEquals(rc.id.value, sha)
        assert(rc.commitTime > 0L, s"Expected positive commitTime, got ${rc.commitTime}")
      finally gr.close()

  test("loadCommit returns ObjectNotFound for an unknown sha"):
    withMinimalRepo("loadCommit-missing"): repo =>
      val gr = openTestRepository(repo)
      try
        val unknown = CommitSha("0" * 40)
        val result = gr.loadCommit(unknown)
        assert(result.isLeft, s"Expected Left, got $result")
      finally gr.close()

  test("RawCommit.isMerge returns true for merge commits"):
    withMinimalRepo("rawcommit-merge"): repo =>
      Process.runChecked(Seq("git", "checkout", "-q", "-b", "feat"), repo): Unit
      commit(repo, "feature work"): Unit
      checkoutMain(repo)
      Process.runChecked(Seq("git", "merge", "--no-ff", "--no-gpg-sign", "-m", "Merge feat", "feat"), repo): Unit
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

  test("createCommit creates an empty commit on HEAD carrying the message"):
    withMinimalRepo("createCommit"): repo =>
      val gr = openTestRepository(repo)
      try
        val parent = gr.head.toOption.flatten.getOrElse(fail("no HEAD"))
        val author = AuthorSignature("Author", "author@example.com", 1700000000L, 0)
        val result = gr.createCommit("version: minor", author, sign = false)
        assert(result.isRight, clues(result))
        val sha = result.toOption.get
        assertEquals(gr.head.toOption.flatten, Some(sha))
        val loaded = gr.loadCommit(sha).toOption.getOrElse(fail("loadCommit failed"))
        assertEquals(loaded.message.trim, "version: minor")
        assertEquals(loaded.parentIds.map(_.value).toList, List(parent.value))
        assert(git(repo, "diff", "--name-only", parent.value, sha.value).trim.isEmpty, "commit should change no files")
      finally gr.close()

  test("createTag creates an annotated tag at the target"):
    withMinimalRepo("createTag"): repo =>
      val gr = openTestRepository(repo)
      try
        val target = gr.head.toOption.flatten.getOrElse(fail("no HEAD"))
        val tagger = AuthorSignature("Tagger", "tagger@example.com", 1700000000L, 0)
        val result = gr.createTag("v9.9.9", target, "Release 9.9.9", tagger, sign = false)
        assert(result.isRight, clues(result))
        val tags = gr.tags.toOption.getOrElse(fail("tags failed"))
        val created = tags.find(_.name == "v9.9.9").getOrElse(fail(s"tag not found in ${tags.map(_.name).toList}"))
        assertEquals(created.kind, TagKind.Annotated)
        assertEquals(created.commit.value, target.value)
        assertEquals(git(repo, "for-each-ref", "--format=%(contents:subject)", "refs/tags/v9.9.9").trim, "Release 9.9.9")
        assertEquals(gr.loadTagger("v9.9.9"), Right(1700000000L), "loadTagger should return the tag's tagger time")
      finally gr.close()

  test("signingKey returns the configured user.signingkey"):
    withMinimalRepo("signingKey"): repo =>
      git(repo, "config", "user.signingkey", "DEADBEEFCAFE"): Unit
      val gr = openTestRepository(repo)
      try assertEquals(gr.signingKey.toOption.flatten, Some("DEADBEEFCAFE"))
      finally gr.close()

  test("defaultSignature reads the configured identity"):
    withMinimalRepo("defaultSignature"): repo =>
      val gr = openTestRepository(repo)
      try
        val sig = gr.defaultSignature.toOption.getOrElse(fail("defaultSignature failed"))
        assertEquals(sig.name, "Test")
        assertEquals(sig.email, "test@example.com")
        assert(sig.whenEpochSeconds > 0L, s"expected positive time, got ${sig.whenEpochSeconds}")
      finally gr.close()
end GitRepositorySuite
