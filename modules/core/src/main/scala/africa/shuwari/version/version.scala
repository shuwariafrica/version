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

import zio.prelude
import zio.prelude.{Ordering as _, *}

import africa.shuwari.version
import africa.shuwari.version.PreRelease.validNonNumberedInstances
import africa.shuwari.version.PreRelease.validNumberedInstances

sealed abstract private class VersionNumberField extends Newtype[Int]:
  override inline def assertion: Assertion[Int] = Assertion.greaterThanOrEqualTo(0)

object MajorVersion extends VersionNumberField:
  given Ord[MajorVersion] = MajorVersion.derive(Ord[Int])
  given Ordering[MajorVersion] = Ord[MajorVersion].toScala
type MajorVersion = MajorVersion.Type

object MinorVersion extends VersionNumberField:
  given Ord[MinorVersion] = MinorVersion.derive(Ord[Int])
  given Ordering[MinorVersion] = Ord[MinorVersion].toScala
type MinorVersion = MinorVersion.Type

object PatchNumber extends VersionNumberField:
  given Ord[PatchNumber] = PatchNumber.derive(Ord[Int])
  given Ordering[PatchNumber] = Ord[PatchNumber].toScala
type PatchNumber = PatchNumber.Type

sealed abstract class PreReleaseClassifier(val aliases: NonEmptyList[String]) extends Product with Serializable:
  private inline def matches(str: String): Boolean = aliases.find(str.equalsIgnoreCase).isDefined
  override def toString: String = aliases.head

object PreReleaseClassifier:

  def unapply(string: String): Option[PreReleaseClassifier] =
    List(Milestone, Alpha, Beta, ReleaseCandidate, Snapshot, Unclassified).find(_.matches(string))

  case object Milestone extends PreReleaseClassifier(NonEmptyList("m", "milestone"))
  case object Alpha extends PreReleaseClassifier(NonEmptyList("alpha", "a"))
  case object Beta extends PreReleaseClassifier(NonEmptyList("beta", "b"))
  case object ReleaseCandidate extends PreReleaseClassifier(NonEmptyList("rc", "cr"))
  case object Snapshot extends PreReleaseClassifier(NonEmptyList("snapshot"))
  case object Unclassified extends PreReleaseClassifier(NonEmptyList("unclassified"))

  given Ord[PreReleaseClassifier] with
    override protected def checkCompare(l: PreReleaseClassifier, r: PreReleaseClassifier): prelude.Ordering =
      if Equal.default[PreReleaseClassifier].equal(l, r)
      then prelude.Ordering.Equals
      else
        def rank(classifier: PreReleaseClassifier) = classifier match
          case Unclassified     => 30
          case Milestone        => 40
          case Alpha            => 50
          case Beta             => 60
          case ReleaseCandidate => 70
          case Snapshot         => 80
        Ord[Int].compare(rank(l), rank(r))

  given Ordering[PreReleaseClassifier] = Ord[PreReleaseClassifier].toScala
end PreReleaseClassifier

object PreReleaseNumber extends Newtype[Int]:
  override inline def assertion: Assertion[Int] = Assertion.greaterThanOrEqualTo(1)
  given Ord[PreReleaseNumber] = PreReleaseNumber.derive(Ord[Int])
  given Ordering[PreReleaseNumber] = Ord[PreReleaseNumber].toScala

type PreReleaseNumber = PreReleaseNumber.Type

final case class PreRelease(classifier: PreReleaseClassifier, number: Option[PreReleaseNumber]):
  assert(validNonNumberedInstances(classifier, number), "Snapshot PreRelease instances cannot have a number defined.")
  assert(validNumberedInstances(classifier, number), "Only Snapshot PreRelease instances cannot have a number defined.")
  override def toString: String = s"$classifier${number.getOrElse("")}"

