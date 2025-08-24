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
    val major = MajorVersion.unsafe(5)
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
    val pre = PreRelease.alpha(PreReleaseNumber.unsafe(2))
    val yaml =
      """|classifier: alpha
         |number: 2
         |""".stripMargin
    // Using assertNoDiff to be robust against trailing newlines
    assertNoDiff(normalizeYaml(pre.asYaml), normalizeYaml(yaml))
    assertEquals(yaml.as[PreRelease], Right(pre))
  }

  test("PreRelease YAML codec should fail for snapshot with a number") {
    val invalidYaml =
      """|classifier: snapshot
         |number: 1
         |""".stripMargin
    val result = invalidYaml.as[PreRelease]
    assert(result.isLeft)
    result.left.foreach { error =>
      assert(error.msg.contains("cannot have a pre-release number"))
    }
  }

  test("PreRelease YAML codec should fail for a numbered classifier without a number") {
    val invalidYaml =
      """|classifier: rc
         |number: !!null
         |""".stripMargin
    val result = invalidYaml.as[PreRelease]
    assert(result.isLeft)
    result.left.foreach { error =>
      assert(error.msg.contains("requires a pre-release number"))
    }
  }

  test("Version YAML codec should succeed for a stable version") {
    val v = Version(MajorVersion.unsafe(1), MinorVersion.unsafe(2), PatchNumber.unsafe(3))
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
    val v = Version(MajorVersion.unsafe(0), MinorVersion.unsafe(1), PatchNumber.unsafe(0), Some(PreRelease.snapshot))
    val yaml =
      """|preRelease:
         |  classifier: snapshot
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
