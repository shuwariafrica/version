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

import version.errors.*

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
  /** Underlying type is always [[Int]] for version components. */
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
  def minimum: T = wrap(minimumValue)

  /** Defines operations available on the opaque type T. */
  extension (t: T)
    /** Semantic alias for [[unwrap]]. Retrieves the underlying integer value of the component. */
    inline def value: Int = unwrap(t)

    /** Returns a new instance incremented by one. Guaranteed safe as it increases the value. */
    inline def increment: T = wrap(unwrap(t) + 1)

  given Ordering[T] = Ordering.by(unwrap)
end VersionComponent

/** Extends [[VersionComponent]] for components that have a defined reset value (typically the minimum value).
  *
  * Instances may be constructed via [[ResetableVersionComponent$ ResetableVersionComponent]].
  *
  * @tparam T
  *   The opaque type itself.
  */
transparent trait ResetableVersionComponent[T] extends VersionComponent[T]:
  /** The reset value for the field (e.g., 0 for Patch, 1 for PreReleaseNumber). */
  inline def reset: T = minimum

/** Represents a major version number. Must be non-negative (>= 0). */
opaque type MajorVersion = Int

/** Provides factory methods, instances, and operations for [[MajorVersion]]. */
object MajorVersion extends ResetableVersionComponent[MajorVersion]:
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
object MinorVersion extends ResetableVersionComponent[MinorVersion]:
  type Error = InvalidMinorVersion

  protected inline def minimumValue: Int = 0
  protected inline def error(value: Int): Error = InvalidMinorVersion(value)

  inline def wrap(value: Int): MinorVersion = value
  inline def unwrap(mv: MinorVersion): Int = mv

/** Represents a patch number. Must be non-negative (>= 0). */
opaque type PatchNumber = Int

/** Provides factory methods, instances, and operations for [[PatchNumber]]. */
object PatchNumber extends ResetableVersionComponent[PatchNumber]:
  type Error = InvalidPatchNumber

  protected inline def minimumValue: Int = 0
  protected inline def error(value: Int): Error = InvalidPatchNumber(value)

  inline def wrap(value: Int): PatchNumber = value
  inline def unwrap(pn: PatchNumber): Int = pn

/** Represents a pre-release number. Must be positive (>= 1). */
opaque type PreReleaseNumber = Int

/** Provides factory methods, instances, and operations for [[PreReleaseNumber]]. */
object PreReleaseNumber extends ResetableVersionComponent[PreReleaseNumber]:
  type Error = InvalidPreReleaseNumber

  protected inline def minimumValue: Int = 1 // Must be positive
  protected inline def error(value: Int): Error = InvalidPreReleaseNumber(value)

  inline def wrap(value: Int): PreReleaseNumber = value
  inline def unwrap(prn: PreReleaseNumber): Int = prn

import scala.annotation.{publicInBinary, targetName}

/** Represents the supported pre-release classifiers in order of precedence (lowest to highest).
  *
  * This enumeration defines the constrained hierarchy used within this library. Declaration order defines precedence.
  *
  * @see
  *   [[PreReleaseClassifier$ PreReleaseClassifier]] companion for behaviour.
  */
enum PreReleaseClassifier:
  case Dev, Milestone, Alpha, Beta, ReleaseCandidate, Snapshot

  /** Returns the canonical string form of the classifier.
    *
    * Thin delegate to [[PreReleaseClassifier.show PreReleaseClassifier.show]].
    */
  override def toString: String = this.show

/** Provides behaviour, instances, and utilities for [[PreReleaseClassifier]].
  *
  * @see
  *   [[PreReleaseClassifier]] enum for case definitions.
  */
