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

final class KeywordParserEdgeSuite extends FunSuite:

  test("Whitespace and case-insensitive parsing, token boundaries enforced") {
    val msg =
      """  Version :   MAJOR
        |version:minor
        |version:patch:3
        |feature: new functionality
        |feat: add caching
        |FIX : bug fix
        |prefixversion: minor
        |versionX: patch
        |version:   minor :   7
        |version: patch: 00012
        |version: major: -1
        |target: V1.2.3+meta
        |target: not-a-version
        |version: ignore
        |major: some breaking change
        |minor: some feature
        |patch: some fix
        |Major:breaking with no space
        |FEAT:feature with no space
        |FIX:fix with no space
        |""".stripMargin
    val ks = KeywordParser.parse(msg)
    // Colon-insensitivity: version:minor (no spaces) should work
    assert(ks.contains(MinorChange))
    // Colon-insensitivity: version:patch:3 (no spaces) absolute should work
    assert(ks.exists { case PatchSet(v) => v.value == 3; case _ => false })
    // Version :   MAJOR (spaces around colon) -> MajorChange
    assert(ks.contains(MajorChange))
    // feature: new (shorthand) + feat: add (shorthand) + minor: some feature (shorthand)
    // + FEAT:feature (shorthand, no space) + version:minor = 5 MinorChange
    assertEquals(ks.count(_ == MinorChange), 5)
    // FIX : bug fix + patch: some fix + FIX:fix with no space = 3 PatchChange
    assertEquals(ks.count(_ == PatchChange), 3)
    // version: minor: 7 (absolute, spaces around colons)
    assert(ks.exists { case MinorSet(v) => v.value == 7; case _ => false })
    // version: patch: 00012 (absolute, leading zeros are parsed as decimal)
    assert(ks.exists { case PatchSet(v) => v.value == 12; case _ => false })
    // Negative absolute ignored
    assert(!ks.exists { case MajorSet(v) if v.value < 0 => true; case _ => false })
    // Token boundaries: prefixversion/versionX do not match
    // version: ignore
    assert(ks.contains(Ignore))
    // major: some breaking change + Major:breaking with no space + Version :   MAJOR = 3 MajorChange
    assertEquals(ks.count(_ == MajorChange), 3)
    // target valid + invalid; only one valid captured
    val targets = ks.collect { case TargetSet(v) => v }
    assert(targets.exists(v => v.major.value == 1 && v.minor.value == 2 && v.patch.value == 3))
  }

  test("Standalone shorthand boundary cases") {
    val msg =
      """breaking: text
        |mybreaking: text
        |prebreaking: text
        |breaking:
        |breaking  :  text with spaces
        |""".stripMargin
    val ks = KeywordParser.parse(msg)
    // Only valid standalone shorthands should match
    // "breaking: text" and "breaking  :  text with spaces" are valid
    // "mybreaking:" and "prebreaking:" have prefixes - should not match
    // "breaking:" (bare) has no text - should not match
    assertEquals(ks.count(_ == MajorChange), 2)
  }

  test("Mixed version: directives") {
    val msg =
      """version: major
        |version: minor: 5
        |version: ignore
        |version: unknown
        |version: fix: 3
        |version: breaking: 2
        |""".stripMargin
    val ks = KeywordParser.parse(msg)
    assert(ks.contains(MajorChange))
    assert(ks.exists { case MinorSet(v) => v.value == 5; case _ => false })
    assert(ks.contains(Ignore))
    assert(ks.exists { case PatchSet(v) => v.value == 3; case _ => false })
    assert(ks.exists { case MajorSet(v) => v.value == 2; case _ => false })
    // version: unknown should be ignored silently
    assertEquals(ks.size, 5)
  }
end KeywordParserEdgeSuite
