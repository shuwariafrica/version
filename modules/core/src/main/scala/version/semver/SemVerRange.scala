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

import scala.annotation.tailrec
import scala.annotation.targetName
import scala.util.boundary
import scala.util.boundary.break

import version.RangeScheme
import version.Strategy
import version.errors.InvalidRangeFormat
import version.errors.RangeError
import version.errors.UnsupportedRewrite
import version.errors.VersionError

/** The character a wildcard position was written with: `x`, `X` or `*`. */
enum Wildcard derives CanEqual:
  case Lower, Upper, Star

/** One position of a [[Partial]]. */
enum Atom derives CanEqual:
  case Number(value: Long)
  case Any(spelling: Wildcard)

/** A version as a range names it: one, two or three positions, and a pre-release only where all three are written.
  *
  * A position left off constrains exactly as a written wildcard does, and differs from one only in rendering.
  */
final case class Partial(major: Atom, minor: Option[Atom], patch: Option[Atom], preRelease: Option[PreRelease]) derives CanEqual

enum Operator derives CanEqual:
  case Equal, Less, LessOrEqual, Greater, GreaterOrEqual

/** One conjunct of a range, holding the construct its author wrote rather than the bounds it stands for. */
enum Term derives CanEqual:
  case Bound(operator: Operator, partial: Partial)
  case Plain(partial: Partial)
  case Tilde(partial: Partial)
  case Caret(partial: Partial)

/** One alternative of a range: the grammar admits a hyphen range or a conjunction of terms, never a mixture.
  *
  * An empty conjunction constrains nothing - what an empty range reads as, and what every any-range desugars to.
  */
enum Clause derives CanEqual:
  case Hyphen(from: Partial, to: Partial)
  case Conjunction(terms: List[Term])

/** A range in the language npm publishes for Semantic Versioning: alternatives separated by `||`, each holding the
  * constructs and spellings its author chose rather than the interval they denote.
  *
  * Instances may be constructed via [[SemVerRange$ SemVerRange]].
  */
final case class SemVerRange(clauses: List[Clause]) derives CanEqual

