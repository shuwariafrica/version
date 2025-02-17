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

  test("VersionNumberField instances provide a factory for the respective initial supported version.") {
    assertEquals(MajorVersion.reset, MajorVersion(zero))
    assertEquals(MinorVersion.reset, MinorVersion(zero))
    assertEquals(PatchNumber.reset, PatchNumber(zero))
    assertEquals(PreReleaseNumber.reset, PreReleaseNumber(zero + 1))
  }

  test("MajorVersion instances provide an unwrapping method.")(assertEquals(MajorVersion.reset.value, zero))

  test("MinorVersion instances provide an unwrapping method.")(assertEquals(MinorVersion.reset.value, zero))

  test("PatchNumber instances provide an unwrapping method.")(assertEquals(PatchNumber.reset.value, zero))

  test("PreReleaseNumber instances provide an unwrapping method.")(assertEquals(PreReleaseNumber(10).value, testValue))

  test("MajorVersion instances provide an increment method.")(assertEquals(MajorVersion(testValue).increment, MajorVersion(incremented)))

  test("MinorVersion instances provide an increment method.")(assertEquals(MinorVersion(testValue).increment, MinorVersion(incremented)))

  test("PatchNumber instances provide an increment method.")(assertEquals(PatchNumber(testValue).increment, PatchNumber(incremented)))

  test("PreReleaseNumber instances provide an increment method.")(
    assertEquals(PreReleaseNumber(testValue).increment, PreReleaseNumber(incremented)))

  test("Version instances provide a 'MajorVersion' increment method.")(
    assertEquals(testVersion.incrementMajorVersion, Version(MajorVersion(2), MinorVersion(0), PatchNumber(0)))
  )

  test("Version instances provide a 'MinorVersion' increment method.")(
    assertEquals(testVersion.incrementMinorVersion, Version(MajorVersion(1), MinorVersion(2), PatchNumber(0)))
  )

  test("Version instances provide a 'PatchNumber' increment method.")(
    assertEquals(testVersion.incrementPatchNumber, Version(MajorVersion(1), MinorVersion(1), PatchNumber(2)))
  )

  test("PreReleaseClassifier instances provide a method to retrieve aliases.") {
    assertEquals(PreReleaseClassifier.Milestone.aliases.sorted, List("milestone", "m").sorted)
    assertEquals(PreReleaseClassifier.Alpha.aliases.sorted, List("alpha", "a").sorted)
    assertEquals(PreReleaseClassifier.Beta.aliases.sorted, List("beta", "b").sorted)
    assertEquals(PreReleaseClassifier.ReleaseCandidate.aliases.sorted, List("rc", "cr").sorted)
    assertEquals(PreReleaseClassifier.Snapshot.aliases.sorted, List("snapshot").sorted)
    assertEquals(PreReleaseClassifier.Unclassified.aliases, List("unclassified"))
  }

  test("PreReleaseClassifier instances provide a method to check versioned status.") {
    PreReleaseClassifier.values.foreach { classifier =>
      val expected = classifier != PreReleaseClassifier.Snapshot && classifier != PreReleaseClassifier.Unclassified // scalafix:ok
      assertEquals(classifier.versioned, expected)
    }
  }

  test("PreRelease instances provide methods to determine their classifier.") {
    val preRelease = PreRelease.milestone(PreReleaseNumber(1))
    assert(preRelease.milestone)
    assert(!preRelease.alpha)
    assert(!preRelease.beta)
    assert(!preRelease.rc)
    assert(!preRelease.snapshot)
    assert(!preRelease.unclassified)
  }

end VersionOperationsSuite
