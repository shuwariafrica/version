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

/** Tests for the development-version metadata shape and the UTC timestamp formatter that backs the sortability
  * invariant.
  */
class DevelopmentVersionSuite extends munit.FunSuite:

  private val scheme = summon[ResolvableScheme[SemVer]]
  private val core = SemVer(Major.fromUnsafe(1), Minor.fromUnsafe(2), Patch.fromUnsafe(3))

  // 2026-05-17T01:45:00Z; verified via GNU `date -u -d '2026-05-17 01:45:00' +%s`.
  private val epochMay17_0145 = 1778982300L
  private val epochMay17_0200 = 1778983200L
  private val epochMay18_0145 = 1779068700L

  private def base(commitTime: Long, sha: String, count: Int): DevelopmentMetadata =
    DevelopmentMetadata(
      branch = Some("main"),
      commitSha = Some(sha),
      commitCount = Some(count),
      commitTime = Some(commitTime),
      prNumber = None,
      isDirty = false
    )

  test("formatUtcTimestamp: epoch produces 197001010000"):
    assertEquals(SemVer.formatUtcTimestamp(0L), "197001010000")

  test("formatUtcTimestamp: post-2038 epoch does not narrow to Int"):
    // 2050-06-15T12:34:00Z = 2538909240 seconds, beyond Int.MaxValue = 2147483647.
    assertEquals(SemVer.formatUtcTimestamp(2538909240L), "205006151234")

  test("formatUtcTimestamp: leap-day"):
    // 2024-02-29T12:34:00Z = 1709210040 seconds.
    assertEquals(SemVer.formatUtcTimestamp(1709210040L), "202402291234")

  test("formatUtcTimestamp: reference timestamp"):
    assertEquals(SemVer.formatUtcTimestamp(epochMay17_0145), "202605170145")

  test("developmentVersion: identifiers appear in the declared order"):
    val meta = DevelopmentMetadata(
      branch = Some("feature-x"),
      commitSha = Some("abcdef123456"),
      commitCount = Some(7),
      commitTime = Some(epochMay17_0145),
      prNumber = Some(42),
      isDirty = true
    )
    val v = scheme.developmentVersion(core, meta)
    val ids = v.metadata.map(_.identifiers).getOrElse(Nil)
    assertEquals(
      ids,
      List("202605170145", "feature-x", "abcdef123456", "pr42", "dirty")
    )

  test("developmentVersion: optional identifiers omitted when absent"):
    val meta = DevelopmentMetadata(
      branch = Some("main"),
      commitSha = Some("abc1234"),
      commitCount = Some(0),
      commitTime = Some(epochMay17_0145),
      prNumber = None,
      isDirty = false
    )
    val v = scheme.developmentVersion(core, meta)
    val ids = v.metadata.map(_.identifiers).getOrElse(Nil)
    assertEquals(ids, List("202605170145", "main", "abc1234"))

  test("developmentVersion: commits count never appears in the rendered identifiers"):
    val meta = DevelopmentMetadata(
      branch = Some("main"),
      commitSha = Some("abc1234"),
      commitCount = Some(99),
      commitTime = Some(epochMay17_0145),
      prNumber = None,
      isDirty = false
    )
    val v = scheme.developmentVersion(core, meta)
    val ids = v.metadata.map(_.identifiers).getOrElse(Nil)
    assert(!ids.exists(_.contains("99")), s"commit count 99 leaked into $ids")

  test("sortability: rendered strings sort chronologically for same base"):
    val earlier = SemVer.Formatter.full.format(scheme.developmentVersion(core, base(epochMay17_0145, "111aaaa", 3)))
    val sameDayLater = SemVer.Formatter.full.format(scheme.developmentVersion(core, base(epochMay17_0200, "222bbbb", 4)))
    val nextDay = SemVer.Formatter.full.format(scheme.developmentVersion(core, base(epochMay18_0145, "333cccc", 5)))
    val ordered = List(earlier, sameDayLater, nextDay)
    assertEquals(ordered.sorted, ordered)
    assert(earlier < sameDayLater, s"$earlier should sort before $sameDayLater")
    assert(sameDayLater < nextDay, s"$sameDayLater should sort before $nextDay")

  test("developmentVersion: empty commitTime produces no leading timestamp identifier"):
    val meta = DevelopmentMetadata(
      branch = Some("main"),
      commitSha = Some("abc1234"),
      commitCount = Some(0),
      commitTime = None,
      prNumber = None,
      isDirty = false
    )
    val v = scheme.developmentVersion(core, meta)
    val ids = v.metadata.map(_.identifiers).getOrElse(Nil)
    assertEquals(ids.headOption, Some("main"))

  test("sanitiseBranchIdentifier: lowercases and replaces slashes"):
    assertEquals(SemVer.sanitiseBranchIdentifier("Feature/Auth-Fix"), "feature-auth-fix")

  test("sanitiseBranchIdentifier: replaces periods, underscores, and other punctuation"):
    assertEquals(SemVer.sanitiseBranchIdentifier("release/v2.0.x"), "release-v2-0-x")
    assertEquals(SemVer.sanitiseBranchIdentifier("Feature_RC.2"), "feature-rc-2")
    assertEquals(SemVer.sanitiseBranchIdentifier("hot!fix+#123"), "hot-fix-123")

  test("sanitiseBranchIdentifier: collapses consecutive separators and trims"):
    assertEquals(SemVer.sanitiseBranchIdentifier("--foo//bar..baz--"), "foo-bar-baz")

  test("sanitiseBranchIdentifier: replaces non-ASCII characters"):
    // Non-ASCII letters fall outside the SemVer grammar; each becomes a hyphen separator.
    assertEquals(SemVer.sanitiseBranchIdentifier("naive-cafe"), "naive-cafe")
    assertEquals(SemVer.sanitiseBranchIdentifier("naïve-café"), "na-ve-caf")

  test("sanitiseBranchIdentifier: empty or all-invalid input becomes 'detached'"):
    assertEquals(SemVer.sanitiseBranchIdentifier(""), "detached")
    assertEquals(SemVer.sanitiseBranchIdentifier("///"), "detached")
    assertEquals(SemVer.sanitiseBranchIdentifier("..."), "detached")

  test("developmentVersion: branch with periods round-trips through sanitisation"):
    val meta = DevelopmentMetadata(
      branch = Some("release/v2.0.x"),
      commitSha = Some("abc1234"),
      commitCount = Some(0),
      commitTime = Some(epochMay17_0145),
      prNumber = None,
      isDirty = false
    )
    val v = scheme.developmentVersion(core, meta)
    val ids = v.metadata.map(_.identifiers).getOrElse(Nil)
    assertEquals(ids, List("202605170145", "release-v2-0-x", "abc1234"))

  test("developmentVersion: branch=None renders as 'detached' identifier"):
    val meta = DevelopmentMetadata(
      branch = None,
      commitSha = Some("abc1234"),
      commitCount = Some(0),
      commitTime = Some(epochMay17_0145),
      prNumber = None,
      isDirty = false
    )
    val v = scheme.developmentVersion(core, meta)
    val ids = v.metadata.map(_.identifiers).getOrElse(Nil)
    assertEquals(ids, List("202605170145", "detached", "abc1234"))
end DevelopmentVersionSuite
