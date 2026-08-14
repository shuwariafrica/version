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

import version.RangeScheme
import version.Strategy

class SemVerRangeSelectionSuite extends FunSuite:

  import RangeCorpus.range
  import SemVerRange.scheme

  private val pool: List[SemVer] =
    List("1.0.0", "1.2.3", "1.5.0", "1.9.9", "2.0.0", "2.1.0", "1.6.0-rc.1").map(SemVer.parseUnsafe)

  private val order: Ordering[SemVer] = summon[Ordering[SemVer]]

  test("the greatest and least admitted candidates are selected under the scheme's own precedence") {
    assertEquals(range("^1.2.3").highest(pool).map(_.show), Some("1.9.9"))
    assertEquals(range("^1.2.3").lowest(pool).map(_.show), Some("1.2.3"))
    assertEquals(range(">=2.0.0").highest(pool).map(_.show), Some("2.1.0"))
    assertEquals(range(">=2.0.0").lowest(pool).map(_.show), Some("2.0.0"))
  }

  test("a candidate the range refuses is never selected, including a pre-release inside the bounds") {
    assertEquals(range("^1.2.3").highest(pool).exists(_.preRelease.isDefined), false)
    assert(pool.exists(v => order.gt(v, SemVer.parseUnsafe("1.5.0")) && v.preRelease.isDefined))
  }

  test("a pool with nothing the range admits yields nothing") {
    assertEquals(range("^9.0.0").highest(pool), None)
    assertEquals(range("^9.0.0").lowest(pool), None)
  }

  test("an empty pool yields nothing") {
    assertEquals(range("^1.2.3").highest(Nil), None)
    assertEquals(range("^1.2.3").lowest(Nil), None)
  }

  test("selection agrees with filtering then taking an extremum, across the corpus") {
    RangeCorpus.lawParsed.zip(RangeCorpus.lawRanges).foreach { (parsed, text) =>
      val admitted = RangeCorpus.lawVersions.filter(v => scheme.admits(parsed)(v))
      assertEquals(parsed.highest(RangeCorpus.lawVersions), admitted.maxOption(using order), s"highest of '$text'")
      assertEquals(parsed.lowest(RangeCorpus.lawVersions), admitted.minOption(using order), s"lowest of '$text'")
    }
  }

  test("a range naming one version outright reports it, and a bounded one reports nothing") {
    assertEquals(scheme.exact(range("1.2.3")).map(_.show), Some("1.2.3"))
    assertEquals(scheme.exact(range("=1.2.3")).map(_.show), Some("1.2.3"))
    assertEquals(scheme.exact(range("1.2.3-rc.1")).map(_.show), Some("1.2.3-rc.1"))
    assertEquals(scheme.exact(range("^1.2.3")), None)
    assertEquals(scheme.exact(range("~1.2.3")), None)
    assertEquals(scheme.exact(range("1.x")), None)
    assertEquals(scheme.exact(range("1.2")), None)
    assertEquals(scheme.exact(range(">=1.2.3 <2.0.0")), None)
    assertEquals(scheme.exact(range("1.2.3 || 1.2.4")), None)
    assertEquals(scheme.exact(range("")), None)
  }

  test("the companion aliases reach the same answers the extensions do") {
    val caret = range("^1.2.3")
    assertEquals(RangeScheme.rewrite(caret, Strategy.Pin, SemVer.parseUnsafe("1.5.0")).map(scheme.show), Right("1.5.0"))
    assertEquals(RangeScheme.highest(caret, pool), caret.highest(pool))
    assertEquals(RangeScheme.lowest(caret, pool), caret.lowest(pool))
    assertEquals(RangeScheme.highest(caret, pool).map(_.show), Some("1.9.9"))
    assertEquals(RangeScheme.lowest(caret, pool).map(_.show), Some("1.2.3"))
  }

end SemVerRangeSelectionSuite
