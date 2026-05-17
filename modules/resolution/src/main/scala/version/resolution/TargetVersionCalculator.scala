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

import version.ResolvableScheme
import version.resolution.domain.Keyword
import version.resolution.domain.Tag

/** Selects or derives the target version core from extracted [[version.resolution.domain.Keyword Keyword]] directives
  * and the reachable tag context, using the supplied [[version.ResolvableScheme ResolvableScheme]] for component
  * manipulation.
  */
object TargetVersionCalculator:

  /** Select the highest accepted target (core only) given the context. */
  def selectValidTarget[V](
    targets: List[V],
    highestReachable: Option[Tag[V]],
    highestRepo: Option[Tag[V]],
    allRepoFinals: List[Tag[V]],
    isHeadOnFinalTag: Boolean
  )(using scheme: ResolvableScheme[V]): Option[V] =
    given Ordering[V] = scheme.ordering
    import scala.math.Ordering.Implicits.infixOrderingOps

    val parsedCore = targets.map(_.core)
    val reachableFinalCore = highestReachable.collect { case t if t.version.isFinal => t.version.core }
    val highestReach = highestReachable.map(_.version)
    val repoHighestFinalCore = allRepoFinals.map(_.version.core).sorted.lastOption
    val repoHighest = highestRepo.map(_.version)

    val accepted = parsedCore.filter { tc =>
      val ruleA = reachableFinalCore.forall(tf => tc > tf)
      val ruleD = if isHeadOnFinalTag && reachableFinalCore.contains(tc) then false else true
      val ruleB = highestReach match
        case Some(v) if !v.isFinal =>
          val prCore = v.core
          tc >= prCore
        case _ => true
      val ruleC =
        (repoHighestFinalCore, repoHighest) match
          case (Some(rf), _)                   => tc > rf
          case (None, Some(vh)) if !vh.isFinal => tc >= vh.core
          case (None, _)                       => true
      ruleA && ruleD && ruleB && ruleC
    }

    accepted.sorted.lastOption
  end selectValidTarget

  /** Compute target core from generic keywords or default advancement from base. */
  def fromKeywords[V](baseVersion: V, keywords: List[Keyword])(using scheme: ResolvableScheme[V]): V =
    import Keyword.*
    val bumps = keywords.collect { case ComponentBump(i) => i }.distinct
    val sets = keywords
      .collect { case ComponentSet(i, v) => (i, v) }
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2).max)
      .toMap

    // Find the highest-precedence component that has a bump or set
    val componentCount = scheme.layout.length
    val affectedIndex: Option[Int] =
      (0 until componentCount).find(i => sets.contains(i) || bumps.contains(i))

    affectedIndex match
      case Some(idx) =>
        val setVal = sets.get(idx)
        val hasBump = bumps.contains(idx)
        if setVal.isDefined then
          // Absolute set: set component, reset below
          baseVersion.core.setComponent(idx, setVal.get) match
            case Right(result) => result
            case Left(_)       => baseVersion.core // invalid value, fall through
        else if hasBump then
          // Relative bump: increment component (resets below per scheme)
          baseVersion.core.incrementComponent(idx)
        else baseVersion.core
      case None =>
        // No directives: default advancement
        if !baseVersion.isFinal then baseVersion.core
        else baseVersion.defaultBump.core
  end fromKeywords
end TargetVersionCalculator
