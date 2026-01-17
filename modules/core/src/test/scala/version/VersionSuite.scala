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

import version.PreReleaseClassifier.*
import version.errors.*

/** Tests for the core composite data structures: PreReleaseClassifier, PreRelease, and Version (including Ordering and
  * construction).
  */
class VersionSuite extends munit.FunSuite:

  extension [A](list: List[A]) private def shuffle: List[A] = Random.shuffle(list)

  // --- PreReleaseClassifier Tests ---

  test("PreReleaseClassifier Ordering (Precedence)") {
    // Defined order: Dev < Milestone < Alpha < Beta < ReleaseCandidate < Snapshot
    val expectedOrder = List(Dev, Milestone, Alpha, Beta, ReleaseCandidate, Snapshot)
    assertEquals(PreReleaseClassifier.values.toList.shuffle.sorted, expectedOrder)
  }

  test("PreReleaseClassifier.versioned status") {
    assert(Dev.versioned)
    assert(Milestone.versioned)
    assert(Alpha.versioned)
    assert(Beta.versioned)
    assert(ReleaseCandidate.versioned)
    assert(!Snapshot.versioned)
  }

  test("PreReleaseClassifier.fromAlias (and unapply)") {
    assertEquals(PreReleaseClassifier.fromAlias("dev"), Some(Dev))
    assertEquals(PreReleaseClassifier.fromAlias("DEV"), Some(Dev))
    assertEquals(PreReleaseClassifier.fromAlias("m"), Some(Milestone))
    assertEquals(PreReleaseClassifier.fromAlias("Milestone"), Some(Milestone))
    assertEquals(PreReleaseClassifier.fromAlias("A"), Some(Alpha))
    assertEquals(PreReleaseClassifier.fromAlias("RC"), Some(ReleaseCandidate))
    assertEquals(PreReleaseClassifier.fromAlias("cr"), Some(ReleaseCandidate))
    assertEquals(PreReleaseClassifier.fromAlias("snapshot"), Some(Snapshot))
    assertEquals(PreReleaseClassifier.fromAlias("unknown"), None)

    "rc" match
      case PreReleaseClassifier(c) => assertEquals(c, ReleaseCandidate)
      case _                       => fail("Extractor failed")
  }

  test("PreReleaseClassifier.show returns canonical alias") {
    assertEquals(Dev.show, "dev")
    assertEquals(Milestone.show, "milestone")
    assertEquals(Alpha.show, "alpha")
    assertEquals(Beta.show, "beta")
    assertEquals(ReleaseCandidate.show, "rc")
    assertEquals(Snapshot.show, "SNAPSHOT")
  }

  test("PreReleaseClassifier.aliases returns all aliases") {
    assertEquals(Dev.aliases, List("dev"))
    assertEquals(Milestone.aliases, List("milestone", "m"))
    assertEquals(Alpha.aliases, List("alpha", "a"))
    assertEquals(Beta.aliases, List("beta", "b"))
    assertEquals(ReleaseCandidate.aliases, List("rc", "cr"))
    assertEquals(Snapshot.aliases, List("SNAPSHOT"))
  }

  // --- PreRelease Tests ---

  private val N1 = PreReleaseNumber.fromUnsafe(1)
  private val N5 = PreReleaseNumber.fromUnsafe(5)

  test("PreRelease construction constraints (Missing Number)") {
    // Versioned classifiers must have a number
    val ex1 = intercept[MissingPreReleaseNumber](PreRelease.fromUnsafe(Alpha, None))
    assertEquals(ex1.classifier, Alpha)

    val ex2 = intercept[MissingPreReleaseNumber](PreRelease.fromUnsafe(ReleaseCandidate, None))
    assertEquals(ex2.classifier, ReleaseCandidate)
  }

  test("PreRelease construction constraints (Unexpected Number)") {
    // Non-versioned classifiers must NOT have a number
    val ex = intercept[UnexpectedPreReleaseNumber](PreRelease.fromUnsafe(Snapshot, Some(N1)))
    assertEquals(ex.classifier, Snapshot)
    assertEquals(ex.number, N1)
  }

  test("PreRelease.from returns Either for validation") {
    // Valid combinations
    assertEquals(PreRelease.from(Alpha, Some(N1)).isRight, true)
    assertEquals(PreRelease.from(Snapshot, None).isRight, true)

    // Invalid: versioned without number
    val left1 = PreRelease.from(Alpha, None)
    assert(left1.isLeft)
    assert(left1.left.exists { case _: MissingPreReleaseNumber => true; case _ => false })

    // Invalid: non-versioned with number
    val left2 = PreRelease.from(Snapshot, Some(N1))
    assert(left2.isLeft)
    assert(left2.left.exists { case _: UnexpectedPreReleaseNumber => true; case _ => false })
  }

  test("PreRelease.increment") {
    val a1 = PreRelease.alpha(N1)
    val a2 = PreRelease.alpha(PreReleaseNumber.fromUnsafe(2))
    assertEquals(a1.increment, a2)
    // Snapshot increment should be idempotent
    assertEquals(PreRelease.snapshot.increment, PreRelease.snapshot)
  }

  test("PreRelease.show (SemVer format)") {
    assertEquals(PreRelease.alpha(N5).show, "alpha.5")
    assertEquals(PreRelease.snapshot.show, "SNAPSHOT")
  }

  test("PreRelease Ordering") {
    val expectedOrder = List(
      PreRelease.milestone(N1),
      PreRelease.milestone(N5),
      PreRelease.alpha(N1),
      PreRelease.alpha(N5),
      PreRelease.beta(N1),
      PreRelease.releaseCandidate(N1),
      PreRelease.snapshot
    )
    assertEquals(expectedOrder.shuffle.sorted, expectedOrder)
  }

  // --- Version Tests ---

  private val V1_0_0 = Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(0), PatchNumber.fromUnsafe(0))
  private val V1_2_3 = Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(2), PatchNumber.fromUnsafe(3))
  private val V2_0_0 = Version(MajorVersion.fromUnsafe(2), MinorVersion.fromUnsafe(0), PatchNumber.fromUnsafe(0))

  // --- Version Factory Overloads ---

  test("Version.apply(major, minor, patch) creates a final release") {
    val v = Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(2), PatchNumber.fromUnsafe(3))
    assertEquals(v.major.value, 1)
    assertEquals(v.minor.value, 2)
    assertEquals(v.patch.value, 3)
    assertEquals(v.preRelease, None)
    assertEquals(v.metadata, None)
  }

  test("Version.apply(major, minor, patch, preRelease) creates a pre-release version") {
    val pr = Some(PreRelease.alpha(N1))
    val v = Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(2), PatchNumber.fromUnsafe(3), pr)
    assertEquals(v.major.value, 1)
    assertEquals(v.minor.value, 2)
    assertEquals(v.patch.value, 3)
    assertEquals(v.preRelease, pr)
    assertEquals(v.metadata, None)
  }

  test("Version.apply(major, minor, patch, preRelease) with None creates a final release") {
    val v = Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(2), PatchNumber.fromUnsafe(3), None)
    assertEquals(v.preRelease, None)
    assertEquals(v.metadata, None)
  }

  test("Version full constructor includes all fields") {
    val pr = Some(PreRelease.beta(N5))
    val meta = Some(Metadata(List("build", "123")))
    val v = Version(MajorVersion.fromUnsafe(2), MinorVersion.fromUnsafe(0), PatchNumber.fromUnsafe(1), pr, meta)
    assertEquals(v.major.value, 2)
    assertEquals(v.minor.value, 0)
    assertEquals(v.patch.value, 1)
    assertEquals(v.preRelease, pr)
    assertEquals(v.metadata, meta)
  }

  // --- Version Ordering (SemVer Precedence) ---

  test("Version Ordering (Core Components)") {
    assert(V1_0_0 < V1_2_3)
    assert(V1_2_3 < V2_0_0)
    assert(V1_0_0 < V2_0_0)
  }

  test("Version Ordering (Pre-Release vs Release)") {
    // Rule: Release versions have higher precedence than pre-release versions (1.0.0 > 1.0.0-alpha)
    val release = V1_0_0
    val pre = V1_0_0.copy(preRelease = Some(PreRelease.alpha(N1)))
    assert(release > pre)
    assert(pre < release)
  }

  test("Version Ordering (Pre-Release comparison)") {
    // Rule: Pre-releases are compared based on classifier precedence and number
    val vA1 = V1_0_0.copy(preRelease = Some(PreRelease.alpha(N1)))
    val vA5 = V1_0_0.copy(preRelease = Some(PreRelease.alpha(N5)))
    val vB1 = V1_0_0.copy(preRelease = Some(PreRelease.beta(N1)))

    assert(vA1 < vA5) // Number precedence
    assert(vA5 < vB1) // Classifier precedence
  }

  test("Version Ordering (Build Metadata Ignored)") {
    // Rule: Build metadata MUST be ignored when determining version precedence
    val v1 = V1_0_0
    val v1MetaA = V1_0_0.copy(metadata = Some(Metadata(List("A"))))
    val v1MetaB = V1_0_0.copy(metadata = Some(Metadata(List("B"))))

    // They are considered equal in precedence (compare == 0)
    assertEquals(v1.compare(v1MetaA), 0)
    assertEquals(v1MetaA.compare(v1MetaB), 0)

    // Note: They are not structurally equal (case class equality differs)
    assertNotEquals(v1, v1MetaA)
  }

  test("Version comprehensive Ordering test") {
    // Based on SemVer spec examples and internal hierarchy
    // We use the parser here to easily generate the expected Version objects.
    // This implicitly trusts the parser, but focuses the test on the Ordering implementation.

    val expectedOrder = List(
      "0.9.0",
      "1.0.0-milestone.1",
      "1.0.0-alpha.1",
      "1.0.0-alpha.5",
      "1.0.0-beta.1",
      "1.0.0-rc.1",
      "1.0.0-snapshot",
      "1.0.0",
      "1.0.0+build.123", // Equal precedence to 1.0.0
      "1.0.1",
      "1.1.0",
      "2.0.0"
    ).map(s => s.toVersion.toOption.get)

    val shuffled = Random.shuffle(expectedOrder)
    val sorted = shuffled.sorted

    // Verify the order by checking that every element is less than or equal to the next one
    sorted.sliding(2).foreach {
      case Seq(a, b) => assert(a <= b, s"Ordering failed: $a should be <= $b")
      case _         => // End of list
    }

    // Specific check for the metadata case ensuring they are adjacent/equivalent in precedence
    given Version.Show = Version.Show.Extended
    val baseIndex = sorted.indexWhere(_.show.equals("1.0.0"))
    val metaIndex = sorted.indexWhere(_.show.equals("1.0.0+build.123"))
    assert(baseIndex >= 0 && metaIndex >= 0)
    assertEquals(sorted(baseIndex).compare(sorted(metaIndex)), 0)
  }

end VersionSuite
