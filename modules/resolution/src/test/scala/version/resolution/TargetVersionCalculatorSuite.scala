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
package version.resolution

import munit.FunSuite

import version.resolution.domain.CommitSha
import version.resolution.domain.Keyword.*
import version.resolution.domain.Tag
import version.semver.*

/** Unit tests for [[TargetVersionCalculator]]. */
class TargetVersionCalculatorSuite extends FunSuite:

  private def v(s: String): SemVer = SemVer.parseUnsafe(s)
  private val sha = CommitSha("0000000000000000000000000000000000000000")
  private def tag(s: String): Tag[SemVer] = Tag(s"v$s", sha, v(s))

  test("target accepted when higher than reachable final"):
    val result = TargetVersionCalculator.selectValidTarget[SemVer](
      targets = List(v("2.2.6")),
      highestReachable = Some(tag("2.2.5")),
      highestRepo = Some(tag("2.2.5")),
      allRepoFinals = List(tag("2.2.5")),
      isHeadOnFinalTag = false
    )
    assertEquals(result, Some(v("2.2.6")))

  test("target rejected when equal to reachable final (regression)"):
    val result = TargetVersionCalculator.selectValidTarget[SemVer](
      targets = List(v("2.2.5")),
      highestReachable = Some(tag("2.2.5")),
      highestRepo = Some(tag("2.2.5")),
      allRepoFinals = List(tag("2.2.5")),
      isHeadOnFinalTag = false
    )
    assertEquals(result, None)

  test("target equality accepted against pre-release core"):
    val result = TargetVersionCalculator.selectValidTarget[SemVer](
      targets = List(v("3.1.0")),
      highestReachable = Some(tag("3.1.0-rc.2")),
      highestRepo = Some(tag("3.1.0-rc.2")),
      allRepoFinals = Nil,
      isHeadOnFinalTag = false
    )
    assertEquals(result, Some(v("3.1.0")))

  test("multiple targets: highest valid core wins"):
    val result = TargetVersionCalculator.selectValidTarget[SemVer](
      targets = List(v("1.5.0"), v("1.6.0")),
      highestReachable = Some(tag("1.4.0")),
      highestRepo = Some(tag("1.4.0")),
      allRepoFinals = List(tag("1.4.0")),
      isHeadOnFinalTag = false
    )
    assertEquals(result, Some(v("1.6.0")))

  test("major bump resets minor and patch"):
    val result = TargetVersionCalculator.fromKeywords[SemVer](v("1.2.3"), List(ComponentBump(0)))
    assertEquals(result.show, "2.0.0")

  test("pre-1.0 major bump caps to a minor bump (no premature 1.0.0)"):
    val result = TargetVersionCalculator.fromKeywords[SemVer](v("0.93.9"), List(ComponentBump(0)))
    assertEquals(result.show, "0.94.0")

  test("pre-1.0 minor bump stays a minor bump"):
    val result = TargetVersionCalculator.fromKeywords[SemVer](v("0.93.9"), List(ComponentBump(1)))
    assertEquals(result.show, "0.94.0")

  test("pre-1.0 major bump on a pre-release base caps to a minor bump of its core"):
    val result = TargetVersionCalculator.fromKeywords[SemVer](v("0.5.0-rc.1"), List(ComponentBump(0)))
    assertEquals(result.show, "0.6.0")

  test("pre-1.0 explicit major set still reaches 1.0.0"):
    val result = TargetVersionCalculator.fromKeywords[SemVer](v("0.93.9"), List(ComponentSet(0, 1)))
    assertEquals(result.show, "1.0.0")

  test("minor bump resets patch"):
    val result = TargetVersionCalculator.fromKeywords[SemVer](v("1.2.3"), List(ComponentBump(1)))
    assertEquals(result.show, "1.3.0")

  test("absolute set overrides relative"):
    val result = TargetVersionCalculator.fromKeywords[SemVer](
      v("1.2.3"),
      List(ComponentSet(1, 9), ComponentBump(1))
    )
    assertEquals(result.show, "1.9.0")

  test("no directives on final base: default patch bump"):
    val result = TargetVersionCalculator.fromKeywords[SemVer](v("1.2.3"), Nil)
    assertEquals(result.show, "1.2.4")

  test("no directives on pre-release base: core unchanged"):
    val result = TargetVersionCalculator.fromKeywords[SemVer](v("3.0.0-rc.3"), Nil)
    assertEquals(result.show, "3.0.0")

  test("absolute patch set"):
    val result = TargetVersionCalculator.fromKeywords[SemVer](v("1.2.3"), List(ComponentSet(2, 10)))
    assertEquals(result.show, "1.2.10")
end TargetVersionCalculatorSuite
