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
  metadata: Option[Metadata]
) extends Ordered[Version]:

  /** Formats the version as a standard SemVer 2.0.0 string. */
  override def toString: String =
    val b = new StringBuilder(s"${major.value}.${minor.value}.${patch.value}")
    preRelease.foreach(pr => b.append('-').append(pr.toString))
    metadata.foreach(meta => b.append(meta.show))
    b.toString() // FIXME: Use an instance of Show

  /** Compares this version with another version according to SemVer precedence rules. */
  override inline def compare(that: Version): Int = summon[Ordering[Version]].compare(this, that)

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
    extension (v: Version) def increment: Version

  /** Provides `given` instances for the [[Increment]] type class. */
  object Increment:
    given Increment[MajorVersion]:
      extension (v: Version)
        inline def increment: Version =
          Version(v.major.increment, MinorVersion.reset, PatchNumber.reset)

    given Increment[MinorVersion]:
      extension (v: Version)
        inline def increment: Version =
          Version(v.major, v.minor.increment, PatchNumber.reset)

    given Increment[PatchNumber]:
      extension (v: Version)
        inline def increment: Version =
          Version(v.major, v.minor, v.patch.increment)

  /** Type class capturing a concrete pre-release classifier as a type `C`.
    *
    * Enables the generic `version.next[C]` and `version.as[C]` operations by associating the singleton type of the
    * enum case (e.g., `PreReleaseClassifier.Alpha.type`) with the classifier value.
    */
  trait PreReleaseClass[C]:
    /** Returns the [[PreReleaseClassifier]] value associated with this type class instance. */
    def classifier: PreReleaseClassifier

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
    metadata: Metadata
  ): Version =
    Version(major, minor, patch, None, Some(metadata))

  /** Creates a `Version` instance with both pre-release and build metadata. */
  inline def apply(
    major: MajorVersion,
    minor: MinorVersion,
    patch: PatchNumber,
    preRelease: PreRelease,
    metadata: Metadata
  ): Version =
    Version(major, minor, patch, Some(preRelease), Some(metadata))

  // --- Read Typeclass ---

  /** Converts a [[Parser.ParsedVersion]] tuple to a [[Version]]. Package-private for internal use. */
  private[version] inline def fromParsed(p: Parser.ParsedVersion): Version =
    Version(p.major, p.minor, p.patch, p.preRelease, p.metadata)

  /** Creates a [[Version]] from a value using the contextual [[Read]] instance.
    *
    * @return
    *   `Right(Version)` on success, `Left(ParseError)` on failure.
    */
  inline def from[A](a: A)(using r: Read[A]): Either[errors.ParseError, Version] =
    a.toVersion

  /** Creates a [[Version]] from a value using the contextual [[Read]] instance, throwing on failure.
    *
    * @throws errors.ParseError
    *   if the input cannot be converted to a valid Version.
    */
  inline def fromUnsafe[A](a: A)(using r: Read[A]): Version =
    a.toVersionUnsafe

  /** Type class for reading [[Version]] instances from various input representations.
    *
    * Enables pluggable parsing strategies. Users may provide custom implementations as `given` instances to support
    * organisation-specific formats or alternative input types.
    *
    * A default `given` instance for `String` is provided in [[version.instances]].
    *
    * @see [[Read$ Read]] for the summoner.
    */
  trait Read[A]:
    /** Attempts to convert the input to a [[Version]].
      *
      * @return
      *   `Right(Version)` on success, `Left(ParseError)` on failure.
      */
    extension (a: A) def toVersion(using PreRelease.Resolver): Either[errors.ParseError, Version]

    /** Converts the input to a [[Version]], throwing on failure.
      *
      * @throws errors.ParseError
      *   if the input cannot be converted to a valid Version.
      */
    extension (a: A) def toVersionUnsafe(using PreRelease.Resolver): Version

  /** Provides the [[Read]] summoner.
    *
    * The default `given` instance for `String` is in [[version.instances]].
    */
  object Read:
    /** Summons the contextual [[Read]] instance. */
    inline def apply[A](using r: Read[A]): Read[A] = r

    /** Default [[Read]] instance for `String`.
      *
      * Parses SemVer strings using the contextual [[PreRelease.Resolver]] for mapping pre-release
      * identifiers. Available as a `given` via `import version.{given, *}`.
      */
    object ReadString extends Read[String]:
      extension (s: String)
        inline def toVersion(using PreRelease.Resolver): Either[errors.ParseError, Version] =
          Parser.parse(s).map(fromParsed)
        inline def toVersionUnsafe(using PreRelease.Resolver): Version =
          toVersion match
            case Right(v) => v
            case Left(e)  => throw e // scalafix:ok

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
          val pre = v.preRelease.fold("")(pr => s"-${pr.show}")
          val meta = v.metadata.fold("") { bm =>
            val truncated = bm.identifiers.map { id =>
              // Truncate SHA identifiers to 7 chars (git short-SHA convention)
              if id.startsWith("sha") && id.length > 10 then s"sha${id.slice(3, 10)}"
              else id
            }
            s"+${truncated.mkString(".")}"
          }
          s"${v.major.value}.${v.minor.value}.${v.patch.value}$pre$meta"
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

  given CanEqual[Version, Version] = CanEqual.derived

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
    inline def next[F](using Increment[F]): Version = v.increment

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
    def set(metadata: Metadata): Version = v.copy(metadata = Some(metadata))

    /** Returns a new [[Version]] with any build metadata removed. */
    def dropMetadata: Version = v.copy(metadata = None)
  end extension

end Version
