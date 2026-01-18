/****************************************************************************
 * Copyright 2023 Shuwari Africa Ltd.                                       *
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
package version.sbt

import munit.FunSuite
import sbt.util.Logger

import version.PreRelease
import version.Version
import version.cli.core.domain.CiProvider
import version.cli.core.domain.CliConfig
import version.sbt.VersionPlugin.internal

class VersionPluginSpec extends FunSuite:

  /** Silent logger for tests — expected fallback messages should not pollute test output. */
  private val testLogger: Logger = Logger.Null

  test("detectCiMetadata recognises GitHub Actions environment") {
    val env = Map(
      "GITHUB_ACTIONS" -> "true",
      "GITHUB_REPOSITORY" -> "shuwari/version",
      "GITHUB_REPOSITORY_OWNER" -> "shuwari",
      "GITHUB_RUN_ID" -> "123",
      "GITHUB_RUN_NUMBER" -> "456",
      "GITHUB_REF_NAME" -> "main",
      "GITHUB_SHA" -> "abcdef1234567890",
      "GITHUB_SERVER_URL" -> "https://github.com"
    )

    val result = internal.detectCiMetadata(env)
    assert(result.nonEmpty, clue(result))
    assertEquals(result.map(_.provider), Some(CiProvider.GitHubActions))
    assertEquals(result.flatMap(_.branch), Some("main"))
  }

  test("detectCiMetadata returns None outside CI") {
    val env = Map.empty[String, String]
    assertEquals(internal.detectCiMetadata(env), None)
  }

  test("defaultVerbose honours VERSION_VERBOSE flag") {
    val enable = Map("VERSION_VERBOSE" -> "true")
    val disable = Map("VERSION_VERBOSE" -> "false")

    assertEquals(internal.defaultVerbose(enable), true)
    assertEquals(internal.defaultVerbose(disable), false)
    assertEquals(internal.defaultVerbose(Map.empty), false)
  }

  test("resolveVersion returns fallback version when not in a Git repository") {
    val repo = os.temp.dir(prefix = "version-plugin-resolve-")
    try
      val cfg = CliConfig(
        repo = repo,
        basisCommit = "HEAD",
        prNumber = None,
        branchOverride = None,
        shaLength = 12,
        verbose = false
      )
      val result = internal.resolveVersion(cfg, testLogger, Version.Read.ReadString, PreRelease.Resolver.given_Resolver)
      assertEquals(result, internal.fallbackVersion)
      assertEquals(result.show, "0.1.0-SNAPSHOT")
    finally os.remove.all(repo)
  }

  test("resolveVersion wraps other resolution failures in MessageOnlyException") {
    val nonExistentRepo = os.temp.dir(prefix = "version-plugin-") / "does-not-exist"
    val cfg = CliConfig(
      repo = nonExistentRepo,
      basisCommit = "HEAD",
      prNumber = None,
      branchOverride = None,
      shaLength = 12,
      verbose = false
    )
    // NotAGitRepository for non-existent path is still handled gracefully
    val result = internal.resolveVersion(cfg, testLogger, Version.Read.ReadString, PreRelease.Resolver.given_Resolver)
    assertEquals(result, internal.fallbackVersion)
  }

end VersionPluginSpec
