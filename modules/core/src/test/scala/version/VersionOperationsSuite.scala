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

/** Tests the ergonomic API provided by the extension methods in [[version]]. */
class VersionOperationsSuite extends munit.FunSuite:

  // --- Fixtures ---
  private val V1_2_3 = Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(2), PatchNumber.fromUnsafe(3))
  private val V0_1_0 = Version(MajorVersion.fromUnsafe(0), MinorVersion.fromUnsafe(1), PatchNumber.fromUnsafe(0))
  private val V1_2_3_M1 = V1_2_3.set(PreRelease.milestone(PreReleaseNumber.fromUnsafe(1)))
  private val V1_2_3_A5 = V1_2_3.set(PreRelease.alpha(PreReleaseNumber.fromUnsafe(5)))
  private val V1_2_3_RC1 = V1_2_3.set(PreRelease.releaseCandidate(PreReleaseNumber.fromUnsafe(1)))
  private val V1_2_3_Snap = V1_2_3.toSnapshot
  private val TestMetadata = Metadata(List("build123"))
  private val V1_2_3_Meta = V1_2_3.set(TestMetadata)
  private val V1_2_3_RC1_Meta = V1_2_3_RC1.set(TestMetadata)

  // --- Status Checks ---

  test("isStable") {
    assert(V1_2_3.isStable)
    assert(V1_2_3_A5.isStable) // Stability depends only on Major version
    assert(!V0_1_0.isStable)
  }

  test("isPreRelease / isFinal") {
    assert(V1_2_3.isFinal && !V1_2_3.isPreRelease)
    assert(V1_2_3_A5.isPreRelease && !V1_2_3_A5.isFinal)
    assert(V1_2_3_Snap.isPreRelease && !V1_2_3_Snap.isFinal)
  }

  test("PreRelease extensions (isAlpha, isSnapshot etc.)") {
    assert(V1_2_3_M1.preRelease.get.isMilestone)
    assert(V1_2_3_A5.preRelease.get.isAlpha)
    assert(V1_2_3_RC1.preRelease.get.isReleaseCandidate)
    assert(V1_2_3_Snap.preRelease.get.isSnapshot)
    assert(!V1_2_3_A5.preRelease.get.isBeta)
  }

  // --- Version Bumping (next[F]) ---

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

  test("next[F] should clear pre-release information") {
    val expected = Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(3), PatchNumber.reset)
    assertEquals(V1_2_3_A5.next[MinorVersion], expected)
  }

  test("next[F] should clear build metadata") {
    val expected = Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(2), PatchNumber.fromUnsafe(4))
    assertEquals(V1_2_3_Meta.next[PatchNumber], expected)
    assertEquals(V1_2_3_RC1_Meta.next[PatchNumber], expected)
  }

  // --- General Pre-Release/Metadata Operations ---

  test("release") {
    assertEquals(V1_2_3_A5.release, V1_2_3)
    assertEquals(V1_2_3.release, V1_2_3) // Idempotent
    // Should preserve metadata
    assertEquals(V1_2_3_RC1_Meta.release, V1_2_3_Meta)
  }

  test("toSnapshot") {
    assertEquals(V1_2_3.toSnapshot, V1_2_3_Snap)
    // Overwrites existing pre-release
    assertEquals(V1_2_3_A5.toSnapshot, V1_2_3_Snap)
    // Preserves metadata
    assertEquals(V1_2_3_Meta.toSnapshot, V1_2_3_Snap.copy(metadata = Some(TestMetadata)))
  }

  test("set(PreRelease) / set(Metadata) / dropMetadata") {
    val newPR = PreRelease.beta(PreReleaseNumber.fromUnsafe(1))
    val newMeta = Metadata(List("new"))

    assertEquals(V1_2_3.set(newPR), V1_2_3.copy(preRelease = Some(newPR)))
    assertEquals(V1_2_3.set(newMeta), V1_2_3.copy(metadata = Some(newMeta)))
    assertEquals(V1_2_3_Meta.dropMetadata, V1_2_3)
  }

  // --- Typed Pre-Release Operations (as[C]) ---

  test("as[C] (Start Sequence)") {
    // Start Alpha track (resets to 1)
    val expectedA1 = V1_2_3.copy(preRelease = Some(PreRelease.alpha(PreReleaseNumber.reset)))
    assertEquals(V1_2_3.as[Alpha.type], expectedA1)
    // Overwrites existing pre-release
    assertEquals(V1_2_3_RC1.as[Alpha.type], expectedA1)
    // Set Snapshot
    assertEquals(V1_2_3.as[Snapshot.type], V1_2_3_Snap)
  }

  test("as[C](n) (Set specific number)") {
    val expectedB5 = Right(V1_2_3.copy(preRelease = Some(PreRelease.beta(PreReleaseNumber.fromUnsafe(5)))))
    assertEquals(V1_2_3.as[Beta.type](5), expectedB5)
    // Overwrite existing
    assertEquals(V1_2_3_A5.as[Beta.type](5), expectedB5)
  }

  test("as[C](n) (Error: Invalid Number)") {
    assertEquals(V1_2_3.as[Alpha.type](0), Left(InvalidPreReleaseNumber(0)))
  }

  test("as[C](n) (Error: Non-Versioned Classifier)") {
    assertEquals(V1_2_3.as[Snapshot.type](1), Left(ClassifierNotVersioned(Snapshot)))
  }

  // --- Typed Pre-Release Operations (next[C]) ---
  // This operation manages progression *within* a pre-release cycle.

  test("advance[C] (Increment same classifier)") {
    val expectedA6 = Right(V1_2_3.copy(preRelease = Some(PreRelease.alpha(PreReleaseNumber.fromUnsafe(6)))))
    assertEquals(V1_2_3_A5.advance[Alpha.type], expectedA6)

    val expectedRC2 = Right(V1_2_3.copy(preRelease = Some(PreRelease.releaseCandidate(PreReleaseNumber.fromUnsafe(2)))))
    assertEquals(V1_2_3_RC1.advance[ReleaseCandidate.type], expectedRC2)
  }

  test("advance[C] (Transition to higher precedence)") {
    // Milestone (0) -> Alpha (1). Resets number.
    val expectedA1 = Right(V1_2_3.copy(preRelease = Some(PreRelease.alpha(PreReleaseNumber.reset))))
    assertEquals(V1_2_3_M1.advance[Alpha.type], expectedA1)

    // Alpha (1) -> RC (3). Resets number.
    val expectedRC1 = Right(V1_2_3.copy(preRelease = Some(PreRelease.releaseCandidate(PreReleaseNumber.reset))))
    assertEquals(V1_2_3_A5.advance[ReleaseCandidate.type], expectedRC1)
  }

  test("advance[C] (Error: Transition to lower precedence)") {
    // RC (3) -> Beta (2)
    assertEquals(
      V1_2_3_RC1.advance[Beta.type],
      Left(InvalidPreReleaseTransition(ReleaseCandidate, Beta))
    )
    // Alpha (1) -> Milestone (0)
    assertEquals(
      V1_2_3_A5.advance[Milestone.type],
      Left(InvalidPreReleaseTransition(Alpha, Milestone))
    )
  }

  test("advance[C] (Error: Operation on final release)") {
    assertEquals(V1_2_3.advance[Alpha.type], Left(NotAPreReleaseVersion()))
  }

  test("advance[C] (Transition to Snapshot)") {
    // Transition from Alpha (1) -> Snapshot (4) is allowed and sets Snapshot without a number
    assertEquals(V1_2_3_A5.advance[Snapshot.type], Right(V1_2_3_Snap))
  }

  test("advance[C] (Transition from Snapshot)") {
    // Snapshot has the highest precedence (ordinal 4). Transitioning to RC (ordinal 3) is a downgrade.
    assertEquals(
      V1_2_3_Snap.advance[ReleaseCandidate.type],
      Left(InvalidPreReleaseTransition(Snapshot, ReleaseCandidate))
    )
  }

  test("Typed operations should preserve build metadata") {
    val base = V1_2_3_RC1_Meta // 1.2.3-rc.1+build123

    // as[C]
    val expectedAs = V1_2_3_Meta.copy(preRelease = Some(PreRelease.alpha(PreReleaseNumber.reset)))
    assertEquals(base.as[Alpha.type], expectedAs)

    // as[C](n)
    val expectedAsN = Right(V1_2_3_Meta.copy(preRelease = Some(PreRelease.alpha(PreReleaseNumber.fromUnsafe(5)))))
    assertEquals(base.as[Alpha.type](5), expectedAsN)

    // advance[C] (increment)
    val expectedNext = Right(V1_2_3_Meta.copy(preRelease = Some(PreRelease.releaseCandidate(PreReleaseNumber.fromUnsafe(2)))))
    assertEquals(base.advance[ReleaseCandidate.type], expectedNext)
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

  // --- Additional Status Checks ---

  test("isStableRelease") {
    assert(V1_2_3.isStableRelease) // 1.x.x final
    assert(!V0_1_0.isStableRelease) // 0.x.x is not stable
    assert(!V1_2_3_A5.isStableRelease) // pre-release
    assert(!V1_2_3_Snap.isStableRelease) // snapshot
  }

  test("isCandidate") {
    assert(V1_2_3_A5.isCandidate) // alpha is a candidate
    assert(V1_2_3_RC1.isCandidate) // rc is a candidate
    assert(V1_2_3_M1.isCandidate) // milestone is a candidate
    assert(!V1_2_3.isCandidate) // final is not
    assert(!V1_2_3_Snap.isCandidate) // snapshot is not a candidate
  }

  test("isSnapshot") {
    assert(V1_2_3_Snap.isSnapshot)
    assert(!V1_2_3.isSnapshot)
    assert(!V1_2_3_A5.isSnapshot)
  }

  test("core returns version without pre-release or metadata") {
    assertEquals(V1_2_3.core, V1_2_3)
    assertEquals(V1_2_3_A5.core, V1_2_3)
    assertEquals(V1_2_3_Meta.core, V1_2_3)
    assertEquals(V1_2_3_RC1_Meta.core, V1_2_3)
  }

  // --- PreRelease isDev extension ---

  test("PreRelease.isDev") {
    val dev1 = PreRelease.dev(PreReleaseNumber.fromUnsafe(1))
    assert(dev1.isDev)
    assert(!PreRelease.alpha(PreReleaseNumber.fromUnsafe(1)).isDev)
    assert(!PreRelease.snapshot.isDev)
  }

  // --- Convenience bumps (nextMajor, nextMinor, nextPatch) ---

  test("nextMajor") {
    val expected = Version(MajorVersion.fromUnsafe(2), MinorVersion.reset, PatchNumber.reset)
    assertEquals(V1_2_3.nextMajor, expected)
    assertEquals(V1_2_3_A5.nextMajor, expected)
  }

  test("nextMinor") {
    val expected = Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(3), PatchNumber.reset)
    assertEquals(V1_2_3.nextMinor, expected)
  }

  test("nextPatch") {
    val expected = Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(2), PatchNumber.fromUnsafe(4))
    assertEquals(V1_2_3.nextPatch, expected)
  }

end VersionOperationsSuite
