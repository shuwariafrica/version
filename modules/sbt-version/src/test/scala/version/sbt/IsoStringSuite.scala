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
package version.sbt

import munit.FunSuite
import sjsonnew.IsoString

import version.sbt.VersionPluginImports.given
import version.semver.SemVer

/** Tests for [[IsoString]] serialisation round-tripping.
  *
  * Verifies that the [[IsoString]] instance for [[SemVer]] is correctly wired to
  * [[SemVer.Formatter$.full Formatter.full]] and [[SemVer.parseUnsafe]], enabling lossless round-trip fidelity for sbt
  * 2.x task caching.
  */
final class IsoStringSuite extends FunSuite:

  private val isoString = summon[IsoString[SemVer]]

  test("IsoString round-trips compound version with pre-release and metadata") {
    // Comprehensive test covering core + pre-release + metadata
    val version = SemVer.parseUnsafe("3.2.1-beta.5+branchrelease.commits99.sha0123456789abcdef.dirty")
    val serialised = isoString.to(version)
    val deserialised = isoString.from(serialised)
    assertEquals(deserialised, version)
    // Verify Formatter.full preserves complete metadata (not truncated)
    assertEquals(serialised, "3.2.1-beta.5+branchrelease.commits99.sha0123456789abcdef.dirty")
  }

  test("IsoString uses Formatter.full (preserves full SHA metadata)") {
    val sha = "abc1234567890def1234567890abc1234567890d"
    val version = SemVer.parseUnsafe(s"1.0.0+sha$sha")
    val serialised = isoString.to(version)
    // Formatter.full should preserve the full SHA, not truncate to 7 chars
    assert(serialised.contains(sha), clues(serialised))
  }

end IsoStringSuite
