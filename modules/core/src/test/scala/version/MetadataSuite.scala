/****************************************************************************
 * Copyright 2023 Shuwari Africa Ltd.                                       *
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

import version.errors.InvalidMetadata

class MetadataSuite extends munit.FunSuite:

  test("Metadata.from should succeed for valid SemVer identifiers") {
    // Identifiers must match [0-9A-Za-z-]
    val valid = List(
      List("build123"),
      List("sha", "a9f8e6d"),
      List("20250824"),
      List("with-hyphens")
    )

    valid.foreach { ids =>
      val result = Metadata.from(ids)
      assert(result.isRight)
      assertEquals(result.toOption.get.identifiers, ids)
    }
  }

  test("Metadata.from should fail for an empty list") {
    val result = Metadata.from(List.empty)
    assertEquals(result, Left(InvalidMetadata(List.empty)))
  }

  test("Metadata.from should fail if any identifier is empty") {
    val ids = List("sha", "")
    val result = Metadata.from(ids)
    assertEquals(result, Left(InvalidMetadata(ids)))
  }

  test("Metadata.from should fail if identifiers contain invalid characters") {
    val invalid = List(
      List("invalid!"),
      List("with space"),
      List("plus+"),
      List("under_score")
    )

    invalid.foreach { ids =>
      val result = Metadata.from(ids)
      assertEquals(result, Left(InvalidMetadata(ids)))
    }
  }

  test("Metadata.apply should throw InvalidMetadata for invalid input") {
    val ex1 = intercept[InvalidMetadata] {
      Metadata(List("invalid!"))
    }
    assertEquals(ex1.identifiers, List("invalid!"))

    val ex2 = intercept[InvalidMetadata] {
      Metadata(List.empty)
    }
    assertEquals(ex2.identifiers, List.empty)
  }

  test("Metadata.show should format correctly with '+' prefix and dot separation") {
    val metadata = Metadata(List("sha", "a9f8e6d"))
    assertEquals(metadata.show, "+sha.a9f8e6d")
  }

end MetadataSuite
