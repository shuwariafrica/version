package version.cli.core

import munit.FunSuite

import version.*
import version.MajorVersion.value
import version.MinorVersion.value
import version.PatchNumber.value
import version.cli.core.domain.Keyword.*
import version.cli.core.parsing.KeywordParser

final class KeywordParserEdgeSuite extends FunSuite:

  given PreRelease.Resolver = PreRelease.Resolver.default

  test("Whitespace and case-insensitive parsing, token boundaries enforced") {
    val msg =
      """  Change :   MAJOR
        |feature: new
        |FIX : bug
        |prechange: minor
        |changeX: patch
        |version:   minor :   7
        |version: patch: 00012
        |version: major: -1
        |target: V1.2.3+meta
        |target: not-a-version
        |""".stripMargin
    val ks = KeywordParser.parse(msg)
    assert(ks.contains(MajorChange))
    assert(ks.contains(MinorChange))
    assert(ks.contains(PatchChange))
    assert(ks.exists { case MinorSet(v) => v.value == 7; case _ => false })
    assert(ks.exists { case PatchSet(v) => v.value == 12; case _ => false })
    // Negative absolute ignored
    assert(!ks.exists { case MajorSet(v) if v.value < 0 => true; case _ => false })
    // Token boundaries: prechange/changeX do not match
    val relCount = ks.count { case MajorChange | MinorChange | PatchChange => true; case _ => false }
    assertEquals(relCount, 3)
    // target valid + invalid; only one valid captured
    val targets = ks.collect { case TargetSet(v) => v }
    assert(targets.exists(v => v.major.value == 1 && v.minor.value == 2 && v.patch.value == 3))
  }
end KeywordParserEdgeSuite
