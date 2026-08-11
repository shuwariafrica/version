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

import version.Difference
import version.VersionScheme

class SemVerDifferenceSuite extends FunSuite:

  private val scheme = summon[VersionScheme[SemVer]]

  private def difference(a: String, b: String): Difference =
    scheme.difference(SemVer.parseUnsafe(a), SemVer.parseUnsafe(b))

  test("a moved numeric component is reported at its own position") {
    assertEquals(difference("1.2.3", "2.0.0"), Difference.Release(0))
    assertEquals(difference("1.2.3", "1.3.0"), Difference.Release(1))
    assertEquals(difference("1.2.3", "1.2.4"), Difference.Release(2))
  }

  test("the most significant moved component wins over lesser ones") {
    assertEquals(difference("1.2.3", "2.9.9"), Difference.Release(0))
    assertEquals(difference("1.2.3", "1.3.9"), Difference.Release(1))
  }

  test("gaining, losing, or changing a pre-release is a qualifier difference") {
    assertEquals(difference("1.2.3-rc.1", "1.2.3"), Difference.Qualifier)
    assertEquals(difference("1.2.3", "1.2.3-rc.1"), Difference.Qualifier)
    assertEquals(difference("1.2.3-rc.1", "1.2.3-rc.2"), Difference.Qualifier)
  }

  test("a change confined to build metadata is a build difference") {
    assertEquals(difference("1.0.0+a", "1.0.0+b"), Difference.Build)
    assertEquals(difference("1.0.0", "1.0.0+b"), Difference.Build)
  }

  test("identical versions differ in nothing") {
    assertEquals(difference("1.0.0", "1.0.0"), Difference.None)
    assertEquals(difference("1.0.0-rc.1+b", "1.0.0-rc.1+b"), Difference.None)
  }

  test("SemVer has no epoch and so never reports one") {
    val pairs = List(("1.0.0", "2.0.0"), ("1.0.0-rc.1", "1.0.0"), ("1.0.0+a", "1.0.0+b"), ("1.0.0", "1.0.0"))
    pairs.foreach((a, b) => assertNotEquals(difference(a, b), Difference.Epoch))
  }

end SemVerDifferenceSuite