/** Provides the parser and the [[version.RangeScheme RangeScheme]] instance for [[SemVerRange]]. */
object SemVerRange:

  /** Reads a range, naming the fragment it stopped at where the input is not one. */
  def parse(input: String): Either[RangeError, SemVerRange] =
    traverse(alternatives(input))(parseClause(_, input)).map(SemVerRange.apply)

  given scheme: RangeScheme[SemVer, SemVerRange]:

    def parse(input: String): Either[VersionError, SemVerRange] = SemVerRange.parse(input)

    extension (range: SemVerRange)

      def show: String = range.clauses.map(clause => render(clause)).mkString(" || ")

      def desugar: SemVerRange = SemVerRange(range.clauses.map(clause => Clause.Conjunction(clauseBounds(clause))))

      def admits(version: SemVer): Boolean = range.clauses.exists(clause => clauseAdmits(clause, version))

      def exact: Option[SemVer] = range.clauses match
        case Clause.Conjunction(Term.Plain(p) :: Nil) :: Nil                 => complete(p)
        case Clause.Conjunction(Term.Bound(Operator.Equal, p) :: Nil) :: Nil => complete(p)
        case _                                                               => None

      @targetName("ext_rewrite")
      def rewrite(strategy: Strategy, version: SemVer): Either[VersionError, SemVerRange] =
        val proposed = strategy match
          case Strategy.Pin     => pinned(version)
          case Strategy.Raise   => SemVerRange(lastMapped(range.clauses, raiseClause(_, version)))
          case Strategy.Replace =>
            if range.admits(version) then range
            else SemVerRange(lastMapped(range.clauses, replaceClause(_, version)))
          case Strategy.Widen =>
            if range.admits(version) then range
            else
              range.clauses.lastOption match
                // An endpoint may only be moved outward for a version lying outside the clause's ordered bounds. One
                // refused solely by the pre-release rule already lies within them, and moving an endpoint to reach it
                // would drop everything beyond - so the range gains an alternative instead.
                case Some(last) if extensible(last) && !ordered(last, version) =>
                  SemVerRange(lastMapped(range.clauses, replaceClause(_, version)))
                case Some(last) => SemVerRange(range.clauses :+ replaceClause(last, version))
                case None       => pinned(version)
        if proposed.admits(version) then Right(proposed) else Left(UnsupportedRewrite(range.show, strategy))
      end rewrite
    end extension
  end scheme

  private val precedence: Ordering[SemVer] = summon[Ordering[SemVer]]

  private val lowestPreRelease: PreRelease = PreRelease(List("0"))

  private def core(major: Long, minor: Long, patch: Long): SemVer =
    SemVer(Major.wrap(major), Minor.wrap(minor), Patch.wrap(patch))

  // Every synthesised exclusive ceiling carries the lowest pre-release there is, so that `<2.0.0-0` excludes
  // `2.0.0-alpha` as well as `2.0.0`.
  private def zeroPre(major: Long, minor: Long, patch: Long): SemVer =
    SemVer(Major.wrap(major), Minor.wrap(minor), Patch.wrap(patch), lowestPreRelease)

  private def bound(operator: Operator, version: SemVer): Term.Bound = Term.Bound(operator, partial(version))

  private val nullSet: List[Term.Bound] = List(bound(Operator.Less, zeroPre(0, 0, 0)))

  // The version one step above `major.minor.patch` at `position`, carrying leftwards where the carrier at that
  // position has no room: `1.<max>.0` steps to `2.0.0`, the least representable version above the exhausted line and
  // so the boundary the construct meant. `None` where the carry runs off the major, beyond which nothing exists.
  @tailrec
  private def successor(major: Long, minor: Long, patch: Long, position: Int): Option[(Long, Long, Long)] =
    position match
      case 0 => Option.when(major != Long.MaxValue)((major + 1, 0L, 0L))
      case 1 => if minor != Long.MaxValue then Some((major, minor + 1, 0L)) else successor(major, minor, patch, 0)
      case _ => if patch != Long.MaxValue then Some((major, minor, patch + 1)) else successor(major, minor, patch, 1)

  // No ceiling at all where the successor runs off the major: nothing representable lies beyond it, so the floor
  // alone admits exactly the versions the ceiling was there to keep in.
  private def ceiling(major: Long, minor: Long, patch: Long, position: Int): List[Term.Bound] =
    successor(major, minor, patch, position).map((a, b, c) => bound(Operator.Less, zeroPre(a, b, c))).toList

  // Strictly above a bound whose successor runs off the major is a set with nothing representable in it.
  private def above(major: Long, minor: Long, patch: Long, position: Int): List[Term.Bound] =
    successor(major, minor, patch, position)
      .fold(nullSet)((a, b, c) => List(bound(Operator.GreaterOrEqual, core(a, b, c))))

  private def pinned(version: SemVer): SemVerRange =
    SemVerRange(List(Clause.Conjunction(List(Term.Plain(partial(version))))))

  private def partial(version: SemVer): Partial =
    Partial(
      Atom.Number(version.major.value),
      Some(Atom.Number(version.minor.value)),
      Some(Atom.Number(version.patch.value)),
      version.preRelease
    )

  private def partial(major: Long, minor: Long, patch: Long): Partial =
    Partial(Atom.Number(major), Some(Atom.Number(minor)), Some(Atom.Number(patch)), None)

  private def alternatives(input: String): List[String] =
    @tailrec def loop(from: Int, acc: List[String]): List[String] =
      input.indexOf("||", from) match
        case -1 => (input.substring(from) :: acc).reverse
        case i  => loop(i + 2, input.substring(from, i) :: acc)
    loop(0, Nil)

  private def parseClause(text: String, whole: String): Either[RangeError, Clause] =
    val trimmed = text.trim
    if trimmed.isEmpty then Right(Clause.Conjunction(Nil))
    else
      hyphenated(trimmed) match
        case Some((from, to)) =>
          for
            lower <- parsePartial(from, whole)
            upper <- parsePartial(to, whole)
          yield Clause.Hyphen(lower, upper)
        case None => traverse(tokens(trimmed))(parseTerm(_, whole)).map(Clause.Conjunction.apply)

  private def hyphenated(clause: String): Option[(String, String)] =
    clause.indexOf(" - ") match
      case -1 => None
      case i  => Some((clause.substring(0, i), clause.substring(i + 3)))

  // `>= 1.2.3` is one comparator its author spaced, so a token of comparison characters alone binds to the token
  // after it; whitespace anywhere else separates conjuncts.
  private def tokens(clause: String): List[String] =
    @tailrec def glue(parts: List[String], acc: List[String]): List[String] = parts match
      case Nil                                             => acc.reverse
      case head :: next :: rest if head.forall(comparator) => glue(s"$head$next" :: rest, acc)
      case head :: rest                                    => glue(rest, head :: acc)
    glue(words(clause), Nil)

  private inline def comparator(c: Char): Boolean =
    c == '<' || c == '>' || c == '=' || c == '^' || c == '~'

  private def words(text: String): List[String] =
    @tailrec def loop(from: Int, acc: List[String]): List[String] =
      if from >= text.length then acc.reverse
      else if text.charAt(from).isWhitespace then loop(from + 1, acc)
      else
        val end = text.indexWhere(_.isWhitespace, from) match
          case -1 => text.length
          case i  => i
        loop(end, text.substring(from, end) :: acc)
    loop(0, Nil)

  private def parseTerm(token: String, whole: String): Either[RangeError, Term] =
    if token.startsWith("^") then parsePartial(token.drop(1), whole).map(Term.Caret.apply)
    // The published grammar spells the tilde with nothing after it. node-semver silently reads `~>` as `~` where
    // renovate reads it as a distinct operator, so accepting it would mean choosing one of two live readings.
    else if token.startsWith("~>") then Left(InvalidRangeFormat(whole, token))
    else if token.startsWith("~") then parsePartial(token.drop(1), whole).map(Term.Tilde.apply)
    else if token.startsWith(">=") then parsePartial(token.drop(2), whole).map(Term.Bound(Operator.GreaterOrEqual, _))
    else if token.startsWith("<=") then parsePartial(token.drop(2), whole).map(Term.Bound(Operator.LessOrEqual, _))
    else if token.startsWith(">") then parsePartial(token.drop(1), whole).map(Term.Bound(Operator.Greater, _))
    else if token.startsWith("<") then parsePartial(token.drop(1), whole).map(Term.Bound(Operator.Less, _))
    else if token.startsWith("=") then parsePartial(token.drop(1), whole).map(Term.Bound(Operator.Equal, _))
    else parsePartial(token, whole).map(Term.Plain.apply)

  private def parsePartial(text: String, whole: String): Either[RangeError, Partial] =
    val fragment = text.trim
    val body = fragment.dropWhile(c => c == 'v' || c == 'V' || c == '=' || c.isWhitespace)
    val invalid = InvalidRangeFormat(whole, fragment)
    val cut = qualifierStart(body)
    positions(if cut < 0 then body else body.substring(0, cut)) match
      case None                        => Left(invalid)
      case Some((major, minor, patch)) =>
        if !leftToRight(major, minor, patch) then Left(invalid)
        // A partial naming all three positions in numbers is a version, and is read as one so that the two grammars
        // cannot drift apart. Build metadata is discarded: precedence excludes it, so it cannot bound anything.
        else if numbered(major, minor, patch) then Parser.parse(body).map(v => partial(v)).left.map(_ => invalid)
        else if cut < 0 then Right(Partial(major, minor, patch, None))
        // The grammar hangs a qualifier off the third position and nowhere else.
        else if patch.isEmpty then Left(invalid)
        else
          val tail = body.substring(cut)
          val plus = tail.indexOf('+'.toInt)
          val pre = Option.when(tail.charAt(0) == '-')(if plus < 0 then tail.drop(1) else tail.substring(1, plus))
          val build = Option.when(plus >= 0)(tail.substring(plus + 1))
          val label = pre.map(t => PreRelease.of(Parser.segments(t)).toOption)
          val metadata = build.map(t => Metadata.of(Parser.segments(t)).toOption)
          if label.exists(_.isEmpty) || metadata.exists(_.isEmpty) then Left(invalid)
          else Right(Partial(major, minor, patch, label.flatten))
  end parsePartial

  private def qualifierStart(body: String): Int =
    val dash = body.indexOf('-'.toInt)
    val plus = body.indexOf('+'.toInt)
    if dash < 0 then plus else if plus < 0 || dash < plus then dash else plus

  private def positions(core: String): Option[(Atom, Option[Atom], Option[Atom])] =
    Parser.segments(core) match
      case a :: Nil           => atom(a).map((_, None, None))
      case a :: b :: Nil      => for x <- atom(a); y <- atom(b) yield (x, Some(y), None)
      case a :: b :: c :: Nil => for x <- atom(a); y <- atom(b); z <- atom(c) yield (x, Some(y), Some(z))
      case _                  => None

  private def atom(segment: String): Option[Atom] = segment match
    case "x"                                                      => Some(Atom.Any(Wildcard.Lower))
    case "X"                                                      => Some(Atom.Any(Wildcard.Upper))
    case "*"                                                      => Some(Atom.Any(Wildcard.Star))
    case s if Identifier.numeric(s) && !Identifier.leadingZero(s) => s.toLongOption.map(Atom.Number.apply)
    case _                                                        => None

  // `1.x.2` names a patch below a wildcard minor, which the grammar has no reading for.
  private def leftToRight(major: Atom, minor: Option[Atom], patch: Option[Atom]): Boolean =
    !(wild(major) && minor.exists(a => !wild(a))) && !(minor.exists(wild) && patch.exists(a => !wild(a)))

  private def numbered(major: Atom, minor: Option[Atom], patch: Option[Atom]): Boolean =
    !wild(major) && minor.exists(a => !wild(a)) && patch.exists(a => !wild(a))

  private def wild(a: Atom): Boolean = a match
    case Atom.Any(_)    => true
    case Atom.Number(_) => false

  private def number(a: Atom): Option[Long] = a match
    case Atom.Number(value) => Some(value)
    case Atom.Any(_)        => None

  private def openMajor(p: Partial): Boolean = wild(p.major)
  private def openMinor(p: Partial): Boolean = openMajor(p) || p.minor.forall(wild)
  private def openPatch(p: Partial): Boolean = openMinor(p) || p.patch.forall(wild)

  private def majorValue(p: Partial): Long = number(p.major).getOrElse(0L)
  private def minorValue(p: Partial): Long = p.minor.flatMap(number).getOrElse(0L)
  private def patchValue(p: Partial): Long = p.patch.flatMap(number).getOrElse(0L)

  private def arity(p: Partial): Int = if p.patch.isDefined then 3 else if p.minor.isDefined then 2 else 1

  private def exactly(p: Partial): SemVer =
    SemVer(Major.wrap(majorValue(p)), Minor.wrap(minorValue(p)), Patch.wrap(patchValue(p)), p.preRelease, None)

  private def complete(p: Partial): Option[SemVer] = Option.unless(openPatch(p))(exactly(p))

  private def traverse[A, B](items: List[A])(read: A => Either[RangeError, B]): Either[RangeError, List[B]] =
    boundary:
      Right(items.map { item =>
        read(item) match
          case Right(value) => value
          case Left(error)  => break(Left(error))
      })

  private def caretBounds(p: Partial): List[Term.Bound] =
    if openMajor(p) then Nil
    else if openMinor(p) then bound(Operator.GreaterOrEqual, core(majorValue(p), 0, 0)) :: ceiling(majorValue(p), 0, 0, 0)
    else if openPatch(p) then
      if majorValue(p) == 0 then bound(Operator.GreaterOrEqual, core(0, minorValue(p), 0)) :: ceiling(0, minorValue(p), 0, 1)
      else bound(Operator.GreaterOrEqual, core(majorValue(p), minorValue(p), 0)) :: ceiling(majorValue(p), 0, 0, 0)
    else
      val top =
        if majorValue(p) != 0 then ceiling(majorValue(p), 0, 0, 0)
        else if minorValue(p) != 0 then ceiling(0, minorValue(p), 0, 1)
        else ceiling(0, 0, patchValue(p), 2)
      bound(Operator.GreaterOrEqual, exactly(p)) :: top

  private def tildeBounds(p: Partial): List[Term.Bound] =
    if openMajor(p) then Nil
    else if openMinor(p) then bound(Operator.GreaterOrEqual, core(majorValue(p), 0, 0)) :: ceiling(majorValue(p), 0, 0, 0)
    else
      val floor = if openPatch(p) then core(majorValue(p), minorValue(p), 0) else exactly(p)
      bound(Operator.GreaterOrEqual, floor) :: ceiling(majorValue(p), minorValue(p), 0, 1)

  private def plainBounds(p: Partial): List[Term.Bound] =
    if openMajor(p) then Nil
    else if openMinor(p) then bound(Operator.GreaterOrEqual, core(majorValue(p), 0, 0)) :: ceiling(majorValue(p), 0, 0, 0)
    else if openPatch(p) then
      bound(Operator.GreaterOrEqual, core(majorValue(p), minorValue(p), 0)) :: ceiling(majorValue(p), minorValue(p), 0, 1)
    else List(bound(Operator.Equal, exactly(p)))

  private def comparatorBounds(operator: Operator, p: Partial): List[Term.Bound] =
    if !openPatch(p) then List(Term.Bound(operator, p))
    // Under a wildcard major a strict comparison admits nothing and an inclusive one forbids nothing.
    else if openMajor(p) then
      operator match
        case Operator.Greater | Operator.Less => nullSet
        case _                                => Nil
    else
      operator match
        case Operator.Greater =>
          if openMinor(p) then above(majorValue(p), 0, 0, 0) else above(majorValue(p), minorValue(p), 0, 1)
        case Operator.LessOrEqual =>
          if openMinor(p) then ceiling(majorValue(p), 0, 0, 0) else ceiling(majorValue(p), minorValue(p), 0, 1)
        case Operator.Less           => List(bound(Operator.Less, zeroPre(majorValue(p), minorValue(p), 0)))
        case Operator.GreaterOrEqual => List(bound(Operator.GreaterOrEqual, core(majorValue(p), minorValue(p), 0)))
        case Operator.Equal          => plainBounds(p)

  private def hyphenBounds(from: Partial, to: Partial): List[Term.Bound] =
    val floor =
      if openMajor(from) then Nil
      else if openMinor(from) then List(bound(Operator.GreaterOrEqual, core(majorValue(from), 0, 0)))
      else if openPatch(from) then List(bound(Operator.GreaterOrEqual, core(majorValue(from), minorValue(from), 0)))
      else List(bound(Operator.GreaterOrEqual, exactly(from)))
    val top =
      if openMajor(to) then Nil
      else if openMinor(to) then ceiling(majorValue(to), 0, 0, 0)
      else if openPatch(to) then ceiling(majorValue(to), minorValue(to), 0, 1)
      else List(bound(Operator.LessOrEqual, exactly(to)))
    floor ++ top

  private def termBounds(t: Term): List[Term.Bound] = t match
    case Term.Caret(p)           => caretBounds(p)
    case Term.Tilde(p)           => tildeBounds(p)
    case Term.Plain(p)           => plainBounds(p)
    case Term.Bound(operator, p) => comparatorBounds(operator, p)

  private def clauseBounds(c: Clause): List[Term.Bound] = c match
    case Clause.Hyphen(from, to) => hyphenBounds(from, to)
    case Clause.Conjunction(ts)  => ts.flatMap(termBounds)

  private def satisfies(version: SemVer, operator: Operator, limit: SemVer): Boolean =
    val comparison = precedence.compare(version, limit)
    operator match
      case Operator.Equal          => comparison == 0
      case Operator.Less           => comparison < 0
      case Operator.LessOrEqual    => comparison <= 0
      case Operator.Greater        => comparison > 0
      case Operator.GreaterOrEqual => comparison >= 0

  private def within(bounds: List[Term.Bound], version: SemVer): Boolean =
    bounds.forall(b => complete(b.partial).exists(limit => satisfies(version, b.operator, limit)))

  private def ordered(c: Clause, version: SemVer): Boolean = within(clauseBounds(c), version)

  private def clauseAdmits(c: Clause, version: SemVer): Boolean =
    val bounds = clauseBounds(c)
    within(bounds, version) && (version.preRelease.isEmpty || bounds.exists(b => optedIn(b.partial, version)))

  // A pre-release candidate is admitted only where a comparator of the same conjunction carries a pre-release at
  // identical numbers, whichever end it bounds. This is what makes membership something other than ordered
  // containment, and node-semver and Rust's resolver reached it independently.
  private def optedIn(p: Partial, version: SemVer): Boolean =
    p.preRelease.nonEmpty && majorValue(p) == version.major.value && minorValue(p) == version.minor.value &&
      patchValue(p) == version.patch.value

  private def align(p: Partial, version: SemVer): Partial =
    def moved(a: Atom, n: Long): Atom = a match
      case wildcard @ Atom.Any(_) => wildcard
      case Atom.Number(_)         => Atom.Number(n)
    val aligned = Partial(
      moved(p.major, version.major.value),
      p.minor.map(moved(_, version.minor.value)),
      p.patch.map(moved(_, version.patch.value)),
      None
    )
    // A pre-release has no reading above a wildcard, so it rides only a partial naming all three positions.
    if openPatch(aligned) then aligned else aligned.copy(preRelease = version.preRelease)

  // The author who wrote `<2.0.0` marked a major boundary and the author who wrote `<2.3.1` a patch one, so the
  // trailing zeros of an exclusive ceiling say at what precision it moves.
  // `None` where the raised ceiling would carry off the major, which drops the term rather than naming a bound
  // nothing can reach - the same omission the desugared ceilings make.
  private def raiseExclusive(p: Partial, version: SemVer): Option[Partial] =
    val position =
      if arity(p) == 1 then 0
      else if arity(p) == 2 then 1
      else if patchValue(p) == 0 && minorValue(p) == 0 then 0
      else if patchValue(p) == 0 then 1
      else 2
    successor(version.major.value, version.minor.value, version.patch.value, position).map { (major, minor, patch) =>
      arity(p) match
        case 1 => Partial(Atom.Number(major), None, None, None)
        case 2 => Partial(Atom.Number(major), Some(Atom.Number(minor)), None, None)
        case _ => partial(major, minor, patch)
    }

  private def replaceTerm(t: Term, version: SemVer): Option[Term] = t match
    case Term.Caret(p)           => Some(Term.Caret(align(p, version)))
    case Term.Tilde(p)           => Some(Term.Tilde(align(p, version)))
    case Term.Plain(p)           => Some(Term.Plain(align(p, version)))
    case Term.Bound(operator, p) =>
      if complete(p).exists(limit => satisfies(version, operator, limit)) then Some(t)
      else if operator == Operator.Less then raiseExclusive(p, version).map(Term.Bound(operator, _))
      else Some(Term.Bound(operator, align(p, version)))

  private def replaceClause(c: Clause, version: SemVer): Clause =
    if clauseAdmits(c, version) then c
    else
      c match
        case Clause.Hyphen(from, to) =>
          if complete(from).forall(limit => precedence.lt(version, limit)) then Clause.Hyphen(align(from, version), to)
          else Clause.Hyphen(from, align(to, version))
        case Clause.Conjunction(ts) => Clause.Conjunction(ts.flatMap(replaceTerm(_, version)))

  private def raiseTerm(t: Term, version: SemVer): Term = t match
    case Term.Caret(p)                              => Term.Caret(align(p, version))
    case Term.Tilde(p)                              => Term.Tilde(align(p, version))
    case Term.Plain(p)                              => Term.Plain(align(p, version))
    case Term.Bound(operator, _) if upper(operator) => t
    case Term.Bound(operator, p)                    => Term.Bound(operator, align(p, version))

  // Raising a floor onto an exclusive bound, or past a ceiling, leaves a clause that no longer admits what it was
  // raised for; the replacement rewrite is the answer in those shapes.
  private def raiseClause(c: Clause, version: SemVer): Clause =
    val raised = c match
      case Clause.Hyphen(from, to) => Clause.Hyphen(align(from, version), to)
      case Clause.Conjunction(ts)  => Clause.Conjunction(ts.map(raiseTerm(_, version)))
    if clauseAdmits(raised, version) then raised else replaceClause(c, version)

  private def lower(o: Operator): Boolean = o match
    case Operator.Greater | Operator.GreaterOrEqual => true
    case _                                          => false

  private def upper(o: Operator): Boolean = o match
    case Operator.Less | Operator.LessOrEqual => true
    case _                                    => false

  // A clause built only from endpoints can be widened by moving one; a clause naming a whole band - a caret, a tilde,
  // an x-range - cannot be extended without saying something else.
  private def extensible(c: Clause): Boolean = c match
    case Clause.Hyphen(_, _)    => true
    case Clause.Conjunction(ts) =>
      ts.nonEmpty && ts.forall:
        case Term.Bound(operator, _) => lower(operator) || upper(operator)
        case _                       => false

  // Rewriting every alternative of `^1.0.0 || ^2.0.0` for `3.0.0` would yield `^3.0.0 || ^3.0.0`. Authors append.
  private def lastMapped(clauses: List[Clause], rewrite: Clause => Clause): List[Clause] =
    clauses match
      case Nil => Nil
      case _   => clauses.init :+ rewrite(clauses.last)

  private def render(w: Wildcard): String = w match
    case Wildcard.Lower => "x"
    case Wildcard.Upper => "X"
    case Wildcard.Star  => "*"

  private def render(a: Atom): String = a match
    case Atom.Number(value) => value.toString
    case Atom.Any(spelling) => render(spelling)

  private def render(p: Partial): String =
    val tail = (p.minor, p.patch) match
      case (Some(minor), Some(patch)) => s".${render(minor)}.${render(patch)}"
      case (Some(minor), None)        => s".${render(minor)}"
      case _                          => ""
    s"${render(p.major)}$tail${p.preRelease.fold("")(pre => s"-${pre.show}")}"

  private def render(o: Operator): String = o match
    case Operator.Equal          => "="
    case Operator.Less           => "<"
    case Operator.LessOrEqual    => "<="
    case Operator.Greater        => ">"
    case Operator.GreaterOrEqual => ">="

  private def render(t: Term): String = t match
    case Term.Bound(operator, p) => s"${render(operator)}${render(p)}"
    case Term.Plain(p)           => render(p)
    case Term.Tilde(p)           => s"~${render(p)}"
    case Term.Caret(p)           => s"^${render(p)}"

  private def render(c: Clause): String = c match
    case Clause.Hyphen(from, to) => s"${render(from)} - ${render(to)}"
    case Clause.Conjunction(ts)  => ts.map(term => render(term)).mkString(" ")

end SemVerRange
