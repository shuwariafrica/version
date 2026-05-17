/****************************************************************************
 * Copyright 2023-2026 Shuwari Africa Ltd.                                  *
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

import version.semver.*

/** Tests for [[SemVer.Formatter]] and the scheme-canonical `show` method. */
class VersionShowSuite extends munit.FunSuite:

  private def V(major: Int, minor: Int, patch: Int) =
    SemVer(Major.fromUnsafe(major), Minor.fromUnsafe(minor), Patch.fromUnsafe(patch))

  private def PRN(i: Int) = PreReleaseNumber.fromUnsafe(i)

  // --- show (VersionScheme canonical) ---

  test("show: core version only") {
    assertEquals(V(1, 2, 3).show, "1.2.3")
  }

  test("show: with pre-release") {
    val v = V(1, 0, 0).copy(preRelease = Some(PreRelease.alpha(PRN(1))))
    assertEquals(v.show, "1.0.0-alpha.1")
  }

  test("show: with snapshot pre-release") {
    val v = V(2, 0, 0).copy(preRelease = Some(PreRelease.snapshot))
    assertEquals(v.show, "2.0.0-SNAPSHOT")
  }

  test("show: excludes build metadata") {
    val meta = Metadata(List("1234567890abc", "main"))
    val v = V(1, 2, 3).copy(metadata = Some(meta))
    assertEquals(v.show, "1.2.3")
  }

  test("show: pre-release present, metadata excluded") {
    val meta = Metadata(List("1234567890abc"))
    val v = V(1, 0, 0).copy(preRelease = Some(PreRelease.beta(PRN(5))), metadata = Some(meta))
    assertEquals(v.show, "1.0.0-beta.5")
  }

  test("show: renders all pre-release classifiers correctly") {
    val prn = PRN(1)
    assertEquals(V(1, 0, 0).copy(preRelease = Some(PreRelease.dev(prn))).show, "1.0.0-dev.1")
    assertEquals(V(1, 0, 0).copy(preRelease = Some(PreRelease.milestone(prn))).show, "1.0.0-milestone.1")
    assertEquals(V(1, 0, 0).copy(preRelease = Some(PreRelease.alpha(prn))).show, "1.0.0-alpha.1")
    assertEquals(V(1, 0, 0).copy(preRelease = Some(PreRelease.beta(prn))).show, "1.0.0-beta.1")
    assertEquals(V(1, 0, 0).copy(preRelease = Some(PreRelease.releaseCandidate(prn))).show, "1.0.0-rc.1")
    assertEquals(V(1, 0, 0).copy(preRelease = Some(PreRelease.snapshot)).show, "1.0.0-SNAPSHOT")
  }

  // --- Formatter.standard ---

  test("Formatter.standard: same output as show") {
    val v = V(1, 0, 0).copy(preRelease = Some(PreRelease.alpha(PRN(1))), metadata = Some(Metadata(List("build"))))
    assertEquals(SemVer.Formatter.standard.format(v), v.show)
  }

  // --- Formatter.full ---

  test("Formatter.full: preserves complete metadata") {
    val meta = Metadata(List("build"))
    val v = V(1, 2, 3).copy(preRelease = Some(PreRelease.alpha(PRN(1))), metadata = Some(meta))
    assertEquals(SemVer.Formatter.full.format(v), "1.2.3-alpha.1+build")
  }

  test("Formatter.full: preserves full SHA (no truncation)") {
    val longSha = "abcdef1234567890abcdef1234567890abcdef12"
    val meta = Metadata(List(longSha, "main"))
    val v = V(1, 0, 0).copy(metadata = Some(meta))
    assertEquals(SemVer.Formatter.full.format(v), s"1.0.0+$longSha.main")
  }

  test("Formatter.full: renders the development-version layout verbatim") {
    val meta = Metadata(List("202605170145", "main", "abcdef123456", "pr42", "dirty"))
    val v = V(2, 1, 0).copy(preRelease = Some(PreRelease.snapshot), metadata = Some(meta))
    assertEquals(
      SemVer.Formatter.full.format(v),
      "2.1.0-SNAPSHOT+202605170145.main.abcdef123456.pr42.dirty"
    )
  }

  // --- Explicit variant selection ---

  test("Formatter variants compared side by side") {
    val meta = Metadata(List("build"))
    val v = V(1, 2, 3).copy(preRelease = Some(PreRelease.alpha(PRN(1))), metadata = Some(meta))
    assertEquals(SemVer.Formatter.standard.format(v), "1.2.3-alpha.1")
    assertEquals(SemVer.Formatter.full.format(v), "1.2.3-alpha.1+build")
  }

  // --- Custom Formatter ---

  test("Custom Formatter implementation") {
    val vPrefix: SemVer.Formatter = (v: SemVer) => s"v${v.major.value}.${v.minor.value}.${v.patch.value}"
    assertEquals(vPrefix.format(V(3, 2, 1)), "v3.2.1")
  }

end VersionShowSuite
