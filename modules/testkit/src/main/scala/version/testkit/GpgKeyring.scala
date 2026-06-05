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
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions

import scala.util.control.NonFatal

/** Throwaway GnuPG keyring for signing tests.
  *
  * The keyring lives in the `GNUPGHOME` the build supplies via `Test / envVars`, pointed at a short, per-platform path so
  * gpg-agent's socket stays within its 108-character limit. When `GNUPGHOME` is absent (an IDE run without that setting)
  * [[home]] is `None` and signing tests register an ignored placeholder instead.
  */
object GpgKeyring:

  /** The configured `GNUPGHOME`, when present and non-empty. */
  def home: Option[String] = sys.env.get("GNUPGHOME").filter(_.nonEmpty)

  /** Wipes and recreates the keyring at `gnupgHome` (owner-only where POSIX permissions apply) and generates a throwaway
    * passphraseless ed25519 signing key, returning its fingerprint.
    */
  def prepare(gnupgHome: String): String =
    val path = Paths.get(gnupgHome)
    Filesystem.removeRecursive(path)
    Files.createDirectories(path)
    // gpg rejects a group/world-accessible home; restrict to the owner where the filesystem supports POSIX permissions.
    try Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------")): Unit
    catch case NonFatal(_) => ()
    generateKey(gnupgHome)

  /** Stops the gpg-agent for `gnupgHome`; best-effort, so a missing `gpgconf` does not fail a suite teardown. */
  def killAgent(gnupgHome: String): Unit =
    try Process.run(Seq("gpgconf", "--homedir", gnupgHome, "--kill", "gpg-agent"), Paths.get(gnupgHome)): Unit
    catch case NonFatal(_) => ()

  /** The gpg executable; `VERSION_GPG` pins a specific binary (e.g. a native gpg on Windows CI). */
  private def gpgProgram: String = sys.env.get("VERSION_GPG").filter(_.nonEmpty).getOrElse("gpg")

  private def generateKey(gnupgHome: String): String =
    val params =
      """%no-protection
        |Key-Type: eddsa
        |Key-Curve: ed25519
        |Key-Usage: sign
        |Name-Real: Version Signing Test
        |Name-Email: signing@version.test
        |Expire-Date: 0
        |%commit
        |""".stripMargin
    val paramFile = Files.createTempFile("version-gpg-", ".params")
    try
      Files.writeString(paramFile, params): Unit
      Process.runChecked(Seq(gpgProgram, "--homedir", gnupgHome, "--batch", "--gen-key", paramFile.toString), Paths.get(gnupgHome)): Unit
      val listed = Process.runChecked(Seq(gpgProgram, "--homedir", gnupgHome, "--list-secret-keys", "--with-colons"), Paths.get(gnupgHome))
      listed.linesIterator
        .find(_.startsWith("fpr:"))
        .map(_.split(":", -1)(9))
        .getOrElse(sys.error(s"no key fingerprint in gpg output:\n$listed"))
    finally Files.deleteIfExists(paramFile): Unit

end GpgKeyring
