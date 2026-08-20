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

import boilerplate.OpaqueType

import scala.annotation.tailrec

import version.errors.InvalidPreRelease
import version.errors.InvalidQualifierCombination
import version.errors.MissingQualifierNumber
import version.errors.UnexpectedQualifierNumber

/** The pre-release labels this library builds and recognises by name.
  *
  * A pre-release may carry any identifiers the specification allows; these are the ones the construction and
  * increment helpers understand.
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
    /** Whether the classifier is written with a number following it. */
    inline def versioned: Boolean = c match
      case Snapshot => false
      case _        => true

    /** Every spelling the classifier is recognised under, canonical form first. */
    inline def aliases: List[String] = c match
      case Dev              => List("dev")
      case Milestone        => List("milestone", "m")
      case Alpha            => List("alpha", "a")
      case Beta             => List("beta", "b")
      case ReleaseCandidate => List("rc", "cr")
      case Snapshot         => List("SNAPSHOT")

    /** The canonical spelling. */
    inline def show: String = c.aliases.head

  private val aliasMap: Map[String, PreReleaseClassifier] =
    PreReleaseClassifier.values.flatMap(c => c.aliases.map(_.toLowerCase -> c)).toMap

  /** The classifier spelled `alias`, matched without regard to case. */
  inline def fromAlias(alias: String): Option[PreReleaseClassifier] =
    import boilerplate.nullable.*
    alias.toLowerCase.option.flatMap(aliasMap.get)

  /** Provides an extractor for matching string aliases. */
  inline def unapply(alias: String): Option[PreReleaseClassifier] = fromAlias(alias)

  given CanEqual[PreReleaseClassifier, PreReleaseClassifier] = CanEqual.derived
end PreReleaseClassifier

/** A pre-release: a non-empty, dot-separated list of `[0-9A-Za-z-]` identifiers, ranking below the release carrying
  * the same numbers.
  *
  * Any identifier list the specification admits is representable, so labels this library has no name for - `x.7.z.92`
  * - survive parsing and ordering unchanged.
  *
  * Instances may be constructed via [[PreRelease$ PreRelease]].
  */
opaque type PreRelease = List[String]

/** Provides factory methods, instances, and operations for [[PreRelease]]. */
object PreRelease extends OpaqueType[PreRelease, List[String]], OpaqueType.Eq[PreRelease]:
  type Error = InvalidPreRelease

  protected inline def wrap(ids: List[String]): PreRelease = ids
  def unwrap(pr: PreRelease): List[String] = pr
  inline def apply(inline identifiers: List[String]): PreRelease = ofUnsafe(identifiers)

  protected inline def validate(ids: List[String]): Either[Error, List[String]] =
    if ids.nonEmpty && ids.forall(id => Identifier.valid(id) && !(Identifier.numeric(id) && Identifier.leadingZero(id)))
    then Right(ids)
    else Left(InvalidPreRelease(ids))

  /** The label marking an in-development build. */
  val snapshot: PreRelease = wrap(List(PreReleaseClassifier.Snapshot.show))

  /** The pre-release naming `classifier`, rejecting a number the classifier does not take and the absence of one it
    * requires.
    */
  def of(
    classifier: PreReleaseClassifier,
    number: Option[PreReleaseNumber]
  ): Either[InvalidQualifierCombination, PreRelease] =
    (classifier.versioned, number) match
      case (true, Some(n))  => Right(numbered(classifier, n))
      case (true, None)     => Left(MissingQualifierNumber(classifier.show))
      case (false, None)    => Right(wrap(List(classifier.show)))
      case (false, Some(n)) => Left(UnexpectedQualifierNumber(classifier.show, n.value))

  /** The pre-release naming `classifier`, throwing where [[of]] rejects the combination. */
  def ofUnsafe(classifier: PreReleaseClassifier, number: Option[PreReleaseNumber]): PreRelease =
    of(classifier, number) match
      case Right(pr) => pr
      case Left(err) => throw err // scalafix:ok

  def dev(number: PreReleaseNumber): PreRelease = numbered(PreReleaseClassifier.Dev, number)
  def milestone(number: PreReleaseNumber): PreRelease = numbered(PreReleaseClassifier.Milestone, number)
  def alpha(number: PreReleaseNumber): PreRelease = numbered(PreReleaseClassifier.Alpha, number)
  def beta(number: PreReleaseNumber): PreRelease = numbered(PreReleaseClassifier.Beta, number)
  def releaseCandidate(number: PreReleaseNumber): PreRelease = numbered(PreReleaseClassifier.ReleaseCandidate, number)

  /** The first pre-release of `classifier`, numbered from one where the classifier takes a number. */
  private[semver] def initial(classifier: PreReleaseClassifier): PreRelease =
    if classifier.versioned then numbered(classifier, PreReleaseNumber.minimum)
    else wrap(List(classifier.show))

  private def numbered(classifier: PreReleaseClassifier, number: PreReleaseNumber): PreRelease =
    wrap(List(classifier.show, number.value.toString))

  extension (pr: PreRelease)
    inline def identifiers: List[String] = unwrap(pr)

    inline def show: String = unwrap(pr).mkString(".")

    /** The classifier named by the leading identifier, where it names one. */
    def classifier: Option[PreReleaseClassifier] = unwrap(pr).headOption.flatMap(PreReleaseClassifier.fromAlias)

    /** The label with its trailing number advanced, unchanged where the label ends in no number. */
    def increment: PreRelease =
      val ids = unwrap(pr)
      ids.lastOption.flatMap(_.toLongOption).fold(pr)(n => wrap(ids.init :+ (n + 1).toString))

  given Ordering[PreRelease]:
    def compare(x: PreRelease, y: PreRelease): Int = compareIdentifiers(unwrap(x), unwrap(y))

  @tailrec
  private def compareIdentifiers(a: List[String], b: List[String]): Int = (a, b) match
    case (Nil, Nil)         => 0
    case (Nil, _)           => -1
    case (_, Nil)           => 1
    case (x :: xs, y :: ys) =>
      val c = compareIdentifier(x, y)
      if c != 0 then c else compareIdentifiers(xs, ys)

  private def compareIdentifier(a: String, b: String): Int =
    (Identifier.numeric(a), Identifier.numeric(b)) match
      // A numeric identifier carries no leading zero, so the longer of two is unambiguously the larger number.
      case (true, true)  => if a.length != b.length then Integer.compare(a.length, b.length) else a.compareTo(b)
      case (true, false) => -1
      case (false, true) => 1
      case _             => a.compareTo(b)
end PreRelease
