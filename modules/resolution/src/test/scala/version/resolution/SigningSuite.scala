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

import munit.FunSuite

import java.nio.file.Files
import java.nio.file.Path

import scala.util.control.NonFatal

import version.resolution.domain.AuthorSignature
import version.testkit.Filesystem
import version.testkit.GpgKeyring
import version.testkit.Process

/** Shared signing tests: exercise the GPG-backed createTag/createCommit paths end to end against a throwaway key.
  *
  * The keyring is provided by [[version.testkit.GpgKeyring GpgKeyring]] (driven by the build's `GNUPGHOME`). When
  * `GNUPGHOME` is absent - an IDE run without that setting - the suite registers a single ignored placeholder, so signing
  * gates CI without breaking ad-hoc local runs.
  */
abstract class SigningSuite extends FunSuite, GitRepositoryTestSupport:

  private val gpgHome: Option[String] = GpgKeyring.home

  private val signerIdentity = AuthorSignature("Version Signing Test", "signing@version.test", 1700000000L, 0)

  /** Fingerprint of the throwaway signing key, generated on first use in the configured GNUPGHOME. */
  private lazy val keyFingerprint: String = GpgKeyring.prepare(gpgHome.get)

  override def afterAll(): Unit = gpgHome.foreach(GpgKeyring.killAgent)

  gpgHome match
    case None =>
      test("signing tests skipped - GNUPGHOME not configured".ignore)(())
    case Some(_) =>
      test("createTag with sign=true produces a git-verifiable signed tag"):
        withRepo("signed-tag"): repo =>
          git(repo, "config", "user.signingkey", keyFingerprint): Unit
          val gr = openTestRepository(repo)
          try
            val target = gr.head.toOption.flatten.getOrElse(fail("no HEAD"))
            val result = gr.createTag("v1.2.3", target, "Release 1.2.3", signerIdentity, sign = true)
            assert(result.isRight, clues(result))
            val obj = git(repo, "cat-file", "tag", "v1.2.3")
            assert(obj.contains("-----BEGIN PGP SIGNATURE-----"), clues(obj))
            val verify = Process.run(Seq("git", "verify-tag", "v1.2.3"), repo)
            assert(verify.successful, clues(verify.stderr, obj))
          finally gr.close()

      test("createCommit with sign=true produces a git-verifiable signed commit"):
        withRepo("signed-commit"): repo =>
          git(repo, "config", "user.signingkey", keyFingerprint): Unit
          val gr = openTestRepository(repo)
          try
            val parent = gr.head.toOption.flatten.getOrElse(fail("no HEAD"))
            val result = gr.createCommit("version: minor", signerIdentity, sign = true)
            assert(result.isRight, clues(result))
            val sha = result.toOption.get
            assertEquals(gr.head.toOption.flatten, Some(sha))
            assertEquals(gr.loadCommit(sha).toOption.map(_.parentIds.map(_.value).toList), Some(List(parent.value)))
            val obj = git(repo, "cat-file", "commit", sha.value)
            assert(obj.contains("gpgsig") && obj.contains("-----BEGIN PGP SIGNATURE-----"), clues(obj))
            val verify = Process.run(Seq("git", "verify-commit", sha.value), repo)
            assert(verify.successful, clues(verify.stderr, obj))
          finally gr.close()
  end match

  private def withRepo[A](name: String)(f: Path => A): A =
    val tmp = Files.createTempDirectory(s"version-sign-$name-")
    try
      initMinimalRepo(tmp)
      f(tmp)
    finally
      try Filesystem.removeRecursive(tmp)
      catch case NonFatal(_) => ()

end SigningSuite
