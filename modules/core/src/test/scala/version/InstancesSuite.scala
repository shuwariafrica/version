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

import version.semver.*

/** Tests for the default `given` instances exported from [[version.semver]].
  *
  * Verifies that the standard imports make core functionality available:
  *   - `SemVer.parse` / `SemVer.parseUnsafe` for parsing
  *   - `PreRelease.Resolver` for pre-release mapping
  */
class InstancesSuite extends munit.FunSuite:

  // Importing version.semver.* should bring in all necessary given instances
  // for parsing to work seamlessly.

  test("import version.semver.* provides SemVer.parse") {
    // This test validates that after `import version.semver.*`,
    // SemVer.parse is available for parsing strings.
    val result = SemVer.parse("1.2.3")
    assert(result.isRight)
    assertEquals(result.toOption.get.major.value, 1)
    assertEquals(result.toOption.get.minor.value, 2)
    assertEquals(result.toOption.get.patch.value, 3)
  }

  test("import version.semver.* provides SemVer.parseUnsafe") {
    val v = SemVer.parseUnsafe("2.0.0-alpha.1")
    assertEquals(v.major.value, 2)
    assert(v.preRelease.exists(_.isAlpha))
  }

  test("import version.semver.* provides default PreRelease.Resolver") {
    // The default resolver handles standard classifiers
    val alpha = SemVer.parse("1.0.0-alpha.5")
    val snapshot = SemVer.parse("1.0.0-SNAPSHOT")
    val rc = SemVer.parse("1.0.0-rc.3")

    assert(alpha.isRight)
    assert(snapshot.isRight)
    assert(rc.isRight)

    assertEquals(alpha.toOption.get.preRelease.get.classifier, PreReleaseClassifier.Alpha)
    assertEquals(snapshot.toOption.get.preRelease.get.classifier, PreReleaseClassifier.Snapshot)
    assertEquals(rc.toOption.get.preRelease.get.classifier, PreReleaseClassifier.ReleaseCandidate)
  }

end InstancesSuite
