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

import scala.util.Random

import version.VersionScheme

class SemVerOrderingSuite extends FunSuite:

  private val scheme = summon[VersionScheme[SemVer]]

  private def v(input: String): SemVer = SemVer.parseUnsafe(input)

  private def cmp(a: String, b: String): Int = Integer.signum(scheme.precedence.compare(v(a), v(b)))

  private def ascending(chain: List[String]): Unit =
    chain.sliding(2).foreach {
      case List(lower, higher) => assertEquals(cmp(lower, higher), -1, s"$lower should rank below $higher")
      case _                   => ()
    }

  test("the specification's own precedence chain holds end to end") {
    ascending(
      List(
        "1.0.0-alpha",
        "1.0.0-alpha.1",
        "1.0.0-alpha.beta",
        "1.0.0-beta",
        "1.0.0-beta.2",
        "1.0.0-beta.11",
        "1.0.0-rc.1",
        "1.0.0"
      )
    )
  }

  test("sorting a shuffled chain reproduces it") {
    val chain = List("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0").map(v)
    assertEquals(Random.shuffle(chain).sorted(using scheme.precedence), chain)
  }

  test("numeric components are compared as numbers, not as text") {
    assertEquals(cmp("1.99.99", "2.0.0"), -1)
    assertEquals(cmp("1.9.0", "1.10.0"), -1)
  }

  test("a numeric pre-release identifier ranks below an alphanumeric one") {
    assertEquals(cmp("1.0.0-1", "1.0.0-alpha"), -1)
  }

  test("numeric pre-release identifiers are compared as numbers") {
    assertEquals(cmp("1.0.0-beta.2", "1.0.0-beta.11"), -1)
  }

  test("where one identifier list is a prefix of another the shorter ranks below it") {
    assertEquals(cmp("1.0.0-alpha", "1.0.0-alpha.1"), -1)
  }

  test("a version with no pre-release ranks above one carrying any") {
    assertEquals(cmp("1.0.0-rc.1", "1.0.0"), -1)
    assertEquals(cmp("1.0.0-alpha", "1.0.0"), -1)
  }

  test("SNAPSHOT ranks below rc.1 under case-sensitive identifier comparison") {
    assertEquals(cmp("1.0.0-SNAPSHOT", "1.0.0-rc.1"), -1)
  }

  test("rc.4 ranks below rc3 because the undotted form is never split") {
    assertEquals(cmp("1.0.0-rc.4", "1.0.0-rc3"), -1)
  }

  test("build metadata takes no part in precedence") {
    assertEquals(cmp("1.0.0+build", "1.0.0"), 0)
    assertEquals(cmp("1.0.0+b1", "1.0.0+b2"), 0)
    assertEquals(cmp("1.0.0-rc.1+b1", "1.0.0-rc.1+b2"), 0)
  }

  test("versions equal under precedence remain structurally distinct where their metadata differs") {
    assertNotEquals(v("1.0.0+b1"), v("1.0.0+b2"))
    assertEquals(cmp("1.0.0+b1", "1.0.0+b2"), 0)
  }

end SemVerOrderingSuite
