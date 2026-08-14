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

class SemVerRangeMembershipSuite extends FunSuite:

  import RangeCorpus.range
  import SemVerRange.scheme

  private def admits(text: String, version: String): Boolean =
    scheme.admits(range(text))(SemVer.parseUnsafe(version))

  private def table(expected: Boolean, vectors: List[(String, String)]): Unit =
    vectors.foreach((text, version) => assertEquals(admits(text, version), expected, s"'$text' against $version"))

  test("a version inside the band a construct names is admitted") {
    table(
      expected = true,
      List(
        "^1.2.3" -> "1.2.3",
        "^1.2.3" -> "1.9.9",
        "^0.2.3" -> "0.2.9",
        "^0.0.3" -> "0.0.3",
        "~1.2.3" -> "1.2.9",
        "1.x" -> "1.9.9",
        "1.2.x" -> "1.2.9",
        ">=1.2.3" -> "5.0.0",
        "<2.0.0" -> "1.9.9",
        "1.2.3 - 2.3.4" -> "2.3.4",
        "^1.0.0 || ^2.0.0" -> "2.5.0",
        "*" -> "9.9.9",
        "" -> "9.9.9"
      )
    )
  }

  test("a version outside the band a construct names is refused") {
    table(
      expected = false,
      List(
        "^1.2.3" -> "1.2.2",
        "^1.2.3" -> "2.0.0",
        "^0.2.3" -> "0.3.0",
        "^0.0.3" -> "0.0.4",
        "~1.2.3" -> "1.3.0",
        "1.2.x" -> "1.3.0",
        "^1.0.0 || ^2.0.0" -> "3.0.0",
        "1.2.3 - 2.3.4" -> "2.3.5"
      )
    )
  }

  test("build metadata takes no part in membership") {
    assert(admits("^1.2.3", "1.2.3+build"))
    assert(admits("=1.2.3", "1.2.3+build.5"))
  }

  test("a pre-release is admitted only where a comparator of the same conjunction opted into its numbers") {
    table(
      expected = true,
      List(
        "^1.2.3-pr.1" -> "1.2.3-pr.2",
        "^1.2.3-pr.1" -> "1.2.3",
        "^1.2.3-pr.1" -> "1.9.9",
        ">=1.0.0-rc.1 <2.0.0" -> "1.0.0-rc.5"
      )
    )
    table(
      expected = false,
      List(
        "^1.2.3" -> "1.3.0-rc.1",
        "^1.2.3" -> "2.0.0-0",
        "^1.2.3-pr.1" -> "1.2.4-alpha.notready",
        "^1.2.3-pr.1" -> "1.2.3-pr.0",
        ">=1.0.0" -> "2.0.0-alpha",
        "*" -> "2.0.0-alpha",
        "" -> "2.0.0-alpha"
      )
    )
  }

  test("the comparator opting a pre-release in may be the ceiling") {
    assert(admits(">=1.0.0 <2.0.0-rc.1", "2.0.0-rc.0"))
  }

  test("membership is not the conjunction of the comparators' ordered tests") {
    val candidate = SemVer.parseUnsafe("1.5.0-rc.1")
    val order = summon[Ordering[SemVer]]
    assert(order.gt(candidate, SemVer.parseUnsafe("1.2.3")))
    assert(order.lt(candidate, SemVer.parseUnsafe("2.0.0-0")))
    assertEquals(admits(">=1.2.3 <2.0.0-0", "1.5.0-rc.1"), false)
  }

  test("a wildcard major under a strict comparison admits nothing") {
    table(expected = false, List(">x" -> "1.0.0", "<x" -> "1.0.0", ">x" -> "0.0.0"))
  }

  test("a wildcard major under an inclusive comparison constrains nothing") {
    table(expected = true, List(">=x" -> "1.0.0", "<=x" -> "9.9.9"))
    assertEquals(admits(">=x", "1.0.0-rc.1"), false)
  }

  test("the empty conjunction and the star spelling admit the same releases") {
    RangeCorpus.versions.foreach { version =>
      assertEquals(
        scheme.admits(range(""))(version),
        scheme.admits(range("*"))(version),
        s"${version.show} under the empty range and the star"
      )
    }
  }

end SemVerRangeMembershipSuite
