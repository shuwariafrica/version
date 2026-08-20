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

class SemVerRangeDesugarSuite extends FunSuite:

  import RangeCorpus.range
  import SemVerRange.scheme

  private def desugared(text: String): String = scheme.show(scheme.desugar(range(text)))

  private def table(name: String, vectors: List[(String, String)]): Unit =
    vectors.foreach((in, out) => assertEquals(desugared(in), out, s"$name: '$in'"))

  test("the caret construct desugars to the compatible band its leftmost non-zero position fixes") {
    table(
      "caret",
      List(
        "^x" -> "",
        "^*" -> "",
        "^1" -> ">=1.0.0 <2.0.0-0",
        "^0.2" -> ">=0.2.0 <0.3.0-0",
        "^1.2" -> ">=1.2.0 <2.0.0-0",
        "^0.0.3" -> ">=0.0.3 <0.0.4-0",
        "^0.2.3" -> ">=0.2.3 <0.3.0-0",
        "^1.2.3" -> ">=1.2.3 <2.0.0-0",
        "^0.0.3-pr.1" -> ">=0.0.3-pr.1 <0.0.4-0",
        "^0.2.3-pr.1" -> ">=0.2.3-pr.1 <0.3.0-0",
        "^1.2.3-pr.1" -> ">=1.2.3-pr.1 <2.0.0-0"
      )
    )
  }

  test("the tilde construct desugars to the band its last written position fixes") {
    table(
      "tilde",
      List(
        "~x" -> "",
        "~1" -> ">=1.0.0 <2.0.0-0",
        "~1.2" -> ">=1.2.0 <1.3.0-0",
        "~0.2" -> ">=0.2.0 <0.3.0-0",
        "~1.2.3" -> ">=1.2.3 <1.3.0-0",
        "~1.2.3-pr.1" -> ">=1.2.3-pr.1 <1.3.0-0"
      )
    )
  }

  test("a wildcard position desugars per the comparator standing before it") {
    table(
      "x-range",
      List(
        "*" -> "",
        "x" -> "",
        "1" -> ">=1.0.0 <2.0.0-0",
        "1.x" -> ">=1.0.0 <2.0.0-0",
        "1.2.x" -> ">=1.2.0 <1.3.0-0",
        "1.2" -> ">=1.2.0 <1.3.0-0",
        "1.2.3" -> "=1.2.3",
        ">1" -> ">=2.0.0",
        ">1.2" -> ">=1.3.0",
        ">=1.2" -> ">=1.2.0",
        "<1.2" -> "<1.2.0-0",
        "<=0.7.x" -> "<0.8.0-0",
        "<=7.x" -> "<8.0.0-0",
        "=1.x" -> ">=1.0.0 <2.0.0-0",
        ">x" -> "<0.0.0-0",
        "<x" -> "<0.0.0-0",
        ">=x" -> "",
        "<=x" -> ""
      )
    )
  }

  test("a hyphen range desugars to an inclusive floor and a ceiling at the precision its end was written to") {
    table(
      "hyphen",
      List(
        "1.2.3 - 2.3.4" -> ">=1.2.3 <=2.3.4",
        "1.2 - 3.4.5" -> ">=1.2.0 <=3.4.5",
        "1.2.3 - 3.4" -> ">=1.2.3 <3.5.0-0",
        "1.2 - 3.4" -> ">=1.2.0 <3.5.0-0",
        "1.2.3 - x" -> ">=1.2.3",
        "x - 2.3.4" -> "<=2.3.4",
        "1.2.3-rc.1 - 2.0.0-rc.1" -> ">=1.2.3-rc.1 <=2.0.0-rc.1"
      )
    )
  }

  test("each alternative of a disjunction desugars on its own") {
    assertEquals(desugared("^1.0.0 || ~2.1"), ">=1.0.0 <2.0.0-0 || >=2.1.0 <2.2.0-0")
  }

  test("an explicit floor at zero behaves exactly as the range that constrains nothing") {
    assertEquals(desugared(">=0.0.0"), ">=0.0.0")
    assertEquals(scheme.admits(range(">=0.0.0"))(SemVer.parseUnsafe("9.9.9")), true)
    assertEquals(scheme.admits(range(">=0.0.0"))(SemVer.parseUnsafe("9.9.9-rc.1")), false)
  }

  test("a ceiling with no representable successor is left off rather than wrapped past the carrier") {
    assertEquals(desugared("^9223372036854775807"), ">=9223372036854775807.0.0")
    assertEquals(desugared("~9223372036854775807"), ">=9223372036854775807.0.0")
    assertEquals(desugared("9223372036854775807.x"), ">=9223372036854775807.0.0")
    assertEquals(desugared("<=9223372036854775807.x"), "")
    assertEquals(desugared("1.2.3 - 9223372036854775807.x"), ">=1.2.3")
    assertEquals(SemVerRange.parse(desugared("^9223372036854775807")).map(scheme.show), Right(">=9223372036854775807.0.0"))
  }

  test("strictly above the greatest representable major admits nothing") {
    assertEquals(desugared(">9223372036854775807"), "<0.0.0-0")
    assertEquals(scheme.admits(range(">9223372036854775807"))(SemVer.parseUnsafe("9223372036854775807.9.9")), false)
  }

  test("a ceiling left off still admits every version the ceiling was there to keep in") {
    val top = range("^9223372036854775807")
    assert(scheme.admits(top)(SemVer.parseUnsafe("9223372036854775807.0.0")))
    assert(scheme.admits(top)(SemVer.parseUnsafe("9223372036854775807.9.9")))
    assertEquals(scheme.admits(top)(SemVer.parseUnsafe("9223372036854775806.9.9")), false)
  }

  test("a successor the position cannot hold carries leftwards to the least version above the exhausted line") {
    val vectors = List(
      ("~1.9223372036854775807", ">=1.9223372036854775807.0 <2.0.0-0", "1.9223372036854775807.5", "2.0.0"),
      ("1.9223372036854775807", ">=1.9223372036854775807.0 <2.0.0-0", "1.9223372036854775807.5", "2.0.0"),
      ("^0.9223372036854775807", ">=0.9223372036854775807.0 <1.0.0-0", "0.9223372036854775807.7", "1.0.0"),
      ("^0.0.9223372036854775807", ">=0.0.9223372036854775807 <0.1.0-0", "0.0.9223372036854775807", "0.1.0"),
      (">1.9223372036854775807", ">=2.0.0", "2.0.0", "1.9223372036854775807.9")
    )
    vectors.foreach { (text, primitive, admitted, refused) =>
      assertEquals(desugared(text), primitive, s"desugaring of '$text'")
      assertEquals(SemVerRange.parse(desugared(text)).map(scheme.show), Right(primitive), s"re-parse of '$text'")
      assert(scheme.admits(range(text))(SemVer.parseUnsafe(admitted)), s"'$text' should admit $admitted")
      assertEquals(scheme.admits(range(text))(SemVer.parseUnsafe(refused)), false, s"'$text' should refuse $refused")
    }
  }

  test("desugaring is idempotent across the corpus") {
    RangeCorpus.lawParsed.zip(RangeCorpus.lawRanges).foreach { (parsed, text) =>
      val once = scheme.desugar(parsed)
      assertEquals(scheme.desugar(once), once, s"idempotence of '$text'")
    }
  }

  test("desugaring preserves membership across the corpus cross-product") {
    RangeCorpus.lawParsed.zip(RangeCorpus.lawRanges).foreach { (parsed, text) =>
      val once = scheme.desugar(parsed)
      RangeCorpus.lawVersions.foreach { version =>
        assertEquals(
          scheme.admits(once)(version),
          scheme.admits(parsed)(version),
          s"membership of ${version.show} under '$text'"
        )
      }
    }
  }

  test("a desugared range renders to something that reads back as itself") {
    RangeCorpus.lawParsed.zip(RangeCorpus.lawRanges).foreach { (parsed, text) =>
      val once = scheme.desugar(parsed)
      assertEquals(SemVerRange.parse(scheme.show(once)), Right(once), s"round trip of desugared '$text'")
    }
  }

end SemVerRangeDesugarSuite
