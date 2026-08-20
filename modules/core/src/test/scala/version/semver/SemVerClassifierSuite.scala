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
package version.semver

import munit.FunSuite

import version.VersionScheme
import version.errors.ClassifierNotVersioned
import version.errors.InvalidComponent
import version.errors.InvalidPreRelease
import version.errors.MissingQualifierNumber
import version.errors.UnexpectedQualifierNumber
import version.semver.PreReleaseClassifier.*

class SemVerClassifierSuite extends FunSuite:

  private val scheme = summon[VersionScheme[SemVer]]

  private def v(input: String): SemVer = SemVer.parseUnsafe(input)

  private def n(value: Long): PreReleaseNumber = PreReleaseNumber.ofUnsafe(value)

  test("a classifier is numbered unless it is SNAPSHOT") {
    assert(PreReleaseClassifier.Dev.versioned)
    assert(PreReleaseClassifier.Milestone.versioned)
    assert(PreReleaseClassifier.Alpha.versioned)
    assert(PreReleaseClassifier.Beta.versioned)
    assert(PreReleaseClassifier.ReleaseCandidate.versioned)
    assert(!PreReleaseClassifier.Snapshot.versioned)
  }

  test("a classifier renders as the first of its aliases") {
    assertEquals(PreReleaseClassifier.Dev.show, "dev")
    assertEquals(PreReleaseClassifier.Milestone.show, "milestone")
    assertEquals(PreReleaseClassifier.Alpha.show, "alpha")
    assertEquals(PreReleaseClassifier.Beta.show, "beta")
    assertEquals(PreReleaseClassifier.ReleaseCandidate.show, "rc")
    assertEquals(PreReleaseClassifier.Snapshot.show, "SNAPSHOT")
  }

  test("an alias resolves to its classifier without regard to case") {
    assertEquals(PreReleaseClassifier.fromAlias("dev"), Some(PreReleaseClassifier.Dev))
    assertEquals(PreReleaseClassifier.fromAlias("DEV"), Some(PreReleaseClassifier.Dev))
    assertEquals(PreReleaseClassifier.fromAlias("m"), Some(PreReleaseClassifier.Milestone))
    assertEquals(PreReleaseClassifier.fromAlias("A"), Some(PreReleaseClassifier.Alpha))
    assertEquals(PreReleaseClassifier.fromAlias("cr"), Some(PreReleaseClassifier.ReleaseCandidate))
    assertEquals(PreReleaseClassifier.fromAlias("snapshot"), Some(PreReleaseClassifier.Snapshot))
    assertEquals(PreReleaseClassifier.fromAlias("unrecognised"), None)
  }

  test("the classifier extractor matches an alias") {
    "rc" match
      case PreReleaseClassifier(c) => assertEquals(c, PreReleaseClassifier.ReleaseCandidate)
      case _                       => fail("the extractor did not match a known alias")
  }

  test("construction pairs a numbered classifier with its number") {
    assertEquals(PreRelease.of(PreReleaseClassifier.Alpha, Some(n(5))).map(_.show), Right("alpha.5"))
    assertEquals(PreRelease.of(PreReleaseClassifier.Snapshot, None).map(_.show), Right("SNAPSHOT"))
  }

  test("a numbered classifier without a number is rejected") {
    assertEquals(PreRelease.of(PreReleaseClassifier.Alpha, None), Left(MissingQualifierNumber("alpha")))
    intercept[MissingQualifierNumber](PreRelease.ofUnsafe(PreReleaseClassifier.ReleaseCandidate, None))
  }

  test("an unnumbered classifier given a number is rejected") {
    assertEquals(
      PreRelease.of(PreReleaseClassifier.Snapshot, Some(n(1))),
      Left(UnexpectedQualifierNumber("SNAPSHOT", 1L))
    )
    intercept[UnexpectedQualifierNumber](PreRelease.ofUnsafe(PreReleaseClassifier.Snapshot, Some(n(1))))
  }

  test("the named factories render their classifier and number") {
    assertEquals(PreRelease.dev(n(1)).show, "dev.1")
    assertEquals(PreRelease.milestone(n(2)).show, "milestone.2")
    assertEquals(PreRelease.alpha(n(5)).show, "alpha.5")
    assertEquals(PreRelease.beta(n(7)).show, "beta.7")
    assertEquals(PreRelease.releaseCandidate(n(3)).show, "rc.3")
    assertEquals(PreRelease.snapshot.show, "SNAPSHOT")
  }

  test("construction from identifiers rejects what the grammar forbids") {
    assertEquals(PreRelease.of(Nil), Left(InvalidPreRelease(Nil)))
    assertEquals(PreRelease.of(List("")), Left(InvalidPreRelease(List(""))))
    assertEquals(PreRelease.of(List("alpha_1")), Left(InvalidPreRelease(List("alpha_1"))))
    assertEquals(PreRelease.of(List("alpha", "01")), Left(InvalidPreRelease(List("alpha", "01"))))
    assertEquals(PreRelease.of(List("alpha", "0")).map(_.show), Right("alpha.0"))
  }

  test("the leading identifier names the classifier where it is a known alias") {
    assertEquals(v("1.0.0-rc.1").preRelease.flatMap(_.classifier), Some(PreReleaseClassifier.ReleaseCandidate))
    assertEquals(v("1.0.0-SNAPSHOT").preRelease.flatMap(_.classifier), Some(PreReleaseClassifier.Snapshot))
    assertEquals(v("1.0.0-x.7.z.92").preRelease.flatMap(_.classifier), None)
    assertEquals(v("1.0.0-rc3").preRelease.flatMap(_.classifier), None)
  }

  test("incrementing a pre-release advances its trailing number") {
    assertEquals(PreRelease.alpha(n(1)).increment.show, "alpha.2")
    assertEquals(PreRelease.of(List("x", "7", "z", "92")).map(_.increment.show), Right("x.7.z.93"))
  }

  test("a pre-release ending in no number is left unchanged by increment") {
    assertEquals(PreRelease.snapshot.increment.show, "SNAPSHOT")
    assertEquals(PreRelease.of(List("alpha")).map(_.increment.show), Right("alpha"))
  }

  test("next on a numeric component resets the components below it") {
    assertEquals(scheme.show(v("1.2.3").next[Major]), "2.0.0")
    assertEquals(scheme.show(v("1.2.3").next[Minor]), "1.3.0")
    assertEquals(scheme.show(v("1.2.3").next[Patch]), "1.2.4")
  }

  test("next on a classifier starts that label on a released version") {
    assertEquals(scheme.show(v("1.2.3").next[Alpha]), "1.2.3-alpha.1")
    assertEquals(scheme.show(v("1.2.3").next[ReleaseCandidate]), "1.2.3-rc.1")
  }

  test("next on the label already in place advances its number") {
    assertEquals(scheme.show(v("1.2.3-alpha.5").next[Alpha]), "1.2.3-alpha.6")
    assertEquals(scheme.show(v("1.2.3-rc.1").next[ReleaseCandidate]), "1.2.3-rc.2")
  }

  test("next on a label that outranks the one in place replaces it") {
    assertEquals(scheme.show(v("1.2.3-alpha.1").next[Beta]), "1.2.3-beta.1")
    assertEquals(scheme.show(v("1.2.3-SNAPSHOT").next[ReleaseCandidate]), "1.2.3-rc.1")
  }

  test("next on a label that does not outrank the one in place moves the patch first") {
    assertEquals(scheme.show(v("1.2.3-beta.1").next[Alpha]), "1.2.4-alpha.1")
    assertEquals(scheme.show(v("1.2.3-rc.1").next[Dev]), "1.2.4-dev.1")
  }

  test("as replaces the pre-release with the first of the named classifier") {
    assertEquals(scheme.show(v("1.2.3").as[Alpha]), "1.2.3-alpha.1")
    assertEquals(scheme.show(v("1.2.3").as[Snapshot]), "1.2.3-SNAPSHOT")
    assertEquals(scheme.show(v("1.2.3-rc.9+sha.abc").as[Beta]), "1.2.3-beta.1")
  }

  test("as with a number sets that number") {
    assertEquals(v("1.2.3").as[Alpha](5).map(scheme.show(_)), Right("1.2.3-alpha.5"))
    assertEquals(SemVer.as[Alpha](v("1.2.3"), 5).map(scheme.show(_)), Right("1.2.3-alpha.5"))
  }

  test("as with a number rejects a classifier that takes none") {
    assertEquals(v("1.2.3").as[Snapshot](1), Left(ClassifierNotVersioned("SNAPSHOT")))
  }

  test("as with a number rejects a number the pre-release cannot carry") {
    assertEquals(
      v("1.2.3").as[Alpha](0),
      Left(InvalidComponent(0L, "Pre-release number", "a positive number (>= 1)"))
    )
  }

end SemVerClassifierSuite
