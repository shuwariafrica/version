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
package version.semver

import munit.FunSuite

import version.errors.InvalidRangeFormat

class SemVerRangeParseSuite extends FunSuite:

  import RangeCorpus.range

  private def shown(text: String): String = SemVerRange.scheme.show(range(text))

  test("a canonically spelled range renders back exactly as it was written") {
    List(
      "^1.2.3",
      "~1.2.3",
      "1.2.3",
      "=1.2.3",
      ">=1.2.3",
      "<=1.2.3",
      "<1.2.3",
      ">1.2.3",
      "1.x",
      "1.X",
      "1.*",
      "1.2.x",
      "*",
      "x",
      "X",
      "^0.0.3",
      "~0.2",
      "^1.2.3-rc.1",
      "1.2.3 - 2.3.4",
      "1.2 - 2.3",
      ">=1.2.3 <2.0.0",
      "^1.0.0 || ^2.0.0",
      "1.2.7 || >=1.2.9 <2.0.0"
    ).foreach(text => assertEquals(shown(text), text, s"rendering of '$text'"))
  }

  test("every corpus range survives a rendering and a reading as an equal value") {
    RangeCorpus.lawRanges.foreach { text =>
      val parsed = range(text)
      assertEquals(SemVerRange.parse(SemVerRange.scheme.show(parsed)), Right(parsed), s"round trip of '$text'")
    }
  }

  test("the empty input is the range that constrains nothing") {
    assertEquals(range(""), SemVerRange(List(Clause.Conjunction(Nil))))
    assertEquals(shown(""), "")
  }

  test("the three wildcard spellings are distinct values") {
    assertNotEquals(range("1.x"), range("1.X"))
    assertNotEquals(range("1.x"), range("1.*"))
    assertEquals(shown("1.X"), "1.X")
    assertEquals(shown("1.*"), "1.*")
  }

  test("the arity the author wrote is carried, so a two-position caret is not a three-position one") {
    assertNotEquals(range("^1.2"), range("^1.2.0"))
    assertEquals(shown("^1.2"), "^1.2")
  }

  test("whitespace between a comparator and its version is trivia") {
    assertEquals(shown(">= 1.2.3"), ">=1.2.3")
    assertEquals(shown("^ 1.2.3"), "^1.2.3")
    assertEquals(shown(">=1.2.3    <2.0.0"), ">=1.2.3 <2.0.0")
    assertEquals(shown("  ^1.0.0   ||   ^2.0.0  "), "^1.0.0 || ^2.0.0")
  }

  test("a leading v is accepted and discarded, as a version parse discards it") {
    assertEquals(shown("^v1.2.3"), "^1.2.3")
    assertEquals(shown("v1.2.3 - v2.3.4"), "1.2.3 - 2.3.4")
  }

  test("build metadata is accepted and discarded, because precedence cannot see it") {
    assertEquals(shown("^1.2.3+build.5"), "^1.2.3")
    assertEquals(range("^1.2.3+build.5"), range("^1.2.3"))
  }

  test("a pre-release rides a partial naming all three positions") {
    assertEquals(shown("^1.2.3-rc.1"), "^1.2.3-rc.1")
    assertEquals(shown("1.2.x-rc.1"), "1.2.x-rc.1")
  }

  test("a partial naming fewer than three positions takes no pre-release") {
    assert(SemVerRange.parse("1.2-rc.1").isLeft)
    assert(SemVerRange.parse("^1-rc.1").isLeft)
  }

  test("input outside the grammar is rejected rather than coerced") {
    List("1.2.3.4", "x.1", "1.x.2", "^1.2.3-01", ">=a.b.c", "1.2.3-", "1..2", "1.2.3+", "^", "01.2.3", "1.2.3_4")
      .foreach(text => assert(SemVerRange.parse(text).isLeft, s"'$text' should be rejected"))
  }

  test("a numeric position too large to carry is rejected rather than thrown out of") {
    assert(SemVerRange.parse(">=99999999999999999999.0.0").isLeft)
  }

  test("a rejection names the fragment that failed, not only the whole range") {
    assertEquals(SemVerRange.parse(">=1.0.0 <x.1"), Left(InvalidRangeFormat(">=1.0.0 <x.1", "x.1")))
  }

  test("the tilde-arrow spelling is rejected, because two ecosystems read it differently") {
    assertEquals(SemVerRange.parse("~>1.2.3"), Left(InvalidRangeFormat("~>1.2.3", "~>1.2.3")))
    assertEquals(SemVerRange.parse("~> 1.2.3"), Left(InvalidRangeFormat("~> 1.2.3", "~>1.2.3")))
  }

  test("a disjunction reads as one clause per alternative") {
    assertEquals(range("^1.0.0 || ^2.0.0").clauses.length, 2)
    assertEquals(range("1.2.7 || >=1.2.9 <2.0.0").clauses.length, 2)
  }

  test("a hyphen range reads as a hyphen clause rather than a pair of comparators") {
    assertEquals(
      range("1.2.3 - 2.3.4").clauses,
      List(
        Clause.Hyphen(
          Partial(Atom.Number(1), Some(Atom.Number(2)), Some(Atom.Number(3)), None),
          Partial(Atom.Number(2), Some(Atom.Number(3)), Some(Atom.Number(4)), None)))
    )
  }

  test("the capability's parse agrees with the narrowed one") {
    assertEquals(SemVerRange.scheme.parse("^1.2.3"), Right(range("^1.2.3")))
    assert(SemVerRange.scheme.parse("~>1.2.3").isLeft)
  }

end SemVerRangeParseSuite
