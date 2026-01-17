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
package version

import boilerplate.OpaqueType

import scala.annotation.publicInBinary

import version.errors.InvalidComponent
import version.errors.InvalidMajorVersion
import version.errors.InvalidMinorVersion
import version.errors.InvalidPatchNumber
import version.errors.InvalidPreReleaseCombination
import version.errors.InvalidPreReleaseNumber
import version.errors.MissingPreReleaseNumber
import version.errors.UnexpectedPreReleaseNumber

/** Capability trait for opaque version components backed by [[Int]].
 *
 * Extends [[boilerplate.OpaqueType]] with version-specific semantics:
 *   - Minimum value validation
 *   - Increment operation
 *   - Standard ordering
 *
 * Instances may be constructed via [[VersionComponent$ VersionComponent]].
 *
 * @tparam T
 *   The opaque type itself.
 */
transparent trait VersionComponent[T] extends OpaqueType[T]:
  /** The underlying value type for this component. */
  final type Type = Int

  /** Error type must be a component validation error. */
  type Error <: InvalidComponent

  /** The minimum valid value for this component. */
  protected def minimumValue: Int

  /** The specific error constructor for this component. */
  protected def error(value: Int): Error

  /** Validates the raw value against the minimum constraint.
   *
   * Required by [[boilerplate.OpaqueType]].
   */
  protected inline def validate(value: Int): Option[Error] =
    if value >= minimumValue then None else Some(error(value))

  /** The minimum valid value for this component, wrapped in the opaque type. */
  inline def minimum: T = wrap(minimumValue)

  extension (t: T)
    /** Alias for [[unwrap]]. Retrieves the underlying integer value of the component. */
    inline def value: Int = unwrap(t)

    /** Returns a new instance incremented by one. */
    inline def increment: T = wrap(unwrap(t) + 1)

  given Ordering[T] = Ordering.by(unwrap)
end VersionComponent

/** Extends [[VersionComponent]] for components that have a defined reset value (typically the minimum value). */
transparent trait ResettableVersionComponent[T] extends VersionComponent[T]:
  /** The reset value for the field (e.g., 0 for Patch, 1 for PreReleaseNumber). */
  inline def reset: T = minimum

/** Represents a major version number. Must be non-negative (>= 0). */
opaque type MajorVersion = Int

/** Provides factory methods, instances, and operations for [[MajorVersion]]. */
object MajorVersion extends ResettableVersionComponent[MajorVersion]:
  type Error = InvalidMajorVersion
  protected inline def minimumValue: Int = 0
  protected inline def error(value: Int): Error = InvalidMajorVersion(value)
  inline def wrap(value: Int): MajorVersion = value
  inline def unwrap(mv: MajorVersion): Int = mv
  extension (v: MajorVersion)
    /** Returns `true` if the major version is greater than 0, indicating a stable release according to SemVer. */
    inline def isStable: Boolean = unwrap(v) > 0

/** Represents a minor version number. Must be non-negative (>= 0). */
opaque type MinorVersion = Int

/** Provides factory methods, instances, and operations for [[MinorVersion]]. */
object MinorVersion extends ResettableVersionComponent[MinorVersion]:
  type Error = InvalidMinorVersion
  protected inline def minimumValue: Int = 0
  protected inline def error(value: Int): Error = InvalidMinorVersion(value)
  inline def wrap(value: Int): MinorVersion = value
  inline def unwrap(mv: MinorVersion): Int = mv

/** Represents a patch number. Must be non-negative (>= 0). */
opaque type PatchNumber = Int

/** Provides factory methods, instances, and operations for [[PatchNumber]]. */
object PatchNumber extends ResettableVersionComponent[PatchNumber]:
  type Error = InvalidPatchNumber
  protected inline def minimumValue: Int = 0
  protected inline def error(value: Int): Error = InvalidPatchNumber(value)
  inline def wrap(value: Int): PatchNumber = value
  inline def unwrap(pn: PatchNumber): Int = pn

/** Represents a pre-release number. Must be positive (>= 1). */
opaque type PreReleaseNumber = Int

/** Provides factory methods, instances, and operations for [[PreReleaseNumber]]. */
object PreReleaseNumber extends ResettableVersionComponent[PreReleaseNumber]:
  type Error = InvalidPreReleaseNumber
  protected inline def minimumValue: Int = 1 // Must be positive
  protected inline def error(value: Int): Error = InvalidPreReleaseNumber(value)
  inline def wrap(value: Int): PreReleaseNumber = value
  inline def unwrap(prn: PreReleaseNumber): Int = prn

/** Represents the supported pre-release classifiers in order of precedence (lowest to highest).
 *
 * This enumeration defines the constrained hierarchy used within this library. Declaration order defines precedence.
 *
 * @see
 *   [[PreReleaseClassifier$ PreReleaseClassifier]] companion for behaviour.
 */
