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

import version.VersionScheme

class SemVerSchemeSuite extends FunSuite:

  private val scheme = summon[VersionScheme[SemVer]]

  private def v(input: String): SemVer = SemVer.parseUnsafe(input)

  test("the scheme is published under the name its specification is known by") {
    assertEquals(scheme.name, "semver")
  }

  test("the canonical rendering carries every part of the version") {
    assertEquals(scheme.show(v("1.2.3")), "1.2.3")
    assertEquals(scheme.show(v("1.2.3-rc.1")), "1.2.3-rc.1")
    assertEquals(scheme.show(v("1.2.3+sha.5114f85")), "1.2.3+sha.5114f85")
    assertEquals(scheme.show(v("1.2.3-rc.1+sha.5114f85")), "1.2.3-rc.1+sha.5114f85")
  }

  test("a version is stable exactly when it carries no pre-release") {
    assert(scheme.stable(v("1.2.3")))
    assert(scheme.stable(v("1.2.3+sha.5114f85")))
    assert(!scheme.stable(v("1.2.3-rc.1")))
    assert(!scheme.stable(v("0.1.0-SNAPSHOT")))
  }

  test("the release projection strips both the pre-release and the build metadata") {
    assertEquals(scheme.release(v("1.2.3-rc.1+sha.5114f85")), v("1.2.3"))
    assertEquals(scheme.release(v("1.2.3")), v("1.2.3"))
  }

  test("the numbers projection reports the three components and nothing else") {
    assertEquals(scheme.numbers(v("1.2.3-rc.1+sha.5114f85")).toList, List(1L, 2L, 3L))
    assertEquals(scheme.numbers(v("20260731093000.0.0")).toList, List(20260731093000L, 0L, 0L))
  }

  test("parsing through the scheme agrees with parsing through the companion") {
    assertEquals(scheme.parse("1.2.3-rc.1"), Right(v("1.2.3-rc.1")))
    assert(scheme.parse("nonsense").isLeft)
  }

  test("the projections are reachable as extensions without importing the instance") {
    val value = v("1.2.3-rc.1+sha.5114f85")
    assertEquals(value.show, "1.2.3-rc.1+sha.5114f85")
    assertEquals(value.release, v("1.2.3"))
    assertEquals(value.numbers.toList, List(1L, 2L, 3L))
    assert(!value.stable)
    assert(!value.snapshot)
  }

end SemVerSchemeSuite
