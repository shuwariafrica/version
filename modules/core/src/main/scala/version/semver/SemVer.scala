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
package version.semver

import scala.annotation.targetName

import version.*
import version.errors.ClassifierNotVersioned
import version.errors.InvalidVersionFormat
import version.errors.VersionError

/** Represents a version conforming to the Semantic Versioning 2.0.0 specification.
  *
  * Format: `MAJOR.MINOR.PATCH[-PRERELEASE][+BUILDMETADATA]`.
  *
  * Instances may be constructed via [[SemVer$ SemVer]].
  */
final case class SemVer(
  major: Major,
  minor: Minor,
  patch: Patch,
  preRelease: Option[PreRelease],
  metadata: Option[Metadata]
)

/** Provides factory methods, type class instances, and extensions for [[SemVer]]. */
object SemVer:

  // --- Type Classes for SemVer-Specific Operations ---

  /** Describes how to bump a version given a specific component type `F`.
    *
    * Enables the generic `version.next[F]` operation.
    */
  trait Increment[F]:
    extension (v: SemVer) def increment: SemVer

  object Increment:
    import PreReleaseClassifier.*

    given Increment[Major]:
      extension (v: SemVer)
        def increment: SemVer =
          SemVer(v.major.increment, Minor.reset, Patch.reset)

    given Increment[Minor]:
      extension (v: SemVer)
        def increment: SemVer =
          SemVer(v.major, v.minor.increment, Patch.reset)

    given Increment[Patch]:
      extension (v: SemVer)
        def increment: SemVer =
          SemVer(v.major, v.minor, v.patch.increment)

    private inline def classifierIncrement(v: SemVer, target: PreReleaseClassifier): SemVer =
      val pr1 = PreRelease.fromUnsafe(target, Some(PreReleaseNumber.reset))
      v.preRelease match
        case None =>
          SemVer(v.major, v.minor, v.patch, pr1)
        case Some(pr) if pr.classifier == target =>
          SemVer(v.major, v.minor, v.patch, pr.increment)
        case Some(pr) if pr.classifier.ordinal < target.ordinal =>
          SemVer(v.major, v.minor, v.patch, pr1)
        case Some(_) =>
          SemVer(v.major, v.minor, v.patch.increment, pr1)

    given Increment[Dev]:
      extension (v: SemVer) def increment: SemVer = classifierIncrement(v, PreReleaseClassifier.Dev)

    given Increment[Milestone]:
      extension (v: SemVer) def increment: SemVer = classifierIncrement(v, PreReleaseClassifier.Milestone)

    given Increment[Alpha]:
      extension (v: SemVer) def increment: SemVer = classifierIncrement(v, PreReleaseClassifier.Alpha)

    given Increment[Beta]:
      extension (v: SemVer) def increment: SemVer = classifierIncrement(v, PreReleaseClassifier.Beta)

    given Increment[ReleaseCandidate]:
      extension (v: SemVer) def increment: SemVer = classifierIncrement(v, PreReleaseClassifier.ReleaseCandidate)
  end Increment

  /** Captures a concrete pre-release classifier as a type `C`. */
  trait PreReleaseClass[C]:
    def classifier: PreReleaseClassifier

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

  // --- Overloaded Constructors ---

  inline def apply(major: Major, minor: Minor, patch: Patch): SemVer =
    SemVer(major, minor, patch, None, None)

  inline def apply(major: Major, minor: Minor, patch: Patch, preRelease: Option[PreRelease]): SemVer =
    SemVer(major, minor, patch, preRelease, None)

  inline def apply(major: Major, minor: Minor, patch: Patch, preRelease: PreRelease): SemVer =
    SemVer(major, minor, patch, Some(preRelease), None)

  inline def apply(major: Major, minor: Minor, patch: Patch, metadata: Metadata): SemVer =
    SemVer(major, minor, patch, None, Some(metadata))

  inline def apply(major: Major, minor: Minor, patch: Patch, preRelease: PreRelease, metadata: Metadata): SemVer =
    SemVer(major, minor, patch, Some(preRelease), Some(metadata))

  // --- Parsing ---

  private[version] inline def fromParsed(p: Parser.ParsedVersion): SemVer =
    SemVer(p.major, p.minor, p.patch, p.preRelease, p.metadata)

  /** Parse a SemVer string using the contextual [[PreRelease.Resolver]] for pre-release identifier mapping. */
  def parse(input: String)(using PreRelease.Resolver): Either[version.errors.ParseError, SemVer] =
    Parser.parse(input).map(fromParsed)

  /** Parse a SemVer string, throwing on failure. */
  def parseUnsafe(input: String)(using PreRelease.Resolver): SemVer =
    parse(input) match
      case Right(v) => v
      case Left(e)  => throw e // scalafix:ok

  // --- Formatter ---

  /** Configurable rendering strategy for [[SemVer]] values.
    *
    * Three instances are provided:
    *   - [[Formatter$.standard standard]] - `MAJOR.MINOR.PATCH[-PRERELEASE]` (same as [[VersionScheme]]'s `show`)
    *   - [[Formatter$.extended extended]] - includes build metadata with SHA truncation
    *   - [[Formatter$.full full]] - includes verbatim build metadata (for serialisation round-trips)
    */
  trait Formatter:
    def format(v: SemVer): String

  object Formatter:
    // Hotpath: batch rendering benefits from reduced allocations
    private inline def appendCore(sb: StringBuilder, v: SemVer): Unit =
      sb.append(v.major.value).append('.').append(v.minor.value).append('.').append(v.patch.value): Unit

    private inline def appendPreRelease(sb: StringBuilder, v: SemVer): Unit =
      v.preRelease.foreach(pr => sb.append('-').append(pr.show): Unit)

    /** Standard rendering without build metadata. Same output as `v.show`. */
    val standard: Formatter = (v: SemVer) =>
      val sb = StringBuilder(24)
      appendCore(sb, v)
      appendPreRelease(sb, v)
      sb.result()

    private val shaPrefix = "sha"
    private val shortShaLength = 7

    /** Extended rendering with build metadata. SHA identifiers truncated to 7 characters. */
    val extended: Formatter = (v: SemVer) =>
      val sb = StringBuilder(64)
      appendCore(sb, v)
      appendPreRelease(sb, v)
      v.metadata.foreach { bm =>
        sb.append('+'): Unit
        // Hotpath: index-based loop avoids iterator allocation from mkString.
        val ids = bm.identifiers
        var i = 0 // scalafix:ok DisableSyntax.var
        while i < ids.length do // scalafix:ok DisableSyntax.while
          if i > 0 then sb.append('.'): Unit
          val id = ids(i)
          if id.startsWith(shaPrefix) && id.length > shaPrefix.length + shortShaLength then
            sb.append(shaPrefix).append(id.substring(shaPrefix.length, shaPrefix.length + shortShaLength)): Unit
          else sb.append(id): Unit
          i += 1
      }
      sb.result()

    /** Full rendering with verbatim build metadata. Use for serialisation round-trips. */
    val full: Formatter = (v: SemVer) =>
      val sb = StringBuilder(64)
      appendCore(sb, v)
      appendPreRelease(sb, v)
      v.metadata.foreach(bm => sb.append('+').append(bm.show): Unit)
      sb.result()
  end Formatter

  // --- Ordering ---

  given Ordering[SemVer]:
    def compare(x: SemVer, y: SemVer): Int =
      val compareNumbers =
        summon[Ordering[(Major, Minor, Patch)]].compare(
          (x.major, x.minor, x.patch),
          (y.major, y.minor, y.patch)
        )
      compareNumbers match
        case 0 =>
          (x.preRelease, y.preRelease) match
            case (None, None)         => 0
            case (Some(_), None)      => -1
            case (None, Some(_))      => 1
            case (Some(px), Some(py)) => summon[Ordering[PreRelease]].compare(px, py)
        case n => n

  given CanEqual[SemVer, SemVer] = CanEqual.derived

  // --- given ResolvableScheme[SemVer] ---

  given ResolvableScheme[SemVer] with
    def name: String = "semver"

    def layout: IArray[ComponentDescriptor] = IArray(
      ComponentDescriptor("major", ComponentRole.Breaking),
      ComponentDescriptor("minor", ComponentRole.Feature),
      ComponentDescriptor("patch", ComponentRole.Fix)
    )

    def parse(input: String): Either[VersionError, SemVer] =
      Parser.parse(input)(using PreRelease.Resolver.given_Resolver).map(fromParsed)

    def ordering: Ordering[SemVer] = summon[Ordering[SemVer]]

    def keywordAliases: Map[String, Int] = Map(
      "major" -> 0,
      "breaking" -> 0,
      "minor" -> 1,
      "feature" -> 1,
      "feat" -> 1,
      "patch" -> 2,
      "fix" -> 2
    )

    def initialVersion: SemVer = SemVer(Major(0), Minor(1), Patch(0))

    def developmentVersion(targetCore: SemVer, meta: DevelopmentMetadata): SemVer =
      val ids = List(
        meta.prNumber.map(n => s"pr${Math.max(0, n)}"),
        meta.branch.map(b => s"branch$b"),
        meta.commitCount.map(n => s"commits$n"),
        meta.commitSha.map(s => s"sha$s"),
        if meta.isDirty then Some("dirty") else None
      ).flatten
      val md = Metadata.from(ids).toOption
      SemVer(targetCore.major, targetCore.minor, targetCore.patch, Some(PreRelease.snapshot), md)

    extension (v: SemVer)
      def show: String = Formatter.standard.format(v)
      def components: IArray[Int] = IArray(v.major.value, v.minor.value, v.patch.value)
      def isFinal: Boolean = v.preRelease.isEmpty
      def core: SemVer = SemVer(v.major, v.minor, v.patch)
      def incrementComponent(index: Int): SemVer = index match
        case 0 => SemVer(v.major.increment, Minor.reset, Patch.reset)
        case 1 => SemVer(v.major, v.minor.increment, Patch.reset)
        case 2 => SemVer(v.major, v.minor, v.patch.increment)
        case _ => v
      def setComponent(index: Int, value: Int): Either[VersionError, SemVer] = index match
        case 0 => Major.from(value).map(m => SemVer(m, Minor.reset, Patch.reset))
        case 1 => Minor.from(value).map(m => SemVer(v.major, m, Patch.reset))
        case 2 => Patch.from(value).map(p => SemVer(v.major, v.minor, p))
        case _ => Left(InvalidVersionFormat(s"Invalid component index: $index"))
      def defaultBump: SemVer = SemVer(v.major, v.minor, v.patch.increment)
      def promoteToRelease: SemVer = v.copy(preRelease = None, metadata = None)
  end given

  // --- Multi-Parameter Extension Companion Alias ---

  inline def as[C](v: SemVer, n: Int)(using cls: PreReleaseClass[C]): Either[VersionError, SemVer] =
    v.as[C](n)

  extension (v: SemVer)
    inline def stable: Boolean = v.major.isStable && !v.snapshot
    inline def snapshot: Boolean = v.preRelease.exists(_.isSnapshot)
    inline def next[F](using Increment[F]): SemVer = v.increment

    @targetName("ext_as_with_number")
    inline def as[C](n: Int)(using cls: PreReleaseClass[C]): Either[VersionError, SemVer] =
      val target = cls.classifier
      if !target.versioned then Left(ClassifierNotVersioned(target.show))
      else
        PreReleaseNumber.from(n) match
          case Left(err)  => Left(err)
          case Right(num) => Right(SemVer(v.major, v.minor, v.patch, PreRelease.fromUnsafe(target, Some(num))))

    inline def as[C](using cls: PreReleaseClass[C]): SemVer =
      val target = cls.classifier
      val pr = if target.versioned then PreRelease.fromUnsafe(target, Some(PreReleaseNumber.reset)) else PreRelease.snapshot
      SemVer(v.major, v.minor, v.patch, pr)
  end extension

end SemVer
