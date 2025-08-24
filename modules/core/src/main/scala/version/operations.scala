package version.operations

// Import necessary types from the parent package.
import version.*
import version.PreRelease.Resolver
import version.errors.*
import version.parser.VersionParser

/** Provides ergonomic extension methods for manipulating and querying [[Version]] instances and related types, as well
  * as convenient string parsing capabilities.
  */

// --- String Parsing Extensions ---

extension (s: String)
  /** Parses the string as a [[Version]] using the contextual [[PreRelease.Resolver]].
    *
    * @return
    *   A `Right(Version)` on success, or `Left(ParseError)` on failure.
    */
  inline def toVersion(using Resolver): Either[ParseError, Version] =
    VersionParser.parse(s)

// --- PreRelease Extensions ---

extension (pr: PreRelease)
  /** Checks if the classifier is [[PreReleaseClassifier.Milestone]]. */
  inline def isMilestone: Boolean = pr.classifier.equals(PreReleaseClassifier.Milestone)

  /** Checks if the classifier is [[PreReleaseClassifier.Alpha]]. */
  inline def isAlpha: Boolean = pr.classifier.equals(PreReleaseClassifier.Alpha)

  /** Checks if the classifier is [[PreReleaseClassifier.Beta]]. */
  inline def isBeta: Boolean = pr.classifier.equals(PreReleaseClassifier.Beta)

  /** Checks if the classifier is [[PreReleaseClassifier.ReleaseCandidate]]. */
  inline def isReleaseCandidate: Boolean = pr.classifier.equals(PreReleaseClassifier.ReleaseCandidate)

  /** Checks if the classifier is [[PreReleaseClassifier.Snapshot]]. */
  inline def isSnapshot: Boolean = pr.classifier.equals(PreReleaseClassifier.Snapshot)

// --- Version Extensions ---

extension (v: Version)

// --- Status Checks ---

  /** Returns `true` if the major version is non-zero, indicating stability according to SemVer. */
  inline def isStable: Boolean = v.major.isStable

  /** Returns `true` if the version has pre-release information. */
  inline def isPreRelease: Boolean = v.preRelease.nonEmpty

  /** Returns `true` if the version is a final release (no pre-release information). */
  inline def isFinal: Boolean = v.preRelease.isEmpty

  // --- Version Bumping Operations (Promoting to Release) ---
  // These operations derive the next stable version and clear pre-release information and build metadata.

  /** Returns the next logical [[Version]] representing with the specified component incremented. Clears pre-release and
    * build metadata.
    *
    * Type-safe generic API: choose which component to bump via the type parameter. Examples:
    *   - v.next[MajorVersion]
    *   - v.next[MinorVersion]
    *   - v.next[PatchNumber]
    *
    * Clears pre-release and build metadata.
    */
  def next[F](using inc: Version.Increment[F]): Version = inc(v)

  // --- Pre-Release and Metadata Operations ---
  // These operations preserve the base version (M.m.p) and existing build metadata unless explicitly cleared.

  /** Returns a new [[Version]] with the pre-release information removed (promoting to a final release).
    *
    * Build metadata is preserved.
    */
  def release: Version = v.copy(preRelease = None)

  /** Returns a new [[Version]] marked as a Snapshot of the current base version. */
  def toSnapshot: Version = v.copy(preRelease = Some(PreRelease.snapshot))

  // --- Typed pre-release operations ---

  /** Advance within pre-release classifiers (Milestone, Alpha, Beta, RC).
    *   - Requires current version to be a pre-release
    *   - Target classifier must have equal or higher precedence; otherwise returns InvalidPreReleaseTransition
    *   - If same classifier: increment number
    *   - If higher classifier: start at 1 (for versioned classifiers) or set to Snapshot (no number)
    */
  def advance[C](using cls: Version.PreReleaseClass[C]): Either[VersionError, Version] =
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
            if target.versioned then PreRelease(target, Some(PreReleaseNumber.reset))
            else PreRelease.snapshot
          Right(v.copy(preRelease = Some(nextPr)))
        else
          // Lower precedence transitions are disallowed
          Left(InvalidPreReleaseTransition(current.classifier, target))

  /** Force-set the pre-release to the given typed classifier with a specific number.
    *   - Works on final or pre-release versions
    *   - Validates that the classifier is versioned and the number >= 1
    */
  def as[C](n: Int)(using cls: Version.PreReleaseClass[C]): Either[VersionError, Version] =
    val target = cls.classifier
    if !target.versioned then Left(ClassifierNotVersioned(target))
    else
      PreReleaseNumber.from(n) match
        case Left(err)  => Left(err)
        case Right(num) => Right(v.copy(preRelease = Some(PreRelease(target, Some(num)))))

  /** Force-set the pre-release to the given typed classifier using minimum/reset number or snapshot.
    *   - If classifier is versioned -> number = 1
    *   - If classifier is Snapshot -> no number
    */
  def as[C](using cls: Version.PreReleaseClass[C]): Version =
    val target = cls.classifier
    val pr = if target.versioned then PreRelease(target, Some(PreReleaseNumber.reset)) else PreRelease.snapshot
    v.copy(preRelease = Some(pr))

  /** Returns a new [[Version]] with the specified pre-release explicitly set. Overwrites existing pre-release. */
  def set(preRelease: PreRelease): Version = v.copy(preRelease = Some(preRelease))

  /** Returns a new [[Version]] with the specified build metadata attached. Overwrites existing metadata. */
  def set(metadata: BuildMetadata): Version = v.copy(buildMetadata = Some(metadata))

  /** Returns a new [[Version]] with any build metadata removed. */
  def dropMetadata: Version = v.copy(buildMetadata = None)

  // --- Convenience Combinations ---

end extension