enum PreReleaseClassifier:
  case Dev, Milestone, Alpha, Beta, ReleaseCandidate, Snapshot

/** Provides behaviour, instances, and utilities for [[PreReleaseClassifier]].
 *
 * @see [[PreReleaseClassifier]] enum for case definitions.
 */
object PreReleaseClassifier:

  type Dev = PreReleaseClassifier.Dev.type
  type Milestone = PreReleaseClassifier.Milestone.type
  type Alpha = PreReleaseClassifier.Alpha.type
  type Beta = PreReleaseClassifier.Beta.type
  type ReleaseCandidate = PreReleaseClassifier.ReleaseCandidate.type
  type Snapshot = PreReleaseClassifier.Snapshot.type

  extension (c: PreReleaseClassifier)
    /** Returns `true` if the classifier requires a [[PreReleaseNumber]]. */
    inline def versioned: Boolean = c match
      case Snapshot => false
      case _        => true

    /** Returns the recognised aliases (first is canonical). */
    inline def aliases: List[String] = c match
      case Dev              => List("dev")
      case Milestone        => List("milestone", "m")
      case Alpha            => List("alpha", "a")
      case Beta             => List("beta", "b")
      case ReleaseCandidate => List("rc", "cr")
      case Snapshot         => List("SNAPSHOT")

    /** Returns the canonical string form of the classifier. */
    inline def show: String = c.aliases.head

  // A map for quick lookup of classifiers by their aliases.
  private val aliasMap: Map[String, PreReleaseClassifier] =
    PreReleaseClassifier.values.flatMap(c => c.aliases.map(_.toLowerCase -> c)).toMap

  /** Attempts to find a [[PreReleaseClassifier]] corresponding to the given string alias.
   *
   * @param alias
   *   The string alias (case-insensitive).
   * @return
   *   `Some(PreReleaseClassifier)` if found, `None` otherwise.
   */
  inline def fromAlias(alias: String): Option[PreReleaseClassifier] =
    import boilerplate.nullable.*
    alias.toLowerCase.option.flatMap(aliasMap.get)

  /** Provides an extractor for matching string aliases. */
  inline def unapply(alias: String): Option[PreReleaseClassifier] = fromAlias(alias)

  given Ordering[PreReleaseClassifier] = Ordering.by(_.ordinal)
  given CanEqual[PreReleaseClassifier, PreReleaseClassifier] = CanEqual.derived
end PreReleaseClassifier

/** Represents structured pre-release version information.
 *
 * Instances are constructed via [[PreRelease$ PreRelease]] companion factory methods.
 *
 * @param classifier
 *   The type of pre-release.
 * @param number
 *   The version number associated with the classifier, if applicable.
 */
final case class PreRelease @publicInBinary private (
  classifier: PreReleaseClassifier,
  number: Option[PreReleaseNumber]
)

/** Provides factory methods, instances, and operations for [[PreRelease]].
 *
 * @see
 *   [[PreRelease]] case class for representation details.
 */
