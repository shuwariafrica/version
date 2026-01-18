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
package version.sbt

import munit.FunSuite
import sjsonnew.IsoString

import version.Version
import version.given
import version.sbt.VersionPluginImports.given

/** Tests for [[IsoString]] serialisation round-tripping.
  *
  * Verifies that the [[IsoString]] instance for [[Version]] is correctly wired to [[Version.Show.Full]] and
  * [[Version.Read.ReadString]], enabling lossless round-trip fidelity for sbt 2.x task caching.
  *
  * Note: Parsing and rendering logic is tested in the core module. These tests verify the sbt-version wiring only.
  */
final class IsoStringSuite extends FunSuite:

  private val isoString = summon[IsoString[Version]]

  test("IsoString round-trips compound version with pre-release and metadata") {
    // Comprehensive test covering core + pre-release + metadata
    val version = "3.2.1-beta.5+branchrelease.commits99.sha0123456789abcdef.dirty".toVersionUnsafe
    val serialised = isoString.to(version)
    val deserialised = isoString.from(serialised)
    assertEquals(deserialised, version)
    // Verify Show.Full preserves complete metadata (not truncated)
    assertEquals(serialised, "3.2.1-beta.5+branchrelease.commits99.sha0123456789abcdef.dirty")
  }

  test("IsoString uses Show.Full (preserves full SHA metadata)") {
    val sha = "abc1234567890def1234567890abc1234567890d"
    val version = s"1.0.0+sha$sha".toVersionUnsafe
    val serialised = isoString.to(version)
    // Show.Full should preserve the full SHA, not truncate to 7 chars
    assert(serialised.contains(sha), clues(serialised))
  }

end IsoStringSuite
