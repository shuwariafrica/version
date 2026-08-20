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

import version.errors.SchemeMismatch
import version.errors.VersionError

/** A version paired with the scheme that reads it, for the boundaries a version must cross without its type: a build
  * setting, a command's output, a registry's answer.
  *
  * Everything [[VersionScheme]] offers a value on its own is available here without naming the type, so a consumer
  * writes `v.show` rather than matching on which scheme produced it. Operations over two versions are answered only
  * where both came from the same scheme.
  *
  * Instances may be constructed via [[Versioned$ Versioned]].
  */
trait Versioned:
  type V
  def value: V
  def scheme: VersionScheme[V]

/** Provides factory methods, operations, and instances for [[Versioned]]. */
object Versioned:

  given CanEqual[Versioned, Versioned] = CanEqual.derived

  /** Carries `v` with the scheme in scope. The result keeps `value` typed, so a locally-constructed carrier needs no
    * match to be read back.
    */
  def apply[A](v: A)(using scheme: VersionScheme[A]): Versioned { type V = A } = make(v, scheme)

  /** Carries `v` with `scheme`, for a scheme held as a value rather than summoned. */
  def of[A](v: A, scheme: VersionScheme[A]): Versioned { type V = A } = make(v, scheme)

  inline def difference(a: Versioned, b: Versioned): Either[VersionError, Difference] = a.difference(b)

  inline def comparedTo(a: Versioned, b: Versioned): Either[VersionError, Int] = a.comparedTo(b)

  extension (v: Versioned)
    /** The canonical rendering, as the carried scheme writes it. */
    def show: String = v.scheme.show(v.value)

    /** Whether the value carries no below-release qualifier. */
    def stable: Boolean = v.scheme.stable(v.value)

    /** The value with every below-release qualifier stripped, still carrying its scheme. */
    def release: Versioned = make(v.scheme.release(v.value), v.scheme)

    /** The leading numeric components, for coarse bucketing. */
    def numbers: IArray[Long] = v.scheme.numbers(v.value)

    /** The most significant tier separating this version from `that`, where one scheme reads both. */
    @targetName("ext_difference")
    def difference(that: Versioned): Either[VersionError, Difference] =
      witness(v, that).map(other => v.scheme.difference(v.value, other))

    /** How this version ranks against `that` under their scheme's precedence, where one scheme reads both. */
    @targetName("ext_comparedTo")
    def comparedTo(that: Versioned): Either[VersionError, Int] =
      witness(v, that).map(other => v.scheme.precedence.compare(v.value, other))

  private def make[A](v: A, s: VersionScheme[A]): Versioned { type V = A } = new Impl(v, s)

  // Two carriers share operations only when one scheme reads both, and the scheme's name is what says so: a value
  // type may serve several schemes, so the type alone would admit a comparison neither scheme defines. The name
  // witnesses what the type cannot, and it is the only thing standing behind the cast.
  private def witness(a: Versioned, b: Versioned): Either[VersionError, a.V] =
    if a.scheme.name == b.scheme.name then Right(b.value.asInstanceOf[a.V]) // scalafix:ok DisableSyntax.asInstanceOf
    else Left(SchemeMismatch(a.scheme.name, b.scheme.name))

  // Equality is the scheme's name paired with the value: the same numbers read by two schemes are two versions,
  // because the schemes rank and render them differently.
  final private class Impl[A](val value: A, val scheme: VersionScheme[A]) extends Versioned:
    type V = A

    override def equals(that: Any): Boolean = that match
      case other: Versioned => scheme.name == other.scheme.name && value.equals(other.value)
      case _                => false

    override def hashCode: Int = scheme.name.hashCode * 31 + value.hashCode

    override def toString: String = s"Versioned(${scheme.name}, ${scheme.show(value)})"

end Versioned
