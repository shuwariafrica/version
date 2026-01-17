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
package version.cli.core

import version.*
import version.cli.core.domain.Keyword
import version.cli.core.domain.Tag

/** Logic for selecting/deriving the target version core given keywords and context.
  *
  * Rules implemented from the specification:
  *   - target overrides (with validation A–F)
  *   - absolutes > relatives; duplicate relatives coalesce to single increment
  *   - reset semantics across components (Major resets Minor & Patch; Minor resets Patch)
  *   - defaults based on pre-release/final base state
  */
object TargetVersionCalculator:

  /** Select the highest accepted target (core only) given the context. */
  def selectValidTarget(
    targets: List[Version],
    highestReachable: Option[Tag],
    highestRepo: Option[Tag],
    allRepoFinals: List[Tag],
    isHeadOnFinalTag: Boolean
  ): Option[Version] =
    val parsedCore = targets.map(dropPre)
    val reachableFinalCore = highestReachable.collect { case t if t.version.preRelease.isEmpty => dropPre(t.version) }
    val highestReach = highestReachable.map(_.version)
    val repoHighestFinalCore = allRepoFinals.map(_.version).map(dropPre).sorted.lastOption
    val repoHighest = highestRepo.map(_.version)

    val accepted = parsedCore.filter { tc =>
      // A) Regressive vs reachable final release (reject <= Tf)
      val ruleA = reachableFinalCore.forall(tf => tc > tf)
      // D) Equal core to final at HEAD commit (forbidden)
      val ruleD = if isHeadOnFinalTag && reachableFinalCore.contains(tc) then false else true
      // B) If highest reachable is pre-release, allow equal to its core; reject lower
      val ruleB = highestReach match
        case Some(v) if v.preRelease.isDefined =>
          val prCore = dropPre(v)
          tc >= prCore
        case _ => true
      // C) Repo-wide when no reachable base; equality allowed only vs pre-release core
      val ruleC =
        (repoHighestFinalCore, repoHighest) match
          case (Some(rf), _)                               => tc > rf
          case (None, Some(vh)) if vh.preRelease.isDefined =>
            tc >= dropPre(vh)
          case (None, _) => true
      ruleA && ruleD && ruleB && ruleC
    }

    accepted.sorted.lastOption
  end selectValidTarget

  /** Compute target core from absolutes/relatives or default advancement from base. */
  def fromKeywords(baseVersion: Version, keywords: List[Keyword]): Version =
    import Keyword.*
    val absolutes = keywords.collect { case a: Absolute => a }
    val relatives = keywords.collect { case r: Relative => r }.distinct

    val majorSet = absolutes.collect { case MajorSet(v) => v }.maxOption
    val minorSet = absolutes.collect { case MinorSet(v) => v }.maxOption
    val patchSet = absolutes.collect { case PatchSet(v) => v }.maxOption

    val hasMajor = relatives.contains(MajorChange)
    val hasMinor = relatives.contains(MinorChange)

    if majorSet.isDefined || hasMajor then Version(majorSet.getOrElse(baseVersion.major.increment), MinorVersion.reset, PatchNumber.reset)
    else if minorSet.isDefined || hasMinor then
      Version(baseVersion.major, minorSet.getOrElse(baseVersion.minor.increment), PatchNumber.reset)
    else if patchSet.isDefined then Version(baseVersion.major, baseVersion.minor, patchSet.get)
    else if baseVersion.preRelease.isDefined then dropPre(baseVersion)
    else Version(baseVersion.major, baseVersion.minor, baseVersion.patch.increment)

  private def dropPre(v: Version): Version = v.copy(preRelease = None, metadata = None)
end TargetVersionCalculator
