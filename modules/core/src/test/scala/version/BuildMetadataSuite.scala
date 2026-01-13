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

import version.errors.InvalidBuildMetadata

class BuildMetadataSuite extends munit.FunSuite:

  test("BuildMetadata.from should succeed for valid SemVer identifiers") {
    // Identifiers must match [0-9A-Za-z-]
    val valid = List(
      List("build123"),
      List("sha", "a9f8e6d"),
      List("20250824"),
      List("with-hyphens")
    )

    valid.foreach { ids =>
      val result = BuildMetadata.from(ids)
      assert(result.isRight)
      assertEquals(result.toOption.get.identifiers, ids)
    }
  }

  test("BuildMetadata.from should fail for an empty list") {
    val result = BuildMetadata.from(List.empty)
    assertEquals(result, Left(InvalidBuildMetadata(List.empty)))
  }

  test("BuildMetadata.from should fail if any identifier is empty") {
    val ids = List("sha", "")
    val result = BuildMetadata.from(ids)
    assertEquals(result, Left(InvalidBuildMetadata(ids)))
  }

  test("BuildMetadata.from should fail if identifiers contain invalid characters") {
    val invalid = List(
      List("invalid!"),
      List("with space"),
      List("plus+"),
      List("under_score")
    )

    invalid.foreach { ids =>
      val result = BuildMetadata.from(ids)
      assertEquals(result, Left(InvalidBuildMetadata(ids)))
    }
  }

  test("BuildMetadata.apply should throw InvalidBuildMetadata for invalid input") {
    val ex1 = intercept[InvalidBuildMetadata] {
      BuildMetadata(List("invalid!"))
    }
    assertEquals(ex1.identifiers, List("invalid!"))

    val ex2 = intercept[InvalidBuildMetadata] {
      BuildMetadata(List.empty)
    }
    assertEquals(ex2.identifiers, List.empty)
  }

  test("BuildMetadata.show should format correctly with '+' prefix and dot separation") {
    val metadata = BuildMetadata(List("sha", "a9f8e6d"))
    assertEquals(metadata.show, "+sha.a9f8e6d")
  }

end BuildMetadataSuite
