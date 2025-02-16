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
package version

import scala.language.strictEquality

import version.PreReleaseClassifier.aliases
import version.errors.*

/** Parses a pre-release string into a `PreRelease` object.
  *
  * @param str
  *   the pre-release string to parse.
  * @return
  *   a `PreRelease` object.
  */
opaque type MajorVersion = Int

/** Represents a minor version number. */
opaque type MinorVersion = Int

/** Represents a patch number. */
opaque type PatchNumber = Int

/** Represents a pre-release number. */
opaque type PreReleaseNumber = Int

/** Trait for version number field objects.
  *
  * @tparam T
  *   the type of the version number field.
  */
sealed trait VersionNumberField[T]:
  /** The initial value of the version number field. */
  def initial: T

  /** Creates a version number field from an integer value.
    * @return
    *   the version number field.
    * @throws InvalidNumericField
    *   if the value is invalid.
    */
  def apply(v: Int): T

  /** Extracts the integer value from a version number field.
    *
    * @param v
    *   the version number field.
    * @return
    *   the integer value.
    */
  def unnapply(v: T): Int = unwrap(v)

  /** Creates a version number field from an integer value, returning an error if the value is invalid.
    * @return
    *   either the version number field or an [[InvalidNumericField]] error.
    */
  def make(v: Int): Either[InvalidNumericField, T]

  /** Unwraps the version number field to its integer value.
    *
    * @param v
    *   the version number field.
    * @return
    *   the integer value.
    */
  def unwrap(v: T): Int

  def increment(v: T): T = apply(unwrap(v) + 1)

  /** Combines two version number fields by adding their integer values.
    *
    * @param v1
    *   the first version number field.
    * @param v2
    *   the second version number field.
    * @return
    *   the combined version number field.
    */
  def combine(v1: T, v2: T): T = apply(unwrap(v1) + unwrap(v2))
  given CanEqual[T, T] = CanEqual.derived
  given Ordering[T] = Ordering[Int].on(unwrap)
end VersionNumberField

private inline def versionNumber[T](v: Int, minimumValue: Int, f: Int => T, e: Int => VersionError): T =
  if v < minimumValue then throw e(v) else f(v) // scalafix:ok

private inline def makeVersionNumber[T, E <: VersionError](v: Int, minimumValue: Int, f: Int => T, e: Int => E): Either[E, T] =
  Either.cond(v >= minimumValue, f(v), e(v))

object MajorVersion extends VersionNumberField[MajorVersion]:
  final override val initial = MajorVersion(0)
  override def apply(value: Int): MajorVersion = versionNumber(value, 0, identity, InvalidMajorVersion.apply)
  override def make(value: Int): Either[InvalidMajorVersion, MajorVersion] =
    makeVersionNumber(value, 0, MajorVersion.apply, InvalidMajorVersion.apply)
  override def unwrap(v: MajorVersion): Int = v

object MinorVersion extends VersionNumberField[MinorVersion]:
  final val initial = MinorVersion(0)
  override def apply(value: Int): MinorVersion = versionNumber(value, 0, identity, InvalidMinorVersion.apply)
  override def make(value: Int): Either[InvalidMinorVersion, MinorVersion] =
    makeVersionNumber(value, 0, MinorVersion.apply, InvalidMinorVersion.apply)
  override def unwrap(v: MinorVersion): Int = v

object PatchNumber extends VersionNumberField[PatchNumber]:
  final val initial = PatchNumber(0)
  def apply(value: Int): PatchNumber = versionNumber(value, 0, identity, InvalidPatchNumber.apply)
  def make(value: Int): Either[InvalidPatchNumber, PatchNumber] = makeVersionNumber(value, 0, PatchNumber.apply, InvalidPatchNumber.apply)
  def unwrap(v: PatchNumber): Int = v