object PreReleaseClassifier:

  extension (c: PreReleaseClassifier)
    /** Returns `true` if the classifier requires a [[PreReleaseNumber]]. */
    @targetName("versionedExtension")
    inline def versioned: Boolean = c match
      case Snapshot => false
      case _        => true

    /** Returns the recognised aliases (first is canonical). */
    @targetName("aliasesExtension")
    inline def aliases: List[String] = c match
      case Dev              => List("dev")
      case Milestone        => List("milestone", "m")
      case Alpha            => List("alpha", "a")
      case Beta             => List("beta", "b")
      case ReleaseCandidate => List("rc", "cr")
      case Snapshot         => List("SNAPSHOT", "snapshot")

    /** Returns the canonical string form of the classifier. */
    @targetName("showExtension")
    inline def show: String = c.aliases.head

  /** Returns `true` if the classifier requires a [[PreReleaseNumber]]. */
  inline def versioned(c: PreReleaseClassifier): Boolean = c.versioned

  /** Returns the recognised aliases (first is canonical). */
  inline def aliases(c: PreReleaseClassifier): List[String] = c.aliases

  /** Returns the canonical string form of the classifier (first alias). */
  inline def show(c: PreReleaseClassifier): String = c.show

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
  def fromAlias(alias: String): Option[PreReleaseClassifier] =
    import boilerplate.nullable.*
    alias.toLowerCase.option.flatMap(aliasMap.get)

  /** Provides an extractor for matching string aliases. */
  def unapply(alias: String): Option[PreReleaseClassifier] = fromAlias(alias)

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
):

  /** Formats the pre-release information as a string suitable for SemVer display.
    *
    * Thin delegate to [[PreRelease.show PreRelease.show]].
    */
  override def toString: String = this.show

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
    @targetName("showExtension")
    inline def show: String =
      pr.number.fold(pr.classifier.show)(n => s"${pr.classifier.show}.${n.value}")

    /** Returns a new [[PreRelease]] with the number incremented, if versioned.
      *
      * If the classifier is not versioned (e.g., Snapshot), returns unchanged.
      */
    @targetName("incrementExtension")
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

  // --- Behaviour Methods (delegating to extensions) ---

  /** Returns the SemVer-compliant string form (e.g., "alpha.1", "snapshot"). */
  inline def show(pr: PreRelease): String = pr.show

  /** Returns a new [[PreRelease]] with the number incremented, if versioned. */
  inline def increment(pr: PreRelease): PreRelease = pr.increment

  // --- Instances ---

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
    def map(identifiers: List[String]): Option[PreRelease]
  end Resolver

  /** Provides the default implementation and implicit instance for [[Resolver]]. */
  object Resolver:

    /** The default mapping strategy.
      *
      * This implementation strictly recognizes the aliases defined in [[PreReleaseClassifier]] and expects the format
      * to conform to the library's constrained structure: either a single identifier `[alias]` (for Snapshot) or
      * exactly two identifiers `[alias].[number]`.
      *
      * Note: The parser may reconcile common formats like "RC1" into `List("RC", "1")` before invoking the mapper.
      */
    given Resolver:
      override def map(identifiers: List[String]): Option[PreRelease] =
        identifiers match
          // Case 1: Single identifier matching a non-versioned classifier (e.g., "snapshot")
          case List(PreReleaseClassifier(c)) if !c.versioned =>
            Some(PreRelease(c, None))

          // Case 2: Two identifiers: classifier and number (e.g., "alpha", "1")
          case List(PreReleaseClassifier(c), n) if c.versioned =>
            // Parse string to Int, then validate via PreReleaseNumber.from(Int)
            n.toIntOption.flatMap(i => PreReleaseNumber.from(i).toOption).map(num => PreRelease(c, Some(num)))

          // Case 3: Any other combination is not supported by this default constrained mapping.
          case _ => None

  end Resolver

end PreRelease

/** Represents build metadata as defined by the Semantic Versioning 2.0.0 specification.
  *
  * Identifiers must comprise only ASCII alphanumerics and hyphens `[0-9A-Za-z-]` and must not be empty. Build metadata
  * does not affect version precedence.
  *
  * Instances may be constructed via [[BuildMetadata$ BuildMetadata]].
  */
opaque type BuildMetadata = List[String]

/** Provides factory methods and operations for [[BuildMetadata]].
  *
  * Extends [[boilerplate.OpaqueType]] with build-metadata-specific validation and semantics.
  *
  * @see
  *   [[BuildMetadata]] opaque type for representation details.
  */
