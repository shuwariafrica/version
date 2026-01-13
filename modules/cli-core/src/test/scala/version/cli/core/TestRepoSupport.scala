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
package version.cli.core

import os.*

import scala.util.control.NonFatal

/** Utility to create and manage ephemeral test Git repositories. */
trait TestRepoSupport:

  /** Create a fresh repository by running the bash fixture script. */
  def withFreshRepo[A](testName: String)(f: os.Path => A): A =
    val tmp = os.temp.dir(prefix = s"version-cli-core-$testName-")
    val scriptPath =
      Option(System.getenv("CREATE_TEST_REPO")).map(os.Path(_)).getOrElse(os.pwd / "scripts" / "create-test-repo.sh")
    try
      os.proc("bash", scriptPath.toString, tmp.toString).call(check = true): Unit
      f(tmp)
    finally
      try os.remove.all(tmp)
      catch case NonFatal(_) => ()

  /** Helper to run a git command in the repo, returning stdout text. */
  def git(repo: os.Path, args: String*): String =
    val quoted = ("git" +: args.toSeq)
      .map { a =>
        val s = a.replace("\\", "\\\\").replace("\"", "\\\"")
        s"\"$s\""
      }
      .mkString(" ")
    os.proc("bash", "-lc", quoted).call(cwd = repo, check = true).out.text()

  /** Checkout a branch or tag (porcelain is acceptable in tests). */
  def checkout(repo: os.Path, ref: String): Unit =
    // Quiet checkout; script disables detached head advice so no extra notes are printed.
    os.proc("git", "checkout", "-q", ref).call(cwd = repo, check = true): Unit
end TestRepoSupport
