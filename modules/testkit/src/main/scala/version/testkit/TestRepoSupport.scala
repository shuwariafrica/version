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
  * Mix this trait into test suites that require a fresh Git repository for each test.
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

  /** Runs a git command in the repository, returning stdout text. */
  def git(repo: os.Path, args: String*): String =
    os.proc("git" +: args).call(cwd = repo, check = true).out.text()

  /** Checks out a branch or tag. */
  def checkout(repo: os.Path, ref: String): Unit =
    os.proc("git", "checkout", "-q", ref).call(cwd = repo, check = true): Unit
end TestRepoSupport
