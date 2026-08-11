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

import munit.FunSuite

import scala.compiletime.testing.typeChecks
import scala.math.Ordering.Implicits.seqOrdering

import version.errors.InvalidVersionFormat
import version.errors.VersionError
import version.semver.SemVer

// A scheme that orders its values but prescribes no advancement, compatibility, or release workflow: the shape of
// those schemes whose bump is a matter of policy rather than of the scheme's own algebra.
final case class Sequence(parts: List[Long])

object Sequence:
  given CanEqual[Sequence, Sequence] = CanEqual.derived

  given VersionScheme[Sequence]:
    def name: String = "sequence"

    def parse(input: String): Either[VersionError, Sequence] =
      val parts = input.split('.').toList
      if parts.nonEmpty && parts.forall(p => p.nonEmpty && p.forall(c => c >= '0' && c <= '9'))
      then Right(Sequence(parts.map(_.toLong)))
      else Left(InvalidVersionFormat(input))

    def precedence: Ordering[Sequence] = Ordering.by(_.parts)

    def difference(a: Sequence, b: Sequence): Difference =
      a.parts.zipAll(b.parts, 0L, 0L).indexWhere((x, y) => x != y) match
        case -1 => Difference.None
        case i  => Difference.Release(i)

    extension (v: Sequence)
      def show: String = v.parts.mkString(".")
      def stable: Boolean = true
      def release: Sequence = v
      def numbers: IArray[Long] = IArray.from(v.parts)
end Sequence

// The shape a scheme registry would hand out: a scheme paired with its value type, usable without naming that type.
trait SchemeHandle:
  type V
  def scheme: VersionScheme[V]

object SchemeHandle:
  def apply[A](using s: VersionScheme[A]): SchemeHandle =
    new SchemeHandle:
      type V = A
      def scheme: VersionScheme[A] = s

class SchemeCapabilitySuite extends FunSuite:

  private val handles: List[(SchemeHandle, String)] = List(
    SchemeHandle[SemVer] -> "1.2.3-rc.1+sha.5114f85",
    SchemeHandle[Sequence] -> "1.2.3.4"
  )

  private def roundTrips(handle: SchemeHandle, input: String): Boolean =
    handle.scheme.parse(input) match
      case Left(_)      => false
      case Right(value) =>
        handle.scheme.parse(handle.scheme.show(value)) match
          case Right(reparsed) => handle.scheme.precedence.compare(value, reparsed) == 0
          case Left(_)         => false

  test("a scheme with no advancement algebra supplies no arithmetic instance") {
    assert(!typeChecks("summon[version.VersionArithmetic[version.Sequence]]"))
  }

  test("a scheme with an advancement algebra supplies one") {
    assert(typeChecks("summon[version.VersionArithmetic[version.semver.SemVer]]"))
  }

  test("a scheme with no release workflow supplies no workflow instance") {
    assert(!typeChecks("summon[version.ResolvableScheme[version.Sequence]]"))
    assert(typeChecks("summon[version.ResolvableScheme[version.semver.SemVer]]"))
  }

  test("no compatibility policy is given, so a consumer must name the rule it means") {
    assert(!typeChecks("summon[version.CompatibilityPolicy[version.semver.SemVer]]"))
    assert(typeChecks("version.semver.SemVer.Compatibility.leftmostNonZero"))
  }

  test("a handle parses, renders, and reparses without its value type being named") {
    handles.foreach((handle, input) => assert(roundTrips(handle, input), s"round trip of '$input'"))
  }

  test("each handle reports the name its scheme is selected by") {
    assertEquals(handles.map(_._1.scheme.name), List("semver", "sequence"))
  }

  test("a version string alone cannot identify its scheme") {
    assert(handles.forall((handle, _) => handle.scheme.parse("1.0.0").isRight))
  }

end SchemeCapabilitySuite
