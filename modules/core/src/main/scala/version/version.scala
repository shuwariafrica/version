package version

import scala.util.Try
import scala.util.boundary
import scala.util.boundary.break

import version.errors.*

/** Represents the capabilities and constraints of an opaque version component backed by an `Int`.
  *
  * @tparam T
  *   The opaque type itself.
  * @tparam E
  *   The specific error type produced if validation fails.
  */
transparent trait OpaqueVersionComponent[T, E <: InvalidComponent]:
  /** The minimum valid value for this component. */
  protected def minimumValue: Int

  /** The specific error constructor for this component. */
  protected def error(value: Int): E

  /** The factory function to wrap the Int into the opaque type T. */
  protected def wrap(value: Int): T

  /** The field name for error reporting. */
  protected def fieldName: String

  /** Creates a version component from an integer value.
    *
    * This method is intended for use when the input is statically known to be valid, or where immediate failure is
    * acceptable (e.g., during parsing when input has already been validated by regex).
    * @return
    *   The validated component instance.
    */
  inline def unsafe(value: Int): T =
    if value < minimumValue then throw error(value) // scalafix:ok
    else wrap(value)

  /** The minimum valid value for this component, wrapped in the opaque type. */
  inline def minimum: T = unsafe(minimumValue)

  /** Safely attempts to create a version component from an integer value (Smart Constructor).
    *
    * @param value
    *   The integer value.
    * @return
    *   `Right(T)` if the value is valid, `Left(E)` otherwise.
    */
  inline def from(value: Int): Either[E, T] =
    Either.cond(value >= minimumValue, wrap(value), error(value))

  /** Safely attempts to create a version component from a string value.
    *
    * @param value
    *   The string representation of the integer.
    * @return
    *   `Right(T)` if the string is a valid integer and meets the constraints, `Left(InvalidNumericField)` otherwise.
    */
  inline def from(value: String): Either[InvalidNumericField, T] =
    // Use boundary/break for efficient exit upon failure
    boundary:
      val intValue = Try(value.toInt).getOrElse(break(Left(InvalidNumericField(fieldName, value))))
      if intValue >= minimumValue then Right(wrap(intValue))
      else Left(InvalidNumericField(fieldName, value))

  /** Defines operations available on the opaque type T. */
  extension (t: T)
    /** Retrieves the underlying integer value of the component. Note: asInstanceOf is required here because the trait
      * does not share the scope of the opaque type definition.
      */
    inline def value: Int = t match
      case i: Int => i
      case _      =>
        // This branch is unreachable for opaque types backed by Int; localized suppression.
        throw new IllegalStateException("Invalid opaque value") // scalafix:ok

    /** Returns a new instance incremented by one. Guaranteed safe as it increases the value. */
    inline def increment: T = wrap(t.value + 1)

  given CanEqual[T, T] = CanEqual.derived
  given Ordering[T] = Ordering.by(_.value)
end OpaqueVersionComponent

/** Extends [[OpaqueVersionComponent]] for components that have a defined reset value (typically the minimum value). */
transparent trait ResetableOpaqueVersionComponent[T, E <: InvalidComponent] extends OpaqueVersionComponent[T, E]:
  /** The reset value for the field (e.g., 0 for Patch, 1 for PreReleaseNumber). */
  inline def reset: T = minimum

/** Represents a major version number. Must be non-negative (>= 0). */
opaque type MajorVersion = Int

/** Provides factory methods, instances, and operations for [[MajorVersion]]. */
object MajorVersion extends ResetableOpaqueVersionComponent[MajorVersion, InvalidMajorVersion]:
  inline def minimumValue: Int = 0
  inline def error(value: Int) = InvalidMajorVersion(value)
  inline def wrap(value: Int): MajorVersion = value
  inline def fieldName: String = "Major"

  extension (v: MajorVersion)
    /** Returns `true` if the major version is greater than 0, indicating a stable release according to SemVer. */
    inline def isStable: Boolean = v.value > 0

/** Represents a minor version number. Must be non-negative (>= 0). */
opaque type MinorVersion = Int

