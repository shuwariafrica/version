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
package version

import scala.util.Random

import version.errors.*

/** Tests the common behaviour of the VersionComponent implementations using a generic test structure. */
class ComponentOpaqueTypeSuite extends munit.FunSuite:

  // Define the expected behaviour for each component type using abstract type members
  trait ComponentTestSpec:
    type T
    type E <: InvalidComponent
    val comp: ResettableVersionComponent[T] { type Error = E }
    val expectedMin: Int
    val expectedError: Int => E

  val specs: List[ComponentTestSpec] = List(
    new ComponentTestSpec:
      type T = MajorVersion; type E = InvalidMajorVersion
      val comp = MajorVersion
      val expectedMin = 0
      val expectedError = InvalidMajorVersion.apply
    ,
    new ComponentTestSpec:
      type T = MinorVersion; type E = InvalidMinorVersion
      val comp = MinorVersion
      val expectedMin = 0
      val expectedError = InvalidMinorVersion.apply
    ,
    new ComponentTestSpec:
      type T = PatchNumber; type E = InvalidPatchNumber
      val comp = PatchNumber
      val expectedMin = 0
      val expectedError = InvalidPatchNumber.apply
    ,
    // PreReleaseNumber minimum is 1
    new ComponentTestSpec:
      type T = PreReleaseNumber; type E = InvalidPreReleaseNumber
      val comp = PreReleaseNumber
      val expectedMin = 1
      val expectedError = InvalidPreReleaseNumber.apply
  )

  // --- Generic Tests applied to all components ---

  specs.foreach { s =>
    // Extract the simple name of the component object for the test description
    val componentName = s.comp.getClass.getSimpleName.stripSuffix("$")

    test(s"$componentName - should have the correct minimum and reset values") {
      // Compare opaque values directly without relying on extension methods
      assertEquals(s.comp.minimum, s.comp.fromUnsafe(s.expectedMin))
      assertEquals(s.comp.reset, s.comp.minimum)
    }

    test(s"$componentName - fromUnsafe should succeed for valid values (min, mid, max)") {
      // Ensures no exceptions are thrown for valid inputs
      assertEquals(s.comp.fromUnsafe(s.expectedMin), s.comp.fromUnsafe(s.expectedMin))
      assertEquals(s.comp.fromUnsafe(10), s.comp.fromUnsafe(10))
      assertEquals(s.comp.fromUnsafe(Int.MaxValue), s.comp.fromUnsafe(Int.MaxValue))
    }

    test(s"$componentName - fromUnsafe should throw the correct exception for invalid values (below min)") {
      val invalidValue = s.expectedMin - 1
      val ex = intercept[Throwable] {
        s.comp.fromUnsafe(invalidValue)
      }
      assertEquals(ex, s.expectedError(invalidValue))
    }

    test(s"$componentName - from(Int) should succeed for valid values") {
      assertEquals(s.comp.from(s.expectedMin), Right(s.comp.fromUnsafe(s.expectedMin)))
      assertEquals(s.comp.from(10), Right(s.comp.fromUnsafe(10)))
    }

    test(s"$componentName - from(Int) should return Left(E) for invalid values") {
      val invalidValue = s.expectedMin - 1
      assertEquals(s.comp.from(invalidValue), Left(s.expectedError(invalidValue)))
    }

    test(s"$componentName - increment should increase the value by one") {
      val initial = s.comp.fromUnsafe(10)
      // Use the increment extension method
      val incremented = s.comp.increment(initial)
      assertEquals(incremented, s.comp.fromUnsafe(11))
    }

    test(s"$componentName - should have correct Ordering") {
      val numbers = List(s.expectedMin, s.expectedMin + 1, 5, 10, 100).filter(_ >= s.expectedMin)
      val components = numbers.map(s.comp.fromUnsafe)
      // Use the given Ordering from the companion
      import s.comp.given
      assertEquals(Random.shuffle(components).sorted, components)
    }
  }

  // --- Specific Tests ---

  test("MajorVersion.isStable should be true for > 0 and false for 0") {
    assert(MajorVersion.fromUnsafe(1).isStable)
    assert(!MajorVersion.fromUnsafe(0).isStable)
  }

end ComponentOpaqueTypeSuite
