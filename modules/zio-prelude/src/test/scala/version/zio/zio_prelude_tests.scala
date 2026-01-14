/****************************************************************************
 * Copyright 2023 Shuwari Africa Ltd.                                       *
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
package version.zio

import munit.FunSuite
import zio.prelude.given

import version.*
import version.zio.prelude.given

class PreludeSpec extends FunSuite:

  test("Equal[MajorVersion] should correctly compare MajorVersion instances") {
    assert(MajorVersion.fromUnsafe(1) === MajorVersion.fromUnsafe(1))
    assert(MajorVersion.fromUnsafe(1) !== MajorVersion.fromUnsafe(2))
  }
  test("Equal[MinorVersion] should correctly compare MinorVersion instances") {
    assert(MinorVersion.fromUnsafe(1) === MinorVersion.fromUnsafe(1))
    assert(MinorVersion.fromUnsafe(1) !== MinorVersion.fromUnsafe(2))
  }
  test("Equal[PatchNumber] should correctly compare PatchNumber instances") {
    assert(PatchNumber.fromUnsafe(1) === PatchNumber.fromUnsafe(1))
    assert(PatchNumber.fromUnsafe(1) !== PatchNumber.fromUnsafe(2))
  }
  test("Equal[PreReleaseNumber] should correctly compare PreReleaseNumber instances") {
    assert(PreReleaseNumber.fromUnsafe(1) === PreReleaseNumber.fromUnsafe(1))
    assert(PreReleaseNumber.fromUnsafe(1) !== PreReleaseNumber.fromUnsafe(2))
  }
  test("Equal[PreReleaseClassifier] should correctly compare PreReleaseClassifier instances") {
    assert(PreReleaseClassifier.Alpha === PreReleaseClassifier.Alpha)
    assert(PreReleaseClassifier.Alpha !== PreReleaseClassifier.Beta)
  }
  test("Equal[PreRelease] should correctly compare PreRelease instances") {
    assert(PreRelease.alpha(PreReleaseNumber.fromUnsafe(1)) === PreRelease.alpha(PreReleaseNumber.fromUnsafe(1)))
    assert(PreRelease.beta(PreReleaseNumber.fromUnsafe(1)) !== PreRelease.alpha(PreReleaseNumber.fromUnsafe(1)))
    assert(PreRelease.alpha(PreReleaseNumber.fromUnsafe(2)) !== PreRelease.alpha(PreReleaseNumber.fromUnsafe(1)))
  }

  test("Ord[MajorVersion] should correctly compare MajorVersion instances") {
    assert(MajorVersion.fromUnsafe(1) === MajorVersion.fromUnsafe(1))
    assert(!(MajorVersion.fromUnsafe(1) === MajorVersion.fromUnsafe(2)))
  }
  test("Ord[MinorVersion] should correctly compare MinorVersion instances") {
    assertEquals(MinorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(1))
    assert(MinorVersion.fromUnsafe(1) < MinorVersion.fromUnsafe(2))
  }

  test("Ord[PatchNumber] should correctly compare PatchNumber instances") {
    assertEquals(PatchNumber.fromUnsafe(1), PatchNumber.fromUnsafe(1))
    assert(PatchNumber.fromUnsafe(1) < PatchNumber.fromUnsafe(2))
  }

  test("Ord[PreReleaseNumber] should correctly compare PreReleaseNumber instances") {
    assertEquals(PreReleaseNumber.fromUnsafe(1), PreReleaseNumber.fromUnsafe(1))
    assert(PreReleaseNumber.fromUnsafe(1) < PreReleaseNumber.fromUnsafe(2))
  }

  test("Ord[PreReleaseClassifier] should correctly compare PreReleaseClassifier instances") {
    assertEquals(PreReleaseClassifier.Alpha, PreReleaseClassifier.Alpha)
    assert(PreReleaseClassifier.Alpha < PreReleaseClassifier.Beta)
  }

  test("Ord[PreRelease] should correctly compare PreRelease instances") {
    assertEquals(PreRelease.alpha(PreReleaseNumber.fromUnsafe(1)), PreRelease.alpha(PreReleaseNumber.fromUnsafe(1)))
    assert(PreRelease.alpha(PreReleaseNumber.fromUnsafe(1)) < PreRelease.beta(PreReleaseNumber.fromUnsafe(1)))
  }

  test("Ord[Version] should correctly compare Version instances") {
    assertEquals(
      Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(0), PatchNumber.fromUnsafe(0)),
      Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(0), PatchNumber.fromUnsafe(0))
    )
    assert(
      Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(0), PatchNumber.fromUnsafe(0)) <
        Version(MajorVersion.fromUnsafe(2), MinorVersion.fromUnsafe(0), PatchNumber.fromUnsafe(0))
    )
  }

  test("Commutative[MajorVersion] should correctly combine MajorVersion instances") {
    val expected = MajorVersion.fromUnsafe(3)
    val a = MajorVersion.fromUnsafe(1)
    val b = MajorVersion.fromUnsafe(2)
    assertEquals(a <> b, expected)
    assertEquals(b <> a, expected)
  }

  test("Commutative[MinorVersion] should correctly combine MinorVersion instances") {
    val expected = MinorVersion.fromUnsafe(3)
    val a = MinorVersion.fromUnsafe(1)
    val b = MinorVersion.fromUnsafe(2)
    assertEquals(a <> b, expected)
    assertEquals(b <> a, expected)
  }

  test("Commutative[PatchNumber] should correctly combine PatchNumber instances") {
    val expected = PatchNumber.fromUnsafe(3)
    val a = PatchNumber.fromUnsafe(1)
    val b = PatchNumber.fromUnsafe(2)
    assertEquals(a <> b, expected)
    assertEquals(b <> a, expected)
  }

  test("Commutative[PreReleaseNumber] should correctly combine PreReleaseNumber instances") {
    val expected = PreReleaseNumber.fromUnsafe(3)
    val a = PreReleaseNumber.fromUnsafe(1)
    val b = PreReleaseNumber.fromUnsafe(2)
    assertEquals(a <> b, expected)
    assertEquals(b <> a, expected)
  }
end PreludeSpec
