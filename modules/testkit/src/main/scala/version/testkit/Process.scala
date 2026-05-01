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

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import scala.jdk.CollectionConverters.*

object Process:

  final case class Result(exitCode: Int, stdout: String, stderr: String):
    def successful: Boolean = exitCode == 0

  def run(command: Seq[String], cwd: Path): Result =
    val stdoutFile = Files.createTempFile("version-testkit-stdout-", ".log")
    val stderrFile = Files.createTempFile("version-testkit-stderr-", ".log")
    try
      val process = startWithRetry(command, cwd, stdoutFile, stderrFile)
      val exitCode = process.waitFor()
      Result(exitCode, readUtf8(stdoutFile), readUtf8(stderrFile))
    finally
      Files.deleteIfExists(stdoutFile): Unit
      Files.deleteIfExists(stderrFile): Unit

  // scalafix:off DisableSyntax.throw
  def runChecked(command: Seq[String], cwd: Path): String =
    val result = run(command, cwd)
    if result.successful then result.stdout
    else throw Failure(command, result)
  // scalafix:on

  private val MaxStartAttempts = 5

  private def startWithRetry(
    command: Seq[String],
    cwd: Path,
    stdoutFile: Path,
    stderrFile: Path
  ): java.lang.Process =
    val pb = new ProcessBuilder(command.asJava)
      .directory(cwd.toFile)
      .redirectOutput(stdoutFile.toFile)
      .redirectError(stderrFile.toFile)
    attemptStart(pb, 1)

  // scalafix:off DisableSyntax.throw
  @scala.annotation.tailrec
  private def attemptStart(pb: ProcessBuilder, attempt: Int): java.lang.Process =
    val outcome: Either[IOException, java.lang.Process] =
      try Right(pb.start())
      catch case e: IOException => Left(e)
    outcome match
      case Right(process)                             => process
      case Left(error) if attempt >= MaxStartAttempts => throw error
      case Left(_)                                    =>
        Thread.sleep((10L << attempt).min(500L))
        attemptStart(pb, attempt + 1)
  // scalafix:on

  private def readUtf8(path: Path): String =
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

  final class Failure(val command: Seq[String], val result: Result)
      extends RuntimeException(
        s"Process [${command.mkString(" ")}] exited with ${result.exitCode}\nstderr: ${result.stderr}"
      )

end Process
