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

import version.VersionScheme

class SemVerFormatterSuite extends FunSuite:

  private val scheme = summon[VersionScheme[SemVer]]

  private val Sha = "0123456789abcdef0123456789abcdef01234567"

  private def v(input: String): SemVer = SemVer.parseUnsafe(input)

  test("the standard rendering omits build metadata") {
    assertEquals(SemVer.Formatter.Standard.format(v("1.2.3-rc.1+sha.abc")), "1.2.3-rc.1")
    assertEquals(SemVer.Formatter.Standard.format(v("1.2.3+sha.abc")), "1.2.3")
  }

  test("the full rendering is the canonical one") {
    List("1.2.3", "1.2.3-rc.1", "1.2.3+sha.abc", "1.2.3-rc.1+sha.abc").foreach { input =>
      assertEquals(SemVer.Formatter.Full.format(v(input)), scheme.show(v(input)))
    }
  }

  test("a truncating rendering shortens only the commit-SHA identifier") {
    val formatter = SemVer.Formatter.Full.withShaLength(7)
    assertEquals(formatter.format(v(s"1.0.0+202311142213.main.$Sha.pr42.dirty")), "1.0.0+202311142213.main.0123456.pr42.dirty")
  }

  test("a truncating rendering leaves a version with no commit SHA untouched") {
    val formatter = SemVer.Formatter.Full.withShaLength(7)
    assertEquals(formatter.format(v("1.0.0+202311142213.main.dirty")), "1.0.0+202311142213.main.dirty")
  }

  test("a truncating rendering refuses a length no digest can be shortened to usefully") {
    intercept[IllegalArgumentException](SemVer.Formatter.Full.withShaLength(6)): Unit
    intercept[IllegalArgumentException](SemVer.Formatter.Full.withShaLength(65)): Unit
  }

end SemVerFormatterSuite
