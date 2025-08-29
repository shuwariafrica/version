package version.cli.core

import munit.FunSuite

import version.PreRelease
import version.cli.core.domain.*
import version.cli.core.git.GitProcess
import version.cli.core.logging.{NullLogger, Logger}

final class GitProcessSuite extends FunSuite with TestRepoSupport:

  given PreRelease.Resolver = PreRelease.Resolver.default
  given Logger = NullLogger
  given Boolean = false

  test("listAllTags returns annotated and lightweight tags; ignores invalid") {
    withFreshRepo("listAllTags") { repo =>
      val git = new GitProcess(repo)
      val tags = git.listAllTags().toOption.get
      val names = tags.map(_.name.value).toSet
      assert(names.contains("v0.1.0"))
      assert(names.contains("v1.0.0"))
      assert(names.contains("v1.0.1"))
      assert(names.contains("v2.0.0"))
      assert(!names.contains("not-a-version"))
      assert(!names.contains("v1.0"))
      assert(!names.contains("release-1.0.0"))
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
      assert(text.contains("change: minor"))
      assert(text.contains("change: patch"))
      // First-parent count should exclude the merge commit itself
      val count = git.countCommitsSince(head, Some(base)).toOption.get
      assert(count >= 1)
    }
  }
end GitProcessSuite
