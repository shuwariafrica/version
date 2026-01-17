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

/** Tests for the default `given` instances exported from [[version.instances]].
  *
  * Verifies that the standard imports make core functionality available:
  *   - `Version.Read[String]` for parsing
  *   - `PreRelease.Resolver` for pre-release mapping
  */
class InstancesSuite extends munit.FunSuite:

  // Importing version.{*, given} should bring in all necessary given instances
  // for parsing to work seamlessly.

  test("import version.{*, given} provides Read[String] for toVersion") {
    // This test validates that after `import version.{*, given}`,
    // the extension method `toVersion` is available on String.
    val result = "1.2.3".toVersion
    assert(result.isRight)
    assertEquals(result.toOption.get.major.value, 1)
    assertEquals(result.toOption.get.minor.value, 2)
    assertEquals(result.toOption.get.patch.value, 3)
  }

  test("import version.{*, given} provides Read[String] for toVersionUnsafe") {
    val v = "2.0.0-alpha.1".toVersionUnsafe
    assertEquals(v.major.value, 2)
    assert(v.preRelease.exists(_.isAlpha))
  }

  test("import version.{*, given} provides default PreRelease.Resolver") {
    // The default resolver handles standard classifiers
    val alpha = "1.0.0-alpha.5".toVersion
    val snapshot = "1.0.0-SNAPSHOT".toVersion
    val rc = "1.0.0-rc.3".toVersion

    assert(alpha.isRight)
    assert(snapshot.isRight)
    assert(rc.isRight)

    assertEquals(alpha.toOption.get.preRelease.get.classifier, PreReleaseClassifier.Alpha)
    assertEquals(snapshot.toOption.get.preRelease.get.classifier, PreReleaseClassifier.Snapshot)
    assertEquals(rc.toOption.get.preRelease.get.classifier, PreReleaseClassifier.ReleaseCandidate)
  }

  test("Version.from uses the contextual Read instance") {
    val result = Version.from("3.1.4")
    assert(result.isRight)
    assertEquals(result.toOption.get.patch.value, 4)
  }

  test("Version.fromUnsafe uses the contextual Read instance") {
    val v = Version.fromUnsafe("0.1.0")
    assertEquals(v.major.value, 0)
    assertEquals(v.minor.value, 1)
  }

  test("Version.Read[String] summoned via apply equals ReadString") {
    // Verify that the summoned instance is the expected singleton
    val summoned = Version.Read[String]
    val expected = Version.Read.ReadString
    // They should be the same object (singleton)
    assert(summoned eq expected)
  }

end InstancesSuite
