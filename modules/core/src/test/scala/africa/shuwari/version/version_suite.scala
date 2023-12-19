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

import scala.util.Random

class VersionSuite extends munit.FunSuite:

  private val naturals = List(0, 1, 2, 3, 4, 5, 7, 9, 30, 37, 420, 1029, 1030)
  extension [A](n: List[A]) def shuffle: List[A] = Random.shuffle(n)

  private def nonNatural = () => -1

  test("MajorVersion has correct Ordering") {
    val majorVersions = naturals.map(MajorVersion.wrap)
    assertEquals(majorVersions, majorVersions.shuffle.sorted)
    assertEquals(majorVersions, majorVersions.sorted)
  }

  test("MajorVersion has to be a Natural number") {
    val majorVersions = naturals.map(MajorVersion.make)
    val invalid = MajorVersion.make(nonNatural.apply)
    assert(invalid.toOption.isEmpty)
    assert(majorVersions.forall(_.toOption.nonEmpty))
  }

  test("MinorVersion has correct Ordering") {
    val minorVersions = naturals.map(MinorVersion.wrap)
    assertEquals(minorVersions.shuffle.sorted, minorVersions)
    assertEquals(minorVersions, minorVersions.sorted)
  }

  test("MinorVersion has to be a Natural number") {
    val minorVersions = naturals.map(MinorVersion.make)
    val invalid = MinorVersion.make(nonNatural.apply)
    assert(invalid.toOption.isEmpty)
    assert(minorVersions.forall(_.toOption.nonEmpty))
  }

  test("PatchNumber has correct Ordering") {
    val patchNumbers = naturals.map(PatchNumber.wrap)
    assertEquals(patchNumbers.shuffle.sorted, patchNumbers)
    assertEquals(patchNumbers, patchNumbers.sorted)
  }

  test("PatchNumber has to be a Natural number") {
    val patchNumbers = naturals.map(PatchNumber.make)
    val invalid = PatchNumber.make(nonNatural.apply)
    assert(invalid.toOption.isEmpty)
    assert(patchNumbers.forall(_.toOption.nonEmpty))
  }

  test("PreReleaseNumber has correct Ordering") {
    val preReleaseNumbers = naturals.map(PreReleaseNumber.wrap)
    assertEquals(preReleaseNumbers.shuffle.sorted, preReleaseNumbers.sorted)
    assertEquals(preReleaseNumbers, preReleaseNumbers.sorted)
  }

  test("PreReleaseNumber has to be a Natural number greater than zero") {
    val preReleaseNumbers = naturals.drop(1).map(PreReleaseNumber.make)
    val invalid = PreReleaseNumber.make(nonNatural.apply)
    assert(invalid.toOption.isEmpty)
    assert(preReleaseNumbers.forall(_.toOption.nonEmpty))
  }

  test("PreReleaseClassifier has correct Ordering") {
    val classifiers = List(
      PreReleaseClassifier.Milestone,
      PreReleaseClassifier.Alpha,
      PreReleaseClassifier.Beta,
      PreReleaseClassifier.ReleaseCandidate,
      PreReleaseClassifier.Snapshot
    )
    assertEquals(classifiers.shuffle.sorted, classifiers.sorted)
    assertEquals(classifiers, classifiers.sorted)
  }

  test("PreRelease has correct Ordering") {
    val preReleases = List(
      PreRelease.milestone(PreReleaseNumber(100)),
      PreRelease.milestone(PreReleaseNumber(101)),
      PreRelease.milestone(PreReleaseNumber(102)),
      PreRelease.alpha(PreReleaseNumber(90)),
      PreRelease.alpha(PreReleaseNumber(91)),
      PreRelease.alpha(PreReleaseNumber(100)),
      PreRelease.alpha(PreReleaseNumber(101)),
      PreRelease.beta(PreReleaseNumber(80)),
      PreRelease.beta(PreReleaseNumber(81)),
      PreRelease.beta(PreReleaseNumber(90)),
      PreRelease.beta(PreReleaseNumber(91)),
      PreRelease.releaseCandidate(PreReleaseNumber(70)),
      PreRelease.releaseCandidate(PreReleaseNumber(71)),
      PreRelease.releaseCandidate(PreReleaseNumber(80)),
      PreRelease.releaseCandidate(PreReleaseNumber(81)),
      PreRelease.snapshot
    )
    assertEquals(preReleases, preReleases.shuffle.sorted)
    assertEquals(preReleases, preReleases.sorted)
  }

  test("Version has correct Ordering") {
    val versions = List(
      Version(MajorVersion(0), MinorVersion(1), PatchNumber(0), PreRelease.milestone(PreReleaseNumber(10))),
      Version(MajorVersion(0), MinorVersion(1), PatchNumber(0), PreRelease.alpha(PreReleaseNumber(9))),
      Version(MajorVersion(0), MinorVersion(1), PatchNumber(0), PreRelease.beta(PreReleaseNumber(8))),
      Version(MajorVersion(0), MinorVersion(1), PatchNumber(0), PreRelease.releaseCandidate(PreReleaseNumber(7))),
      Version(MajorVersion(0), MinorVersion(1), PatchNumber(0), PreRelease.snapshot),
      Version(MajorVersion(0), MinorVersion(1), PatchNumber(0)),
      Version(MajorVersion(0), MinorVersion(1), PatchNumber(5)),
      Version(MajorVersion(0), MinorVersion(4), PatchNumber(5)),
      Version(MajorVersion(1), MinorVersion(4), PatchNumber(5))
    )
    assertEquals(versions, versions.shuffle.sorted)
    assertEquals(versions, versions.sorted)
  }

  test("Stable versions identified correctly") {
    val version = Version(MajorVersion(0), MinorVersion(1), PatchNumber(0), PreRelease.milestone(PreReleaseNumber(10)))
    assertEquals(version.stable, false)
    assertEquals(version.copy(preRelease = None).stable, true)
  }

end VersionSuite
