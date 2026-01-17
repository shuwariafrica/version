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
import version.cli.core.git.GitProcess
import version.cli.core.logging.*
import version.given

/** Verifies verbose logging covers key resolver + git steps and that disabling verbose suppresses those entries. */
final class LoggingSuite extends FunSuite with TestRepoSupport:

  final private class BufferingLogger extends Logger:
    val entries = scala.collection.mutable.ListBuffer.empty[LogEntry]
    def log(entry: LogEntry): Unit = entries += entry

  test("GitProcess emits expected verbose entries when verbose=true") {
    withFreshRepo("logging-git") { repo =>
      val logger = BufferingLogger()
      given Logger = logger
      given Boolean = true
      val git = new GitProcess(repo)
      // Exercise several methods
      val head = git.resolveRev("HEAD").toOption.get
      assert(logger.entries.exists(_.message.startsWith("Resolved HEAD ->")), clues(logger.entries.mkString("\n")))
      val tags = git.listAllTags().toOption.get
      assert(tags.nonEmpty)
      assert(logger.entries.exists(_.message.contains("parsed SemVer annotated tag(s)")))
      val reachable = git.findReachableTags(head).toOption.get
      assert(reachable.nonEmpty)
      assert(logger.entries.exists(_.message.startsWith("Tag v")))
      val clean = git.isWorkingDirectoryClean().toOption.get
      assert(clean)
      assert(logger.entries.exists(_.message.startsWith("Worktree clean=")))
      val branch = git.getBranchName().toOption.get
      assert(branch.contains("main"))
      assert(logger.entries.exists(_.message.startsWith("Branch resolved:")))
    }
  }

  test("Resolver emits high-level lifecycle messages (snapshot path)") {
    withFreshRepo("logging-resolver") { repo =>
      // Dirty worktree to force snapshot path
      os.write.append(repo / "README.md", "\nextra\n"): Unit
      val logger = BufferingLogger()
      val cfg = CliConfig(repo = repo, basisCommit = "HEAD", prNumber = None, branchOverride = None, shaLength = 12, verbose = true)
      val res = VersionCliCore.resolve(cfg, logger, true)
      assert(res.isRight, clues(res))
      val msgs = logger.entries.map(_.message)
      // Begin + snapshot path entered
      assert(msgs.exists(_.startsWith("Begin resolution basisCommit=")), clues(msgs.mkString("\n")))
      assert(msgs.exists(_.startsWith("Entering snapshot path")), clues(msgs.mkString("\n")))
      // Reachable or commits (no base) + metadata + final snapshot
      assert(msgs.exists(m => m.startsWith("Reachable tags:") || m.startsWith("Commits (no base tag):")), clues(msgs.mkString("\n")))
      assert(msgs.exists(_.startsWith("Build metadata assembled:")), clues(msgs.mkString("\n")))
      assert(msgs.exists(_.startsWith("Final snapshot version:")), clues(msgs.mkString("\n")))
    }
  }

  test("Verbose=false suppresses verbose messages while errors still log") {
    withFreshRepo("logging-off") { repo =>
      val logger = BufferingLogger()
      val cfg = CliConfig(repo = repo, basisCommit = "HEAD", prNumber = None, branchOverride = None, shaLength = 12, verbose = false)
      val _ = VersionCliCore.resolve(cfg, logger, false)
      // Expect no verbose entries captured
      assertEquals(logger.entries.count(_.level == LogLevel.Verbose), 0)
    }
  }
end LoggingSuite
