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
package version

import scala.annotation.targetName

import version.errors.VersionError

/** The capability of reading and reshaping the ranges a scheme's ecosystem writes, over that scheme's own range type.
  *
  * A range is the author's written form as structure, not an interval: `^1.2.3` and `>=1.2.3 <2.0.0-0` are different
  * values, because a rewrite has to give back the construct the author chose. Keyed on the version type and the range
  * type together so that a range value selects its capability without the call site naming either.
  *
  * Composed with [[VersionScheme]] through `using` rather than by extending it, so a scheme whose ecosystem writes no
  * ranges simply supplies no instance.
  *
  * @see
  *   [[RangeScheme$ RangeScheme]] companion for the uncurried call form.
  */
trait RangeScheme[V, R]:

  /** Reads a range, rejecting anything outside the scheme's grammar rather than coercing it. */
  def parse(input: String): Either[VersionError, R]

  extension (range: R)

    /** The canonical rendering within the written form: the construct and precision the author chose survive, the
      * whitespace they wrote does not. [[parse]] reads this back as an equal range.
      */
    def show: String

    /** The same range written in the scheme's primitive comparators, which admits exactly what this one admits. */
    def desugar: R

    /** Whether `version` is a member, under the scheme's own membership rule.
      *
      * Membership is not ordered containment: a scheme may refuse a version that lies between the range's bounds -
      * SemVer refuses a pre-release no comparator of the same conjunction opted into.
      */
    def admits(version: V): Boolean

    /** The version this range names outright, where it names one - which is what tells a caller the dependency is
      * pinned rather than bounded.
      */
    def exact: Option[V]

    /** This range rewritten so that it admits `version`, in the manner `strategy` asks for.
      *
      * A `Right` is guaranteed to admit `version`; where no rewrite of this written form under this strategy can, the
      * result is `Left`.
      */
    @targetName("ext_rewrite")
    def rewrite(strategy: Strategy, version: V): Either[VersionError, R]

    /** The greatest of `versions` this range admits. */
    final def highest(versions: Iterable[V])(using scheme: VersionScheme[V]): Option[V] =
      extremum(versions, scheme.precedence.gt)

    /** The least of `versions` this range admits. */
    final def lowest(versions: Iterable[V])(using scheme: VersionScheme[V]): Option[V] =
      extremum(versions, scheme.precedence.lt)

    private def extremum(versions: Iterable[V], better: (V, V) => Boolean): Option[V] =
      versions.foldLeft(Option.empty[V]): (best, candidate) =>
        if !range.admits(candidate) then best
        else best.filter(!better(candidate, _)).orElse(Some(candidate))

  end extension
end RangeScheme

/** Provides the uncurried form of [[RangeScheme]]'s multi-parameter operations. */
object RangeScheme:

  inline def rewrite[V, R](range: R, strategy: Strategy, version: V)(using
    RangeScheme[V, R]
  ): Either[VersionError, R] =
    range.rewrite(strategy, version)

  inline def highest[V, R](range: R, versions: Iterable[V])(using RangeScheme[V, R], VersionScheme[V]): Option[V] =
    range.highest(versions)

  inline def lowest[V, R](range: R, versions: Iterable[V])(using RangeScheme[V, R], VersionScheme[V]): Option[V] =
    range.lowest(versions)
