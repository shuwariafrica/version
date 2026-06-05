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

class UtcSuite extends FunSuite:

  test("compact: epoch produces 197001010000"):
    assertEquals(Utc.compact(0L), "197001010000")

  test("compact: post-2038 epoch does not narrow to Int"):
    // 2050-06-15T12:34:00Z = 2538909240 seconds, beyond Int.MaxValue = 2147483647.
    assertEquals(Utc.compact(2538909240L), "205006151234")

  test("compact: leap-day"):
    // 2024-02-29T12:34:00Z = 1709210040 seconds.
    assertEquals(Utc.compact(1709210040L), "202402291234")

  test("compact: reference timestamp"):
    assertEquals(Utc.compact(1778982300L), "202605170145")

  test("dateTime: epoch"):
    assertEquals(Utc.dateTime(0L), "1970-01-01 00:00")

  test("dateTime: reference timestamp to the minute"):
    assertEquals(Utc.dateTime(1778982300L), "2026-05-17 01:45")

  test("dateTime: leap-day"):
    assertEquals(Utc.dateTime(1709210040L), "2024-02-29 12:34")

end UtcSuite
