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
) extends Version:
  override def show: String = SemVer.Formatter.Standard.format(this)

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

  /** Named [[version.Formatter Formatter]] instances for SemVer rendering. */
  object Formatter:
    // Commit SHAs in build metadata are lowercase hex of full hash length (40 for SHA-1, 64 for SHA-256).
    // The shape distinguishes them from the other emitted identifiers (12-digit timestamp, branch slug, pr<N>, dirty).
    private inline def isShaIdentifier(id: String): Boolean =
      (id.length == 40 || id.length == 64) &&
        id.forall(c => (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))

    // Append into a caller-supplied StringBuilder to elide per-call intermediate Strings.
    private inline def appendCore(sb: StringBuilder, v: SemVer): Unit =
      sb.append(v.major.value).append('.').append(v.minor.value).append('.').append(v.patch.value): Unit

    private inline def appendPreRelease(sb: StringBuilder, v: SemVer): Unit =
      v.preRelease.foreach(pr => sb.append('-').append(pr.show): Unit)

    private def appendMetadata(sb: StringBuilder, v: SemVer, shaTrunc: Option[Int]): Unit =
      // scalafix:off DisableSyntax.var
      // Single traversal over identifiers; var tracks whether to emit the leading `.`.
      v.metadata.foreach: m =>
        sb.append('+'): Unit
        var first = true
        m.identifiers.foreach: id =>
          if !first then sb.append('.'): Unit
          first = false
          val toAppend = shaTrunc match
            case Some(n) if isShaIdentifier(id) => id.take(n)
            case _                              => id
          sb.append(toAppend): Unit
      // scalafix:on DisableSyntax.var

    /** Renders core plus pre-release; omits build metadata. Equivalent to `v.show`. */
    case object Standard extends version.Formatter[SemVer]:
      def format(v: SemVer): String =
        val sb = StringBuilder(24)
        appendCore(sb, v)
        appendPreRelease(sb, v)
        sb.result()

    /** Renders core plus pre-release plus build metadata. Commit SHAs are emitted verbatim; round-trips through
      * [[SemVer$.parse SemVer.parse]] return an equal value.
      */
    case object Full extends version.Formatter[SemVer]:
      def format(v: SemVer): String =
        val sb = StringBuilder(64)
        appendCore(sb, v)
        appendPreRelease(sb, v)
        appendMetadata(sb, v, None)
        sb.result()

      /** Returns a [[version.Formatter Formatter]] that truncates the commit-SHA build-metadata identifier to
        * `length` characters. Other identifiers are emitted verbatim. `length` must be in `[7, 64]` (SHA-1 = 40,
        * SHA-256 = 64).
        */
      def withShaLength(length: Int): version.Formatter[SemVer] =
        require(length >= 7 && length <= 64, s"shaLength must be in [7, 64], got $length")
        FullWithShaLength(length)
    end Full

    final private case class FullWithShaLength(shaLength: Int) extends version.Formatter[SemVer]:
      def format(v: SemVer): String =
        val sb = StringBuilder(64)
        appendCore(sb, v)
        appendPreRelease(sb, v)
        appendMetadata(sb, v, Some(shaLength))
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

  // --- Development-version timestamp formatting ---

  /** Sanitise an arbitrary branch label for use as a SemVer build-metadata identifier.
    *
    * The SemVer 2.0.0 grammar restricts identifiers to `[0-9A-Za-z-]`. This helper lowercases the input, replaces any
    * other character with `-`, collapses runs of `-`, trims leading/trailing `-`, and returns `"detached"` for an
    * empty result. The input is not mutated; consumers retain the original branch label in
    * [[version.DevelopmentMetadata DevelopmentMetadata]].
    */
  private[version] def sanitiseBranchIdentifier(name: String): String =
    // scalafix:off DisableSyntax.var
    // Use a var tracker to drop the per-char boxing of a fold-with-tuple shape.
    val lower = name.toLowerCase
    val sb = new StringBuilder(lower.length)
    var prevHyphen = false
    lower.foreach { ch =>
      val ok = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '-'
      if ok then
        if ch == '-' then
          if !prevHyphen then
            sb.append('-'): Unit
            prevHyphen = true
        else
          sb.append(ch): Unit
          prevHyphen = false
      else if !prevHyphen then
        sb.append('-'): Unit
        prevHyphen = true
    }
    val raw = sb.result()
    val trimmed = raw.dropWhile(_ == '-').reverse.dropWhile(_ == '-').reverse
    if trimmed.isEmpty then "detached" else trimmed
    // scalafix:on DisableSyntax.var
  end sanitiseBranchIdentifier

  /** Render an epoch-seconds (UTC) timestamp as a 12-character `yyyymmddhhmm` identifier.
    *
    * The fixed width keeps lexicographic ordering aligned with chronological ordering for snapshots of the same base.
    * Conversion uses Howard Hinnant's civil-from-days algorithm so no `java.time` dependency is required (Scala Native
    * does not ship the full `java.time` package).
    */
  private[version] def formatUtcTimestamp(epochSeconds: Long): String =
    val secondsPerDay = 86400L
    val days = Math.floorDiv(epochSeconds, secondsPerDay)
    val secondsOfDay = Math.floorMod(epochSeconds, secondsPerDay).toInt
    val z = days + 719468L
    val era = if z >= 0 then z / 146097L else (z - 146096L) / 146097L
    val doe = (z - era * 146097L).toInt
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val baseYear = yoe + era * 400L
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if mp < 10 then mp + 3 else mp - 9
    val year = if month <= 2 then baseYear + 1 else baseYear
    val hour = secondsOfDay / 3600
    val minute = (secondsOfDay % 3600) / 60
    val sb = StringBuilder(12)
    appendYear(sb, year)
    appendPad2(sb, month)
    appendPad2(sb, day)
    appendPad2(sb, hour)
    appendPad2(sb, minute)
    sb.result()

  private inline def appendPad2(sb: StringBuilder, n: Int): Unit =
    if n < 10 then sb.append('0').append(n): Unit else sb.append(n): Unit

  private inline def appendYear(sb: StringBuilder, y: Long): Unit =
    if y < 0 then sb.append(y): Unit
    else if y < 10 then sb.append("000").append(y): Unit
    else if y < 100 then sb.append("00").append(y): Unit
    else if y < 1000 then sb.append('0').append(y): Unit
    else sb.append(y): Unit

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
      // Spine `<timestamp>.<branch>.<short-sha>` is invariant. The 12-character UTC
      // timestamp first lets raw string comparison of snapshots of the same base sort
      // chronologically. Tail-conditional flags (`pr<N>`, `dirty`) trail.
      val branchId = meta.branch.map(SemVer.sanitiseBranchIdentifier).getOrElse("detached")
      val ids = List(
        meta.commitTime.map(SemVer.formatUtcTimestamp),
        Some(branchId),
        meta.commitSha,
        meta.prNumber.map(n => s"pr${Math.max(0, n)}"),
        if meta.isDirty then Some("dirty") else None
      ).flatten
      val md = Metadata.from(ids).toOption
      SemVer(targetCore.major, targetCore.minor, targetCore.patch, Some(PreRelease.snapshot), md)

    extension (v: SemVer)
      def components: IArray[Int] = IArray(v.major.value, v.minor.value, v.patch.value)
      def isFinal: Boolean = v.preRelease.isEmpty
      override def isSnapshot: Boolean = v.preRelease.exists(_.isSnapshot)
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
