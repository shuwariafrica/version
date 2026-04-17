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
package version.resolution

import munit.FunSuite

import version.resolution.domain.Keyword
import version.resolution.domain.Keyword.*
import version.resolution.parsing.KeywordParser
import version.semver.*

/** Unit tests for [[KeywordParser]] using the SemVer scheme's keyword aliases. */
class KeywordParserSuite extends FunSuite:

  private def parse(msg: String): List[Keyword] = KeywordParser.parse[SemVer](msg)

  // --- Relative increments ---

  test("version: major produces ComponentBump(0)"):
    assertEquals(parse("version: major"), List(ComponentBump(0)))

  test("version: breaking produces ComponentBump(0)"):
    assertEquals(parse("version: breaking"), List(ComponentBump(0)))

  test("version: minor produces ComponentBump(1)"):
    assertEquals(parse("version: minor"), List(ComponentBump(1)))

  test("version: feature produces ComponentBump(1)"):
    assertEquals(parse("version: feature"), List(ComponentBump(1)))

  test("version: feat produces ComponentBump(1)"):
    assertEquals(parse("version: feat"), List(ComponentBump(1)))

  test("version: patch is a no-op (fix-role default)"):
    assertEquals(parse("version: patch"), Nil)

  test("version: fix is a no-op (fix-role default)"):
    assertEquals(parse("version: fix"), Nil)

  // --- Absolute sets ---

  test("version: major: 3 produces ComponentSet(0, 3)"):
    assertEquals(parse("version: major: 3"), List(ComponentSet(0, 3)))

  test("version: minor: 5 produces ComponentSet(1, 5)"):
    assertEquals(parse("version: minor: 5"), List(ComponentSet(1, 5)))

  test("version: patch: 2 produces ComponentSet(2, 2)"):
    assertEquals(parse("version: patch: 2"), List(ComponentSet(2, 2)))

  test("version: fix: 7 produces ComponentSet(2, 7)"):
    assertEquals(parse("version: fix: 7"), List(ComponentSet(2, 7)))

  // --- Standalone shorthands ---

  test("breaking: text produces ComponentBump(0)"):
    assertEquals(parse("breaking: Remove deprecated API"), List(ComponentBump(0)))

  test("feat: text produces ComponentBump(1)"):
    assertEquals(parse("feat: Add caching support"), List(ComponentBump(1)))

  test("feature: text produces ComponentBump(1)"):
    assertEquals(parse("feature: New feature"), List(ComponentBump(1)))

  test("fix: text is a no-op (fix-role default)"):
    assertEquals(parse("fix: Handle edge case"), Nil)

  test("standalone shorthand requires non-empty text"):
    assertEquals(parse("breaking:"), Nil)
    assertEquals(parse("feat:"), Nil)

  // --- Target directive ---

  test("target: 2.0.0 produces TargetSet"):
    assertEquals(parse("target: 2.0.0"), List(TargetSet("2.0.0")))

  test("target: v2.0.0 produces TargetSet with v prefix"):
    assertEquals(parse("target: v2.0.0"), List(TargetSet("v2.0.0")))

  // --- Ignore directives ---

  test("version: ignore produces IgnoreSelf"):
    assertEquals(parse("version: ignore"), List(IgnoreSelf))

  test("version: ignore-merged produces IgnoreMerged"):
    assertEquals(parse("version: ignore-merged"), List(IgnoreMerged))

  test("version: ignore: <sha> produces IgnoreCommits"):
    val result = parse("version: ignore: abc1234")
    assertEquals(result.length, 1)
    result.head match
      case IgnoreCommits(shas) => assert(shas.contains("abc1234"))
      case other               => fail(s"Expected IgnoreCommits, got $other")

  test("version: ignore: <sha>..<sha> produces IgnoreRange"):
    val result = parse("version: ignore: abc1234..def5678")
    assertEquals(result, List(IgnoreRange("abc1234", "def5678")))

  // --- Case insensitivity ---

  test("keywords are case-insensitive"):
    assertEquals(parse("VERSION: MAJOR"), List(ComponentBump(0)))
    assertEquals(parse("Version: Minor"), List(ComponentBump(1)))

  // --- Word boundary alignment ---

  test("keywords must be word-boundary aligned"):
    assertEquals(parse("reversion: 1.0.0"), Nil)
    assertEquals(parse("retarget: 2.0.0"), Nil)

  // --- Multiple keywords per message ---

  test("multiple keywords across lines"):
    val msg = "version: major\nversion: minor"
    val result = parse(msg)
    assert(result.contains(ComponentBump(0)))
    assert(result.contains(ComponentBump(1)))

  // --- Unrecognised words ---

  test("unrecognised word after version: is silently ignored"):
    assertEquals(parse("version: majorx"), Nil)

  // --- Negative absolute values ---

  test("negative absolute value is silently ignored"):
    assertEquals(parse("version: major: -1"), Nil)
end KeywordParserSuite
