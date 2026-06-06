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
package version.resolution

import boilerplate.nullable.*

import java.nio.charset.StandardCharsets

import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** Detached-signature helper backed by the system `gpg` binary.
  *
  * Shared by both [[GitRepository]] backends so the signing crypto follows a single path. The subprocess inherits the
  * current process environment - notably `GNUPGHOME` - mirroring git's own gpg invocation, and reads the object payload
  * from stdin to EOF before emitting, so the write-then-read exchange below cannot deadlock for the small buffers
  * involved.
  */
private[resolution] object GpgSigner:

  /** The gpg executable. `VERSION_GPG` overrides the default `gpg` so environments where a bare `gpg` resolves to the
    * wrong build - notably Windows CI, where Git ships an MSYS2 gpg that mishandles native paths - can pin a specific
    * binary.
    */
  private val gpgProgram: String = sys.env.get("VERSION_GPG").filter(_.nonEmpty).getOrElse("gpg")

  /** Produces an ASCII-armoured detached signature over `payload`, signed with the gpg identity `keyId`.
    *
    * Trailing whitespace is stripped from the returned block: libgit2's `git_commit_create_with_signature` and JGit's
    * commit header folding both append a continuation line per embedded newline, so a trailing newline would leave a
    * stray blank header line; tags append the block with an explicit terminating newline at the call site instead.
    */
  def sign(payload: Array[Byte], keyId: String): Either[GitError, String] =
    val command = Seq(gpgProgram, "--batch", "--no-tty", "--armor", "--detach-sign", "--local-user", keyId)
    try
      val builder = new ProcessBuilder(command.asJava)
      builder.redirectError(ProcessBuilder.Redirect.INHERIT): Unit
      val process = builder.start().unsafe
      val stdin = process.getOutputStream.unsafe
      stdin.write(payload)
      stdin.flush()
      stdin.close()
      val output = process.getInputStream.unsafe.readAllBytes().unsafe
      val exit = process.waitFor()
      if exit != 0 then Left(GitError.SigningFailure(s"gpg signing failed (exit code $exit)"))
      else
        val armoured = trimTrailing(new String(output, StandardCharsets.UTF_8))
        if armoured.isEmpty then Left(GitError.SigningFailure("gpg produced an empty signature"))
        else Right(armoured)
    catch case NonFatal(e) => Left(GitError.SigningFailure(s"gpg invocation failed: ${describe(e)}"))

  private def trimTrailing(s: String): String =
    s.substring(0, s.lastIndexWhere(!_.isWhitespace) + 1)

  private def describe(e: Throwable): String =
    e.getMessage.getOrElse("unknown error")
end GpgSigner
