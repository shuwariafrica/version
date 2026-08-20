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

import version.DevelopmentMetadata
import version.Intent
import version.Request
import version.ResolvableScheme
import version.VersionScheme

class SemVerWorkflowSuite extends FunSuite:

  private val scheme = summon[VersionScheme[SemVer]]
  private val workflow = summon[ResolvableScheme[SemVer]]

  // 1700000000 is 2023-11-14 22:13:20 UTC.
  private val CommitTime = 1700000000L
  private val Timestamp = "202311142213"
  private val Sha = "0123456789abcdef0123456789abcdef01234567"

  private val Absent = DevelopmentMetadata(None, None, None, None, None, isDirty = false)

  private def v(input: String): SemVer = SemVer.parseUnsafe(input)

  private def development(release: String, meta: DevelopmentMetadata): String =
    scheme.show(workflow.developmentVersion(v(release), meta))

  test("a project with no releases starts at 0.1.0") {
    assertEquals(scheme.show(workflow.initialVersion), "0.1.0")
  }

  test("the default target advances a released version by the least significant intent") {
    assertEquals(scheme.show(workflow.defaultTarget(v("1.2.3"))), "1.2.4")
    assertEquals(scheme.show(workflow.defaultTarget(v("0.4.2"))), "0.4.3")
    assertEquals(scheme.show(workflow.defaultTarget(v("0.0.7"))), "0.0.8")
  }

  test("the default target releases a pending pre-release rather than advancing past it") {
    assertEquals(scheme.show(workflow.defaultTarget(v("1.2.3-rc.1"))), "1.2.3")
    assertEquals(scheme.show(workflow.defaultTarget(v("1.2.3-rc.1+sha.abc"))), "1.2.3")
  }

  test("a fix directive emits an intent rather than being suppressed") {
    assertEquals(workflow.directives.get("fix"), Some(Request.Advance(Intent.Fix)))
  }

  test("the directive vocabulary maps component names to bumps and change words to intents") {
    assertEquals(workflow.directives.get("major"), Some(Request.Bump("major")))
    assertEquals(workflow.directives.get("minor"), Some(Request.Bump("minor")))
    assertEquals(workflow.directives.get("patch"), Some(Request.Bump("patch")))
    assertEquals(workflow.directives.get("breaking"), Some(Request.Advance(Intent.Breaking)))
    assertEquals(workflow.directives.get("feature"), Some(Request.Advance(Intent.Feature)))
    assertEquals(workflow.directives.get("feat"), Some(Request.Advance(Intent.Feature)))
    assertEquals(workflow.directives.get("stable"), Some(Request.Advance(Intent.Stable)))
    assertEquals(workflow.directives.get("unknown"), None)
  }

  test("a version is a snapshot exactly when its pre-release names the SNAPSHOT classifier") {
    assert(workflow.snapshot(v("1.0.0-SNAPSHOT")))
    assert(workflow.snapshot(v("1.0.0-snapshot")))
    assert(!workflow.snapshot(v("1.0.0-rc.1")))
    assert(!workflow.snapshot(v("1.0.0")))
  }

  test("a development version carries the SNAPSHOT pre-release over the release numbers") {
    val result = workflow.developmentVersion(v("1.2.3"), Absent.copy(branch = Some("main")))
    assertEquals(result.major.value, 1L)
    assertEquals(result.minor.value, 2L)
    assertEquals(result.patch.value, 3L)
    assert(workflow.snapshot(result))
  }

  test("development metadata is assembled as timestamp, branch, commit, pull request, dirty flag") {
    assertEquals(
      development(
        "1.2.3",
        Absent.copy(branch = Some("main"), commitSha = Some(Sha), commitTime = Some(CommitTime), prNumber = Some(42), isDirty = true)),
      s"1.2.3-SNAPSHOT+$Timestamp.main.$Sha.pr42.dirty"
    )
  }

  test("absent metadata leaves out its identifier rather than emitting a placeholder") {
    assertEquals(development("1.2.3", Absent.copy(branch = Some("main"))), "1.2.3-SNAPSHOT+main")
    assertEquals(
      development("1.2.3", Absent.copy(branch = Some("main"), commitSha = Some(Sha), commitTime = Some(CommitTime))),
      s"1.2.3-SNAPSHOT+$Timestamp.main.$Sha"
    )
  }

  test("a missing branch is recorded as detached") {
    assertEquals(development("1.2.3", Absent.copy(commitTime = Some(CommitTime))), s"1.2.3-SNAPSHOT+$Timestamp.detached")
  }

  test("a branch label is lowercased and reduced to the build-metadata charset") {
    assertEquals(development("1.0.0", Absent.copy(branch = Some("Feature/ABC-123"))), "1.0.0-SNAPSHOT+feature-abc-123")
  }

  test("runs of replaced characters collapse to a single hyphen and the ends are trimmed") {
    assertEquals(development("1.0.0", Absent.copy(branch = Some("__wip//branch__"))), "1.0.0-SNAPSHOT+wip-branch")
  }

  test("a branch label with nothing usable in it is recorded as detached") {
    assertEquals(development("1.0.0", Absent.copy(branch = Some("///"))), "1.0.0-SNAPSHOT+detached")
  }

  test("a negative pull request number is clamped rather than emitted as an invalid identifier") {
    assertEquals(development("1.0.0", Absent.copy(branch = Some("main"), prNumber = Some(-7))), "1.0.0-SNAPSHOT+main.pr0")
  }

  test("a development version parses back to an equal value") {
    val rendered = development(
      "1.2.3",
      Absent.copy(branch = Some("main"), commitSha = Some(Sha), commitTime = Some(CommitTime), prNumber = Some(42), isDirty = true))
    assertEquals(SemVer.parse(rendered).map(scheme.show(_)), Right(rendered))
  }

end SemVerWorkflowSuite
