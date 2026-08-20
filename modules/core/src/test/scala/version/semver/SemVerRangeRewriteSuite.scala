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

import version.Strategy
import version.errors.UnsupportedRewrite

class SemVerRangeRewriteSuite extends FunSuite:

  import RangeCorpus.range
  import SemVerRange.scheme

  private def rewritten(text: String, strategy: Strategy, version: String): String =
    scheme.rewrite(range(text))(strategy, SemVer.parseUnsafe(version)) match
      case Right(out) => scheme.show(out)
      case Left(e)    => s"rejected: ${e.getMessage}"

  private def table(strategy: Strategy, vectors: List[(String, String, String)]): Unit =
    vectors.foreach((text, version, expected) =>
      assertEquals(rewritten(text, strategy, version), expected, s"$strategy of '$text' for $version"))

  test("pinning names the version outright, whatever the range said before") {
    table(
      Strategy.Pin,
      List(
        ("^1.2.3", "1.5.0", "1.5.0"),
        ("~1.2", "1.5.0", "1.5.0"),
        ("1.x", "2.0.0", "2.0.0"),
        (">=1.0.0 <2.0.0", "1.9.0", "1.9.0"),
        ("*", "2.0.0-rc.1", "2.0.0-rc.1")
      )
    )
  }

  test("raising moves the floor and keeps the construct, the arity and the wildcard spelling") {
    table(
      Strategy.Raise,
      List(
        ("^1.0.0", "1.1.0", "^1.1.0"),
        ("^1.0.0", "2.0.0", "^2.0.0"),
        ("~1.2.3", "1.2.9", "~1.2.9"),
        ("^1.2", "1.5.0", "^1.5"),
        ("^1", "2.0.0", "^2"),
        ("1.2.3", "1.2.4", "1.2.4"),
        ("1.x", "2.3.4", "2.x"),
        ("1.*", "2.3.4", "2.*"),
        (">=1.2.3", "1.5.0", ">=1.5.0"),
        (">=1.0.0 <2.0.0", "1.5.0", ">=1.5.0 <2.0.0"),
        ("1.2.3 - 2.0.0", "1.5.0", "1.5.0 - 2.0.0"),
        ("^1.0.0 || ^2.0.0", "2.5.0", "^1.0.0 || ^2.5.0")
      )
    )
  }

  test("raising leaves a ceiling alone, and falls back to a replacement where the raised clause would refuse") {
    table(
      Strategy.Raise,
      List(
        ("<2.0.0", "1.5.0", "<2.0.0"),
        (">1.0.0", "1.5.0", ">1.0.0"),
        ("1.2.7 || >=1.2.9 <2.0.0", "2.0.0", "1.2.7 || >=1.2.9 <3.0.0")
      )
    )
  }

  test("replacing leaves a range that already admits alone") {
    table(Strategy.Replace, List(("^1.2.3", "1.5.0", "^1.2.3"), ("1.x", "1.5.0", "1.x")))
  }

  test("replacing reshapes the last clause as little as will admit the version") {
    table(
      Strategy.Replace,
      List(
        ("^1.2.3", "2.0.0", "^2.0.0"),
        ("~1.2.3", "1.3.0", "~1.3.0"),
        ("^0.2.3", "0.3.0", "^0.3.0"),
        ("1.x", "2.3.4", "2.x"),
        ("1.2.x", "1.3.4", "1.3.x"),
        ("1.2.3", "2.0.0", "2.0.0"),
        ("<=2.0.0", "2.5.0", "<=2.5.0"),
        (">=2.0.0", "1.0.0", ">=1.0.0"),
        (">=1.0.0 <2.0.0", "2.5.0", ">=1.0.0 <3.0.0"),
        ("1.2.3 - 2.0.0", "2.5.0", "1.2.3 - 2.5.0"),
        ("1.2.3 - 2.0.0", "1.0.0", "1.0.0 - 2.0.0"),
        ("^1.0.0 || ^2.0.0", "3.0.0", "^1.0.0 || ^3.0.0"),
        ("^1.2.3", "2.0.0-rc.1", "^2.0.0-rc.1")
      )
    )
  }

  test("an exclusive ceiling moves at the precision its trailing zeros mark") {
    table(
      Strategy.Replace,
      List(("<2.0.0", "2.5.0", "<3.0.0"), ("<2.3.0", "2.5.0", "<2.6.0"), ("<2.3.1", "2.5.0", "<2.5.1"))
    )
  }

  test("widening moves an endpoint outward only where the clause is built from endpoints") {
    table(
      Strategy.Widen,
      List(
        ("^1.0.0", "1.5.0", "^1.0.0"),
        ("<2.0.0", "2.5.0", "<3.0.0"),
        (">=1.0.0 <2.0.0", "2.5.0", ">=1.0.0 <3.0.0"),
        ("1.2.3 - 2.0.0", "2.5.0", "1.2.3 - 2.5.0")
      )
    )
  }

  test("widening a clause naming a whole band adds an alternative rather than stretching it") {
    table(
      Strategy.Widen,
      List(
        ("^1.0.0", "2.0.0", "^1.0.0 || ^2.0.0"),
        ("~1.2.3", "1.3.0", "~1.2.3 || ~1.3.0"),
        ("1.x", "2.3.4", "1.x || 2.x"),
        ("^1.0.0 || ^2.0.0", "3.0.0", "^1.0.0 || ^2.0.0 || ^3.0.0")
      )
    )
  }

  test("a written form that no rewrite can make admit the version is rejected with the strategy named") {
    assertEquals(
      scheme.rewrite(range(">0.9.0"))(Strategy.Replace, SemVer.parseUnsafe("0.9.0")),
      Left(UnsupportedRewrite(">0.9.0", Strategy.Replace))
    )
    assertEquals(
      scheme.rewrite(range("*"))(Strategy.Replace, SemVer.parseUnsafe("2.0.0-rc.1")),
      Left(UnsupportedRewrite("*", Strategy.Replace))
    )
  }

  test("a rewrite needing a ceiling the carrier cannot hold drops the ceiling instead of wrapping it") {
    assertEquals(rewritten("<9223372036854775807.0.0", Strategy.Replace, "9223372036854775807.5.0"), "")
    assertEquals(rewritten("<9223372036854775807.0.0", Strategy.Raise, "9223372036854775807.5.0"), "")
    assertEquals(rewritten("<9223372036854775806.0.0", Strategy.Replace, "9223372036854775805.5.0"), "<9223372036854775806.0.0")
  }

  test("a right is guaranteed to admit the version, across the corpus cross-product") {
    forEachRewrite { (text, strategy, version, result) =>
      result.foreach(out => assert(scheme.admits(out)(version), s"$strategy of '$text' for ${version.show} yielded '${scheme.show(out)}'"))
    }
  }

  test("a rewritten range renders to something that reads back as itself, across the corpus cross-product") {
    forEachRewrite { (text, strategy, version, result) =>
      result.foreach(out =>
        assertEquals(
          SemVerRange.parse(scheme.show(out)),
          Right(out),
          s"round trip after $strategy of '$text' for ${version.show}"
        ))
    }
  }

  test("replacing is a no-op wherever the range already admits, across the corpus cross-product") {
    RangeCorpus.lawParsed.zip(RangeCorpus.lawRanges).foreach { (parsed, text) =>
      RangeCorpus.lawVersions.filter(v => scheme.admits(parsed)(v)).foreach { version =>
        assertEquals(
          scheme.rewrite(parsed)(Strategy.Replace, version),
          Right(parsed),
          s"replace of '$text' for ${version.show}"
        )
      }
    }
  }

  test("widening never drops a version the range already admitted, across the corpus cross-product") {
    RangeCorpus.lawParsed.zip(RangeCorpus.lawRanges).foreach { (parsed, text) =>
      val admitted = RangeCorpus.lawVersions.filter(v => scheme.admits(parsed)(v))
      RangeCorpus.lawVersions.foreach { version =>
        scheme.rewrite(parsed)(Strategy.Widen, version).foreach { out =>
          val lost = admitted.filterNot(v => scheme.admits(out)(v))
          assertEquals(
            lost.map(_.show),
            Nil,
            s"widen of '$text' for ${version.show} yielded '${scheme.show(out)}'"
          )
        }
      }
    }
  }

  test("pinning yields a range whose exact version is the one pinned, across the corpus cross-product") {
    RangeCorpus.lawParsed.zip(RangeCorpus.lawRanges).foreach { (parsed, text) =>
      RangeCorpus.lawVersions.foreach { version =>
        assertEquals(
          scheme.rewrite(parsed)(Strategy.Pin, version).map(scheme.exact),
          Right(Some(version)),
          s"pin of '$text' for ${version.show}"
        )
      }
    }
  }

  private def forEachRewrite(check: (String, Strategy, SemVer, Option[SemVerRange]) => Unit): Unit =
    Strategy.values.foreach { strategy =>
      RangeCorpus.lawParsed.zip(RangeCorpus.lawRanges).foreach { (parsed, text) =>
        RangeCorpus.lawVersions.foreach { version =>
          check(text, strategy, version, scheme.rewrite(parsed)(strategy, version).toOption)
        }
      }
    }

end SemVerRangeRewriteSuite
