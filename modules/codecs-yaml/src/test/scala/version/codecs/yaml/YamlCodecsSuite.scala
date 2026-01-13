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
package version.codecs.yaml

import org.virtuslab.yaml.*

import version.*
import version.codecs.yaml.given

class YamlCodecsSuite extends munit.FunSuite:

  // Normalize trailing whitespace on each line to avoid brittle diffs due to emitter formatting
  private def normalizeYaml(s: String): String =
    // 1) Remove spaces/tabs immediately before a newline (works across all JS/JVM targets)
    val noTrailBeforeNl = s.replaceAll("([ \t]+)(\\r?\\n)", "$2")
    // 2) Remove trailing spaces/tabs at the very end of the string
    noTrailBeforeNl.replaceAll("[ \t]+$", "")

  test("MajorVersion YAML codec should succeed for valid input") {
    val major = MajorVersion.fromUnsafe(5)
    val yaml = "5"
    assertNoDiff(normalizeYaml(major.asYaml), normalizeYaml(yaml))
    assertEquals(yaml.as[MajorVersion], Right(major))
  }

  test("MajorVersion YAML codec should fail for invalid input") {
    val result = "-1".as[MajorVersion]
    assert(result.isLeft, s"Expected failure but got $result")
    result.swap.foreach(error => assert(error.msg.contains("Major Version must be a non-negative number")))
  }

  test("PreRelease YAML codec should succeed for valid input") {
    val pre = PreRelease.alpha(PreReleaseNumber.fromUnsafe(2))
    val yaml =
      """|classifier: alpha
         |number: 2
         |""".stripMargin
    // Using assertNoDiff to be robust against trailing newlines
    assertNoDiff(normalizeYaml(pre.asYaml), normalizeYaml(yaml))
    assertEquals(yaml.as[PreRelease], Right(pre))
  }

  test("PreRelease YAML codec should fail for snapshot with a number") {
    val yaml =
      """|classifier: snapshot
         |number: 1
         |""".stripMargin
    val result = yaml.as[PreRelease]
    assert(result.isLeft, s"Expected failure for snapshot with number but got $result")
  }

  test("PreRelease YAML codec should fail for a numbered classifier without a number") {
    val yaml =
      """|classifier: rc
         |""".stripMargin
    val result = yaml.as[PreRelease]
    assert(result.isLeft, s"Expected failure for RC without number but got $result")
  }

  test("Version YAML codec should succeed for a stable version") {
    val v = Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(2), PatchNumber.fromUnsafe(3))
    val yaml =
      """|preRelease: !!null
         |buildMetadata: !!null
         |major: 1
         |patch: 3
         |minor: 2
         |""".stripMargin
    assertNoDiff(normalizeYaml(v.asYaml), normalizeYaml(yaml))
    assertEquals(yaml.as[Version], Right(v))
  }

  test("Version YAML codec should succeed for a pre-release version") {
    val v = Version(MajorVersion.fromUnsafe(0), MinorVersion.fromUnsafe(1), PatchNumber.fromUnsafe(0), Some(PreRelease.snapshot))
    val yaml =
      """|preRelease:
         |  classifier: SNAPSHOT
         |  number: !!null
         |buildMetadata: !!null
         |major: 0
         |patch: 0
         |minor: 1
         |""".stripMargin
    assertNoDiff(normalizeYaml(v.asYaml), normalizeYaml(yaml))
    assertEquals(yaml.as[Version], Right(v))
  }

end YamlCodecsSuite