object PreReleaseNumber extends VersionNumberField[PreReleaseNumber]:
  final val initial = PreReleaseNumber(1)
  def apply(value: Int): PreReleaseNumber = versionNumber(value, 1, identity, InvalidPreReleaseNumber.apply)
  def make(value: Int): Either[InvalidPreReleaseNumber, PreReleaseNumber] =
    makeVersionNumber(value, 1, PreReleaseNumber.apply, InvalidPreReleaseNumber.apply)
  def unwrap(v: PreReleaseNumber): Int = v

/** Enum representing supported pre-release classifiers. */
enum PreReleaseClassifier:
  case Milestone, Alpha, Beta, ReleaseCandidate, Snapshot, Unclassified
  override def toString: String = aliases(this).head

object PreReleaseClassifier:
  final private val classifierMap = PreReleaseClassifier.values.flatMap(c => aliases(c).map(_.nn.toLowerCase -> c)).toMap

  /** Retrieves the `PreReleaseClassifier` from a string.
    *
    * @param string
    *   the string representation of the classifier.
    * @return
    *   an `Option[PreReleaseClassifier]` if the string matches a classifier, None otherwise.
    */
  def unapply(string: String): Option[PreReleaseClassifier] = classifierMap.get(string.toLowerCase)

  /** Checks if the classifier requires a [[PreReleaseNumber]].
    *
    * @param classifier
    *   the pre-release classifier.
    * @return
    *   true if the classifier requires a version number, false otherwise.
    */
  inline def versioned(classifier: PreReleaseClassifier): Boolean =
    classifier != PreReleaseClassifier.Snapshot && classifier != PreReleaseClassifier.Unclassified // scalafix:ok

  /** Retrieves the list of aliases for the pre-release classifier.
    *
    * @param classifier
    *   the pre-release classifier.
    * @return
    *   a list of aliases for the classifier.
    */
  inline def aliases(classifier: PreReleaseClassifier): List[String] = classifier match
    case PreReleaseClassifier.Unclassified     => List("unclassified")
    case PreReleaseClassifier.Milestone        => List("m", "milestone")
    case PreReleaseClassifier.Alpha            => List("alpha", "a")
    case PreReleaseClassifier.Beta             => List("beta", "b")
    case PreReleaseClassifier.ReleaseCandidate => List("rc", "cr")
    case PreReleaseClassifier.Snapshot         => List("snapshot")

  given Ordering[PreReleaseClassifier] = Ordering.by(_.ordinal)
  given CanEqual[PreReleaseClassifier, PreReleaseClassifier] = CanEqual.derived
end PreReleaseClassifier

/** Represents an instance of pre-release version information. */
final case class PreRelease private[version] (classifier: PreReleaseClassifier, number: Option[PreReleaseNumber]):
  if !PreReleaseClassifier.versioned(classifier) && number.nonEmpty then
    throw InvalidSnapshotPreRelease(classifier, number.get) // scalafix:ok
  if PreReleaseClassifier.versioned(classifier) && number.isEmpty then throw InvalidNumberedPreRelease(classifier) // scalafix:ok

  override def toString: String = s"$classifier${number.getOrElse("")}"

object PreRelease:

  /** Creates a snapshot pre-release. */
  def snapshot: PreRelease = PreRelease(PreReleaseClassifier.Snapshot, None)

  /** Creates an unclassified pre-release. */
  def unclassified: PreRelease = PreRelease(PreReleaseClassifier.Unclassified, None)

  /** Creates a milestone pre-release with a specified number. */
  def milestone(number: PreReleaseNumber): PreRelease = PreRelease(PreReleaseClassifier.Milestone, Some(number))

  /** Creates an alpha pre-release with a specified number. */
  def alpha(number: PreReleaseNumber): PreRelease = PreRelease(PreReleaseClassifier.Alpha, Some(number))

  /** Creates a beta pre-release with a specified number. */
  def beta(number: PreReleaseNumber): PreRelease = PreRelease(PreReleaseClassifier.Beta, Some(number))

  /** Creates a release candidate pre-release with a specified number. */
  def releaseCandidate(number: PreReleaseNumber): PreRelease =
    PreRelease(PreReleaseClassifier.ReleaseCandidate, Some(number))

  given Ordering[PreRelease] = Ordering.by((pr: PreRelease) => (pr.classifier, pr.number))(
    Ordering.Tuple2(Ordering[PreReleaseClassifier], Ordering.Option(Ordering[PreReleaseNumber])))

  given CanEqual[PreRelease, PreRelease] = CanEqual.derived
