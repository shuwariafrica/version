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

// The cross-product the range laws are checked over: one range per construct the grammar offers, and versions either
// side of every boundary those ranges draw, including the pre-releases the membership rule turns on.
object RangeCorpus:

  val ranges: List[String] = List(
    "^1.2.3",
    "~1.2.3",
    "1.2.3",
    "=1.2.3",
    ">=1.2.3",
    "<=1.2.3",
    "<1.2.3",
    ">1.2.3",
    "1.x",
    "1.2.x",
    "*",
    "",
    "^0.0.3",
    "^0.2.3",
    "~0.2",
    "^1.2.3-rc.1",
    "1.2.3 - 2.3.4",
    "1.2 - 2.3",
    ">=1.2.3 <2.0.0",
    "^1.0.0 || ^2.0.0",
    "1.2.7 || >=1.2.9 <2.0.0"
  )

  val versions: List[SemVer] = List(
    "0.0.1",
    "0.0.3",
    "0.2.3",
    "0.3.0",
    "1.0.0",
    "1.2.2",
    "1.2.3",
    "1.2.7",
    "1.2.9",
    "1.5.0",
    "1.9.9",
    "2.0.0",
    "2.3.4",
    "3.0.0",
    "1.2.3-rc.1",
    "1.2.3-rc.2",
    "2.0.0-rc.1",
    "1.5.0-rc.1"
  ).map(SemVer.parseUnsafe)

  // Ranges whose ceiling sits where the carrier has no successor to give, so the ceiling is left off entirely. The
  // laws run over these beside the corpus proper, which stays the set the design record pins.
  val boundaryRanges: List[String] = List(
    "^9223372036854775807",
    "~9223372036854775807",
    "9223372036854775807.x",
    "<=9223372036854775807.x",
    ">9223372036854775807",
    "<9223372036854775807.0.0",
    "~1.9223372036854775807",
    "^0.9223372036854775807",
    "1.9223372036854775807",
    "^0.0.9223372036854775807",
    ">1.9223372036854775807"
  )

  val boundaryVersions: List[SemVer] =
    List(
      "9223372036854775806.0.0",
      "9223372036854775807.0.0",
      "9223372036854775807.9.9",
      "1.9223372036854775807.5",
      "0.9223372036854775807.7",
      "0.0.9223372036854775807"
    ).map(SemVer.parseUnsafe)

  val lawRanges: List[String] = ranges ++ boundaryRanges

  val lawVersions: List[SemVer] = versions ++ boundaryVersions

  val lawParsed: List[SemVerRange] = lawRanges.map(range)

  def range(text: String): SemVerRange = SemVerRange.parse(text) match
    case Right(r) => r
    case Left(e)  => throw e // scalafix:ok

end RangeCorpus
