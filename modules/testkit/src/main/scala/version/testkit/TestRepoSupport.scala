/****************************************************************
 * Copyright © 2023, 2026 Shuwari Africa Ltd.                   *
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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

import scala.util.control.NonFatal

/** Utility trait for creating and managing ephemeral test Git repositories. */
trait TestRepoSupport:

  def withFreshRepo[A](testName: String)(f: Path => A): A =
    val tmp = Files.createTempDirectory(s"version-test-$testName-")
    try
      TestRepoBuilder.create(tmp)
      f(tmp)
    finally
      try Filesystem.removeRecursive(tmp)
      catch case NonFatal(_) => ()

  def initMinimalRepo(repoDir: Path): Unit =
    Filesystem.removeRecursive(repoDir)
    Files.createDirectories(repoDir)
    run(repoDir, "init", "-q")
    run(repoDir, "config", "user.name", "Test")
    run(repoDir, "config", "user.email", "test@example.com")
    run(repoDir, "config", "advice.detachedHead", "false")
    run(repoDir, "config", "commit.gpgsign", "false")
    Files.writeString(repoDir.resolve("README.md"), "# Test\n"): Unit
    run(repoDir, "add", "README.md")
    run(repoDir, "commit", "--no-gpg-sign", "-m", "init")
    // Ensure branch is named 'main' (handles Git versions defaulting to 'master')
    run(repoDir, "branch", "-M", "main")

  def git(repo: Path, args: String*): String =
    Process.runChecked("git" +: args, repo)

  def checkout(repo: Path, ref: String): Unit =
    run(repo, "checkout", "-q", ref)

  def checkoutMain(repo: Path): Unit =
    checkoutMain(repo, "HEAD")

  def checkoutMain(repo: Path, fallbackRef: String): Unit =
    val tryMaster = scala.util.Try(git(repo, "checkout", "-q", "master"))
    if tryMaster.isFailure then
      val tryMain = scala.util.Try(git(repo, "checkout", "-q", "main"))
      if tryMain.isFailure then run(repo, "checkout", "-q", "-B", "main", fallbackRef)
    val currentBranch = git(repo, "rev-parse", "--abbrev-ref", "HEAD").trim
    if currentBranch != "main" then run(repo, "branch", "-M", "main")

  def commit(repo: Path, msg: String): String =
    Files.writeString(
      repo.resolve("README.md"),
      s"\n$msg\n",
      StandardOpenOption.CREATE,
      StandardOpenOption.APPEND
    ): Unit
    run(repo, "add", "README.md")
    run(repo, "commit", "--no-gpg-sign", "-m", msg)
    git(repo, "rev-parse", "HEAD").trim.toLowerCase

  def tag(repo: Path, tagName: String, message: String): Unit =
    run(repo, "tag", "-a", "--no-sign", "-m", message, tagName)

  def tag(repo: Path, tagName: String): Unit =
    tag(repo, tagName, tagName)

  private def run(repo: Path, args: String*): Unit =
    Process.runChecked("git" +: args, repo): Unit

end TestRepoSupport
