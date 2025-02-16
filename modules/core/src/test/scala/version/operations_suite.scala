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
package version

class VersionOperationsSuite extends munit.FunSuite:
  private inline def zero = 0
  private inline def testValue = 10
  private inline def incremented = 11

  private val testVersion =
    Version(MajorVersion(1), MinorVersion(1), PatchNumber(1), PreRelease.snapshot)

  test("Newtype[Int] instances provide an increment function.") {
    assertEquals(MajorVersion(incremented), MajorVersion.increment(MajorVersion(testValue)))
    assertEquals(MinorVersion(incremented), MinorVersion.increment(MinorVersion(testValue)))
    assertEquals(PatchNumber(incremented), PatchNumber.increment(PatchNumber(testValue)))
    assertEquals(PreReleaseNumber(incremented), PreReleaseNumber.increment(PreReleaseNumber(testValue)))
  }

  test("VersionNumberField instances provide a factory for zero.") {
    assertEquals(MajorVersion(zero), MajorVersion.initial)
    assertEquals(MinorVersion(zero), MinorVersion.initial)
    assertEquals(PatchNumber(zero), PatchNumber.initial)
//    assertEquals(PreReleaseNumber(zero), PreReleaseNumber.zero)
  }

  test("MajorVersion instances provide a method to determine if value is zero.") {
    assertEquals(MajorVersion(zero) == MajorVersion.initial, true) // scalafix:ok
    assertEquals(MajorVersion(testValue) == MajorVersion.initial, false) // scalafix:ok
  }

  test("MajorVersion instances provide an unwrapping method.")(assertEquals(zero, MajorVersion.initial.value))

  test("MinorVersion instances provide an unwrapping method.")(assertEquals(zero, MinorVersion.initial.value))

  test("PatchNumber instances provide an unwrapping method.")(assertEquals(zero, PatchNumber.initial.value))

  test("PreReleaseNumber instances provide an unwrapping method.")(assertEquals(testValue, PreReleaseNumber(10).value))

  test("MajorVersion instances provide an increment method.")(assertEquals(MajorVersion(incremented), MajorVersion(testValue).increment))

  test("MinorVersion instances provide an increment method.")(assertEquals(MinorVersion(incremented), MinorVersion(testValue).increment))

  test("PatchNumber instances provide an increment method.")(assertEquals(PatchNumber(incremented), PatchNumber(testValue).increment))

  test("PreReleaseNumber instances provide an increment method.")(
    assertEquals(PreReleaseNumber(incremented), PreReleaseNumber(testValue).increment))

  test("Version instances provide a 'MajorVersion' increment method.")(
    assertEquals(testVersion.incrementMajorVersion, Version(MajorVersion(2), MinorVersion(0), PatchNumber(0)))
  )

  test("Version instances provide a 'MinorVersion' increment method.")(
    assertEquals(testVersion.incrementMinorVersion, Version(MajorVersion(1), MinorVersion(2), PatchNumber(0)))
  )

  test("Version instances provide a 'MajorVersion' increment method.")(
    assertEquals(testVersion.incrementPatchNumber, Version(MajorVersion(1), MinorVersion(1), PatchNumber(2)))
  )
end VersionOperationsSuite
