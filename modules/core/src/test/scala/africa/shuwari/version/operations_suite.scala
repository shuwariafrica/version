/*****************************************************************
 * Copyright © Shuwari Africa Ltd. All rights reserved.          *
 *                                                               *
 * Shuwari Africa Ltd. licenses this file to you under the terms *
 * of the Apache License Version 2.0 (the "License"); you may    *
 * not use this file except in compliance with the License. You  *
 * may obtain a copy of the License at:                          *
 *                                                               *
 *     https://www.apache.org/licenses/LICENSE-2.0               *
 *                                                               *
 * Unless required by applicable law or agreed to in writing,    *
 * software distributed under the License is distributed on an   *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,  *
 * either express or implied. See the License for the specific   *
 * language governing permissions and limitations under the      *
 * License.                                                      *
 *****************************************************************/
package africa.shuwari.version

class VersionOperationsSuite extends munit.FunSuite:
  private inline def zero = 0
  private inline def initial = 10
  private inline def incremented = 11

  private val testVersion =
    Version(MajorVersion.wrap(1), MinorVersion.wrap(1), PatchNumber.wrap(1), PreRelease.snapshot)

  test("Newtype[Int] instances provide an increment function.") {
    assertEquals(MajorVersion(incremented), MajorVersion.increment(MajorVersion(initial)))
    assertEquals(MinorVersion(incremented), MinorVersion.increment(MinorVersion(initial)))
    assertEquals(PatchNumber(incremented), PatchNumber.increment(PatchNumber(initial)))
    assertEquals(PreReleaseNumber(incremented), PreReleaseNumber.increment(PreReleaseNumber(initial)))
  }

  test("VersionNumberField instances provide a factory for zero.") {
    assertEquals(MajorVersion(zero), MajorVersion.zero)
    assertEquals(MinorVersion(zero), MinorVersion.zero)
    assertEquals(PatchNumber(zero), PatchNumber.zero)
//    assertEquals(PreReleaseNumber(zero), PreReleaseNumber.zero)
  }

  test("MajorVersion instances provide an unwrapping method.")(assertEquals(zero, MajorVersion.zero.value))

  test("MinorVersion instances provide an unwrapping method.")(assertEquals(zero, MinorVersion.zero.value))

  test("PatchNumber instances provide an unwrapping method.")(assertEquals(zero, PatchNumber.zero.value))

  test("PreReleaseNumber instances provide an unwrapping method.")(
    assertEquals(initial, PreReleaseNumber.wrap(10).value))

  test("MajorVersion instances provide an increment method.")(
    assertEquals(MajorVersion(incremented), MajorVersion(initial).increment))

  test("MinorVersion instances provide an increment method.")(
    assertEquals(MinorVersion(incremented), MinorVersion(initial).increment))

  test("PatchNumber instances provide an increment method.")(
    assertEquals(PatchNumber(incremented), PatchNumber(initial).increment))

  test("PreReleaseNumber instances provide an increment method.")(
    assertEquals(PreReleaseNumber(incremented), PreReleaseNumber(initial).increment))

  test("Version instances provide a 'MajorVersion' increment method.")(
    assertEquals(
      testVersion.incrementMajorVersion,
      Version(MajorVersion.wrap(2), MinorVersion.wrap(0), PatchNumber.wrap(0)))
  )

  test("Version instances provide a 'MinorVersion' increment method.")(
    assertEquals(
      testVersion.incrementMinorVersion,
      Version(MajorVersion.wrap(1), MinorVersion.wrap(2), PatchNumber.wrap(0)))
  )

  test("Version instances provide a 'MajorVersion' increment method.")(
    assertEquals(
      testVersion.incrementPatchNumber,
      Version(MajorVersion.wrap(1), MinorVersion.wrap(1), PatchNumber.wrap(2)))
  )
end VersionOperationsSuite
