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

import version.PreReleaseClassifier.*
import version.errors.*

/** Tests the ergonomic API provided by the extension methods in [[Version$ Version]]. */
class VersionOperationsSuite extends munit.FunSuite:

  // --- Fixtures ---
  private val V1_2_3 = Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(2), PatchNumber.fromUnsafe(3))
  private val V0_1_0 = Version(MajorVersion.fromUnsafe(0), MinorVersion.fromUnsafe(1), PatchNumber.fromUnsafe(0))
  private val V1_2_3_D1 = V1_2_3.copy(preRelease = Some(PreRelease.dev(PreReleaseNumber.fromUnsafe(1))))
  private val V1_2_3_M1 = V1_2_3.copy(preRelease = Some(PreRelease.milestone(PreReleaseNumber.fromUnsafe(1))))
  private val V1_2_3_A5 = V1_2_3.copy(preRelease = Some(PreRelease.alpha(PreReleaseNumber.fromUnsafe(5))))
  private val V1_2_3_B1 = V1_2_3.copy(preRelease = Some(PreRelease.beta(PreReleaseNumber.fromUnsafe(1))))
  private val V1_2_3_RC1 = V1_2_3.copy(preRelease = Some(PreRelease.releaseCandidate(PreReleaseNumber.fromUnsafe(1))))
  private val V1_2_3_Snap = V1_2_3.as[Snapshot]
  private val TestMetadata = Metadata(List("build123"))
  private val V1_2_3_Meta = V1_2_3.copy(metadata = Some(TestMetadata))
  private val V1_2_3_RC1_Meta = V1_2_3_RC1.copy(metadata = Some(TestMetadata))

  // --- Status Checks ---

  test("stable (major > 0 and not snapshot)") {
    assert(V1_2_3.stable)
    assert(V1_2_3_A5.stable) // Pre-release but not snapshot
    assert(V1_2_3_RC1.stable)
    assert(!V0_1_0.stable) // Major = 0
    assert(!V1_2_3_Snap.stable) // Snapshot
    assert(!V0_1_0.as[Snapshot].stable) // Both major = 0 and snapshot
  }

  test("snapshot") {
    assert(V1_2_3_Snap.snapshot)
    assert(!V1_2_3.snapshot)
    assert(!V1_2_3_A5.snapshot)
  }

  test("PreRelease extensions (isDev, isAlpha, isSnapshot etc.)") {
    assert(V1_2_3_D1.preRelease.get.isDev)
    assert(V1_2_3_M1.preRelease.get.isMilestone)
    assert(V1_2_3_A5.preRelease.get.isAlpha)
    assert(V1_2_3_B1.preRelease.get.isBeta)
    assert(V1_2_3_RC1.preRelease.get.isReleaseCandidate)
    assert(V1_2_3_Snap.preRelease.get.isSnapshot)
    assert(!V1_2_3_A5.preRelease.get.isBeta)
  }

  // --- Version Bumping (next[F] for core components) ---

  test("next[MajorVersion]") {
    val expected = Version(MajorVersion.fromUnsafe(2), MinorVersion.reset, PatchNumber.reset)
    assertEquals(V1_2_3.next[MajorVersion], expected)
  }

  test("next[MinorVersion]") {
    val expected = Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(3), PatchNumber.reset)
    assertEquals(V1_2_3.next[MinorVersion], expected)
  }

  test("next[PatchNumber]") {
    val expected = Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(2), PatchNumber.fromUnsafe(4))
    assertEquals(V1_2_3.next[PatchNumber], expected)
  }

  test("next[F] for core components clears pre-release and metadata") {
    val expected = Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(3), PatchNumber.reset)
    assertEquals(V1_2_3_A5.next[MinorVersion], expected)
    assertEquals(V1_2_3_RC1_Meta.next[MinorVersion], expected)
  }

  // --- Version Bumping (next[C] for pre-release classifiers) ---

  test("next[C] on final version starts pre-release cycle") {
    val expectedA1 = V1_2_3.copy(preRelease = Some(PreRelease.alpha(PreReleaseNumber.reset)))
    assertEquals(V1_2_3.next[Alpha], expectedA1)

    val expectedRC1 = V1_2_3.copy(preRelease = Some(PreRelease.releaseCandidate(PreReleaseNumber.reset)))
    assertEquals(V1_2_3.next[ReleaseCandidate], expectedRC1)
  }

  test("next[C] with same classifier increments number") {
    val expectedA6 = V1_2_3.copy(preRelease = Some(PreRelease.alpha(PreReleaseNumber.fromUnsafe(6))))
    assertEquals(V1_2_3_A5.next[Alpha], expectedA6)

    val expectedRC2 = V1_2_3.copy(preRelease = Some(PreRelease.releaseCandidate(PreReleaseNumber.fromUnsafe(2))))
    assertEquals(V1_2_3_RC1.next[ReleaseCandidate], expectedRC2)
  }

  test("next[C] to higher-precedence classifier advances within cycle") {
    // Alpha (ordinal 2) → Beta (ordinal 3)
    val expectedB1 = V1_2_3.copy(preRelease = Some(PreRelease.beta(PreReleaseNumber.reset)))
    assertEquals(V1_2_3_A5.next[Beta], expectedB1)

    // Dev (ordinal 0) → RC (ordinal 4)
    val expectedRC1 = V1_2_3.copy(preRelease = Some(PreRelease.releaseCandidate(PreReleaseNumber.reset)))
    assertEquals(V1_2_3_D1.next[ReleaseCandidate], expectedRC1)
  }

  test("next[C] to lower-precedence classifier bumps patch") {
    // Beta (ordinal 3) → Alpha (ordinal 2) = bump patch
    val V1_2_4_A1 = Version(
      MajorVersion.fromUnsafe(1),
      MinorVersion.fromUnsafe(2),
      PatchNumber.fromUnsafe(4),
      PreRelease.alpha(PreReleaseNumber.reset)
    )
    assertEquals(V1_2_3_B1.next[Alpha], V1_2_4_A1)

    // RC (ordinal 4) → Dev (ordinal 0) = bump patch
    val V1_2_4_D1 = Version(
      MajorVersion.fromUnsafe(1),
      MinorVersion.fromUnsafe(2),
      PatchNumber.fromUnsafe(4),
      PreRelease.dev(PreReleaseNumber.reset)
    )
    assertEquals(V1_2_3_RC1.next[Dev], V1_2_4_D1)
  }

  test("next[C] from snapshot bumps patch (snapshot has highest precedence)") {
    // Snapshot (ordinal 5) → Alpha (ordinal 2) = bump patch
    val V1_2_4_A1 = Version(
      MajorVersion.fromUnsafe(1),
      MinorVersion.fromUnsafe(2),
      PatchNumber.fromUnsafe(4),
      PreRelease.alpha(PreReleaseNumber.reset)
    )
    assertEquals(V1_2_3_Snap.next[Alpha], V1_2_4_A1)
  }

  // --- Core Extraction ---

  test("core returns version without pre-release or metadata") {
    assertEquals(V1_2_3.core, V1_2_3)
    assertEquals(V1_2_3_A5.core, V1_2_3)
    assertEquals(V1_2_3_Meta.core, V1_2_3)
    assertEquals(V1_2_3_RC1_Meta.core, V1_2_3)
  }

  // --- Typed Pre-Release Operations (as[C]) ---

  test("as[C] sets classifier with default number") {
    val expectedA1 = V1_2_3.copy(preRelease = Some(PreRelease.alpha(PreReleaseNumber.reset)))
    assertEquals(V1_2_3.as[Alpha], expectedA1)
    // Overwrites existing pre-release
    assertEquals(V1_2_3_RC1.as[Alpha], expectedA1)
    // Set Snapshot
    assertEquals(V1_2_3.as[Snapshot], V1_2_3_Snap)
  }

  test("as[C](n) sets specific number") {
    val expectedB5 = Right(V1_2_3.copy(preRelease = Some(PreRelease.beta(PreReleaseNumber.fromUnsafe(5)))))
    assertEquals(V1_2_3.as[Beta](5), expectedB5)
    // Overwrite existing
    assertEquals(V1_2_3_A5.as[Beta](5), expectedB5)
  }

  test("as[C](n) returns error for invalid number") {
    assertEquals(V1_2_3.as[Alpha](0), Left(InvalidPreReleaseNumber(0)))
  }

  test("as[C](n) returns error for non-versioned classifier") {
    assertEquals(V1_2_3.as[Snapshot](1), Left(ClassifierNotVersioned(PreReleaseClassifier.Snapshot)))
  }

  test("as[C] clears build metadata") {
    val base = V1_2_3_RC1_Meta
    val expectedAs = Version(V1_2_3.major, V1_2_3.minor, V1_2_3.patch, PreRelease.alpha(PreReleaseNumber.reset))
    assertEquals(base.as[Alpha], expectedAs)
    assert(base.as[Alpha].metadata.isEmpty)

    val expectedAsN = Right(Version(V1_2_3.major, V1_2_3.minor, V1_2_3.patch, PreRelease.alpha(PreReleaseNumber.fromUnsafe(5))))
    assertEquals(base.as[Alpha](5), expectedAsN)
    assert(base.as[Alpha](5).exists(_.metadata.isEmpty))
  }

  // --- Version.Read Typeclass ---

  test("String extension 'toVersion'") {
    assertEquals("1.2.3".toVersion, Right(V1_2_3))
    assertEquals("1.2.3-rc.1+build123".toVersion, Right(V1_2_3_RC1_Meta))
    assert("invalid".toVersion.isLeft)
  }

  test("String extension 'toVersionUnsafe'") {
    assertEquals("1.2.3".toVersionUnsafe, V1_2_3)
    assertEquals("1.2.3-rc.1+build123".toVersionUnsafe, V1_2_3_RC1_Meta)
    intercept[errors.ParseError]("invalid".toVersionUnsafe)
  }

  test("Version.from[A] factory method") {
    assertEquals(Version.from("1.2.3"), Right(V1_2_3))
    assertEquals(Version.from("1.2.3-rc.1+build123"), Right(V1_2_3_RC1_Meta))
    assert(Version.from("invalid").isLeft)
  }

  test("Version.fromUnsafe[A] factory method") {
    assertEquals(Version.fromUnsafe("1.2.3"), V1_2_3)
    assertEquals(Version.fromUnsafe("1.2.3-rc.1+build123"), V1_2_3_RC1_Meta)
    intercept[errors.ParseError](Version.fromUnsafe("invalid"))
  }

  test("Version.Read.ReadString is a stable singleton instance") {
    val reader = Version.Read.ReadString
    assertEquals(reader.toVersion("1.2.3"), Right(V1_2_3))
    assertEquals(reader.toVersionUnsafe("1.2.3"), V1_2_3)
    assert(reader.toVersion("invalid").isLeft)
  }

  test("Version.Read.apply summons the contextual instance") {
    val reader = Version.Read[String]
    assertEquals(reader.toVersion("1.2.3"), Right(V1_2_3))
  }

  // --- PreRelease isDev extension ---

  test("PreRelease.isDev") {
    val dev1 = PreRelease.dev(PreReleaseNumber.fromUnsafe(1))
    assert(dev1.isDev)
    assert(!PreRelease.alpha(PreReleaseNumber.fromUnsafe(1)).isDev)
    assert(!PreRelease.snapshot.isDev)
  }

end VersionOperationsSuite
