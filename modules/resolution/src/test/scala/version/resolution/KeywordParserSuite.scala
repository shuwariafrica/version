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
package version.resolution

import munit.FunSuite

import version.resolution.domain.Keyword
import version.resolution.domain.Keyword.*
import version.resolution.parsing.KeywordParser
import version.semver.*

/** Unit tests for [[KeywordParser]] using the SemVer scheme's keyword aliases. */
class KeywordParserSuite extends FunSuite:

  private def parse(msg: String): List[Keyword] = KeywordParser.parse[SemVer](msg)

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

  test("version: major: 3 produces ComponentSet(0, 3)"):
    assertEquals(parse("version: major: 3"), List(ComponentSet(0, 3)))

  test("version: minor: 5 produces ComponentSet(1, 5)"):
    assertEquals(parse("version: minor: 5"), List(ComponentSet(1, 5)))

  test("version: patch: 2 produces ComponentSet(2, 2)"):
    assertEquals(parse("version: patch: 2"), List(ComponentSet(2, 2)))

  test("version: fix: 7 produces ComponentSet(2, 7)"):
    assertEquals(parse("version: fix: 7"), List(ComponentSet(2, 7)))

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

  test("target: 2.0.0 produces TargetSet"):
    assertEquals(parse("target: 2.0.0"), List(TargetSet("2.0.0")))

  test("target: v2.0.0 produces TargetSet with v prefix"):
    assertEquals(parse("target: v2.0.0"), List(TargetSet("v2.0.0")))

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

  test("keywords are case-insensitive"):
    assertEquals(parse("VERSION: MAJOR"), List(ComponentBump(0)))
    assertEquals(parse("Version: Minor"), List(ComponentBump(1)))

  test("keywords must be word-boundary aligned"):
    assertEquals(parse("reversion: 1.0.0"), Nil)
    assertEquals(parse("retarget: 2.0.0"), Nil)

  test("multiple keywords across lines"):
    val msg = "version: major\nversion: minor"
    val result = parse(msg)
    assert(result.contains(ComponentBump(0)))
    assert(result.contains(ComponentBump(1)))

  test("unrecognised word after version: is silently ignored"):
    assertEquals(parse("version: majorx"), Nil)

  test("negative absolute value is silently ignored"):
    assertEquals(parse("version: major: -1"), Nil)

  test("[breaking] produces ComponentBump(0)"):
    assertEquals(parse("[breaking] Remove deprecated API"), List(ComponentBump(0)))

  test("[feat] produces ComponentBump(1)"):
    assertEquals(parse("[feat] add caching"), List(ComponentBump(1)))

  test("[major] bare bracketed keyword produces ComponentBump(0)"):
    assertEquals(parse("[major]"), List(ComponentBump(0)))

  test("[minor] produces ComponentBump(1)"):
    assertEquals(parse("[minor] add helper"), List(ComponentBump(1)))

  test("[fix] is a no-op (fix-role default)"):
    assertEquals(parse("[fix] correct typo"), Nil)

  test("[ignore] produces IgnoreSelf"):
    assertEquals(parse("[ignore] docs only"), List(IgnoreSelf))

  test("[ignore-merged] produces IgnoreMerged"):
    assertEquals(parse("[ignore-merged]"), List(IgnoreMerged))

  test("bracketed inner whitespace is tolerated"):
    assertEquals(parse("[ breaking ]"), List(ComponentBump(0)))

  test("[breaking] is case-insensitive"):
    assertEquals(parse("[BREAKING]"), List(ComponentBump(0)))

  test("[version: major] yields exactly one keyword (no double-count)"):
    assertEquals(parse("[version: major]"), List(ComponentBump(0)))

  test("[version: major: 3] yields exactly one ComponentSet (no double-count)"):
    assertEquals(parse("[version: major: 3]"), List(ComponentSet(0, 3)))

  test("[target: 2.0.0] yields exactly one TargetSet (no double-count)"):
    assertEquals(parse("[target: 2.0.0]"), List(TargetSet("2.0.0")))

  test("bracketed prose and non-keywords are ignored"):
    assertEquals(parse("[skip ci]"), Nil)
    assertEquals(parse("[ci skip]"), Nil)
    assertEquals(parse("[WIP]"), Nil)
    assertEquals(parse("[JIRA-123] fix login"), Nil)
    assertEquals(parse("[major refactor]"), Nil)

  test("unterminated bracket is ignored"):
    assertEquals(parse("[breaking but no close"), Nil)

  test("embedded brackets are not matched (boundary alignment)"):
    assertEquals(parse("somebracketin[breaking]inline"), Nil)
    assertEquals(parse("foo[breaking]"), Nil)
    assertEquals(parse("[breaking]bar"), Nil)

  test("bracket directive permits a non-word char after the close"):
    assertEquals(parse("Remove old API [breaking]."), List(ComponentBump(0)))
    assertEquals(parse("[breaking], and more"), List(ComponentBump(0)))
    assertEquals(parse("done [feat]"), List(ComponentBump(1)))

  test("standalone shorthand is boundary-aligned, not a substring"):
    assertEquals(parse("prefixbreaking: text"), Nil)

  test("[core][breaking] fires the second bracket"):
    assertEquals(parse("[core][breaking] Text"), List(ComponentBump(0)))

  test("adjacent brackets each fire"):
    assertEquals(parse("[major][minor]"), List(ComponentBump(0), ComponentBump(1)))
    assertEquals(parse("[fix][breaking]"), List(ComponentBump(0)))
    assertEquals(parse("[ignore][feat]"), List(IgnoreSelf, ComponentBump(1)))

  test("empty or whitespace bracket then a directive"):
    assertEquals(parse("[][breaking]"), List(ComponentBump(0)))
    assertEquals(parse("[ ][breaking]"), List(ComponentBump(0)))

  test("stray brackets before, after, or doubled"):
    assertEquals(parse("][breaking]"), List(ComponentBump(0)))
    assertEquals(parse("[breaking]["), List(ComponentBump(0)))
    assertEquals(parse("[major]]"), List(ComponentBump(0)))

  test("a shorthand inside a bracket fires once"):
    assertEquals(parse("[breaking: drop the legacy API]"), List(ComponentBump(0)))
    assertEquals(parse("[feat: add caching]"), List(ComponentBump(1)))

  test("a bracket wrapping a mid-content directive is opaque"):
    assertEquals(parse("[see version: major]"), Nil)
    assertEquals(parse("[foo version: major]"), Nil)

  test("a bracket led by a directive fires, ignoring trailing prose"):
    assertEquals(parse("[version: major rollout]"), List(ComponentBump(0)))

  test("a directive after an opaque bracket still fires"):
    assertEquals(parse("[foo] version: major"), List(ComponentBump(0)))

  test("a word after the close defeats self-containment"):
    assertEquals(parse("[see version: major]x"), List(ComponentBump(0)))

  test("hyphen-glued brackets are not directives"):
    assertEquals(parse("-[breaking]"), Nil)
    assertEquals(parse("[breaking]-x"), Nil)

  test("a non-word, non-hyphen neighbour after the close is permitted"):
    assertEquals(parse("[breaking].more"), List(ComponentBump(0)))

  test("non-ASCII in the content is not a keyword; after the close is tolerated"):
    assertEquals(parse("[breáking]"), Nil)
    assertEquals(parse("[breaking] 修正"), List(ComponentBump(0)))

  test("a trailing carriage return does not block a bracket"):
    assertEquals(parse("[breaking]\r"), List(ComponentBump(0)))
end KeywordParserSuite
