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

import version.CompatibilityPolicy

class SemVerCompatibilitySuite extends FunSuite:

  private def v(input: String): SemVer = SemVer.parseUnsafe(input)

  private val sameMajor = SemVer.Compatibility.sameMajor
  private val leftmostNonZero = SemVer.Compatibility.leftmostNonZero
  private val strict = CompatibilityPolicy.strict[SemVer]

  test("sameMajor admits any two stable releases sharing a major at or above one") {
    assert(sameMajor.compatible(v("1.2.3"), v("1.9.9")))
    assert(sameMajor.compatible(v("2.0.0"), v("2.0.0")))
  }

  test("sameMajor refuses a change of major") {
    assert(!sameMajor.compatible(v("1.2.3"), v("2.0.0")))
  }

  test("sameMajor treats every initial-development release as incompatible with every other") {
    assert(!sameMajor.compatible(v("0.4.2"), v("0.4.3")))
    assert(!sameMajor.compatible(v("0.4.2"), v("0.4.2")))
  }

  test("leftmostNonZero admits two stable releases sharing their leftmost non-zero component") {
    assert(leftmostNonZero.compatible(v("1.2.3"), v("1.9.9")))
    assert(leftmostNonZero.compatible(v("0.4.2"), v("0.4.9")))
    assert(leftmostNonZero.compatible(v("0.0.3"), v("0.0.3")))
  }

  test("leftmostNonZero refuses a change to the leftmost non-zero component") {
    assert(!leftmostNonZero.compatible(v("1.2.3"), v("2.0.0")))
    assert(!leftmostNonZero.compatible(v("0.4.2"), v("0.5.0")))
    assert(!leftmostNonZero.compatible(v("0.0.3"), v("0.0.4")))
  }

  test("the two policies disagree below 1.0.0, which is why neither is the default") {
    assert(leftmostNonZero.compatible(v("0.4.2"), v("0.4.9")))
    assert(!sameMajor.compatible(v("0.4.2"), v("0.4.9")))
  }

  test("neither policy admits a pre-release on either side") {
    assert(!sameMajor.compatible(v("1.2.3-rc.1"), v("1.2.4")))
    assert(!sameMajor.compatible(v("1.2.3"), v("1.2.4-rc.1")))
    assert(!leftmostNonZero.compatible(v("1.2.3-rc.1"), v("1.2.4")))
  }

  test("strict admits a version only in place of an equal one") {
    assert(strict.compatible(v("1.2.3"), v("1.2.3")))
    assert(!strict.compatible(v("1.2.3"), v("1.2.4")))
    assert(!strict.compatible(v("1.0.0+a"), v("1.0.0+b")))
  }

end SemVerCompatibilitySuite
