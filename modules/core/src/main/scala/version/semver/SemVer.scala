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

import boilerplate.ValueCodec

import scala.annotation.targetName

import version.*
import version.errors.ClassifierNotVersioned
import version.errors.ParseError
import version.errors.UnsupportedComponent
import version.errors.VersionError

/** A version under the Semantic Versioning 2.0.0 specification: `MAJOR.MINOR.PATCH[-PRERELEASE][+BUILDMETADATA]`.
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

  /** Advances a version towards the component or classifier named by the phantom type `F`. Consumed by `next`. */
  trait Increment[F]:
    extension (v: SemVer) def increment: SemVer

  /** Provides the [[Increment]] instances for the numeric components and the named classifiers. */
  object Increment:
    import PreReleaseClassifier.*

    given Increment[Major]:
      extension (v: SemVer) def increment: SemVer = SemVer(v.major.increment, Minor.reset, Patch.reset)

    given Increment[Minor]:
      extension (v: SemVer) def increment: SemVer = SemVer(v.major, v.minor.increment, Patch.reset)

    given Increment[Patch]:
      extension (v: SemVer) def increment: SemVer = SemVer(v.major, v.minor, v.patch.increment)

    private def classifierIncrement(v: SemVer, target: PreReleaseClassifier): SemVer =
      val restarted = PreRelease.initial(target)
      v.preRelease match
        case None                                       => SemVer(v.major, v.minor, v.patch, restarted)
        case Some(pr) if pr.classifier.contains(target) => SemVer(v.major, v.minor, v.patch, pr.increment)
        // Restarting the label only advances the version where the new label outranks the old one; where it does
        // not, the patch moves first so that the result still succeeds what it came from.
        case Some(pr) if summon[Ordering[PreRelease]].gt(restarted, pr) =>
          SemVer(v.major, v.minor, v.patch, restarted)
        case Some(_) => SemVer(v.major, v.minor, v.patch.increment, restarted)

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

  /** Names a concrete [[PreReleaseClassifier]] at the type level, so that `as` can be addressed by type. */
  trait PreReleaseClass[C]:
    def classifier: PreReleaseClassifier

  /** Provides the [[PreReleaseClass]] instance for each classifier. */
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

  inline def apply(major: Major, minor: Minor, patch: Patch): SemVer =
    SemVer(major, minor, patch, None, None)

  inline def apply(major: Major, minor: Minor, patch: Patch, preRelease: Option[PreRelease]): SemVer =
    SemVer(major, minor, patch, preRelease, None)

  inline def apply(major: Major, minor: Minor, patch: Patch, preRelease: PreRelease): SemVer =
    SemVer(major, minor, patch, Some(preRelease), None)

  @targetName("apply_with_metadata")
  inline def apply(major: Major, minor: Minor, patch: Patch, metadata: Metadata): SemVer =
    SemVer(major, minor, patch, None, Some(metadata))

  inline def apply(major: Major, minor: Minor, patch: Patch, preRelease: PreRelease, metadata: Metadata): SemVer =
    SemVer(major, minor, patch, Some(preRelease), Some(metadata))

  /** Reads a version string, accepting the conventional leading `v` or `V`. */
  def parse(input: String): Either[ParseError, SemVer] = Parser.parse(input)

  /** Reads a version string, throwing the [[version.errors.ParseError ParseError]] on rejection. */
  def parseUnsafe(input: String): SemVer =
    parse(input) match
      case Right(v) => v
      case Left(e)  => throw e // scalafix:ok

  /** Provides the [[version.Formatter Formatter]] instances that render a version other than canonically. */
  object Formatter:
    // A commit SHA is lowercase hex of full digest length; none of the other identifiers this library emits into
    // build metadata - a 12-digit timestamp, a branch slug, `pr<N>`, `dirty` - can take that shape.
    private inline def isShaIdentifier(id: String): Boolean =
      (id.length == 40 || id.length == 64) &&
        id.forall(c => (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))

    private inline def appendCore(sb: StringBuilder, v: SemVer): Unit =
      sb.append(v.major.value).append('.').append(v.minor.value).append('.').append(v.patch.value): Unit

    private inline def appendPreRelease(sb: StringBuilder, v: SemVer): Unit =
      v.preRelease.foreach(pr => sb.append('-').append(pr.show): Unit)

    private def appendMetadata(sb: StringBuilder, v: SemVer, shaTrunc: Option[Int]): Unit =
      // scalafix:off DisableSyntax.var
      // Single traversal over the identifiers; the var tracks whether a separator is due.
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

    /** Renders the numbers and the pre-release, omitting build metadata. */
    case object Standard extends version.Formatter[SemVer]:
      def format(v: SemVer): String =
        val sb = StringBuilder(24)
        appendCore(sb, v)
        appendPreRelease(sb, v)
        sb.result()

    /** Renders every part, matching the canonical form `show` produces. */
    case object Full extends version.Formatter[SemVer]:
      def format(v: SemVer): String =
        val sb = StringBuilder(64)
        appendCore(sb, v)
        appendPreRelease(sb, v)
        appendMetadata(sb, v, None)
        sb.result()

      /** As [[Full]], but truncating a commit-SHA build-metadata identifier to `length` characters. `length` must be
        * in `[7, 64]`.
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

  given Ordering[SemVer]:
    def compare(x: SemVer, y: SemVer): Int =
      val numbers = summon[Ordering[(Major, Minor, Patch)]].compare(
        (x.major, x.minor, x.patch),
        (y.major, y.minor, y.patch)
      )
      if numbers != 0 then numbers
      else
        (x.preRelease, y.preRelease) match
          case (None, None)       => 0
          case (Some(_), None)    => -1
          case (None, Some(_))    => 1
          case (Some(a), Some(b)) => summon[Ordering[PreRelease]].compare(a, b)

  given CanEqual[SemVer, SemVer] = CanEqual.derived

  given scheme: VersionScheme[SemVer]:
    def name: String = "semver"

    def parse(input: String): Either[VersionError, SemVer] = Parser.parse(input)

    def precedence: Ordering[SemVer] = summon[Ordering[SemVer]]

    def difference(a: SemVer, b: SemVer): Difference =
      if a.major != b.major then Difference.Release(0)
      else if a.minor != b.minor then Difference.Release(1)
      else if a.patch != b.patch then Difference.Release(2)
      else if differs(a.preRelease, b.preRelease) then Difference.Qualifier
      else if differs(a.metadata, b.metadata) then Difference.Build
      else Difference.None

    extension (v: SemVer)
      def show: String = Formatter.Full.format(v)
      def stable: Boolean = v.preRelease.isEmpty
      def release: SemVer = SemVer(v.major, v.minor, v.patch)
      def numbers: IArray[Long] = IArray(v.major.value, v.minor.value, v.patch.value)

  given arithmetic: VersionArithmetic[SemVer]:
    def apply(v: SemVer, request: Request): Either[VersionError, SemVer] = request match
      case Request.Advance(intent) => Right(advance(v, intent))
      case Request.Bump(component) =>
        axis(component).map(a => step(v.release, v.preRelease.nonEmpty, a))
      case Request.Assign(component, value) =>
        axis(component).flatMap(a => assign(v.release, a, value))

  given resolvable: ResolvableScheme[SemVer]:
    def initialVersion: SemVer = SemVer(Major(0), Minor(1), Patch(0))

    def developmentVersion(release: SemVer, metadata: DevelopmentMetadata): SemVer =
      // The spine `<timestamp>.<branch>.<sha>` is invariant, and the 12-character UTC timestamp leads so that raw
      // string comparison of snapshots of one base sorts them chronologically. Conditional flags trail.
      val branch = metadata.branch.map(sanitiseBranchIdentifier).getOrElse("detached")
      val identifiers = List(
        metadata.commitTime.map(Utc.compact),
        Some(branch),
        metadata.commitSha,
        metadata.prNumber.map(n => s"pr${Math.max(0, n)}"),
        Option.when(metadata.isDirty)("dirty")
      ).flatten
      SemVer(release.major, release.minor, release.patch, Some(PreRelease.snapshot), Metadata.of(identifiers).toOption)

    def defaultTarget(base: SemVer): SemVer = advance(base, Intent.Fix)

    def directives: Map[String, Request] = Map(
      "major" -> Request.Bump("major"),
      "minor" -> Request.Bump("minor"),
      "patch" -> Request.Bump("patch"),
      "breaking" -> Request.Advance(Intent.Breaking),
      "feature" -> Request.Advance(Intent.Feature),
      "feat" -> Request.Advance(Intent.Feature),
      "fix" -> Request.Advance(Intent.Fix),
      "stable" -> Request.Advance(Intent.Stable)
    )

    extension (v: SemVer) def snapshot: Boolean = v.preRelease.exists(_.classifier.contains(PreReleaseClassifier.Snapshot))
  end resolvable

  /** Binds a version to the text carrying it - a path segment, a query parameter, a header - for a service that reads
    * and writes versions at a wire boundary.
    */
  given valueCodec: ValueCodec.Aux[SemVer, VersionError] = ValueCodec(Parser.parse(_), v => v.show)

  /** Provides the named compatibility rules for [[SemVer]]. No instance is given: the two rules disagree below
    * `1.0.0`, and choosing between them is the consumer's commitment.
    */
  object Compatibility:

    /** Clause 8 read strictly: both stable, at or above `1.0.0`, and sharing a major. */
    val sameMajor: CompatibilityPolicy[SemVer] = (a: SemVer, b: SemVer) => a.stable && b.stable && a.major.value >= 1 && a.major == b.major

    /** The caret rule of Cargo, npm and Composer: both stable, and sharing their leftmost non-zero component. */
    val leftmostNonZero: CompatibilityPolicy[SemVer] = (a: SemVer, b: SemVer) =>
      a.stable && b.stable && {
        if a.major.value > 0 || b.major.value > 0 then a.major == b.major
        else if a.minor.value > 0 || b.minor.value > 0 then a.minor == b.minor
        else a.patch == b.patch
      }

  def as[C](v: SemVer, n: Long)(using PreReleaseClass[C]): Either[VersionError, SemVer] = v.as[C](n)

  extension (v: SemVer)
    def next[F](using Increment[F]): SemVer = v.increment

    @targetName("ext_as_with_number")
    def as[C](n: Long)(using cls: PreReleaseClass[C]): Either[VersionError, SemVer] =
      val target = cls.classifier
      if !target.versioned then Left(ClassifierNotVersioned(target.show))
      else
        for
          number <- PreReleaseNumber.of(n)
          label <- PreRelease.of(target, Some(number))
        yield SemVer(v.major, v.minor, v.patch, label)

    def as[C](using cls: PreReleaseClass[C]): SemVer =
      SemVer(v.major, v.minor, v.patch, PreRelease.initial(cls.classifier))
  end extension

  private def advance(v: SemVer, intent: Intent): SemVer =
    val base = v.release
    val pending = v.preRelease.nonEmpty
    intent match
      case Intent.Stable   => if base.major.value >= 1 then base else SemVer(Major(1), Minor(0), Patch(0))
      case Intent.Breaking => step(base, pending, tier(base))
      case Intent.Feature  => step(base, pending, below(base, 1))
      case Intent.Fix      => step(base, pending, below(base, 2))

  // The compatibility axis is the leftmost non-zero component. `Breaking` moves it and the lesser intents move below
  // it, bounded by the patch, which yields the >= 1.0.0, 0.y.z and 0.0.z advancement tiers from a single rule.
  private def tier(base: SemVer): Int =
    if base.major.value > 0 then 0
    else if base.minor.value > 0 then 1
    else 2

  private def below(base: SemVer, positions: Int): Int = math.min(tier(base) + positions, 2)

  // A pending pre-release whose components below the axis are all zero already sits on the requested boundary, so the
  // request is satisfied by releasing it rather than by advancing past it.
  private def step(base: SemVer, pending: Boolean, axis: Int): SemVer =
    if pending && onBoundary(base, axis) then base else bump(base, axis)

  private def onBoundary(base: SemVer, axis: Int): Boolean = axis match
    case 0 => base.minor.value == 0 && base.patch.value == 0
    case 1 => base.patch.value == 0
    case _ => true

  private def bump(base: SemVer, axis: Int): SemVer = axis match
    case 0 => SemVer(base.major.increment, Minor.reset, Patch.reset)
    case 1 => SemVer(base.major, base.minor.increment, Patch.reset)
    case _ => SemVer(base.major, base.minor, base.patch.increment)

  private def assign(base: SemVer, axis: Int, value: Long): Either[VersionError, SemVer] = axis match
    case 0 => Major.of(value).map(m => SemVer(m, Minor.reset, Patch.reset))
    case 1 => Minor.of(value).map(m => SemVer(base.major, m, Patch.reset))
    case _ => Patch.of(value).map(p => SemVer(base.major, base.minor, p))

  private def axis(component: String): Either[VersionError, Int] = component match
    case "major" => Right(0)
    case "minor" => Right(1)
    case "patch" => Right(2)
    case _       => Left(UnsupportedComponent("semver", component))

  private def differs[A](x: Option[A], y: Option[A])(using CanEqual[A, A]): Boolean = (x, y) match
    case (Some(a), Some(b)) => a != b
    case (None, None)       => false
    case _                  => true

  // A branch label is emitted as one build-metadata identifier, whose grammar admits only `[0-9A-Za-z-]`, so
  // anything else is replaced and runs are collapsed to keep it a single identifier. A label left with nothing
  // usable becomes `detached`, which is also what an absent branch yields. The label itself is never altered - it
  // stays in DevelopmentMetadata for callers that want it.
  private def sanitiseBranchIdentifier(name: String): String =
    // scalafix:off DisableSyntax.var
    // A var tracker drops the per-character boxing a fold-with-tuple shape would incur.
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

end SemVer
