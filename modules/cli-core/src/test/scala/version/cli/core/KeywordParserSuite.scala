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
package version.cli.core

import munit.FunSuite

import version.MajorVersion.value
import version.MinorVersion.value
import version.PatchNumber.value
import version.cli.core.domain.Keyword.*
import version.cli.core.parsing.KeywordParser
import version.{*, given}

final class KeywordParserSuite extends FunSuite:

  test("version: relative increments (bump tokens and synonyms)") {
    val msg =
      """version: major
        |version: breaking
        |version: minor
        |version: feature
        |version: feat
        |version: patch
        |version: fix
        |""".stripMargin
    val ks = KeywordParser.parse(msg)
    // Major increment from both major and breaking
    assertEquals(ks.count(_ == MajorChange), 2)
    // Minor increment from minor, feature, and feat
    assertEquals(ks.count(_ == MinorChange), 3)
    // Patch relative increments (version: patch, version: fix) are ignored (patch is default)
    // No PatchChange should be emitted
  }

  test("standalone shorthands require non-empty text after colon") {
    val msg =
      """breaking: Remove deprecated API
        |major: Introduce new module
        |feat: Add caching
        |feature: Add endpoint
        |minor: Extend options
        |fix: Handle null
        |patch: Correct typo
        |breaking:
        |fix:
        |major:
        |feat:
        |""".stripMargin
    val ks = KeywordParser.parse(msg)
    // Valid shorthands: major/breaking → MajorChange, feat/feature/minor → MinorChange
    // fix/patch shorthands are recognised but ignored (patch is default behaviour)
    assertEquals(ks.count(_ == MajorChange), 2)
    assertEquals(ks.count(_ == MinorChange), 3) // feat, feature, minor
    // Invalid bare shorthands (no text) should NOT produce keywords
    // Total should be 5 (2 Major + 3 Minor), not 7
    assertEquals(ks.size, 5)
  }

  test("legacy change: keyword is NOT supported") {
    val msg =
      """change: major
        |change: minor
        |change: patch
        |change: breaking
        |""".stripMargin
    val ks = KeywordParser.parse(msg)
    // The change: keyword is no longer recognised
    assertEquals(ks.size, 0)
  }

  test("version: component: N absolute sets; overflow-safe int parsing") {
    val msg =
      s"""version: major: 2
         |version: minor: 10
         |version: patch: 5
         |version: breaking: 3
         |version: feature: 7
         |version: feat: 8
         |version: fix: 9
         |version: major: ${Int.MaxValue}
         |version: patch: 999999999999999999999999
         |""".stripMargin
    val ks = KeywordParser.parse(msg)
    assert(ks.exists { case MajorSet(v) => v.value == 2; case _ => false })
    assert(ks.exists { case MajorSet(v) => v.value == 3; case _ => false }) // from breaking synonym
    assert(ks.exists { case MinorSet(v) => v.value == 10; case _ => false })
    assert(ks.exists { case MinorSet(v) => v.value == 7; case _ => false }) // from feature synonym
    assert(ks.exists { case MinorSet(v) => v.value == 8; case _ => false }) // from feat synonym
    assert(ks.exists { case PatchSet(v) => v.value == 5; case _ => false })
    assert(ks.exists { case PatchSet(v) => v.value == 9; case _ => false }) // from fix synonym
    assert(ks.exists { case MajorSet(v) => v.value == Int.MaxValue; case _ => false })
    // Overflow should be ignored (no PatchSet for the huge number)
    assert(!ks.exists { case PatchSet(v) if v.value > 1000000 => true; case _ => false })
  }

  test("version: ignore directive") {
    val msg =
      """version: ignore
        |version: IGNORE
        |Version: Ignore
        |""".stripMargin
    val ks = KeywordParser.parse(msg)
    assertEquals(ks.count(_ == IgnoreSelf), 3)
  }

  test("version: ignore with SHA list") {
    val msg =
      """version: ignore: abc1234
        |version: ignore: def5678, abc1234567890abcd
        |version: ignore: 1234567
        |""".stripMargin
    val ks = KeywordParser.parse(msg)
    val ignoreCommits = ks.collect { case IgnoreCommits(shas) => shas }
    assertEquals(ignoreCommits.size, 3)
    assert(ignoreCommits.exists(_.contains("abc1234")))
    assert(ignoreCommits.exists(s => s.contains("def5678") && s.contains("abc1234567890abcd")))
    assert(ignoreCommits.exists(_.contains("1234567")))
  }

  test("version: ignore with SHA range") {
    val msg = "version: ignore: abc1234..def5678"
    val ks = KeywordParser.parse(msg)
    val ranges = ks.collect { case IgnoreRange(from, to) => (from, to) }
    assertEquals(ranges.size, 1)
    assertEquals(ranges.head, ("abc1234", "def5678"))
  }

  test("version: ignore-merged directive") {
    val msg =
      """version: ignore-merged
        |Version: IGNORE-MERGED
        |version:ignore-merged
        |""".stripMargin
    val ks = KeywordParser.parse(msg)
    // All 3 forms should match (case-insensitive, with/without space after colon)
    assertEquals(ks.count(_ == IgnoreMerged), 3)
  }

  test("version: ignore with invalid SHA (too short) is silently ignored") {
    val msg = "version: ignore: abc"
    val ks = KeywordParser.parse(msg)
    // Per specification: "Invalid SHA references are silently ignored"
    // Should NOT produce any ignore directive
    assertEquals(ks.size, 0)
    assertEquals(ks.count(_ == IgnoreSelf), 0)
    assertEquals(ks.collect { case IgnoreCommits(_) => () }.size, 0)
  }

  test("version: ignore with non-hex characters in SHA is silently ignored") {
    val msg = "version: ignore: xyz1234"
    val ks = KeywordParser.parse(msg)
    // 'x', 'y', 'z' are not valid hex characters
    assertEquals(ks.size, 0)
  }

  test("version: ignore with incomplete range is silently ignored") {
    val msg = "version: ignore: abc1234.."
    val ks = KeywordParser.parse(msg)
    // Incomplete range (no 'to' SHA)
    assertEquals(ks.size, 0)
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
