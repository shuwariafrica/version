package version.cli.core

import munit.FunSuite

import version.*
import version.MajorVersion.value
import version.MinorVersion.value
import version.PatchNumber.value
import version.cli.core.domain.Keyword.*
import version.cli.core.parsing.KeywordParser

final class KeywordParserSuite extends FunSuite:

  given PreRelease.Resolver = PreRelease.Resolver.default

  test("change: major|breaking; minor|feature; patch|fix (case-insensitive, whitespace tolerant)") {
    val msg =
      """BREAKING:   MAJOR
        |change:minor
        |change:   fix
        |noise changeX: major
        |rechange: minor
        |""".stripMargin
    val ks = KeywordParser.parse(msg)
    assert(ks.contains(MajorChange))
    assert(ks.contains(MinorChange))
    assert(ks.contains(PatchChange))
    // Ensure boundary: 'changeX' or 'rechange' do not match
    assertEquals(
      ks.count {
        case MajorChange | MinorChange | PatchChange => true
        case _                                       => false
      },
      3)
  }

  test("version: component: N absolute sets; overflow-safe int parsing") {
    val msg =
      s"""version: major: 2
         |version: minor: 10
         |version: patch: 5
         |version: major: ${Int.MaxValue}
         |version: patch: 999999999999999999999999
         |""".stripMargin
    val ks = KeywordParser.parse(msg)
    assert(ks.exists { case MajorSet(v) => v.value == 2 || v.value == Int.MaxValue; case _ => false })
    assert(ks.exists { case MinorSet(v) => v.value == 10; case _ => false })
    assert(ks.exists { case PatchSet(v) => v.value == 5; case _ => false })
    // Overflow should be ignored (no PatchSet for the huge number)
    assert(!ks.exists { case PatchSet(v) if v.value > 1000000 => true; case _ => false })
  }

  test("target: vX.Y.Z[-pre][+meta] parses; stores full version; selection later drops pre/meta") {
    val msg =
      """target: v3.2.0-beta.1+meta
        |target: 1.2.3
        |retarget: 9.9.9
        |""".stripMargin
    val ks = KeywordParser.parse(msg)
    val targets = ks.collect { case TargetSet(v) => v }
    assert(targets.exists(v => v.major.value == 3 && v.minor.value == 2 && v.patch.value == 0 && v.preRelease.nonEmpty))
    assert(targets.exists(v => v.major.value == 1 && v.minor.value == 2 && v.patch.value == 3 && v.preRelease.isEmpty))
    // 'retarget' must not match
    assertEquals(targets.size, 2)
  }
end KeywordParserSuite
