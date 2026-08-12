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

import version.resolution.domain.CiProvider
import version.sbt.VersionPlugin.internal
import version.testkit.Filesystem

class VersionPluginSpec extends FunSuite:

  // The fallback paths log by design; silencing keeps expected messages out of the test output.
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

  private def resolvedAt(path: String): internal.VersionResult[?] =
    internal.resolve(internal.defaultResolver, None, path, testLogger)

  private def target(result: internal.VersionResult[?]): String = result match
    case r: internal.VersionResult[v] => r.scheme.show(r.target)

  test("a directory in no repository falls back to the scheme's empty-metadata development version") {
    val outside = Files.createTempDirectory("version-plugin-resolve-")
    try
      val result = resolvedAt(outside.toString)
      assertEquals(internal.render(result), "0.1.0-SNAPSHOT")
      assertEquals(target(result), "0.1.0")
    finally Filesystem.removeRecursive(outside)
  }

  test("a path that does not exist falls back rather than failing") {
    val result = resolvedAt(Files.createTempDirectory("version-plugin-").resolve("does-not-exist").toString)
    assertEquals(internal.render(result), "0.1.0-SNAPSHOT")
    assertEquals(target(result), "0.1.0")
  }

  test("the published version drops build metadata that the carrier's own rendering keeps") {
    val outside = Files.createTempDirectory("version-plugin-rendering-")
    try
      val result = resolvedAt(outside.toString)
      assert(!internal.render(result).contains("+"), clue(internal.render(result)))
      result match
        case r: internal.VersionResult[v] => assertEquals(r.scheme.show(r.value), "0.1.0-SNAPSHOT+detached")
    finally Filesystem.removeRecursive(outside)
  }

end VersionPluginSpec
