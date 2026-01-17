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

import scala.util.control.NonFatal

/** Utility trait for creating and managing ephemeral test Git repositories.
  *
  * Mix this trait into test suites that require a fresh Git repository for each test. Provides cross-platform utilities
  * that handle differences between Git versions (e.g., default branch naming).
  */
trait TestRepoSupport:

  /** Create a fresh repository using the cross-platform Scala builder.
    *
    * @param testName
    *   A name to include in the temp directory prefix for debugging.
    * @param f
    *   The test function to run with the repository path.
    * @return
    *   The result of the test function.
    */
  def withFreshRepo[A](testName: String)(f: os.Path => A): A =
    val tmp = os.temp.dir(prefix = s"version-test-$testName-")
    try
      TestRepoBuilder.create(tmp)
      f(tmp)
    finally
      try os.remove.all(tmp)
      catch case NonFatal(_) => ()

  /** Initialises a minimal Git repository with proper cross-platform branch naming.
    *
    * Creates a fresh repository with an initial commit on a branch named `main`. Handles Git versions that default to
    * `master` by renaming the branch after initialisation.
    *
    * @param repoDir
    *   The directory where the repository will be created. Will be removed if it exists.
    */
  def initMinimalRepo(repoDir: os.Path): Unit =
    if os.exists(repoDir) then os.remove.all(repoDir)
    os.makeDir.all(repoDir)
    os.proc("git", "init", "-q").call(cwd = repoDir, check = true): Unit
    os.proc("git", "config", "user.name", "Test").call(cwd = repoDir, check = true): Unit
    os.proc("git", "config", "user.email", "test@example.com").call(cwd = repoDir, check = true): Unit
    os.proc("git", "config", "advice.detachedHead", "false").call(cwd = repoDir, check = true): Unit
    os.proc("git", "config", "commit.gpgsign", "false").call(cwd = repoDir, check = true): Unit
    os.write(repoDir / "README.md", "# Test\n")
    os.proc("git", "add", "README.md").call(cwd = repoDir, check = true): Unit
    os.proc("git", "commit", "--no-gpg-sign", "-m", "init").call(cwd = repoDir, check = true): Unit
    // Ensure branch is named 'main' (handles Git versions defaulting to 'master')
    os.proc("git", "branch", "-M", "main").call(cwd = repoDir, check = true): Unit

  /** Runs a git command in the repository, returning stdout text. */
  def git(repo: os.Path, args: String*): String =
    os.proc("git" +: args).call(cwd = repo, check = true).out.text()

  /** Checks out a branch or tag. */
  def checkout(repo: os.Path, ref: String): Unit =
    os.proc("git", "checkout", "-q", ref).call(cwd = repo, check = true): Unit

  /** Checks out the main branch, handling both `master` and `main` conventions.
    *
    * Attempts to checkout `master` first, then `main`. If neither exists, creates `main` from HEAD. Finally, ensures
    * the branch is named `main`.
    *
    * @param repo
    *   The repository path.
    */
  def checkoutMain(repo: os.Path): Unit =
    checkoutMain(repo, "HEAD")

  /** Checks out the main branch, handling both `master` and `main` conventions.
    *
    * Attempts to checkout `master` first, then `main`. If neither exists, creates `main` from the specified fallback
    * ref. Finally, ensures the branch is named `main`.
    *
    * @param repo
    *   The repository path.
    * @param fallbackRef
    *   The ref to create `main` from if neither `master` nor `main` exists.
    */
  def checkoutMain(repo: os.Path, fallbackRef: String): Unit =
    val tryMaster = scala.util.Try(git(repo, "checkout", "-q", "master"))
    if tryMaster.isFailure then
      val tryMain = scala.util.Try(git(repo, "checkout", "-q", "main"))
      if tryMain.isFailure then os.proc("git", "checkout", "-q", "-B", "main", fallbackRef).call(cwd = repo, check = true): Unit
    val currentBranch = git(repo, "rev-parse", "--abbrev-ref", "HEAD").trim
    if currentBranch != "main" then os.proc("git", "branch", "-M", "main").call(cwd = repo, check = true): Unit

  /** Creates a commit with the given message and returns its SHA.
    *
    * Appends the message to README.md, stages, commits, and returns the full SHA (lowercase).
    *
    * @param repo
    *   The repository path.
    * @param msg
    *   The commit message.
    * @return
    *   The full commit SHA in lowercase.
    */
  def commit(repo: os.Path, msg: String): String =
    os.write.append(repo / "README.md", s"\n$msg\n")
    os.proc("git", "add", "README.md").call(cwd = repo, check = true): Unit
    os.proc("git", "commit", "--no-gpg-sign", "-m", msg).call(cwd = repo, check = true): Unit
    git(repo, "rev-parse", "HEAD").trim.toLowerCase

  /** Creates an annotated tag at the current HEAD.
    *
    * @param repo
    *   The repository path.
    * @param tagName
    *   The tag name (e.g., "v1.0.0").
    * @param message
    *   The tag message.
    */
  def tag(repo: os.Path, tagName: String, message: String): Unit =
    os.proc("git", "tag", "-a", "--no-sign", "-m", message, tagName).call(cwd = repo, check = true): Unit

  /** Creates an annotated tag at the current HEAD using the tag name as the message. */
  def tag(repo: os.Path, tagName: String): Unit =
    tag(repo, tagName, tagName)
end TestRepoSupport
