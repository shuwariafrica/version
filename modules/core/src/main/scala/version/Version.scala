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

  /** Compares this version with another version according to SemVer precedence rules. */
  override inline def compare(that: Version): Int = summon[Ordering[Version]].compare(this, that)

/** Provides factory methods, utility functions, and type class instances for [[Version]]. */
object Version:

  /** Type class describing how to bump a version given a specific component type `F`.
    *
    * Enables the generic `version.next[F]` operation. Instances are provided for:
    *   - Core components: [[MajorVersion]], [[MinorVersion]], [[PatchNumber]]
    *   - Versioned pre-release classifiers: [[PreReleaseClassifier.Dev Dev]], [[PreReleaseClassifier.Milestone Milestone]],
    *     [[PreReleaseClassifier.Alpha Alpha]], [[PreReleaseClassifier.Beta Beta]], [[PreReleaseClassifier.ReleaseCandidate ReleaseCandidate]]
    *
    * [[PreReleaseClassifier.Snapshot Snapshot]] has no `Increment` instance — use `as[Snapshot]` instead.
    *
    * For core components:
    *   - Increments the targeted component
    *   - Resets all lower-precedence components
    *   - Clears pre-release and build metadata
    *
    * For pre-release classifiers:
    *   - Same classifier: increments the pre-release number
    *   - Higher-precedence classifier: sets to `.1` of that classifier
    *   - Lower-precedence classifier or snapshot: bumps patch, then sets to `.1` of that classifier
    *   - Final version: sets to `.1` of that classifier on the current core
    */
  trait Increment[F]:
    extension (v: Version) def increment: Version

  /** Provides `given` instances for the [[Increment]] type class. */
  object Increment:
    import PreReleaseClassifier.*

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

    // --- Versioned Pre-Release Classifier Instances ---

    private inline def classifierIncrement(v: Version, target: PreReleaseClassifier): Version =
      val pr1 = PreRelease.fromUnsafe(target, Some(PreReleaseNumber.reset))
      v.preRelease match
        case None =>
          // Final version → start pre-release cycle on current core
          Version(v.major, v.minor, v.patch, pr1)
        case Some(pr) if pr.classifier == target =>
          // Same classifier → increment number
          Version(v.major, v.minor, v.patch, pr.increment)
        case Some(pr) if pr.classifier.ordinal < target.ordinal =>
          // Current has lower precedence → advance to higher classifier
          Version(v.major, v.minor, v.patch, pr1)
        case Some(_) =>
          // Current has higher or equal precedence (snapshot) → bump patch, start new cycle
          Version(v.major, v.minor, v.patch.increment, pr1)

    given Increment[Dev]:
      extension (v: Version) inline def increment: Version = classifierIncrement(v, PreReleaseClassifier.Dev)

    given Increment[Milestone]:
      extension (v: Version) inline def increment: Version = classifierIncrement(v, PreReleaseClassifier.Milestone)

    given Increment[Alpha]:
      extension (v: Version) inline def increment: Version = classifierIncrement(v, PreReleaseClassifier.Alpha)

    given Increment[Beta]:
      extension (v: Version) inline def increment: Version = classifierIncrement(v, PreReleaseClassifier.Beta)

    given Increment[ReleaseCandidate]:
      extension (v: Version) inline def increment: Version = classifierIncrement(v, PreReleaseClassifier.ReleaseCandidate)
  end Increment

  /** Type class capturing a concrete pre-release classifier as a type `C`.
    *
    * Enables the generic `as[C]` operation by associating the singleton type of the enum case
    * (e.g., `PreReleaseClassifier.Alpha`) with the classifier value.
    */
  trait PreReleaseClass[C]:
    /** Returns the [[PreReleaseClassifier]] value associated with this type class instance. */
    def classifier: PreReleaseClassifier

  /** Provides `given` instances for the [[PreReleaseClass]] type class. */
  object PreReleaseClass:
    import PreReleaseClassifier.*

    given PreReleaseClass[Dev]:
      def classifier: PreReleaseClassifier = PreReleaseClassifier.Dev

    given PreReleaseClass[Milestone]:
      def classifier: PreReleaseClassifier = PreReleaseClassifier.Milestone

    given PreReleaseClass[Alpha]:
      def classifier: PreReleaseClassifier = PreReleaseClassifier.Alpha

    given PreReleaseClass[Beta]:
      def classifier: PreReleaseClassifier = PreReleaseClassifier.Beta

    given PreReleaseClass[ReleaseCandidate]:
      def classifier: PreReleaseClassifier = PreReleaseClassifier.ReleaseCandidate

    given PreReleaseClass[Snapshot]:
      def classifier: PreReleaseClassifier = PreReleaseClassifier.Snapshot

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

    /** Full SemVer rendering WITH complete build metadata (no truncation).
      *
      * Unlike [[Extended]], this preserves all metadata identifiers verbatim, including full SHA hashes. Use this for
      * serialisation where round-trip fidelity is required.
      */
    object Full extends Show:
      extension (v: Version)
        def show: String =
          val pre = v.preRelease.fold("")(pr => s"-${pr.show}")
          val meta = v.metadata.fold("")(bm => s"+${bm.identifiers.mkString(".")}")
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

    /** Returns `true` if the version represents a stable release (major > 0 and not a snapshot). */
    inline def stable: Boolean = v.major.isStable && !v.snapshot

    /** Returns `true` if the pre-release classifier is [[PreReleaseClassifier.Snapshot]]. */
    inline def snapshot: Boolean = v.preRelease.exists(_.isSnapshot)

    /** Returns the core version (major.minor.patch) without pre-release or build metadata. */
    inline def core: Version = Version(v.major, v.minor, v.patch)

    // --- Version Bumping Operations ---

    /** Returns the next logical [[Version]] with the specified component incremented.
      *
      * For core components ([[MajorVersion]], [[MinorVersion]], [[PatchNumber]]):
      *   - Clears pre-release and build metadata
      *
      * For versioned pre-release classifiers ([[PreReleaseClassifier.Dev Dev]], [[PreReleaseClassifier.Milestone Milestone]],
      * [[PreReleaseClassifier.Alpha Alpha]], [[PreReleaseClassifier.Beta Beta]], [[PreReleaseClassifier.ReleaseCandidate ReleaseCandidate]]):
      *   - Same classifier: increments the pre-release number
      *   - Higher-precedence classifier: sets to `.1` of that classifier
      *   - Lower-precedence classifier or snapshot: bumps patch, then sets to `.1` of that classifier
      *   - Final version: sets to `.1` of that classifier on the current core
      *
      * [[PreReleaseClassifier.Snapshot Snapshot]] has no `Increment` instance — use `as[Snapshot]` instead.
      *
      * {{{
      * // Core component examples
      * v.next[MajorVersion]    // 1.2.3 → 2.0.0
      * v.next[MinorVersion]    // 1.2.3 → 1.3.0
      * v.next[PatchNumber]     // 1.2.3 → 1.2.4
      *
      * // Pre-release classifier examples
      * v.next[Alpha]           // 1.2.3 → 1.2.3-alpha.1
      * v.next[Alpha]           // 1.2.3-alpha.1 → 1.2.3-alpha.2
      * v.next[Beta]            // 1.2.3-alpha.5 → 1.2.3-beta.1
      * v.next[Alpha]           // 1.2.3-beta.1 → 1.2.4-alpha.1 (lower precedence → new patch)
      * }}}
      */
    inline def next[F](using Increment[F]): Version = v.increment

    // --- Typed Pre-Release Operations ---

    /** Sets the pre-release to the given classifier with a specific number.
      *
      * Unlike `next[C]`, this does not consider precedence — it directly sets the classifier
      * and number. Clears build metadata (consistent with `next[C]`).
      *
      * @return
      *   `Right(Version)` on success, `Left(error)` if the classifier is non-versioned or number is invalid.
      */
    inline def as[C](n: Int)(using cls: PreReleaseClass[C]): Either[VersionError, Version] =
      val target = cls.classifier
      if !target.versioned then Left(ClassifierNotVersioned(target))
      else
        PreReleaseNumber.from(n) match
          case Left(err)  => Left(err)
          case Right(num) => Right(Version(v.major, v.minor, v.patch, PreRelease.fromUnsafe(target, Some(num))))

    /** Sets the pre-release to the given classifier with the default number (1 for versioned, none for Snapshot).
      *
      * Unlike `next[C]`, this does not consider precedence — it directly sets the classifier.
      * Clears build metadata (consistent with `next[C]`).
      *
      * {{{
      * v.as[Alpha]    // 1.2.3 → 1.2.3-alpha.1
      * v.as[Snapshot] // 1.2.3 → 1.2.3-snapshot
      * }}}
      */
    inline def as[C](using cls: PreReleaseClass[C]): Version =
      val target = cls.classifier
      val pr = if target.versioned then PreRelease.fromUnsafe(target, Some(PreReleaseNumber.reset)) else PreRelease.snapshot
      Version(v.major, v.minor, v.patch, pr)
  end extension

end Version
