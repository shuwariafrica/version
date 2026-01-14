/****************************************************************
 * Copyright © Shuwari Africa Ltd.                              *
 *                                                              *
 * This file is licensed to you under the terms of the Apache   *
 * License Version 2.0 (the "License"); you may not use this    *
 * file except in compliance with the License. You may obtain   *
 * a copy of the License at:                                    *
 *                                                              *
 *     https://www.apache.org/licenses/LICENSE-2.0              *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, *
 * either express or implied. See the License for the specific  *
 * language governing permissions and limitations under the     *
 * License.                                                     *
 ****************************************************************/
package version.cli.core

import munit.FunSuite

import version.*
import version.cli.core.domain.*

final class TargetVersionCalculatorSuite extends FunSuite:

  private def core(m: Int, n: Int, p: Int) =
    Version(
      MajorVersion.from(m).getOrElse(sys.error(s"invalid major: $m")),
      MinorVersion.from(n).getOrElse(sys.error(s"invalid minor: $n")),
      PatchNumber.from(p).getOrElse(sys.error(s"invalid patch: $p"))
    )

  private def tag(name: String, m: Int, n: Int, p: Int, pre: Option[PreRelease] = None) = // scalafix:ok
    Tag(TagName(name), CommitSha("deadbeef"), core(m, n, p).copy(preRelease = pre))

  test("Rule A: reject target <= reachable final core") {
    val reachFinal = Some(tag("v1.0.0", 1, 0, 0))
    val accepted = TargetVersionCalculator.selectValidTarget(
      targets = List(core(1, 0, 0), core(1, 0, 1)),
      highestReachable = reachFinal,
      highestRepo = None,
      allRepoFinals = List(reachFinal.get),
      isHeadOnFinalTag = false
    )
    assertEquals(accepted, Some(core(1, 0, 1)))
  }

  test("Rule B: accept target == pre-release core; reject lower than pre-release core") {
    val rc = PreRelease.releaseCandidate(PreReleaseNumber.from(2).getOrElse(sys.error("invalid pr num")))
    val reachPre = Some(tag("v1.1.0-rc.2", 1, 1, 0, Some(rc)))
    val accepted = TargetVersionCalculator.selectValidTarget(
      targets = List(core(1, 1, 0), core(1, 0, 9)),
      highestReachable = reachPre,
      highestRepo = None,
      allRepoFinals = Nil,
      isHeadOnFinalTag = false
    )
    assertEquals(accepted, Some(core(1, 1, 0)))
  }

  test("Rule C: no base; repo has final -> reject <= final; else accept >= highest pre core") {
    val finals = List(tag("v4.3.0", 4, 3, 0))
    val accepted1 = TargetVersionCalculator.selectValidTarget(
      targets = List(core(3, 9, 9)),
      highestReachable = None,
      highestRepo = finals.headOption,
      allRepoFinals = finals,
      isHeadOnFinalTag = false
    )
    assertEquals(accepted1, None)

    val rc = tag(
      "v2.0.0-rc.1",
      2,
      0,
      0,
      Some(PreRelease.releaseCandidate(PreReleaseNumber.from(1).getOrElse(sys.error("invalid pr num"))))
    )
    val accepted2 = TargetVersionCalculator.selectValidTarget(
      targets = List(core(2, 0, 0)),
      highestReachable = None,
      highestRepo = Some(rc),
      allRepoFinals = Nil,
      isHeadOnFinalTag = false
    )
    assertEquals(accepted2, Some(core(2, 0, 0)))
  }

  test("Rule D: head exactly at reachable final -> equal target rejected") {
    val f = tag("v1.0.0", 1, 0, 0)
    val accepted = TargetVersionCalculator.selectValidTarget(
      targets = List(core(1, 0, 0), core(1, 0, 1)),
      highestReachable = Some(f),
      highestRepo = None,
      allRepoFinals = List(f),
      isHeadOnFinalTag = true
    )
    assertEquals(accepted, Some(core(1, 0, 1)))
  }

  test("Absolutes and relatives precedence with resets and defaults") {
    val base = core(1, 2, 3)
    val v1 = TargetVersionCalculator.fromKeywords(base, List(Keyword.MajorChange))
    assertEquals(v1, core(2, 0, 0))

    val v2 = TargetVersionCalculator.fromKeywords(base, List(Keyword.MinorChange))
    assertEquals(v2, core(1, 3, 0))

    val v3 = TargetVersionCalculator.fromKeywords(base, List(Keyword.PatchChange))
    assertEquals(v3, core(1, 2, 4))

    val v4 = TargetVersionCalculator.fromKeywords(
      base,
      List(Keyword.MajorSet(MajorVersion.from(3).getOrElse(sys.error("invalid major"))), Keyword.MinorChange)
    )
    assertEquals(v4, core(3, 0, 0))

    val pre = base.copy(preRelease = Some(PreRelease.snapshot))
    val v5 = TargetVersionCalculator.fromKeywords(pre, Nil)
    assertEquals(v5, base.copy(preRelease = None, buildMetadata = None))
  }

  test("Duplicate relative increments coalesce to single increment") {
    val base = core(1, 2, 3)
    val v1 = TargetVersionCalculator.fromKeywords(base, List(Keyword.MinorChange, Keyword.MinorChange))
    assertEquals(v1, core(1, 3, 0))
  }

  test("Absolute overrides relative; highest absolute for same component wins") {
    val base = core(1, 2, 3)
    val v = TargetVersionCalculator.fromKeywords(
      base,
      List(
        Keyword.MinorChange,
        Keyword.MinorSet(MinorVersion.from(9).getOrElse(sys.error("invalid minor"))),
        Keyword.MinorSet(MinorVersion.from(7).getOrElse(sys.error("invalid minor")))
      )
    )
    assertEquals(v, core(1, 9, 0))
  }

  test("Multiple targets: choose highest valid after applying ignore rules") {
    val finals = List(tag("v2.2.5", 2, 2, 5))
    val accepted = TargetVersionCalculator.selectValidTarget(
      targets = List(core(2, 2, 4), core(2, 2, 6), core(2, 2, 5)),
      highestReachable = None,
      highestRepo = finals.headOption,
      allRepoFinals = finals,
      isHeadOnFinalTag = false
    )
    // 2.2.4 and 2.2.5 are ignored; 2.2.6 is valid and chosen
    assertEquals(accepted, Some(core(2, 2, 6)))
  }
end TargetVersionCalculatorSuite