/** Provides factory methods, instances, and operations for [[MinorVersion]]. */
object MinorVersion extends ResetableOpaqueVersionComponent[MinorVersion, InvalidMinorVersion]:
  inline def minimumValue: Int = 0
  inline def error(value: Int) = InvalidMinorVersion(value)
  inline def wrap(value: Int): MinorVersion = value
  inline def fieldName: String = "Minor"

/** Represents a patch number. Must be non-negative (>= 0). */
opaque type PatchNumber = Int

/** Provides factory methods, instances, and operations for [[PatchNumber]]. */
object PatchNumber extends ResetableOpaqueVersionComponent[PatchNumber, InvalidPatchNumber]:
  inline def minimumValue: Int = 0
  inline def error(value: Int) = InvalidPatchNumber(value)
  inline def wrap(value: Int): PatchNumber = value
  inline def fieldName: String = "Patch"

/** Represents a pre-release number. Must be positive (>= 1). */
opaque type PreReleaseNumber = Int

/** Provides factory methods, instances, and operations for [[PreReleaseNumber]]. */
object PreReleaseNumber extends ResetableOpaqueVersionComponent[PreReleaseNumber, InvalidPreReleaseNumber]:
  inline def minimumValue: Int = 1 // Must be positive
  inline def error(value: Int) = InvalidPreReleaseNumber(value)
  inline def wrap(value: Int): PreReleaseNumber = value
  inline def fieldName: String = "PreReleaseNumber"

/** Represents the supported pre-release classifiers in order of precedence (lowest to highest).
  *
  * This enumeration defines the constrained hierarchy used within this library. The order matches the original
  * requirement.
  */
enum PreReleaseClassifier:
  case Milestone, Alpha, Beta, ReleaseCandidate, Snapshot

  /** Returns the primary alias for the classifier. */
  override def toString: String = this.aliases.head

  /** Checks if the classifier requires a [[PreReleaseNumber]]. */
  inline def versioned: Boolean = this match
    case Snapshot => false
    case _        => true

  /** Retrieves the list of recognized aliases for the classifier (all lowercase). The first element is the canonical
    * representation.
    */
  final def aliases: List[String] = this match
    case Milestone        => List("milestone", "m")
    case Alpha            => List("alpha", "a")
    case Beta             => List("beta", "b")
    case ReleaseCandidate => List("rc", "cr")
    case Snapshot         => List("snapshot")
end PreReleaseClassifier

/** Provides utility methods and instances for [[PreReleaseClassifier]]. */
object PreReleaseClassifier:
  // A map for quick lookup of classifiers by their aliases.
  private val aliasMap: Map[String, PreReleaseClassifier] =
    PreReleaseClassifier.values.flatMap(c => c.aliases.map(_ -> c)).toMap

  /** Attempts to find a [[PreReleaseClassifier]] corresponding to the given string alias.
    *
    * @param alias
    *   The string alias (case-insensitive).
    * @return
    *   `Some(PreReleaseClassifier)` if found, `None` otherwise.
    */
  def fromAlias(alias: String): Option[PreReleaseClassifier] =
    // Use .nn (non-null) for safety, although String inputs are typically safe in this context.
    aliasMap.get(alias.toLowerCase.nn)

  /** Provides an extractor for matching string aliases. */
  def unapply(alias: String): Option[PreReleaseClassifier] = fromAlias(alias)

  given Ordering[PreReleaseClassifier] = Ordering.by(_.ordinal)
  given CanEqual[PreReleaseClassifier, PreReleaseClassifier] = CanEqual.derived
end PreReleaseClassifier

/** Represents structured pre-release version information.
  *
  * @param classifier
  *   The type of pre-release.
  * @param number
  *   The version number associated with the classifier, if applicable.
  */
