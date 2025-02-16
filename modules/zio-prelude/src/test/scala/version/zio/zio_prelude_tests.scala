/****************************************************************
 * Copyright © Shuwari Africa Ltd.                              *
 *                                                              *
 * This file is licensed to you under the terms of the Apache   *
 * License Version 2.0 (the "License"); you may not use this    *
 * file except in compliance with the License. You may obtain   *
 * a copy of the License at:                                    *
 *                                                              *
 *     https://www.apache.org/licenses/LICENSE-2.0              *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, *
 * either express or implied. See the License for the specific  *
 * language governing permissions and limitations under the     *
 * License.                                                     *
 ****************************************************************/
package version.zio

import munit.FunSuite
import zio.prelude.given

import version.*
import version.zio.prelude.given

class PreludeSpec extends FunSuite:

  test("Equal[MajorVersion] should correctly compare MajorVersion instances") {
    assert(MajorVersion(1) === MajorVersion(1))
    assert(MajorVersion(1) !== MajorVersion(2))
  }
  test("Equal[MinorVersion] should correctly compare MinorVersion instances") {
    assert(MinorVersion(1) === MinorVersion(1))
    assert(MinorVersion(1) !== MinorVersion(2))
  }
  test("Equal[PatchNumber] should correctly compare PatchNumber instances") {
    assert(PatchNumber(1) === PatchNumber(1))
    assert(PatchNumber(1) !== PatchNumber(2))
  }
  test("Equal[PreReleaseNumber] should correctly compare PreReleaseNumber instances") {
    assert(PreReleaseNumber(1) === PreReleaseNumber(1))
    assert(PreReleaseNumber(1) !== PreReleaseNumber(2))
  }
  test("Equal[PreReleaseClassifier] should correctly compare PreReleaseClassifier instances") {
    assert(PreReleaseClassifier.Alpha === PreReleaseClassifier.Alpha)
    assert(PreReleaseClassifier.Alpha !== PreReleaseClassifier.Beta)
  }
  test("Equal[PreRelease] should correctly compare PreRelease instances") {
    assert(PreRelease.alpha(PreReleaseNumber(1)) === PreRelease.alpha(PreReleaseNumber(1)))
    assert(PreRelease.beta(PreReleaseNumber(1)) !== PreRelease.alpha(PreReleaseNumber(1)))
    assert(PreRelease.alpha(PreReleaseNumber(2)) !== PreRelease.alpha(PreReleaseNumber(1)))
  }

  test("Ord[MajorVersion] should correctly compare MajorVersion instances") {
    assert(MajorVersion(1) === MajorVersion(1))
    assert(!(MajorVersion(1) === MajorVersion(2)))
  }
  test("Ord[MinorVersion] should correctly compare MinorVersion instances") {
    assertEquals(MinorVersion(1), MinorVersion(1))
    assert(MinorVersion(1) < MinorVersion(2))
  }

  test("Ord[PatchNumber] should correctly compare PatchNumber instances") {
    assertEquals(PatchNumber(1), PatchNumber(1))
    assert(PatchNumber(1) < PatchNumber(2))
  }

  test("Ord[PreReleaseNumber] should correctly compare PreReleaseNumber instances") {
    assertEquals(PreReleaseNumber(1), PreReleaseNumber(1))
    assert(PreReleaseNumber(1) < PreReleaseNumber(2))
  }

  test("Ord[PreReleaseClassifier] should correctly compare PreReleaseClassifier instances") {
    assertEquals(PreReleaseClassifier.Alpha, PreReleaseClassifier.Alpha)
    assert(PreReleaseClassifier.Alpha < PreReleaseClassifier.Beta)
  }

  test("Ord[PreRelease] should correctly compare PreRelease instances") {
    assertEquals(PreRelease.alpha(PreReleaseNumber(1)), PreRelease.alpha(PreReleaseNumber(1)))
    assert(PreRelease.alpha(PreReleaseNumber(1)) < PreRelease.beta(PreReleaseNumber(1)))
  }

  test("Ord[Version] should correctly compare Version instances") {
    assertEquals(Version(MajorVersion(1), MinorVersion(0), PatchNumber(0)), Version(MajorVersion(1), MinorVersion(0), PatchNumber(0)))
    assert(Version(MajorVersion(1), MinorVersion(0), PatchNumber(0)) < Version(MajorVersion(2), MinorVersion(0), PatchNumber(0)))
  }

  test("Commutative[MajorVersion] should correctly combine MajorVersion instances") {
    val expected = MajorVersion(3)
    assertEquals(MajorVersion.combine(MajorVersion(1), MajorVersion(2)), expected)
    assertEquals(MajorVersion.combine(MajorVersion(2), MajorVersion(1)), expected)
  }

  test("Commutative[MinorVersion] should correctly combine MinorVersion instances") {
    val expected = MinorVersion(3)
    assertEquals(MinorVersion.combine(MinorVersion(1), MinorVersion(2)), expected)
    assertEquals(MinorVersion.combine(MinorVersion(2), MinorVersion(1)), expected)
  }

  test("Commutative[PatchNumber] should correctly combine PatchNumber instances") {
    val expected = PatchNumber(3)
    assertEquals(PatchNumber.combine(PatchNumber(1), PatchNumber(2)), expected)
    assertEquals(PatchNumber.combine(PatchNumber(2), PatchNumber(1)), expected)
  }

  test("Commutative[PreReleaseNumber] should correctly combine PreReleaseNumber instances") {
    val expected = PreReleaseNumber(3)
    assertEquals(PreReleaseNumber.combine(PreReleaseNumber(1), PreReleaseNumber(2)), expected)
    assertEquals(PreReleaseNumber.combine(PreReleaseNumber(2), PreReleaseNumber(1)), expected)
  }
end PreludeSpec
