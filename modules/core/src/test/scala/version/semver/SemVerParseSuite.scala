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

import version.errors.InvalidMetadata
import version.errors.InvalidNumericField
import version.errors.InvalidPreRelease
import version.errors.InvalidVersionFormat

class SemVerParseSuite extends FunSuite:

  private def parsed(input: String): SemVer = SemVer.parse(input) match
    case Right(v) => v
    case Left(e)  => fail(s"parse of '$input' failed: ${e.getMessage}")

  private def identifiers(input: String): List[String] =
    parsed(input).preRelease.map(_.identifiers).getOrElse(Nil)

  test("a bare three-part core parses to its components") {
    assertEquals(parsed("1.2.3"), SemVer(Major(1), Minor(2), Patch(3)))
  }

  test("a leading v or V is accepted and discarded") {
    assertEquals(parsed("v1.2.3"), SemVer(Major(1), Minor(2), Patch(3)))
    assertEquals(parsed("V1.2.3"), SemVer(Major(1), Minor(2), Patch(3)))
  }

  test("components beyond the range of Int are carried") {
    assertEquals(parsed("20260731093000.0.0").major.value, 20260731093000L)
  }

  test("pre-release identifiers are kept exactly as written") {
    assertEquals(identifiers("1.0.0-alpha.1"), List("alpha", "1"))
  }

  test("an undotted classifier and number is preserved, not split into two identifiers") {
    assertEquals(identifiers("1.0.0-rc3"), List("rc3"))
  }

  test("identifiers this library has no classifier for are accepted") {
    assertEquals(identifiers("1.0.0-x.7.z.92"), List("x", "7", "z", "92"))
  }

  test("a single unnumbered identifier is accepted") {
    assertEquals(identifiers("1.0.0-alpha"), List("alpha"))
    assertEquals(identifiers("1.0.0-SNAPSHOT"), List("SNAPSHOT"))
  }

  test("build metadata is kept as its own identifier list") {
    assertEquals(parsed("1.0.0+sha.5114f85").metadata.map(_.identifiers), Some(List("sha", "5114f85")))
  }

  test("build metadata may carry a leading zero where a pre-release identifier may not") {
    assertEquals(parsed("1.0.0+01").metadata.map(_.identifiers), Some(List("01")))
    assert(SemVer.parse("1.0.0-01").isLeft)
  }

  test("a version carrying both a pre-release and build metadata parses into both") {
    val v = parsed("1.2.3-rc.1+sha.5114f85")
    assertEquals(v.preRelease.map(_.identifiers), Some(List("rc", "1")))
    assertEquals(v.metadata.map(_.identifiers), Some(List("sha", "5114f85")))
  }

  test("rendering a parsed version and reading it back yields an equal value") {
    List("1.2.3", "1.2.3-rc.1", "1.0.0+01", "1.2.3-rc.1+sha.5114f85", "1.0.0-x.7.z.92").foreach { input =>
      val v = parsed(input)
      assertEquals(SemVer.parse(v.show), Right(v), s"round trip of '$input'")
    }
  }

  test("a core component with a leading zero is rejected as malformed") {
    assertEquals(SemVer.parse("01.0.0"), Left(InvalidVersionFormat("01.0.0")))
    assertEquals(SemVer.parse("1.00.0"), Left(InvalidVersionFormat("1.00.0")))
    assertEquals(SemVer.parse("1.0.01"), Left(InvalidVersionFormat("1.0.01")))
  }

  test("a core of other than three parts is rejected as malformed") {
    assertEquals(SemVer.parse("1.0"), Left(InvalidVersionFormat("1.0")))
    assertEquals(SemVer.parse("1.0.0.0"), Left(InvalidVersionFormat("1.0.0.0")))
  }

  test("a non-numeric core component is rejected as malformed") {
    assertEquals(SemVer.parse("1.2.3x"), Left(InvalidVersionFormat("1.2.3x")))
    assertEquals(SemVer.parse("a.b.c"), Left(InvalidVersionFormat("a.b.c")))
  }

  test("empty input is rejected as malformed") {
    assertEquals(SemVer.parse(""), Left(InvalidVersionFormat("")))
    assertEquals(SemVer.parse("v"), Left(InvalidVersionFormat("v")))
  }

  test("a core component too large to carry is reported as an invalid numeric field") {
    assertEquals(SemVer.parse("99999999999999999999.0.0"), Left(InvalidNumericField("Major", "99999999999999999999")))
    assertEquals(SemVer.parse("1.99999999999999999999.0"), Left(InvalidNumericField("Minor", "99999999999999999999")))
    assertEquals(SemVer.parse("1.0.99999999999999999999"), Left(InvalidNumericField("Patch", "99999999999999999999")))
  }

  test("an empty pre-release identifier is reported against the pre-release") {
    assertEquals(SemVer.parse("1.0.0-"), Left(InvalidPreRelease(List(""))))
    assertEquals(SemVer.parse("1.0.0-alpha..1"), Left(InvalidPreRelease(List("alpha", "", "1"))))
  }

  test("a pre-release identifier outside the permitted charset is reported against the pre-release") {
    assertEquals(SemVer.parse("1.0.0-alpha_1"), Left(InvalidPreRelease(List("alpha_1"))))
  }

  test("a numeric pre-release identifier with a leading zero is reported against the pre-release") {
    assertEquals(SemVer.parse("1.0.0-alpha.01"), Left(InvalidPreRelease(List("alpha", "01"))))
  }

  test("an empty or out-of-charset build identifier is reported against the metadata") {
    assertEquals(SemVer.parse("1.0.0+"), Left(InvalidMetadata(List(""))))
    assertEquals(SemVer.parse("1.0.0+build_1"), Left(InvalidMetadata(List("build_1"))))
  }

  test("parseUnsafe throws the same error parse reports") {
    assertEquals(SemVer.parseUnsafe("1.2.3"), SemVer(Major(1), Minor(2), Patch(3)))
    val thrown = intercept[InvalidVersionFormat](SemVer.parseUnsafe("nonsense"))
    assertEquals(thrown, InvalidVersionFormat("nonsense"))
  }

end SemVerParseSuite
