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

import munit.FunSuite

import version.Intent
import version.Request
import version.VersionArithmetic
import version.VersionScheme
import version.errors.InvalidComponent
import version.errors.UnsupportedComponent

class SemVerArithmeticSuite extends FunSuite:

  private val scheme = summon[VersionScheme[SemVer]]
  private val arithmetic = summon[VersionArithmetic[SemVer]]

  private def v(input: String): SemVer = SemVer.parseUnsafe(input)

  private def applied(base: String, request: Request): String =
    arithmetic(v(base), request) match
      case Right(result) => scheme.show(result)
      case Left(e)       => fail(s"$request against '$base' was rejected: ${e.getMessage}")

  private def advance(base: String, intent: Intent): String = applied(base, Request.Advance(intent))

  private def bump(base: String, component: String): String = applied(base, Request.Bump(component))

  private def assign(base: String, component: String, value: Long): String =
    applied(base, Request.Assign(component, value))

  test("above 1.0.0 each intent moves its own component") {
    assertEquals(advance("1.2.3", Intent.Fix), "1.2.4")
    assertEquals(advance("1.2.3", Intent.Feature), "1.3.0")
    assertEquals(advance("1.2.3", Intent.Breaking), "2.0.0")
  }

  test("Stable leaves a version that has already reached 1.0.0 where it is") {
    assertEquals(advance("1.2.3", Intent.Stable), "1.2.3")
    assertEquals(advance("1.2.3-rc.1", Intent.Stable), "1.2.3")
  }

  test("Stable graduates an initial-development version to 1.0.0") {
    assertEquals(advance("0.4.2", Intent.Stable), "1.0.0")
    assertEquals(advance("0.0.7", Intent.Stable), "1.0.0")
  }

  test("an intent the pending pre-release already sits on is absorbed by releasing it") {
    assertEquals(advance("1.2.3-rc.1", Intent.Fix), "1.2.3")
    assertEquals(advance("1.3.0-rc.1", Intent.Feature), "1.3.0")
    assertEquals(advance("2.0.0-rc.1", Intent.Breaking), "2.0.0")
  }

  test("an intent the pending pre-release does not reach still advances") {
    assertEquals(advance("1.2.3-rc.1", Intent.Feature), "1.3.0")
    assertEquals(advance("1.2.3-rc.1", Intent.Breaking), "2.0.0")
  }

  test("a breaking change on a pending 0.5.0 line is absorbed into that release") {
    assertEquals(advance("0.5.0-rc.1", Intent.Breaking), "0.5.0")
  }

  test("below 1.0.0 breaking moves the minor and lesser intents move the patch") {
    assertEquals(advance("0.4.2", Intent.Breaking), "0.5.0")
    assertEquals(advance("0.4.2", Intent.Feature), "0.4.3")
    assertEquals(advance("0.4.2", Intent.Fix), "0.4.3")
  }

  test("below 0.1.0 every intent moves the patch") {
    assertEquals(advance("0.0.7", Intent.Breaking), "0.0.8")
    assertEquals(advance("0.0.7", Intent.Feature), "0.0.8")
    assertEquals(advance("0.0.7", Intent.Fix), "0.0.8")
  }

  test("a named component is exempt from the initial-development policy an intent obeys") {
    assertEquals(bump("0.93.9", "major"), "1.0.0")
    assertEquals(advance("0.93.9", Intent.Breaking), "0.94.0")
    assertEquals(bump("0.4.2", "major"), "1.0.0")
  }

  test("bumping a named component resets every component below it") {
    assertEquals(bump("1.2.3", "major"), "2.0.0")
    assertEquals(bump("1.2.3", "minor"), "1.3.0")
    assertEquals(bump("1.2.3", "patch"), "1.2.4")
    assertEquals(bump("0.4.2", "minor"), "0.5.0")
  }

  test("bumping a named component is absorbed by a pending pre-release on that boundary") {
    assertEquals(bump("1.0.0-rc.1", "major"), "1.0.0")
    assertEquals(bump("1.3.0-rc.1", "minor"), "1.3.0")
    assertEquals(bump("1.2.3-rc.1", "patch"), "1.2.3")
  }

  test("assigning a named component sets it and resets every component below it") {
    assertEquals(assign("1.2.3", "major", 5), "5.0.0")
    assertEquals(assign("0.93.9", "minor", 93), "0.93.0")
    assertEquals(assign("1.2.3", "patch", 9), "1.2.9")
  }

  test("assignment is never absorbed by a pending pre-release") {
    assertEquals(assign("1.0.0-rc.1", "major", 1), "1.0.0")
    assertEquals(assign("1.0.0-rc.1", "patch", 4), "1.0.4")
  }

  test("advancement discards the pre-release and build metadata of its base") {
    assertEquals(advance("1.2.3-rc.1+sha.abc", Intent.Feature), "1.3.0")
    assertEquals(bump("1.2.3-rc.1+sha.abc", "minor"), "1.3.0")
    assertEquals(assign("1.2.3-rc.1+sha.abc", "patch", 9), "1.2.9")
  }

  test("a component name the scheme does not use is rejected as unsupported") {
    assertEquals(arithmetic(v("1.2.3"), Request.Bump("epoch")), Left(UnsupportedComponent("semver", "epoch")))
    assertEquals(arithmetic(v("1.2.3"), Request.Assign("nope", 1)), Left(UnsupportedComponent("semver", "nope")))
  }

  test("a negative assignment is rejected against the component it addresses") {
    assertEquals(
      arithmetic(v("1.2.3"), Request.Assign("minor", -1)),
      Left(InvalidComponent(-1L, "Minor version", "a non-negative number (>= 0)"))
    )
  }

end SemVerArithmeticSuite
