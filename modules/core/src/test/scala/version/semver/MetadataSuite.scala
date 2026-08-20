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

class MetadataSuite extends FunSuite:

  test("identifiers within the permitted charset are accepted and rendered dot-separated") {
    assertEquals(Metadata.of(List("build", "0123", "a-b")).map(_.show), Right("build.0123.a-b"))
  }

  test("a numeric identifier may carry a leading zero") {
    assertEquals(Metadata.of(List("01")).map(_.identifiers), Right(List("01")))
  }

  test("an empty identifier list is rejected") {
    assertEquals(Metadata.of(Nil), Left(InvalidMetadata(Nil)))
  }

  test("an empty identifier is rejected") {
    assertEquals(Metadata.of(List("build", "")), Left(InvalidMetadata(List("build", ""))))
  }

  test("a character outside the ASCII alphanumerics and hyphen is rejected") {
    assertEquals(Metadata.of(List("build_1")), Left(InvalidMetadata(List("build_1"))))
    assertEquals(Metadata.of(List("build.1")), Left(InvalidMetadata(List("build.1"))))
  }

  test("a letter outside ASCII is rejected, matching what the parser accepts") {
    // Built from a code point so that this file stays ASCII while still exercising a non-ASCII letter.
    val accented = "caf" + 233.toChar
    assertEquals(Metadata.of(List(accented)), Left(InvalidMetadata(List(accented))))
    assert(SemVer.parse(s"1.0.0+$accented").isLeft)
  }

  test("unsafe construction throws what safe construction reports") {
    intercept[InvalidMetadata](Metadata.ofUnsafe(List("build_1")))
  }

end MetadataSuite
