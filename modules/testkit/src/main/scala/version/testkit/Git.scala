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

import java.nio.file.Path

/** Runs `git` against a repository named with `-C`, since a [[Process]] command takes no working directory. */
object Git:

  def run(repo: Path, args: String*): Process.Result = Process.run(command(repo, args))

  def runChecked(repo: Path, args: String*): String = Process.runChecked(command(repo, args))

  private def command(repo: Path, args: Seq[String]): Seq[String] = Seq("git", "-C", repo.toString) ++ args
