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

import scala.util.Random

import version.errors.InvalidNumberedPreRelease
import version.errors.InvalidSnapshotPreRelease

class VersionSuite extends munit.FunSuite:

  private val naturals = List(0, 1, 2, 3, 4, 5, 7, 9, 30, 37, 420, 1029, 1030)
  extension [A](n: List[A]) def shuffle: List[A] = Random.shuffle(n)

  private def nonNatural = () => -1

  test("MajorVersion has correct Ordering") {
    val majorVersions = naturals.map(MajorVersion.apply)
    assertEquals(majorVersions, majorVersions.shuffle.sorted)
    assertEquals(majorVersions.sorted, majorVersions)
  }

  test("MajorVersion has to be a Natural number") {
    val majorVersions = naturals.map(MajorVersion.make)
    val invalid = MajorVersion.make(nonNatural.apply)
    assert(invalid.toOption.isEmpty)
    assert(majorVersions.forall(_.toOption.nonEmpty))
  }

  test("MinorVersion has correct Ordering") {
    val minorVersions = naturals.map(MinorVersion.apply)
    assertEquals(minorVersions.shuffle.sorted, minorVersions)
    assertEquals(minorVersions.sorted, minorVersions)
  }

  test("MinorVersion has to be a Natural number") {
    val minorVersions = naturals.map(MinorVersion.make)
    val invalid = MinorVersion.make(nonNatural.apply)
    assert(invalid.toOption.isEmpty)
    assert(minorVersions.forall(_.toOption.nonEmpty))
  }

  test("PatchNumber has correct Ordering") {
    val patchNumbers = naturals.map(PatchNumber.apply)
    assertEquals(patchNumbers.shuffle.sorted, patchNumbers)
    assertEquals(patchNumbers.sorted, patchNumbers)
  }

  test("PatchNumber has to be a Natural number") {
    val patchNumbers = naturals.map(PatchNumber.make)
    val invalid = PatchNumber.make(nonNatural.apply)
    assert(invalid.toOption.isEmpty)
    assert(patchNumbers.forall(_.toOption.nonEmpty))
  }

  test("PreReleaseNumber has correct Ordering") {
    val preReleaseNumbers = naturals.drop(1).map(PreReleaseNumber.apply)
    assertEquals(preReleaseNumbers.shuffle.sorted, preReleaseNumbers)
    assertEquals(preReleaseNumbers.sorted, preReleaseNumbers)
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
    assertEquals(classifiers.shuffle.sorted, classifiers)
    assertEquals(classifiers.sorted, classifiers)
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
    assertEquals(preReleases.shuffle.sorted, preReleases)
    assertEquals(preReleases.sorted, preReleases)
  }

  test("PreRelease instances cannot be initialised with PreReleaseNumbers for Snapshot releases") {
    type E = InvalidSnapshotPreRelease
    intercept[E](PreRelease(PreReleaseClassifier.Snapshot, Some(PreReleaseNumber.apply(1))))
  }

  test("PreRelease instances cannot be initialised with PreReleaseNumbers for Unclassified releases") {
    type E = InvalidSnapshotPreRelease
    intercept[E](PreRelease(PreReleaseClassifier.Unclassified, Some(PreReleaseNumber.apply(1))))
  }

  test("PreRelease instances cannot be initialised without PreReleaseNumbers for Milestone releases") {
    type E = InvalidNumberedPreRelease
    intercept[E](PreRelease(PreReleaseClassifier.Milestone, None))
  }

  test("PreRelease instances cannot be initialised without PreReleaseNumbers for Alpha releases") {
    type E = InvalidNumberedPreRelease
    intercept[E](PreRelease(PreReleaseClassifier.Alpha, None))
  }
  test("PreRelease instances cannot be initialised without PreReleaseNumbers for Beta releases") {
    type E = InvalidNumberedPreRelease
    intercept[E](PreRelease(PreReleaseClassifier.Beta, None))
  }

  test("PreRelease instances cannot be initialised without PreReleaseNumbers for ReleaseCandidate releases") {
    type E = InvalidNumberedPreRelease
    intercept[E](PreRelease(PreReleaseClassifier.ReleaseCandidate, None))
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
    assertEquals(versions.shuffle.sorted, versions)
    assertEquals(versions.sorted, versions)
  }

  test("Stable versions identified correctly") {
    val version = Version(MajorVersion(0), MinorVersion(1), PatchNumber(0), PreRelease.milestone(PreReleaseNumber(10)))
    assertEquals(version.stable, false)
    assertEquals(version.copy(preRelease = None).stable, true)
  }

end VersionSuite
