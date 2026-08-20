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

import boilerplate.ValueCodec
import munit.FunSuite

import version.errors.InvalidVersionFormat
import version.errors.VersionError

class SemVerCodecSuite extends FunSuite:

  private val codec = summon[ValueCodec.Aux[SemVer, VersionError]]

  test("a version survives being written out and read back") {
    List("1.2.3", "0.1.0-rc.1", "2.0.0+sha.5114f85", "1.0.0-x.7.z.92+build.1").foreach: text =>
      val decoded = codec.decode(text)
      assertEquals(decoded.map(codec.encode), Right(text), text)
  }

  test("text that is not a version is refused with the reason, not an exception") {
    assertEquals(codec.decode("2.0"), Left(InvalidVersionFormat("2.0")))
  }

end SemVerCodecSuite