object PreRelease:

  private inline def nonNumberedPreReleaseClassifier(classifier: PreReleaseClassifier): Boolean =
    classifier === PreReleaseClassifier.Snapshot || classifier === PreReleaseClassifier.Unclassified

  private inline def validNonNumberedInstances(
    classifier: PreReleaseClassifier,
    number: Option[PreReleaseNumber]): Boolean =
    if nonNumberedPreReleaseClassifier(classifier) then if number.isEmpty then true else false
    else true

  private inline def validNumberedInstances(
    classifier: PreReleaseClassifier,
    number: Option[PreReleaseNumber]): Boolean =
    if !nonNumberedPreReleaseClassifier(classifier) then if number.nonEmpty then true else false
    else true

  def snapshot: PreRelease = PreRelease(PreReleaseClassifier.Snapshot, None)
  def unclassified: PreRelease = PreRelease(PreReleaseClassifier.Unclassified, None)
  def milestone(number: PreReleaseNumber): PreRelease = PreRelease(PreReleaseClassifier.Milestone, Some(number))
  def alpha(number: PreReleaseNumber): PreRelease = PreRelease(PreReleaseClassifier.Alpha, Some(number))
  def beta(number: PreReleaseNumber): PreRelease = PreRelease(PreReleaseClassifier.Beta, Some(number))
  def releaseCandidate(number: PreReleaseNumber): PreRelease =
    PreRelease(PreReleaseClassifier.ReleaseCandidate, Some(number))

  given Ord[PreRelease] with
    override def checkCompare(l: PreRelease, r: PreRelease): prelude.Ordering = (l.number, r.number) match
      case (Some(lnum), Some(rnum)) =>
        Ord[(PreReleaseClassifier, PreReleaseNumber)].compare((l.classifier, lnum), (r.classifier, rnum))
      case _ => Ord[PreReleaseClassifier].compare(l.classifier, r.classifier)

  given Ordering[PreRelease] = Ord[PreRelease].toScala
end PreRelease

final case class Version(
  majorVersion: MajorVersion,
  minorVersion: MinorVersion,
  patchNumber: PatchNumber,
  preRelease: Option[PreRelease]):

  def parts: (MajorVersion, MinorVersion, PatchNumber, Option[PreRelease]) =
    (majorVersion, minorVersion, patchNumber, preRelease)

  def stable: Boolean = preRelease.isEmpty

  override def toString: String =
    s"$majorVersion.$minorVersion.$patchNumber${preRelease.map(preRelease => "-" + preRelease).getOrElse("")}"

object Version:
  def apply(majorVersion: MajorVersion, minorVersion: MinorVersion, patchNumber: PatchNumber): Version =
    apply(majorVersion, minorVersion, patchNumber, None)

  def apply(parts: (MajorVersion, MinorVersion, PatchNumber)): Version = apply(parts._1, parts._2, parts._3, None)

  def apply(parts: (MajorVersion, MinorVersion, PatchNumber, PreRelease)): Version =
    apply(parts._1, parts._2, parts._3, Some(parts._4))

  def apply(
    majorVersion: MajorVersion,
    minorVersion: MinorVersion,
    patchNumber: PatchNumber,
    preRelease: PreRelease): Version =
    apply(majorVersion, minorVersion, patchNumber, Some(preRelease))

  given Ord[Version] with
    override protected def checkCompare(l: Version, r: Version): prelude.Ordering =
      object PreReleaseOptionOrd extends Ord[Option[PreRelease]]:
        override protected def checkCompare(l: Option[PreRelease], r: Option[PreRelease]): prelude.Ordering =
          (l, r) match
            case (Some(lpr), Some(rpr)) => Ord.default[PreRelease].compare(lpr, rpr)
            case (None, None)           => prelude.Ordering.Equals
            case (Some(_), None)        => prelude.Ordering.LessThan
            case (None, Some(_))        => prelude.Ordering.GreaterThan
      Ord
        .Tuple4Ord(Ord.default[MajorVersion], Ord.default[MinorVersion], Ord.default[PatchNumber], PreReleaseOptionOrd)
        .compare(l.parts, r.parts)

  given Ordering[Version] = Ord[Version].toScala
end Version