final case class PreRelease private[version] (classifier: PreReleaseClassifier, number: Option[PreReleaseNumber]):
  // Import value extension method for concise access within toString
  import PreReleaseNumber.value

  // Validate the combination of classifier and number upon instantiation.
  if classifier.versioned && number.isEmpty then throw MissingPreReleaseNumber(classifier) // scalafix:ok
  if !classifier.versioned && number.nonEmpty then throw UnexpectedPreReleaseNumber(classifier, number.get) // scalafix:ok

  /** Formats the pre-release information as a string suitable for SemVer display (e.g., "alpha.1", "snapshot").
    *
    * We use a dot separator for versioned pre-releases, aligning with SemVer recommendations.
    */
  override def toString: String =
    number.fold(classifier.toString)(n => s"$classifier.${n.value}")

  /** Returns a new [[PreRelease]] with the number incremented, if applicable.
    *
    * If the current pre-release is not versioned (e.g., Snapshot), it returns itself.
    */
  def increment: PreRelease =
    if classifier.versioned
    then copy(number = number.map(_.increment))
    else this

end PreRelease

/** Provides factory methods and instances for [[PreRelease]]. */
object PreRelease:

  // --- Specific Factories ---

  /** A constant representing a Snapshot pre-release. */
  val snapshot: PreRelease = new PreRelease(PreReleaseClassifier.Snapshot, None)

  /** Creates a Milestone pre-release with the specified number. */
  def milestone(number: PreReleaseNumber): PreRelease = new PreRelease(PreReleaseClassifier.Milestone, Some(number))

  /** Creates an Alpha pre-release with the specified number. */
  def alpha(number: PreReleaseNumber): PreRelease = new PreRelease(PreReleaseClassifier.Alpha, Some(number))

  /** Creates a Beta pre-release with the specified number. */
  def beta(number: PreReleaseNumber): PreRelease = new PreRelease(PreReleaseClassifier.Beta, Some(number))

  /** Creates a Release Candidate pre-release with the specified number. */
  def releaseCandidate(number: PreReleaseNumber): PreRelease =
    new PreRelease(PreReleaseClassifier.ReleaseCandidate, Some(number))

  /** Defines the ordering for [[PreRelease]] instances.
    *
    * Ordering is based first on the classifier's precedence, and then on the pre-release number.
    */
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
    given default: Resolver with
      override def map(identifiers: List[String]): Option[PreRelease] =
        identifiers match
          // Case 1: Single identifier matching a non-versioned classifier (e.g., "snapshot")
          case List(PreReleaseClassifier(c)) if !c.versioned =>
            Some(PreRelease(c, None))

          // Case 2: Two identifiers: classifier and number (e.g., "alpha", "1")
          case List(PreReleaseClassifier(c), n) if c.versioned =>
            // PreReleaseNumber.from handles validation (must be positive integer, within Int range)
            PreReleaseNumber.from(n).toOption.map(num => PreRelease(c, Some(num)))

          // Case 3: Any other combination is not supported by this default constrained mapping.
          case _ => None
    end default

  end Resolver

end PreRelease

/** Represents build metadata as defined by the Semantic Versioning 2.0.0 specification.
  *
  * Identifiers must comprise only ASCII alphanumerics and hyphens `[0-9A-Za-z-]` and must not be empty. Build metadata
  * does not affect version precedence.
  */
opaque type BuildMetadata = List[String]

/** Provides factory methods and operations for [[BuildMetadata]]. */
object BuildMetadata:
  /** Creates [[BuildMetadata]] from a list of identifiers.
    *
    * @throws InvalidBuildMetadata
    *   if any identifier is empty or contains invalid characters.
    */
  def apply(identifiers: List[String]): BuildMetadata =
    if identifiers.nonEmpty && identifiers.forall(isValidIdentifier) then identifiers
    else throw InvalidBuildMetadata(identifiers) // scalafix:ok

  /** Safely creates [[BuildMetadata]] from a list of identifiers. */
  def from(identifiers: List[String]): Either[InvalidBuildMetadata, BuildMetadata] =
    Either.cond(
      identifiers.nonEmpty && identifiers.forall(isValidIdentifier),
      identifiers,
      InvalidBuildMetadata(identifiers)
    )

  /** Checks if an identifier is valid according to SemVer 2.0.0 rules. */
  private def isValidIdentifier(id: String): Boolean =
    // Checks for non-empty and allowed characters [0-9A-Za-z-]
    id.nonEmpty && id.forall { c =>
      // Avoid universal equality; check hyphen membership via indexOf on a constant string
      c.isLetterOrDigit || ("-".indexOf(c) >= 0)
    }

  /** Unwraps the [[BuildMetadata]] into a list of strings. */
  extension (metadata: BuildMetadata)
    inline def identifiers: List[String] = metadata

    /** Renders the metadata as a SemVer compliant string (including the leading '+'). */
    def render: String = s"+${metadata.mkString(".")}"

  // Build metadata does not affect version precedence, so no Ordering is provided.
  given CanEqual[BuildMetadata, BuildMetadata] = CanEqual.derived
