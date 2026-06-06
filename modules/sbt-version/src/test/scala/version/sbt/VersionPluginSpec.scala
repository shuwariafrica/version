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
package version.sbt

import munit.FunSuite
import sbt.util.Logger

import java.nio.file.Files

import version.ResolvableScheme
import version.resolution.ResolutionConfig
import version.resolution.ResolutionMode
import version.resolution.domain.CiProvider
import version.sbt.VersionPlugin.internal
import version.semver.SemVer
import version.testkit.Filesystem

class VersionPluginSpec extends FunSuite:

  /** Silent logger for tests -- expected fallback messages should not pollute test output. */
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

  test("resolveResult returns the scheme's empty-metadata development version when not in a Git repository") {
    val repo = Files.createTempDirectory("version-plugin-resolve-")
    try
      val cfg = ResolutionConfig.default[SemVer](repo.toString)
      val scheme = summon[ResolvableScheme[SemVer]]
      val result = internal.resolveResult(cfg, testLogger, scheme)
      assertEquals(result.resolved.show, "0.1.0-SNAPSHOT")
      assertEquals(result.target.show, "0.1.0")
      assertEquals(result.mode, ResolutionMode.Development)
    finally Filesystem.removeRecursive(repo)
  }

  test("resolveResult handles non-existent paths gracefully") {
    val nonExistentRepo = Files.createTempDirectory("version-plugin-").resolve("does-not-exist")
    val cfg = ResolutionConfig.default[SemVer](nonExistentRepo.toString)
    val scheme = summon[ResolvableScheme[SemVer]]
    val result = internal.resolveResult(cfg, testLogger, scheme)
    assertEquals(result.resolved.show, "0.1.0-SNAPSHOT")
    assertEquals(result.target.show, "0.1.0")
  }

end VersionPluginSpec
