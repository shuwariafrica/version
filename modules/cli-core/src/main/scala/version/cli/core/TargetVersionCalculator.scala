package version.cli.core

import version.*
import version.cli.core.domain.Keyword
import version.cli.core.domain.Tag
import version.operations.isFinal
import version.operations.isPreRelease

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
    val reachableFinalCore = highestReachable.collect { case t if t.version.isFinal => dropPre(t.version) }
    val highestReach = highestReachable.map(_.version)
    val repoHighestFinalCore = allRepoFinals.map(_.version).map(dropPre).sorted(using Version.ordering).lastOption
    val repoHighest = highestRepo.map(_.version)

    val accepted = parsedCore.filter { tc =>
      // A) Regressive vs reachable final release (reject <= Tf)
      val ruleA = reachableFinalCore.forall(tf => Version.ordering.gt(tc, tf))
      // D) Equal core to final at HEAD commit (forbidden)
      val ruleD = if isHeadOnFinalTag && reachableFinalCore.contains(tc) then false else true
      // B) If highest reachable is pre-release, allow equal to its core; reject lower
      val ruleB = highestReach match
        case Some(v) if v.isPreRelease =>
          val prCore = dropPre(v)
          Version.ordering.gteq(tc, prCore)
        case _ => true
      // C) Repo-wide when no reachable base; equality allowed only vs pre-release core
      val ruleC =
        (repoHighestFinalCore, repoHighest) match
          case (Some(rf), _)                       => Version.ordering.gt(tc, rf)
          case (None, Some(vh)) if vh.isPreRelease =>
            Version.ordering.gteq(tc, dropPre(vh))
          case (None, _) => true
      ruleA && ruleD && ruleB && ruleC
    }

    accepted.sorted(using Version.ordering).lastOption
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
    val hasPatch = relatives.contains(PatchChange)

    if majorSet.isDefined || hasMajor then Version(majorSet.getOrElse(baseVersion.major.increment), MinorVersion.reset, PatchNumber.reset)
    else if minorSet.isDefined || hasMinor then
      Version(baseVersion.major, minorSet.getOrElse(baseVersion.minor.increment), PatchNumber.reset)
    else if patchSet.isDefined || hasPatch then
      Version(baseVersion.major, baseVersion.minor, patchSet.getOrElse(baseVersion.patch.increment))
    else if baseVersion.isPreRelease then dropPre(baseVersion)
    else Version(baseVersion.major, baseVersion.minor, baseVersion.patch.increment)

  private def dropPre(v: Version): Version = v.copy(preRelease = None, buildMetadata = None)
end TargetVersionCalculator
