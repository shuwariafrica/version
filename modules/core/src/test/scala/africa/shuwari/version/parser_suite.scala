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

class VersionParserSuite extends munit.FunSuite:

  val baseVersionStrings = Map(
    "0.0.1" -> Version(MajorVersion.wrap(0), MinorVersion.wrap(0), PatchNumber.wrap(1)),
    "0.1.0" -> Version(MajorVersion.wrap(0), MinorVersion.wrap(1), PatchNumber.wrap(0)),
    "1.0.0" -> Version(MajorVersion.wrap(1), MinorVersion.wrap(0), PatchNumber.wrap(0)),
    "1.1.1" -> Version(MajorVersion.wrap(1), MinorVersion.wrap(1), PatchNumber.wrap(1)),
    "99.999.9999" -> Version(MajorVersion.wrap(99), MinorVersion.wrap(999), PatchNumber.wrap(9999))
  )

  val alphaVersions = joinVersions(
    Map(
      "Alpha1" -> PreRelease.alpha(PreReleaseNumber.wrap(1)),
      "alpha-1" -> PreRelease.alpha(PreReleaseNumber.wrap(1)),
      "ALPHA90" -> PreRelease.alpha(PreReleaseNumber.wrap(90)),
      "a1" -> PreRelease.alpha(PreReleaseNumber.wrap(1)),
      "a-1" -> PreRelease.alpha(PreReleaseNumber.wrap(1)),
      "A90" -> PreRelease.alpha(PreReleaseNumber.wrap(90))
    ))

  val betaVersions = joinVersions(
    Map(
      "Beta1" -> PreRelease.beta(PreReleaseNumber.wrap(1)),
      "beta-1" -> PreRelease.beta(PreReleaseNumber.wrap(1)),
      "BETA90" -> PreRelease.beta(PreReleaseNumber.wrap(90)),
      "b1" -> PreRelease.beta(PreReleaseNumber.wrap(1)),
      "b-1" -> PreRelease.beta(PreReleaseNumber.wrap(1)),
      "B90" -> PreRelease.beta(PreReleaseNumber.wrap(90))
    ))

  val milestoneVersions = joinVersions(
    Map(
      "Milestone1" -> PreRelease.milestone(PreReleaseNumber.wrap(1)),
      "milestone-1" -> PreRelease.milestone(PreReleaseNumber.wrap(1)),
      "MILESTONE90" -> PreRelease.milestone(PreReleaseNumber.wrap(90)),
      "m1" -> PreRelease.milestone(PreReleaseNumber.wrap(1)),
      "m-1" -> PreRelease.milestone(PreReleaseNumber.wrap(1)),
      "M90" -> PreRelease.milestone(PreReleaseNumber.wrap(90))
    ))

  val releaseCandidateVersions = joinVersions(
    Map(
      "rc1" -> PreRelease.releaseCandidate(PreReleaseNumber.wrap(1)),
      "rc-1" -> PreRelease.releaseCandidate(PreReleaseNumber.wrap(1)),
      "RC90" -> PreRelease.releaseCandidate(PreReleaseNumber.wrap(90)),
      "cr1" -> PreRelease.releaseCandidate(PreReleaseNumber.wrap(1)),
      "cr-1" -> PreRelease.releaseCandidate(PreReleaseNumber.wrap(1)),
      "CR90" -> PreRelease.releaseCandidate(PreReleaseNumber.wrap(90))
    ))

  val snapshotVersions = joinVersions(
    Map(
      "Snapshot" -> PreRelease.snapshot,
      "snapshot" -> PreRelease.snapshot,
      "SNAPSHOT" -> PreRelease.snapshot
    ))

  val unclassifiedPreReleases = joinVersions(
    Map(
      "rc0" -> PreRelease.unclassified,
      "m0" -> PreRelease.unclassified,
      "FIX29" -> PreRelease.unclassified,
      "pre" -> PreRelease.unclassified
    ))

  test("Non-PreRelease versions are parsed correctly")(assertVersionsEqual(baseVersionStrings))

  test("'alpha' PreRelease versions are parsed correctly")(
    alphaVersions.foreach(kv => assertEquals(VersionParser.version(kv._1), Some(kv._2))))

  test("'beta' PreRelease versions are parsed correctly")(
    betaVersions.foreach(kv => assertEquals(VersionParser.version(kv._1), Some(kv._2))))

  test("'milestone' PreRelease versions are parsed correctly")(
    milestoneVersions.foreach(kv => assertEquals(VersionParser.version(kv._1), Some(kv._2))))

  test("'rc' PreRelease versions are parsed correctly")(
    releaseCandidateVersions.foreach(kv => assertEquals(VersionParser.version(kv._1), Some(kv._2))))

  test("'snapshot' PreRelease versions are parsed correctly")(
    snapshotVersions.foreach(kv => assertEquals(VersionParser.version(kv._1), Some(kv._2))))

  test("'unclassified' PreRelease versions are parsed correctly")(
    unclassifiedPreReleases.foreach(kv => assertEquals(VersionParser.version(kv._1), Some(kv._2))))

  private inline def assertVersionsEqual(versions: Map[String, Version]): Unit =
    versions.foreach(kv => assertEquals(VersionParser.version(kv._1), Some(kv._2)))

  private inline def joinVersions(pair: Map[String, PreRelease]): Map[String, Version] = pair.flatMap { kv =>
    baseVersionStrings
      .map(ver => (s"${ver._1}-${kv._1}", ver._2.copy(preRelease = Some(kv._2))))
  }

end VersionParserSuite