object BuildMetadata extends OpaqueType[BuildMetadata]:
  type Type = List[String]
  type Error = InvalidBuildMetadata

  inline def wrap(ids: List[String]): BuildMetadata = ids
  inline def unwrap(bm: BuildMetadata): List[String] = bm

  protected inline def validate(ids: List[String]): Option[Error] =
    if ids.nonEmpty && ids.forall(isValidIdentifier) then None
    else Some(InvalidBuildMetadata(ids))

  /** Checks if an identifier is valid according to SemVer 2.0.0 rules. */
  private def isValidIdentifier(id: String): Boolean =
    // Checks for non-empty and allowed characters [0-9A-Za-z-]
    id.nonEmpty && id.forall { c =>
      // Avoid universal equality; check hyphen membership via indexOf on a constant string
      c.isLetterOrDigit || ("-".indexOf(c) >= 0)
    }

  /** Creates [[BuildMetadata]] from a list of identifiers.
    *
    * @throws InvalidBuildMetadata
    *   if any identifier is empty or contains invalid characters.
    */
  def apply(identifiers: List[String]): BuildMetadata = fromUnsafe(identifiers)

  /** Semantic alias for [[unwrap]]. Returns the list of metadata identifiers. */
  extension (metadata: BuildMetadata)
    inline def identifiers: List[String] = unwrap(metadata)

    /** Returns the SemVer-compliant metadata string (including leading '+'). */
    @targetName("showExtension")
    inline def show: String = s"+${unwrap(metadata).mkString(".")}"

  /** Returns the SemVer-compliant metadata string (including leading '+'). */
  inline def show(bm: BuildMetadata): String = bm.show

  // Build metadata does not affect version precedence, so no Ordering is provided.
end BuildMetadata

/** Represents a version conforming to the Semantic Versioning 2.0.0 specification.
  *
  * Format: `MAJOR.MINOR.PATCH[-PRERELEASE][+BUILDMETADATA]`.
  *
  * Instances may be constructed via [[Version$ Version]].
  */
final case class Version(
  major: MajorVersion,
  minor: MinorVersion,
  patch: PatchNumber,
  preRelease: Option[PreRelease],
  buildMetadata: Option[BuildMetadata]
) extends Ordered[Version]:

  /** Formats the version as a standard SemVer 2.0.0 string. */
  override def toString: String =
    val b = new StringBuilder(s"${major.value}.${minor.value}.${patch.value}")
    preRelease.foreach(pr => b.append('-').append(pr.toString))
    buildMetadata.foreach(meta => b.append(meta.show))
    b.toString()

  /** Compares this version with another version according to SemVer precedence rules. */
  override def compare(that: Version): Int = summon[Ordering[Version]].compare(this, that)

end Version

