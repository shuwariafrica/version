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

/** Tests for the [[Version.Show]] type class — both standard instances and custom implementations. */
class VersionShowSuite extends munit.FunSuite:

  // Helpers
  private def V(major: Int, minor: Int, patch: Int) =
    Version(MajorVersion.fromUnsafe(major), MinorVersion.fromUnsafe(minor), PatchNumber.fromUnsafe(patch))

  private def PRN(i: Int) = PreReleaseNumber.fromUnsafe(i)

  // --- Version.Show.Standard Tests ---

  test("Version.Show.Standard: core version only") {
    val v = V(1, 2, 3)
    assertEquals(v.show, "1.2.3")
  }

  test("Version.Show.Standard: with pre-release") {
    val v = V(1, 0, 0).copy(preRelease = Some(PreRelease.alpha(PRN(1))))
    assertEquals(v.show, "1.0.0-alpha.1")
  }

  test("Version.Show.Standard: with snapshot pre-release") {
    val v = V(2, 0, 0).copy(preRelease = Some(PreRelease.snapshot))
    assertEquals(v.show, "2.0.0-SNAPSHOT")
  }

  test("Version.Show.Standard: excludes build metadata") {
    val meta = Metadata(List("sha1234567890abc", "branch", "main"))
    val v = V(1, 2, 3).copy(metadata = Some(meta))
    assertEquals(v.show, "1.2.3")
  }

  test("Version.Show.Standard: pre-release present, metadata excluded") {
    val meta = Metadata(List("sha1234567890abc"))
    val v = V(1, 0, 0).copy(preRelease = Some(PreRelease.beta(PRN(5))), metadata = Some(meta))
    assertEquals(v.show, "1.0.0-beta.5")
  }

  // --- Version.Show.Extended Tests ---

  test("Version.Show.Extended: core version only") {
    given Version.Show = Version.Show.Extended
    val v = V(1, 2, 3)
    assertEquals(v.show, "1.2.3")
  }

  test("Version.Show.Extended: with pre-release only") {
    given Version.Show = Version.Show.Extended
    val v = V(1, 0, 0).copy(preRelease = Some(PreRelease.releaseCandidate(PRN(3))))
    assertEquals(v.show, "1.0.0-rc.3")
  }

  test("Version.Show.Extended: with build metadata only") {
    given Version.Show = Version.Show.Extended
    val meta = Metadata(List("build", "123"))
    val v = V(1, 2, 3).copy(metadata = Some(meta))
    assertEquals(v.show, "1.2.3+build.123")
  }

  test("Version.Show.Extended: with both pre-release and metadata") {
    given Version.Show = Version.Show.Extended
    val meta = Metadata(List("sha1234567890abc", "branch", "main"))
    val v = V(1, 0, 0).copy(preRelease = Some(PreRelease.alpha(PRN(1))), metadata = Some(meta))
    // SHA truncated to 7 chars (git convention)
    assertEquals(v.show, "1.0.0-alpha.1+sha1234567.branch.main")
  }

  test("Version.Show.Extended: complex metadata with SHA truncation") {
    given Version.Show = Version.Show.Extended
    val meta = Metadata(List("pr42", "branchfeature-x", "commits5", "shaabcdef1234567", "dirty"))
    val v = V(2, 1, 0).copy(preRelease = Some(PreRelease.snapshot), metadata = Some(meta))
    // SHA truncated to 7 chars (git convention)
    assertEquals(v.show, "2.1.0-SNAPSHOT+pr42.branchfeature-x.commits5.shaabcdef1.dirty")
  }

  // --- Explicit Instance Selection ---

  test("Explicit selection of Show instance via using") {
    val meta = Metadata(List("build"))
    val v = V(1, 2, 3).copy(preRelease = Some(PreRelease.alpha(PRN(1))), metadata = Some(meta))

    // Use the Show instances directly
    assertEquals(Version.Show.Standard.show(v), "1.2.3-alpha.1")
    assertEquals(Version.Show.Extended.show(v), "1.2.3-alpha.1+build")
  }

  // --- Custom Show Instance Tests ---

  test("Custom Show instance can be provided") {
    // Custom instance that only shows major version with 'v' prefix
    given customShow: Version.Show:
      extension (v: Version) def show: String = s"v${v.major.value}"

    val v = V(3, 2, 1).copy(preRelease = Some(PreRelease.alpha(PRN(1))))
    assertEquals(v.show, "v3")
  }

  test("Custom Show instance with v prefix and no metadata") {
    // Custom instance that adds 'v' prefix and excludes metadata
    given vPrefixShow: Version.Show:
      extension (v: Version)
        def show: String =
          val core = s"v${v.major.value}.${v.minor.value}.${v.patch.value}"
          v.preRelease.fold(core)(pr => s"$core-${pr.show}")

    val meta = Metadata(List("sha1234567890abcdef"))
    val v = V(1, 0, 0).copy(preRelease = Some(PreRelease.snapshot), metadata = Some(meta))
    assertEquals(v.show, "v1.0.0-SNAPSHOT")
  }

  test("Custom Show instance overrides when in scope") {
    // Define custom in local scope — should be used
    given customShow: Version.Show:
      extension (v: Version) def show: String = "custom"

    val v = V(1, 2, 3)
    assertEquals(v.show, "custom")
  }

  // --- Show.apply Summoner ---

  test("Version.Show.apply summons the contextual instance") {
    val showInstance = Version.Show.apply
    val v = V(1, 2, 3)
    assertEquals(showInstance.show(v), "1.2.3")
  }

  test("Version.Show.apply summons Extended when promoted to given") {
    given Version.Show = Version.Show.Extended
    val showInstance = Version.Show.apply
    val meta = Metadata(List("build"))
    val v = V(1, 2, 3).copy(metadata = Some(meta))
    assertEquals(showInstance.show(v), "1.2.3+build")
  }

  // --- All Pre-Release Classifiers with Show ---

  test("Version.Show renders all pre-release classifiers correctly") {
    val prn = PRN(1)

    assertEquals(V(1, 0, 0).copy(preRelease = Some(PreRelease.dev(prn))).show, "1.0.0-dev.1")
    assertEquals(V(1, 0, 0).copy(preRelease = Some(PreRelease.milestone(prn))).show, "1.0.0-milestone.1")
    assertEquals(V(1, 0, 0).copy(preRelease = Some(PreRelease.alpha(prn))).show, "1.0.0-alpha.1")
    assertEquals(V(1, 0, 0).copy(preRelease = Some(PreRelease.beta(prn))).show, "1.0.0-beta.1")
    assertEquals(V(1, 0, 0).copy(preRelease = Some(PreRelease.releaseCandidate(prn))).show, "1.0.0-rc.1")
    assertEquals(V(1, 0, 0).copy(preRelease = Some(PreRelease.snapshot)).show, "1.0.0-SNAPSHOT")
  }

end VersionShowSuite
