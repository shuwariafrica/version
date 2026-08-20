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

import version.errors.SchemeMismatch
import version.errors.VersionError
import version.semver.SemVer

class VersionedSuite extends FunSuite:

  // A second scheme over the same value type, so that the carrier is judged on the scheme it holds rather than on
  // the type of what it holds.
  private val house: VersionScheme[SemVer] = new VersionScheme[SemVer]:
    def name: String = "house"
    def parse(input: String): Either[VersionError, SemVer] = SemVer.parse(input)
    def precedence: Ordering[SemVer] = Ordering.by(_.patch.value)
    def difference(a: SemVer, b: SemVer): Difference = Difference.Build

    extension (v: SemVer)
      def show: String = s"house-${v.patch.value}"
      def stable: Boolean = true
      def release: SemVer = v
      def numbers: IArray[Long] = IArray(v.patch.value)

  private val released = SemVer.parseUnsafe("2.1.0")
  private val pending = SemVer.parseUnsafe("2.1.0-rc.1")

  test("a locally carried value is still read back at its own type") {
    val carried = Versioned(released)
    val recovered: SemVer = carried.value
    assertEquals(recovered.major.value, 2L)
  }

  test("every scheme answers for its own value, with no type named at the boundary") {
    val mixed: List[Versioned] =
      List(Versioned(pending), Versioned(released), Versioned(Sequence(List(1L, 2L))), Versioned.of(released, house))
    assertEquals(mixed.map(_.show), List("2.1.0-rc.1", "2.1.0", "1.2", "house-0"))
    assertEquals(mixed.map(_.stable), List(false, true, true, true))
    assertEquals(mixed.head.release.show, "2.1.0")
    assertEquals(mixed(1).numbers.toList, List(2L, 1L, 0L))
  }

  test("a scheme-specific derivation stays one match deep") {
    val mixed: List[Versioned] = List(Versioned(pending), Versioned(released), Versioned(Sequence(List(9L))))
    val majors = mixed.collect: carried =>
      carried.value match
        case v: SemVer if carried.stable => v.major.value
    assertEquals(majors, List(2L))
  }

  test("a version is its scheme and its value together, so one payload under two schemes is two versions") {
    assertEquals(Versioned(released), Versioned(released))
    assertNotEquals(Versioned(released), Versioned.of(released, house))
    assertEquals(Set(Versioned(released), Versioned(released), Versioned.of(released, house)).size, 2)
  }

  test("two versions of one scheme compare and differ") {
    assertEquals(Versioned(released).difference(Versioned(pending)), Right(Difference.Qualifier))
    assertEquals(Versioned(pending).comparedTo(Versioned(released)), Right(-1))
    assertEquals(Versioned.comparedTo(Versioned(released), Versioned(released)), Right(0))
  }

  test("two versions of different schemes are refused rather than compared") {
    assertEquals(
      Versioned(released).difference(Versioned.of(released, house)),
      Left(SchemeMismatch("semver", "house"))
    )
    assertEquals(
      Versioned(released).comparedTo(Versioned(Sequence(List(1L)))),
      Left(SchemeMismatch("semver", "sequence"))
    )
  }

  test("a carrier refined with further capability is still a carrier") {
    trait Advancing extends Versioned:
      def next: Versioned

    val advancing: Advancing = new Advancing:
      type V = SemVer
      val value: SemVer = released
      val scheme: VersionScheme[SemVer] = summon[VersionScheme[SemVer]]
      def next: Versioned = Versioned(SemVer.parseUnsafe("2.1.1"))

    val carried: Versioned = advancing
    assertEquals(carried.show, "2.1.0")
    assertEquals(advancing.next.show, "2.1.1")
  }

  test("a value with no scheme cannot be carried") {
    assert(!typeChecks("case class Unschemed(n: Int); version.Versioned(Unschemed(1))"))
    assert(typeChecks("version.Versioned(version.semver.SemVer.parseUnsafe(\"1.0.0\"))"))
  }

end VersionedSuite