/** Provides factory methods, utility functions, and type class instances for [[Version]]. */
object Version:

  /** Represents the core components of a version that can be bumped. */
  enum Component:
    case Major, Minor, Patch

  object Component:
    given CanEqual[Component, Component] = CanEqual.derived

  /** Type class describing how to bump a version given a specific opaque component type `F`.
    *
    * This enables the generic `version.next[F]` operation.
    *
    * Implementations must follow SemVer rules:
    *   - Increment the targeted component
    *   - Reset all lower-precedence components to their reset/minimum values
    *   - Clear pre-release and build metadata (use the 3-arg Version.apply)
    */
  trait Increment[F]:
    def apply(v: Version): Version

  /** Provides `given` instances for the [[Increment]] type class. */
  object Increment:
    given Increment[MajorVersion]:
      def apply(v: Version): Version =
        Version(v.major.increment, MinorVersion.reset, PatchNumber.reset)

    given Increment[MinorVersion]:
      def apply(v: Version): Version =
        Version(v.major, v.minor.increment, PatchNumber.reset)

    given Increment[PatchNumber]:
      def apply(v: Version): Version =
        Version(v.major, v.minor, v.patch.increment)

  /** Type class capturing a concrete pre-release classifier as a type `C`.
    *
    * This enables the generic `version.next[C]` and `version.as[C]` operations by associating the singleton type of the
    * enum case (e.g., `PreReleaseClassifier.Alpha.type`) with the classifier value.
    */
  trait PreReleaseClass[C]:
    inline def classifier: PreReleaseClassifier

  /** Provides `given` instances for the [[PreReleaseClass]] type class. */
  object PreReleaseClass:
    given PreReleaseClass[PreReleaseClassifier.Dev.type]:
      inline def classifier: PreReleaseClassifier = PreReleaseClassifier.Dev
    given PreReleaseClass[PreReleaseClassifier.Milestone.type]:
      inline def classifier: PreReleaseClassifier = PreReleaseClassifier.Milestone
    given PreReleaseClass[PreReleaseClassifier.Alpha.type]:
      inline def classifier: PreReleaseClassifier = PreReleaseClassifier.Alpha
    given PreReleaseClass[PreReleaseClassifier.Beta.type]:
      inline def classifier: PreReleaseClassifier = PreReleaseClassifier.Beta
    given PreReleaseClass[PreReleaseClassifier.ReleaseCandidate.type]:
      inline def classifier: PreReleaseClassifier = PreReleaseClassifier.ReleaseCandidate
    given PreReleaseClass[PreReleaseClassifier.Snapshot.type]:
      inline def classifier: PreReleaseClassifier = PreReleaseClassifier.Snapshot

  /** Creates a `Version` instance from core components (final release). */
  inline def apply(major: MajorVersion, minor: MinorVersion, patch: PatchNumber): Version =
    Version(major, minor, patch, None, None)

  /** Creates a `Version` instance with a pre-release component. */
  inline def apply(
    major: MajorVersion,
    minor: MinorVersion,
    patch: PatchNumber,
    preRelease: Option[PreRelease]
  ): Version =
    Version(major, minor, patch, preRelease, None)

  /** Creates a `Version` instance with a pre-release (no metadata). */
  inline def apply(
    major: MajorVersion,
    minor: MinorVersion,
    patch: PatchNumber,
    preRelease: PreRelease
  ): Version =
    Version(major, minor, patch, Some(preRelease), None)

  /** Creates a `Version` instance with build metadata (no pre-release). */
  inline def apply(
    major: MajorVersion,
    minor: MinorVersion,
    patch: PatchNumber,
    metadata: BuildMetadata
  ): Version =
    Version(major, minor, patch, None, Some(metadata))

  /** Creates a `Version` instance with both pre-release and build metadata. */
  inline def apply(
    major: MajorVersion,
    minor: MinorVersion,
    patch: PatchNumber,
    preRelease: PreRelease,
    metadata: BuildMetadata
  ): Version =
    Version(major, minor, patch, Some(preRelease), Some(metadata))

  // --- Parsing ---

  /** Parses a SemVer string.
    *
    * @param s
    *   The version string to parse.
    * @return
    *   `Right(Version)` on success, `Left(ParseError)` on failure.
    */
  def parse(s: String)(using PreRelease.Resolver): Either[errors.ParseError, Version] =
    parser.VersionParser.parse(s)

  /** Unsafe parse — throws on invalid input.
    *
    * @param s
    *   The version string to parse.
    * @throws errors.ParseError
    *   if the string is not a valid SemVer version.
    */
  def parseUnsafe(s: String)(using PreRelease.Resolver): Version =
    parse(s) match
      case Right(v) => v
      case Left(e)  => throw e // scalafix:ok DisableSyntax.throw

  /** Default [[Show]] instance for automatic resolution. Delegates to [[Show.Standard]]. */
  inline given Show = Show.Standard

  /** Type class for rendering [[Version]] instances to string representations.
    *
    * Enables pluggable rendering strategies. Users may provide custom implementations as `given` instances to support
    * organisation-specific formats.
    *
    * Two standard instances are provided:
    *   - [[Show.Standard]] — excludes build metadata
    *   - [[Show.Extended]] — includes build metadata
    */
  trait Show:
    extension (v: Version) def show: String

  /** Provides [[Show]] instances and a summoner.
    *
    * The default `given` instance in [[Version$ Version]] delegates to [[Standard]] (excludes build metadata).
    * [[Extended]] includes build metadata — promote it to a local `given` when needed:
    * {{{
    * given Version.Show = Version.Show.Extended
    * v.show // now includes build metadata
    * }}}
    */
  object Show:

    /** Summons the contextual show instance. */
    inline def apply(using s: Show): Show = s

    /** Standard SemVer rendering WITHOUT build metadata. */
    object Standard extends Show:
      extension (v: Version)
        def show: String =
          val core = s"${v.major.value}.${v.minor.value}.${v.patch.value}"
          v.preRelease.fold(core)(pr => s"$core-${pr.show}")

    /** Extended SemVer rendering WITH build metadata.
      *
      * SHA identifiers (prefixed with `sha`) are truncated to 7 characters following the standard git short-SHA
      * convention.
      *
      * Not a `given` by default — promote to local `given` when needed:
      * {{{
      * given Version.Show = Version.Show.Extended
      * }}}
      */
    object Extended extends Show:
      extension (v: Version)
        def show: String =
          val core = s"${v.major.value}.${v.minor.value}.${v.patch.value}"
          val pre = v.preRelease.fold("")(pr => s"-${pr.show}")
          val meta = v.buildMetadata.fold("") { bm =>
            val truncated = bm.identifiers.map { id =>
              // Truncate SHA identifiers to 7 chars (git short-SHA convention)
              if id.startsWith("sha") && id.length > 10 then s"sha${id.slice(3, 10)}"
              else id
            }
            s"+${truncated.mkString(".")}"
          }
          s"$core$pre$meta"
  end Show

  /** Ordering according to Semantic Versioning 2.0.0 precedence rules. */
  given Ordering[Version]:
    def compare(x: Version, y: Version): Int =
      // 1. Compare Major, Minor, Patch numerically (SemVer Spec 11.1).
      val compareNumbers =
        // We rely on the derived Ordering for the opaque types themselves.
        summon[Ordering[(MajorVersion, MinorVersion, PatchNumber)]].compare(
          (x.major, x.minor, x.patch),
          (y.major, y.minor, y.patch)
        )

      compareNumbers match
        case 0 =>
          // 2. Compare Pre-Release (SemVer Spec 11.3 and 11.4).
          (x.preRelease, y.preRelease) match
            case (None, None) => 0
            // Rule: A normal version has higher precedence than a pre-release version (1.0.0 > 1.0.0-alpha).
            case (Some(_), None) => -1
            case (None, Some(_)) => 1
            // Compare the pre-release components based on their own ordering rules.
            case (Some(px), Some(py)) => summon[Ordering[PreRelease]].compare(px, py)
        case n => n

  // 3. Build metadata MUST be ignored (SemVer Spec 10).

  given CanEqual[Version, Version] = CanEqual.derived

  // --- Version Extensions ---

  import version.errors.*

  extension (v: Version)
    // --- Status Checks ---

    /** Returns `true` if the major version is non-zero, indicating stability according to SemVer. */
    inline def isStable: Boolean = v.major.isStable

    /** Returns `true` if the version has pre-release information. */
    inline def isPreRelease: Boolean = v.preRelease.nonEmpty

    /** Returns `true` if the version is a final release (no pre-release information). */
    inline def isFinal: Boolean = v.preRelease.isEmpty

    /** Returns `true` if the major version is non-zero and there is no pre-release information. */
    inline def isStableRelease: Boolean = v.major.isStable && v.preRelease.isEmpty

    /** Returns `true` if the pre-release classifier is [[PreReleaseClassifier.Snapshot]]. */
    inline def isSnapshot: Boolean = v.preRelease.exists(_.isSnapshot)

    /** Returns `true` if a pre-release exists and is not Snapshot (i.e., a release candidate). */
    inline def isCandidate: Boolean = v.preRelease.exists(pr => !pr.isSnapshot)

    /** Returns the core version (major.minor.patch) without pre-release or build metadata. */
    inline def core: Version = Version(v.major, v.minor, v.patch)

    // --- Version Bumping Operations ---

    /** Returns the next logical [[Version]] with the specified component incremented.
      *
      * Clears pre-release and build metadata.
      *
      * Type-safe generic API: choose which component to bump via the type parameter. Examples:
      *   - `v.next[MajorVersion]`
      *   - `v.next[MinorVersion]`
      *   - `v.next[PatchNumber]`
      */
    inline def next[F](using inc: Increment[F]): Version = inc(v)

    /** Returns the next major version, resetting minor and patch to zero. */
    inline def nextMajor: Version = Version(v.major.increment, MinorVersion.reset, PatchNumber.reset)

    /** Returns the next minor version, resetting patch to zero. */
    inline def nextMinor: Version = Version(v.major, v.minor.increment, PatchNumber.reset)

    /** Returns the next patch version. */
    inline def nextPatch: Version = Version(v.major, v.minor, v.patch.increment)

    // --- Pre-Release and Metadata Operations ---

    /** Returns a new [[Version]] with the pre-release information removed (promoting to a final release).
      *
      * Build metadata is preserved.
      */
    def release: Version = v.copy(preRelease = None)

    /** Returns a new [[Version]] marked as a Snapshot of the current base version. */
    def toSnapshot: Version = v.copy(preRelease = Some(PreRelease.snapshot))

    // --- Typed Pre-Release Operations ---

    /** Advance within pre-release classifiers.
      *
      *   - Requires current version to be a pre-release
      *   - Target classifier must have equal or higher precedence; otherwise returns InvalidPreReleaseTransition
      *   - If same classifier: increment number
      *   - If higher classifier: start at 1 (for versioned classifiers) or set to Snapshot (no number)
      */
    inline def advance[C](using cls: PreReleaseClass[C]): Either[VersionError, Version] =
      val target = cls.classifier
      v.preRelease match
        case None          => Left(NotAPreReleaseVersion())
        case Some(current) =>
          if current.classifier.equals(target) then
            // Same classifier: increment if versioned; for Snapshot increment is a no-op
            Right(v.copy(preRelease = Some(current.increment)))
          else if current.classifier.ordinal < target.ordinal then
            // Higher precedence: start at 1 for versioned classifiers, or set Snapshot
            val nextPr =
              if target.versioned then PreRelease.fromUnsafe(target, Some(PreReleaseNumber.reset))
              else PreRelease.snapshot
            Right(v.copy(preRelease = Some(nextPr)))
          else
            // Lower precedence transitions are disallowed
            Left(InvalidPreReleaseTransition(current.classifier, target))

    /** Force-set the pre-release to the given typed classifier with a specific number.
      *
      *   - Works on final or pre-release versions
      *   - Validates that the classifier is versioned and the number >= 1
      */
    inline def as[C](n: Int)(using cls: PreReleaseClass[C]): Either[VersionError, Version] =
      val target = cls.classifier
      if !target.versioned then Left(ClassifierNotVersioned(target))
      else
        PreReleaseNumber.from(n) match
          case Left(err)  => Left(err)
          case Right(num) => Right(v.copy(preRelease = Some(PreRelease.fromUnsafe(target, Some(num)))))

    /** Force-set the pre-release to the given typed classifier using minimum/reset number or snapshot.
      *
      *   - If classifier is versioned -> number = 1
      *   - If classifier is Snapshot -> no number
      */
    inline def as[C](using cls: PreReleaseClass[C]): Version =
      val target = cls.classifier
      val pr = if target.versioned then PreRelease.fromUnsafe(target, Some(PreReleaseNumber.reset)) else PreRelease.snapshot
      v.copy(preRelease = Some(pr))

    /** Returns a new [[Version]] with the specified pre-release explicitly set. Overwrites existing pre-release. */
    def set(preRelease: PreRelease): Version = v.copy(preRelease = Some(preRelease))

    /** Returns a new [[Version]] with the specified build metadata attached. Overwrites existing metadata. */
    def set(metadata: BuildMetadata): Version = v.copy(buildMetadata = Some(metadata))

    /** Returns a new [[Version]] with any build metadata removed. */
    def dropMetadata: Version = v.copy(buildMetadata = None)
  end extension

end Version