object PreRelease:

  // --- Smart Constructors ---

  /** Safe construction with validation.
   *
   * @return
   *   `Right(PreRelease)` if valid, `Left(error)` otherwise.
   */
  def from(
    classifier: PreReleaseClassifier,
    number: Option[PreReleaseNumber]
  ): Either[InvalidPreReleaseCombination, PreRelease] =
    if classifier.versioned && number.isEmpty then Left(MissingPreReleaseNumber(classifier))
    else if !classifier.versioned && number.nonEmpty then Left(UnexpectedPreReleaseNumber(classifier, number.get))
    else Right(PreRelease(classifier, number))

  /** Unsafe construction — throws on validation failure. */
  def fromUnsafe(
    classifier: PreReleaseClassifier,
    number: Option[PreReleaseNumber]
  ): PreRelease =
    from(classifier, number) match
      case Right(pr) => pr
      case Left(err) => throw err // scalafix:ok

  // --- Specific Factories (trusted, no validation needed) ---

  /** A constant representing a Snapshot pre-release. */
  val snapshot: PreRelease = PreRelease(PreReleaseClassifier.Snapshot, None)

  /** Creates a Dev pre-release with the specified number. */
  def dev(number: PreReleaseNumber): PreRelease = PreRelease(PreReleaseClassifier.Dev, Some(number))

  /** Creates a Milestone pre-release with the specified number. */
  def milestone(number: PreReleaseNumber): PreRelease = PreRelease(PreReleaseClassifier.Milestone, Some(number))

  /** Creates an Alpha pre-release with the specified number. */
  def alpha(number: PreReleaseNumber): PreRelease = PreRelease(PreReleaseClassifier.Alpha, Some(number))

  /** Creates a Beta pre-release with the specified number. */
  def beta(number: PreReleaseNumber): PreRelease = PreRelease(PreReleaseClassifier.Beta, Some(number))

  /** Creates a Release Candidate pre-release with the specified number. */
  def releaseCandidate(number: PreReleaseNumber): PreRelease =
    PreRelease(PreReleaseClassifier.ReleaseCandidate, Some(number))

  // --- Extensions ---

  extension (pr: PreRelease)
    /** Returns the SemVer-compliant string form (e.g., "alpha.1", "snapshot"). */
    inline def show: String =
      pr.number.fold(pr.classifier.show)(n => s"${pr.classifier.show}.${n.value}")

    /** Returns a new [[PreRelease]] with the number incremented, if versioned.
     *
     * If the classifier is not versioned (e.g., Snapshot), returns unchanged.
     */
    inline def increment: PreRelease =
      if pr.classifier.versioned then PreRelease(pr.classifier, pr.number.map(_.increment))
      else pr

    /** Returns `true` if the classifier is [[PreReleaseClassifier.Dev]]. */
    inline def isDev: Boolean = pr.classifier.equals(PreReleaseClassifier.Dev)

    /** Returns `true` if the classifier is [[PreReleaseClassifier.Milestone]]. */
    inline def isMilestone: Boolean = pr.classifier.equals(PreReleaseClassifier.Milestone)

    /** Returns `true` if the classifier is [[PreReleaseClassifier.Alpha]]. */
    inline def isAlpha: Boolean = pr.classifier.equals(PreReleaseClassifier.Alpha)

    /** Returns `true` if the classifier is [[PreReleaseClassifier.Beta]]. */
    inline def isBeta: Boolean = pr.classifier.equals(PreReleaseClassifier.Beta)

    /** Returns `true` if the classifier is [[PreReleaseClassifier.ReleaseCandidate]]. */
    inline def isReleaseCandidate: Boolean = pr.classifier.equals(PreReleaseClassifier.ReleaseCandidate)

    /** Returns `true` if the classifier is [[PreReleaseClassifier.Snapshot]]. */
    inline def isSnapshot: Boolean = pr.classifier.equals(PreReleaseClassifier.Snapshot)
  end extension

  /** Ordering based on classifier precedence, then number. */
  given Ordering[PreRelease] = Ordering.by(pr => (pr.classifier, pr.number))

  given CanEqual[PreRelease, PreRelease] = CanEqual.derived

  /** Defines a strategy (type class) for mapping raw Semantic Versioning pre-release identifiers to the structured
   * [[PreRelease]] type.
   *
   * This enables pluggable behaviour, allowing users to provide a `given` instance to customise how different
   * pre-release formats (e.g., "1.0.0-dev.5", "1.0.0-feature-x") are interpreted within the constrained hierarchy
   * defined by [[PreReleaseClassifier]].
   */
  trait Resolver:
    /** Attempts to map a list of SemVer pre-release identifiers (dot-separated components) to a structured
     * [[PreRelease]].
     *
     * The parser handles the splitting of the raw pre-release string (e.g., "alpha.1" -> `List("alpha", "1")`) before
     * invoking this method.
     *
     * @param identifiers
     *   The list of identifiers parsed from the pre-release string.
     * @return
     *   `Some(PreRelease)` if the mapping is successful and recognized, `None` otherwise.
     */
    extension (identifiers: List[String]) def resolve: Option[PreRelease]
  end Resolver

  /** Provides the default implementation and implicit instance for [[Resolver]]. */
  object Resolver:

    /** The default mapping strategy.
     *
     * This implementation strictly recognises the aliases defined in [[PreReleaseClassifier]] and expects the format
     * to conform to the library's constrained structure: either a single identifier `[alias]` (for Snapshot) or
     * exactly two identifiers `[alias].[number]`.
     *
     * Note: The parser may reconcile common formats like "RC1" into `List("RC", "1")` before invoking the mapper.
     */
    given Resolver:
      extension (identifiers: List[String])
        inline def resolve: Option[PreRelease] =
          identifiers match
            case List(PreReleaseClassifier(c)) if !c.versioned =>
              Some(PreRelease(c, None)) //  Single identifier matching a non-versioned classifier
            case List(PreReleaseClassifier(c), n) if c.versioned => // Two identifiers: classifier and number (e.g., "alpha", "1")
              // Parse string to Int, then validate via PreReleaseNumber.from(Int)
              n.toIntOption.flatMap(i => PreReleaseNumber.from(i).toOption).map(num => PreRelease(c, Some(num)))
            case _ => None
  end Resolver
end PreRelease
