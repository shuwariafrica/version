package version

import scala.util.Random

import version.errors.*

/** Tests the common behaviour of the OpaqueVersionComponent implementations using a generic test structure. */
class ComponentOpaqueTypeSuite extends munit.FunSuite:

  // Define the expected behaviour for each component type using abstract type members
  trait ComponentTestSpec:
    type T
    type E <: InvalidComponent
    val comp: ResetableOpaqueVersionComponent[T, E]
    val expectedMin: Int
    val expectedError: Int => E
    val expectedFieldName: String

  val specs: List[ComponentTestSpec] = List(
    new ComponentTestSpec:
      type T = MajorVersion; type E = InvalidMajorVersion
      val comp = MajorVersion
      val expectedMin = 0
      val expectedError = InvalidMajorVersion.apply
      val expectedFieldName = "Major"
    ,
    new ComponentTestSpec:
      type T = MinorVersion; type E = InvalidMinorVersion
      val comp = MinorVersion
      val expectedMin = 0
      val expectedError = InvalidMinorVersion.apply
      val expectedFieldName = "Minor"
    ,
    new ComponentTestSpec:
      type T = PatchNumber; type E = InvalidPatchNumber
      val comp = PatchNumber
      val expectedMin = 0
      val expectedError = InvalidPatchNumber.apply
      val expectedFieldName = "Patch"
    ,
    // PreReleaseNumber minimum is 1
    new ComponentTestSpec:
      type T = PreReleaseNumber; type E = InvalidPreReleaseNumber
      val comp = PreReleaseNumber
      val expectedMin = 1
      val expectedError = InvalidPreReleaseNumber.apply
      val expectedFieldName = "PreReleaseNumber"
  )

  // --- Generic Tests applied to all components ---

  specs.foreach { s =>
    // Extract the simple name of the component object for the test description
    val componentName = s.comp.getClass.getSimpleName.stripSuffix("$")

    test(s"$componentName - should have the correct minimum and reset values") {
      // Compare opaque values directly without relying on extension methods
      assertEquals(s.comp.minimum, s.comp.unsafe(s.expectedMin))
      assertEquals(s.comp.reset, s.comp.minimum)
    }

    test(s"$componentName - unsafe should succeed for valid values (min, mid, max)") {
      // Ensures no exceptions are thrown for valid inputs
      assertEquals(s.comp.unsafe(s.expectedMin), s.comp.unsafe(s.expectedMin))
      assertEquals(s.comp.unsafe(10), s.comp.unsafe(10))
      assertEquals(s.comp.unsafe(Int.MaxValue), s.comp.unsafe(Int.MaxValue))
    }

    test(s"$componentName - unsafe should throw the correct exception for invalid values (below min)") {
      val invalidValue = s.expectedMin - 1
      val ex = intercept[Throwable] {
        s.comp.unsafe(invalidValue)
      }
      assertEquals(ex, s.expectedError(invalidValue))
    }

    test(s"$componentName - from(Int) should succeed for valid values") {
      assertEquals(s.comp.from(s.expectedMin), Right(s.comp.unsafe(s.expectedMin)))
      assertEquals(s.comp.from(10), Right(s.comp.unsafe(10)))
    }

    test(s"$componentName - from(Int) should return Left(E) for invalid values") {
      val invalidValue = s.expectedMin - 1
      assertEquals(s.comp.from(invalidValue), Left(s.expectedError(invalidValue)))
    }

    test(s"$componentName - from(String) should succeed for valid strings") {
      assertEquals(s.comp.from(s.expectedMin.toString), Right(s.comp.unsafe(s.expectedMin)))
      assertEquals(s.comp.from("10"), Right(s.comp.unsafe(10)))
    }

    test(s"$componentName - from(String) should return Left(InvalidNumericField) for values below minimum") {
      val invalidValue = (s.expectedMin - 1).toString
      assertEquals(
        s.comp.from(invalidValue),
        Left(InvalidNumericField(s.expectedFieldName, invalidValue))
      )
    }

    test(s"$componentName - from(String) should return Left(InvalidNumericField) for invalid formats") {
      assertEquals(s.comp.from("abc"), Left(InvalidNumericField(s.expectedFieldName, "abc")))
      assertEquals(s.comp.from("1.5"), Left(InvalidNumericField(s.expectedFieldName, "1.5")))
    }

    test(s"$componentName - from(String) should return Left(InvalidNumericField) for integer overflows") {
      val overflow = (BigInt(Int.MaxValue) + 1).toString
      assertEquals(s.comp.from(overflow), Left(InvalidNumericField(s.expectedFieldName, overflow)))
    }

    test(s"$componentName - increment should increase the value by one") {
      val initial = s.comp.unsafe(10)
      // Call the extension method through the component to avoid scope issues
      val incremented = s.comp.increment(initial)
      assertEquals(incremented, s.comp.unsafe(11))
    }

    test(s"$componentName - should have correct Ordering") {
      val numbers = List(s.expectedMin, s.expectedMin + 1, 5, 10, 100).filter(_ >= s.expectedMin)
      val components = numbers.map(s.comp.unsafe)
      // Import givens (including Ordering) provided by the component implementation
      import s.comp.given
      assertEquals(Random.shuffle(components).sorted, components)
    }
  }

  // --- Specific Tests ---

  test("MajorVersion.isStable should be true for > 0 and false for 0") {
    assert(MajorVersion.unsafe(1).isStable)
    assert(!MajorVersion.unsafe(0).isStable)
  }

end ComponentOpaqueTypeSuite
