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

import version.errors.InvalidComponent

class ComponentSuite extends FunSuite:

  test("a non-negative value is accepted by each core component") {
    assertEquals(Major.from(0L).map(_.value), Right(0L))
    assertEquals(Minor.from(7L).map(_.value), Right(7L))
    assertEquals(Patch.from(42L).map(_.value), Right(42L))
  }

  test("a negative value is rejected with the component named in the message") {
    assertEquals(Major.from(-1L), Left(InvalidComponent(-1L, "Major version", "a non-negative number (>= 0)")))
    assertEquals(Minor.from(-1L), Left(InvalidComponent(-1L, "Minor version", "a non-negative number (>= 0)")))
    assertEquals(Patch.from(-1L), Left(InvalidComponent(-1L, "Patch number", "a non-negative number (>= 0)")))
    assertEquals(Major.from(-1L).swap.map(_.message), Right("Major version must be a non-negative number (>= 0). Found: -1"))
  }

  test("a pre-release number must be positive") {
    assertEquals(PreReleaseNumber.from(1L).map(_.value), Right(1L))
    assertEquals(
      PreReleaseNumber.from(0L),
      Left(InvalidComponent(0L, "Pre-release number", "a positive number (>= 1)"))
    )
  }

  test("components carry values beyond the range of Int") {
    assertEquals(Major.from(Long.MaxValue).map(_.value), Right(Long.MaxValue))
    assertEquals(Minor.from(4294967296L).map(_.value), Right(4294967296L))
  }

  test("increment advances by one and saturates rather than wrapping") {
    assertEquals(Major.fromUnsafe(1L).increment.value, 2L)
    assertEquals(Patch.fromUnsafe(0L).increment.value, 1L)
    assertEquals(Major.fromUnsafe(Long.MaxValue).increment.value, Long.MaxValue)
  }

  test("each component resets to its own minimum") {
    assertEquals(Major.reset.value, 0L)
    assertEquals(Minor.reset.value, 0L)
    assertEquals(Patch.reset.value, 0L)
    assertEquals(PreReleaseNumber.reset.value, 1L)
  }

  test("components order by their carried value") {
    assertEquals(List(Major.fromUnsafe(2L), Major.fromUnsafe(0L), Major.fromUnsafe(1L)).sorted.map(_.value), List(0L, 1L, 2L))
  }

  test("unsafe construction throws what safe construction reports") {
    intercept[InvalidComponent](Major.fromUnsafe(-1L))
  }

end ComponentSuite