end PreRelease

/** Represents a version with major, minor, patch, and optional pre-release components.
  *
  * A `Version` instance is used to manage semantically versioned version numbers, which follow the pattern:
  * `MAJOR.MINOR.PATCH[-PRERELEASE]`.
  *
  * @param major
  *   the major version. Incremented for incompatible API changes.
  * @param minor
  *   the minor version. Incremented for adding functionality in a backwards-compatible manner.
  * @param patch
  *   the patch number. Incremented for backwards-compatible bug fixes.
  * @param preRelease
  *   the optional pre-release component. Used to denote pre-release versions (e.g., alpha, beta).
  */
final case class Version(major: MajorVersion, minor: MinorVersion, patch: PatchNumber, preRelease: Option[PreRelease]):
  /** Retrieves the components of the version as a tuple.
    *
    * @return
    *   a tuple containing the major, minor, patch, and optional pre-release components.
    */
  def parts: (MajorVersion, MinorVersion, PatchNumber, Option[PreRelease]) = (major, minor, patch, preRelease)

  /** Checks if the version is stable (i.e., does not have a pre-release component). */
  def stable: Boolean = preRelease.isEmpty

  override def toString: String =
    s"$major.$minor.$patch${preRelease.map(preRelease => "-" + preRelease).getOrElse("")}"

/** Companion object for [[Version]].
  *
  * Provides factory methods for creating `Version` instances and related functionality.
  */
object Version:
  /** Creates a `Version` instance without a pre-release component.
    *
    * @param majorVersion
    *   the major version.
    * @param minorVersion
    *   the minor version.
    * @param patchNumber
    *   the patch number.
    * @return
    *   a `Version` instance.
    */
  def apply(majorVersion: MajorVersion, minorVersion: MinorVersion, patchNumber: PatchNumber): Version =
    apply(majorVersion, minorVersion, patchNumber, None)

  /** Creates a `Version` instance from a tuple without a pre-release component.
    *
    * @param parts
    *   a tuple containing the major, minor, and patch components.
    * @return
    *   a `Version` instance.
    */
  def apply(parts: (MajorVersion, MinorVersion, PatchNumber)): Version = apply(parts._1, parts._2, parts._3, None)

  /** Creates a `Version` instance from a tuple with a pre-release component.
    *
    * @param parts
    *   a tuple containing the major, minor, patch, and pre-release components.
    * @return
    *   a `Version` instance.
    */
  def apply(parts: (MajorVersion, MinorVersion, PatchNumber, PreRelease)): Version =
    apply(parts._1, parts._2, parts._3, Some(parts._4))

  /** Creates a `Version` instance with a pre-release component.
    *
    * @param majorVersion
    *   the major version.
    * @param minorVersion
    *   the minor version.
    * @param patchNumber
    *   the patch number.
    * @param preRelease
    *   the pre-release component.
    * @return
    *   a `Version` instance.
    */
  def apply(majorVersion: MajorVersion, minorVersion: MinorVersion, patchNumber: PatchNumber, preRelease: PreRelease): Version =
    apply(majorVersion, minorVersion, patchNumber, Some(preRelease))

  given Ordering[Version] =
    given Ordering[Option[PreRelease]] with
      def compare(l: Option[PreRelease], r: Option[PreRelease]): Int =
        (l, r) match
          case (Some(lpr), Some(rpr)) => Ordering[PreRelease].compare(lpr, rpr)
          case (None, None)           => 0
          case (Some(_), None)        => -1
          case (None, Some(_))        => 1
    Ordering.by((v: Version) => (v.major, v.minor, v.patch, v.preRelease))
  given CanEqual[Version, Version] = CanEqual.derived
end Version
