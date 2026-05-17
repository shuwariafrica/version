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
package version.testkit

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Creates a deterministic test repository with multiple branches, tags, annotated and lightweight tags, pre-releases,
  * multi-tag commits, ignored/non-semver tags, and unreachable tags.
  *
  * Only annotated tags are recognised as valid version tags per the specification. Lightweight tags are created in this
  * test repository specifically to verify they are correctly ignored by the version resolution logic.
  */
object TestRepoBuilder:

  def create(repoDir: Path): Unit =
    Filesystem.removeRecursive(repoDir)
    Files.createDirectories(repoDir)

    run(repoDir, "init", "-q")
    run(repoDir, "config", "user.name", "Version CLI Test")
    run(repoDir, "config", "user.email", "test@example.com")
    run(repoDir, "config", "advice.detachedHead", "false")
    run(repoDir, "config", "commit.gpgsign", "false")
    run(repoDir, "config", "tag.gpgsign", "false")
    runIgnoreError(repoDir, "config", "gpg.sign", "false")
    runIgnoreError(repoDir, "config", "gpg.format", "openpgp")
    run(repoDir, "config", "core.editor", "true")
    run(repoDir, "config", "core.hooksPath", "/dev/null")

    // c1: initial commit, lightweight semver tag (should be IGNORED) and an invalid tag
    writeReadme(repoDir, "# Test Repo\n")
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "Initial commit")
    val c1 = git(repoDir, "rev-parse", "HEAD").trim
    run(repoDir, "tag", "v0.1.0")
    run(repoDir, "tag", "not-a-version")

    // c2: version: minor (to simulate activity before v1.0.0)
    appendReadme(repoDir, "line 1\n")
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "version: minor")

    // Tag v1.0.0 (annotated) on c2
    run(repoDir, "tag", "--annotate", "--no-sign", "--message", "Release 1.0.0", "v1.0.0")

    run(repoDir, "branch", "release/1.0", "v1.0.0")
    run(repoDir, "checkout", "-q", "release/1.0")
    appendReadme(repoDir, "hotfix\n")
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "version: patch")
    run(repoDir, "tag", "--annotate", "--no-sign", "--message", "Release 1.0.1", "v1.0.1")

    checkoutMain(repoDir)

    appendReadme(repoDir, "post-1.0 work\n")
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "target: 1.0.2")
    appendReadme(repoDir, "patch work\n")
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "version: patch")

    // Multi-tag same commit (pre-release and final)
    appendReadme(repoDir, "2.0 line\n")
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "prep 2.0")
    run(repoDir, "tag", "--annotate", "--no-sign", "--message", "2.0.0-rc.1 on same commit", "v2.0.0-rc.1")
    run(repoDir, "tag", "--annotate", "--no-sign", "--message", "2.0.0 final on same commit", "v2.0.0")

    // Pre-release branch with reachable pre-release
    run(repoDir, "branch", "pre-from-1.0", "v1.0.0")
    run(repoDir, "checkout", "-q", "pre-from-1.0")
    appendReadme(repoDir, "minor bump line\n")
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "version: minor")
    run(repoDir, "tag", "--annotate", "--no-sign", "--message", "1.1.0-rc.1 pre-release", "v1.1.0-rc.1")
    run(repoDir, "checkout", "-q", "main")

    // Unreachable high tag on another branch for repo-wide tests
    run(repoDir, "checkout", "-q", "-b", "unreachable-from-main", c1)
    appendReadme(repoDir, "unreach\n")
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "unreachable branch work")
    run(repoDir, "tag", "--annotate", "--no-sign", "--message", "Repo-wide high tag", "v4.3.0")
    run(repoDir, "checkout", "-q", "main")

    // Additional ignored tags
    run(repoDir, "tag", "v1.0")
    run(repoDir, "tag", "release-1.0.0")
    run(repoDir, "tag", "v3.0.0")
    run(repoDir, "tag", "0.5.0")

    // Feature branch from v1.0.0, merged back into main with a merge commit
    run(repoDir, "checkout", "-q", "-b", "feature/merge", "v1.0.0")
    Files.writeString(repoDir.resolve("FEATURE.txt"), "feat work\n"): Unit
    run(repoDir, "add", "FEATURE.txt")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "feat: Add new feature")
    Files.writeString(repoDir.resolve("BUGFIX.txt"), "bugfix\n"): Unit
    run(repoDir, "add", "BUGFIX.txt")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "fix: Handle edge case")
    run(repoDir, "checkout", "-q", "main")
    run(repoDir, "merge", "--no-ff", "--no-gpg-sign", "-m", "merge feature/merge", "feature/merge")
    appendReadme(repoDir, "post-merge\n")
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "housekeeping\n\nversion: ignore")
  end create

  private def git(repoDir: Path, args: String*): String =
    Process.runChecked("git" +: args, repoDir)

  private def run(repoDir: Path, args: String*): Unit =
    Process.runChecked("git" +: args, repoDir): Unit

  private def runIgnoreError(repoDir: Path, args: String*): Unit =
    Process.run("git" +: args, repoDir): Unit

  private def writeReadme(repoDir: Path, content: String): Unit =
    Files.writeString(repoDir.resolve("README.md"), content): Unit

  private def appendReadme(repoDir: Path, content: String): Unit =
    Files.writeString(
      repoDir.resolve("README.md"),
      content,
      StandardOpenOption.CREATE,
      StandardOpenOption.APPEND
    ): Unit

  private def checkoutMain(repoDir: Path): Unit =
    val tryMaster = scala.util.Try(git(repoDir, "checkout", "-q", "master"))
    if tryMaster.isFailure then
      val tryMain = scala.util.Try(git(repoDir, "checkout", "-q", "main"))
      if tryMain.isFailure then run(repoDir, "checkout", "-q", "-B", "main", "v1.0.0")

    val currentBranch = git(repoDir, "rev-parse", "--abbrev-ref", "HEAD").trim
    if currentBranch != "main" then run(repoDir, "branch", "-M", "main")

end TestRepoBuilder
