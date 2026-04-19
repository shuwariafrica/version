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

/** Supported pre-release classifiers in order of precedence (lowest to highest).
  *
  * Declaration order defines precedence.
  *
  * @see
  *   [[PreReleaseClassifier$ PreReleaseClassifier]] companion for behaviour.
  */
enum PreReleaseClassifier:
  case Dev, Milestone, Alpha, Beta, ReleaseCandidate, Snapshot

/** Provides behaviour, instances, and utilities for [[PreReleaseClassifier]].
  *
  * @see
  *   [[PreReleaseClassifier]] enum for case definitions.
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

  private val aliasMap: Map[String, PreReleaseClassifier] =
    PreReleaseClassifier.values.flatMap(c => c.aliases.map(_.toLowerCase -> c)).toMap

  /** Attempts to find a [[PreReleaseClassifier]] corresponding to the given string alias.
    *
    * @param alias
    *   The string alias (case-insensitive).
    */
  inline def fromAlias(alias: String): Option[PreReleaseClassifier] =
    import boilerplate.nullable.*
    alias.toLowerCase.option.flatMap(aliasMap.get)

  /** Provides an extractor for matching string aliases. */
  inline def unapply(alias: String): Option[PreReleaseClassifier] = fromAlias(alias)

  given Ordering[PreReleaseClassifier] = Ordering.by(_.ordinal)
  given CanEqual[PreReleaseClassifier, PreReleaseClassifier] = CanEqual.derived
end PreReleaseClassifier

/** Structured pre-release version information.
  *
  * Instances are constructed via [[PreRelease$ PreRelease]] companion factory methods.
  *
  * @param classifier
  *   The type of pre-release.
  * @param number
  *   The version number associated with the classifier, if applicable.
  */
final case class PreRelease private (
  classifier: PreReleaseClassifier,
  number: Option[PreReleaseNumber]
)

/** Provides factory methods, instances, and operations for [[PreRelease]]. */
object PreRelease:

  import version.errors.InvalidQualifierCombination
  import version.errors.MissingQualifierNumber
  import version.errors.UnexpectedQualifierNumber

  /** Safe construction with validation. */
  def from(
    classifier: PreReleaseClassifier,
    number: Option[PreReleaseNumber]
  ): Either[InvalidQualifierCombination, PreRelease] =
    if classifier.versioned && number.isEmpty then Left(MissingQualifierNumber(classifier.show))
    else if !classifier.versioned && number.nonEmpty then Left(UnexpectedQualifierNumber(classifier.show, number.get.value))
    else Right(PreRelease(classifier, number))

  /** Unsafe construction - throws on validation failure. */
  def fromUnsafe(
    classifier: PreReleaseClassifier,
    number: Option[PreReleaseNumber]
  ): PreRelease =
    from(classifier, number) match
      case Right(pr) => pr
      case Left(err) => throw err // scalafix:ok

  val snapshot: PreRelease = PreRelease(PreReleaseClassifier.Snapshot, None)

  def dev(number: PreReleaseNumber): PreRelease = PreRelease(PreReleaseClassifier.Dev, Some(number))
  def milestone(number: PreReleaseNumber): PreRelease = PreRelease(PreReleaseClassifier.Milestone, Some(number))
  def alpha(number: PreReleaseNumber): PreRelease = PreRelease(PreReleaseClassifier.Alpha, Some(number))
  def beta(number: PreReleaseNumber): PreRelease = PreRelease(PreReleaseClassifier.Beta, Some(number))
  def releaseCandidate(number: PreReleaseNumber): PreRelease =
    PreRelease(PreReleaseClassifier.ReleaseCandidate, Some(number))

  extension (pr: PreRelease)
    /** Returns the SemVer-compliant string form (e.g., "alpha.1", "snapshot"). */
    inline def show: String =
      pr.number.fold(pr.classifier.show)(n => s"${pr.classifier.show}.${n.value}")

    /** Returns a new [[PreRelease]] with the number incremented, if versioned. */
    inline def increment: PreRelease =
      if pr.classifier.versioned then PreRelease(pr.classifier, pr.number.map(_.increment))
      else pr

    inline def isDev: Boolean = pr.classifier.equals(PreReleaseClassifier.Dev)
    inline def isMilestone: Boolean = pr.classifier.equals(PreReleaseClassifier.Milestone)
    inline def isAlpha: Boolean = pr.classifier.equals(PreReleaseClassifier.Alpha)
    inline def isBeta: Boolean = pr.classifier.equals(PreReleaseClassifier.Beta)
    inline def isReleaseCandidate: Boolean = pr.classifier.equals(PreReleaseClassifier.ReleaseCandidate)
    inline def isSnapshot: Boolean = pr.classifier.equals(PreReleaseClassifier.Snapshot)
  end extension

  given Ordering[PreRelease] = Ordering.by(pr => (pr.classifier, pr.number))

  given CanEqual[PreRelease, PreRelease] = CanEqual.derived

  /** Strategy for mapping raw SemVer pre-release identifiers to the structured [[PreRelease]] type.
    *
    * Enables pluggable behaviour for interpreting non-standard pre-release formats.
    */
  trait Resolver:
    extension (identifiers: List[String]) def resolve: Option[PreRelease]

  object Resolver:
    given Resolver:
      extension (identifiers: List[String])
        def resolve: Option[PreRelease] =
          identifiers match
            case List(PreReleaseClassifier(c)) if !c.versioned =>
              Some(PreRelease(c, None))
            case List(PreReleaseClassifier(c), n) if c.versioned =>
              n.toIntOption.flatMap(i => PreReleaseNumber.from(i).toOption).map(num => PreRelease(c, Some(num)))
            case _ => None
end PreRelease
