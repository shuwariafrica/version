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
package version.testkit

/** Cross-platform test repository builder using os-lib.
  *
  * Creates a deterministic test repository with multiple branches, tags, annotated and lightweight tags, pre-releases,
  * multi-tag commits, ignored/non-semver tags, and unreachable tags.
  *
  * **Important**: Only annotated tags are recognised as valid version tags per the specification. Lightweight tags are
  * created in this test repository specifically to verify they are correctly ignored by the version resolution logic.
  *
  * This replaces the shell script `scripts/create-test-repo.sh` with a pure Scala implementation that works on all
  * platforms without shell dependencies.
  */
object TestRepoBuilder:

  /** Creates a test repository at the specified path.
    *
    * @param repoDir
    *   The directory where the repository will be created. Will be removed if it exists.
    */
  def create(repoDir: os.Path): Unit =
    // Clean up and create directory
    if os.exists(repoDir) then os.remove.all(repoDir)
    os.makeDir.all(repoDir)

    // Initialise repository
    run(repoDir, "init", "-q")
    run(repoDir, "config", "user.name", "Version CLI Test")
    run(repoDir, "config", "user.email", "test@example.com")
    // Silence detached HEAD advice in tests
    run(repoDir, "config", "advice.detachedHead", "false")
    // Disable any signing and editor prompts for non-interactive runs
    run(repoDir, "config", "commit.gpgsign", "false")
    run(repoDir, "config", "tag.gpgsign", "false")
    gitIgnoreError(repoDir, "config", "gpg.sign", "false")
    gitIgnoreError(repoDir, "config", "gpg.format", "openpgp")
    run(repoDir, "config", "core.editor", "true")
    run(repoDir, "config", "core.hooksPath", "/dev/null")

    // c1: initial commit, lightweight semver tag (should be IGNORED) and an invalid tag
    os.write(repoDir / "README.md", "# Test Repo\n")
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "Initial commit")
    val c1 = git(repoDir, "rev-parse", "HEAD").trim
    run(repoDir, "tag", "v0.1.0") // lightweight - should be ignored
    run(repoDir, "tag", "not-a-version")

    // c2: version: minor (to simulate activity before v1.0.0)
    os.write.append(repoDir / "README.md", "line 1\n")
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "version: minor")

    // Tag v1.0.0 (annotated, explicitly not signed) on c2
    run(repoDir, "tag", "--annotate", "--no-sign", "--message", "Release 1.0.0", "v1.0.0")

    // Create release/1.0 branch at v1.0.0
    run(repoDir, "branch", "release/1.0", "v1.0.0")
    run(repoDir, "checkout", "-q", "release/1.0")
    // cR1: patch bump on maintenance using version: directive
    os.write.append(repoDir / "README.md", "hotfix\n")
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "version: patch")
    run(repoDir, "tag", "--annotate", "--no-sign", "--message", "Release 1.0.1", "v1.0.1")

    // Return to main - handle both master and main branch names
    checkoutMain(repoDir)

    // If currently at v1.0.0, add a couple of commits
    os.write.append(repoDir / "README.md", "post-1.0 work\n")
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "target: 1.0.2")
    os.write.append(repoDir / "README.md", "patch work\n")
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "version: patch")

    // Multi-tag same commit (pre-release and final)
    os.write.append(repoDir / "README.md", "2.0 line\n")
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "prep 2.0")
    run(repoDir, "tag", "--annotate", "--no-sign", "--message", "2.0.0-rc.1 on same commit", "v2.0.0-rc.1")
    run(repoDir, "tag", "--annotate", "--no-sign", "--message", "2.0.0 final on same commit", "v2.0.0")

    // Pre-release branch with reachable pre-release
    run(repoDir, "branch", "pre-from-1.0", "v1.0.0")
    run(repoDir, "checkout", "-q", "pre-from-1.0")
    os.write.append(repoDir / "README.md", "minor bump line\n")
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "version: minor")
    run(repoDir, "tag", "--annotate", "--no-sign", "--message", "1.1.0-rc.1 pre-release", "v1.1.0-rc.1")
    run(repoDir, "checkout", "-q", "main")

    // Unreachable high tag on another branch for repo-wide tests
    run(repoDir, "checkout", "-q", "-b", "unreachable-from-main", c1)
    os.write.append(repoDir / "README.md", "unreach\n")
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "unreachable branch work")
    run(repoDir, "tag", "--annotate", "--no-sign", "--message", "Repo-wide high tag", "v4.3.0")
    run(repoDir, "checkout", "-q", "main")

    // Additional ignored tags: lightweight with valid semver (should be ignored) and non-semver
    run(repoDir, "tag", "v1.0") // lightweight, invalid semver (partial) - ignored
    run(repoDir, "tag", "release-1.0.0") // lightweight, non-semver - ignored
    run(repoDir, "tag", "v3.0.0") // lightweight with valid semver - MUST BE IGNORED (only annotated count)
    run(repoDir, "tag", "0.5.0") // lightweight without v prefix - MUST BE IGNORED

    // Create a feature branch from v1.0.0, add commits, and merge back into main with a merge commit
    run(repoDir, "checkout", "-q", "-b", "feature/merge", "v1.0.0")
    os.write(repoDir / "FEATURE.txt", "feat work\n")
    run(repoDir, "add", "FEATURE.txt")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "feat: Add new feature") // Conventional Commits style
    os.write(repoDir / "BUGFIX.txt", "bugfix\n")
    run(repoDir, "add", "BUGFIX.txt")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "fix: Handle edge case") // standalone shorthand
    run(repoDir, "checkout", "-q", "main")
    // Ensure merge is non fast-forward
    run(repoDir, "merge", "--no-ff", "--no-gpg-sign", "-m", "merge feature/merge", "feature/merge")
    // Add a post-merge linear commit with version: ignore directive
    os.write.append(repoDir / "README.md", "post-merge\n")
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "--message", "housekeeping\n\nversion: ignore")
  end create

  /** Runs a git command in the repository directory, returning stdout. */
  private def git(repoDir: os.Path, args: String*): String =
    os.proc("git" +: args).call(cwd = repoDir, check = true, stderr = os.Pipe).out.text()

  /** Runs a git command, discarding output (for commands where result is not needed). */
  private def run(repoDir: os.Path, args: String*): Unit =
    os.proc("git" +: args).call(cwd = repoDir, check = true, stderr = os.Pipe): Unit

  /** Runs a git command, ignoring errors (for optional config settings). */
  private def gitIgnoreError(repoDir: os.Path, args: String*): Unit =
    scala.util.Try(os.proc("git" +: args).call(cwd = repoDir, check = true, stderr = os.Pipe)): Unit

  /** Checks out the main branch, handling both 'master' and 'main' conventions. */
  private def checkoutMain(repoDir: os.Path): Unit =
    // Try master first, then main, then create main from v1.0.0
    val tryMaster = scala.util.Try(git(repoDir, "checkout", "-q", "master"))
    if tryMaster.isFailure then
      val tryMain = scala.util.Try(git(repoDir, "checkout", "-q", "main"))
      if tryMain.isFailure then run(repoDir, "checkout", "-q", "-B", "main", "v1.0.0")

    // Ensure branch is named 'main'
    val currentBranch = git(repoDir, "rev-parse", "--abbrev-ref", "HEAD").trim
    if currentBranch != "main" then run(repoDir, "branch", "-M", "main")

end TestRepoBuilder