end BuildMetadata

/** Represents a version conforming to the Semantic Versioning 2.0.0 specification.
  *
  * Format: `MAJOR.MINOR.PATCH[-PRERELEASE][+BUILDMETADATA]`.
  *
  * @param major
  *   The major version component.
  * @param minor
  *   The minor version component.
  * @param patch
  *   The patch number component.
  * @param preRelease
  *   The optional pre-release component.
  * @param buildMetadata
  *   The optional build metadata. Ignored for precedence calculations.
  */
final case class Version(
  major: MajorVersion,
  minor: MinorVersion,
  patch: PatchNumber,
  preRelease: Option[PreRelease] = None,
  buildMetadata: Option[BuildMetadata] = None
) extends Ordered[Version]:

  /** Formats the version as a standard SemVer 2.0.0 string. */
  override def toString: String =
    val b = new StringBuilder(s"${major.value}.${minor.value}.${patch.value}")
    preRelease.foreach(pr => b.append('-').append(pr.toString))
    buildMetadata.foreach(meta => b.append(meta.render))
    b.toString()

  /** Compares this version with another version according to SemVer precedence rules. */
  override def compare(that: Version): Int = Version.ordering.compare(this, that)

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
    given major: Increment[MajorVersion] with
      def apply(v: Version): Version =
        Version(v.major.increment, MinorVersion.reset, PatchNumber.reset)

    given minor: Increment[MinorVersion] with
      def apply(v: Version): Version =
        Version(v.major, v.minor.increment, PatchNumber.reset)

    given patch: Increment[PatchNumber] with
      def apply(v: Version): Version =
        Version(v.major, v.minor, v.patch.increment)

  /** Type class capturing a concrete pre-release classifier as a type `C`.
    *
    * This enables the generic `version.next[C]` and `version.as[C]` operations by associating the singleton type of the
    * enum case (e.g., `PreReleaseClassifier.Alpha.type`) with the classifier value.
    */
  trait PreReleaseClass[C]:
    def classifier: PreReleaseClassifier

  /** Provides `given` instances for the [[PreReleaseClass]] type class. */
  object PreReleaseClass:
    given milestone: PreReleaseClass[PreReleaseClassifier.Milestone.type] with
      def classifier: PreReleaseClassifier = PreReleaseClassifier.Milestone
    given alpha: PreReleaseClass[PreReleaseClassifier.Alpha.type] with
      def classifier: PreReleaseClassifier = PreReleaseClassifier.Alpha
    given beta: PreReleaseClass[PreReleaseClassifier.Beta.type] with
      def classifier: PreReleaseClassifier = PreReleaseClassifier.Beta
    given rc: PreReleaseClass[PreReleaseClassifier.ReleaseCandidate.type] with
      def classifier: PreReleaseClassifier = PreReleaseClassifier.ReleaseCandidate
    given snapshot: PreReleaseClass[PreReleaseClassifier.Snapshot.type] with
      def classifier: PreReleaseClassifier = PreReleaseClassifier.Snapshot

  /** Creates a `Version` instance from core components (final release). */
  inline def apply(major: MajorVersion, minor: MinorVersion, patch: PatchNumber): Version =
    new Version(major, minor, patch, None, None)

  /** Defines the ordering for [[Version]] instances according to Semantic Versioning 2.0.0 precedence rules. */
  given ordering: Ordering[Version] with
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
end Version
