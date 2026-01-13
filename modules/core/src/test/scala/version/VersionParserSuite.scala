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

import version.PreRelease.Resolver
import version.errors.*

/** Tests the parsing logic, SemVer compliance, and the PreRelease.Resolver mechanism. */
class VersionParserSuite extends munit.FunSuite:

  // Helpers for constructing expected versions
  private def V(major: Int, minor: Int, patch: Int) =
    Version(MajorVersion.fromUnsafe(major), MinorVersion.fromUnsafe(minor), PatchNumber.fromUnsafe(patch))

  private def PRN(i: Int) = PreReleaseNumber.fromUnsafe(i)

  // --- Basic Parsing (M.m.p) ---

  test("Parse valid basic versions") {
    assertEquals("1.2.3".toVersion, Right(V(1, 2, 3)))
    assertEquals("0.0.1".toVersion, Right(V(0, 0, 1)))
    assertEquals("10.99.1000".toVersion, Right(V(10, 99, 1000)))
  }

  // --- Pre-Release Parsing (Default Resolver) ---

  test("Parse standard dot-separated pre-release formats") {
    val expectedA1 = V(1, 0, 0).copy(preRelease = Some(PreRelease.alpha(PRN(1))))
    val expectedRC10 = V(1, 0, 0).copy(preRelease = Some(PreRelease.releaseCandidate(PRN(10))))

    assertEquals("1.0.0-alpha.1".toVersion, Right(expectedA1))
    assertEquals("1.0.0-rc.10".toVersion, Right(expectedRC10))
  }

  test("Handle case-insensitivity and aliases in pre-release") {
    val expectedA1 = V(1, 0, 0).copy(preRelease = Some(PreRelease.alpha(PRN(1))))
    assertEquals("1.0.0-ALPHA.1".toVersion, Right(expectedA1))
    assertEquals("1.0.0-a.1".toVersion, Right(expectedA1))
    assertEquals("1.0.0-SNAPSHOT".toVersion, Right(V(1, 0, 0).copy(preRelease = Some(PreRelease.snapshot))))
  }

  test("Parse reconciled (non-dot-separated) pre-release formats (e.g., RC1)") {
    // The parser reconciles "RC1" into List("RC", "1")
    val expectedRC10 = V(1, 0, 0).copy(preRelease = Some(PreRelease.releaseCandidate(PRN(10))))
    assertEquals("1.0.0-RC10".toVersion, Right(expectedRC10))
    assertEquals("1.0.0-m5".toVersion, Right(V(1, 0, 0).copy(preRelease = Some(PreRelease.milestone(PRN(5))))))
  }

  // --- Build Metadata Parsing ---

  test("Parse build metadata") {
    val meta1 = BuildMetadata(List("build123"))
    val meta2 = BuildMetadata(List("20250825", "sha-abc"))

    assertEquals("1.0.0+build123".toVersion, Right(V(1, 0, 0).copy(buildMetadata = Some(meta1))))
    assertEquals("1.0.0+20250825.sha-abc".toVersion, Right(V(1, 0, 0).copy(buildMetadata = Some(meta2))))
  }

  test("Parse full versions (Pre-Release and Metadata)") {
    val expected = V(1, 2, 3).copy(
      preRelease = Some(PreRelease.alpha(PRN(1))),
      buildMetadata = Some(BuildMetadata(List("meta")))
    )
    assertEquals("1.2.3-alpha.1+meta".toVersion, Right(expected))
  }

  // --- Invalid Formats (Structural/SemVer Rules) ---

  test("Reject invalid structural formats") {
    val invalid = List("1", "1.2", "1.2.", "a.b.c", "1.2.3-", "1.2.3+", "1.2.3-+")
    invalid.foreach { input =>
      assertEquals(input.toVersion, Left(InvalidVersionFormat(input)))
    }
  }

  test("Reject trailing dot in pre-release or build identifiers") {
    val invalid = List(
      "1.0.0-alpha.", // trailing dot
      "1.0.0-alpha.1.", // trailing dot after numeric
      "1.0.0+meta.", // trailing dot in build
      "1.0.0+meta..build" // empty identifier in build
    )
    invalid.foreach(in => assert(in.toVersion.isLeft, clues(in)))
  }

  test("Reject numeric identifiers with leading zeros in pre-release") {
    assertEquals("1.0.0-alpha.01".toVersion, Left(InvalidVersionFormat("1.0.0-alpha.01")))
  }

  test("Accept combined classifier+number forms (case-insensitive) and reject leading zero after split") {
    val ok = "1.0.0-RC10".toVersion
    assert(ok.exists(_.preRelease.exists(_.classifier == PreReleaseClassifier.ReleaseCandidate)))
    // RC01 should split into RC + 01 then leading zero numeric invalid => unrecognized OR invalid format?
    // After split we get identifiers List("RC", "01") -> numeric identifier has leading zero => structural invalid
    assertEquals("1.0.0-RC01".toVersion, Left(InvalidVersionFormat("1.0.0-RC01")))
  }

  test("Build metadata multi identifiers accepted and rendered") {
    val v = "1.2.3+build.abc-xyz.20250101".toVersion.toOption.get
    assertEquals(v.buildMetadata.map(_.identifiers), Some(List("build", "abc-xyz", "20250101")))
  }

  test("Reject leading zeros in numeric components (SemVer violation)") {
    assertEquals("01.2.3".toVersion, Left(InvalidVersionFormat("01.2.3")))
    assertEquals("1.02.3".toVersion, Left(InvalidVersionFormat("1.02.3")))
    assertEquals("1.2.03".toVersion, Left(InvalidVersionFormat("1.2.03")))
    // Also applies to pre-release numeric identifiers (caught by the main regex)
    assertEquals("1.0.0-alpha.01".toVersion, Left(InvalidVersionFormat("1.0.0-alpha.01")))
  }

  test("Reject invalid build metadata formats") {
    // Invalid characters (e.g., '_')
    val inputInvalidChar = "1.0.0+build_123"
    assert(inputInvalidChar.toVersion.isLeft)

    // Empty identifiers (caught by the main regex)
    assertEquals("1.0.0+build.".toVersion, Left(InvalidVersionFormat("1.0.0+build.")))
    assertEquals("1.0.0+build..sha".toVersion, Left(InvalidVersionFormat("1.0.0+build..sha")))
  }

  // --- Invalid Content (Overflow/Resolver Constraints) ---

  test("Reject integer overflows (InvalidNumericField)") {
    val overflow = (BigInt(Int.MaxValue) + 1).toString
    val inputM = s"$overflow.0.0"
    assertEquals(inputM.toVersion, Left(InvalidNumericField("Major", overflow)))
  }

  test("Reject unrecognized Pre-Release identifiers (UnrecognizedPreRelease)") {
    // Structurally valid SemVer, but unknown to the default resolver.
    assertEquals("1.0.0-feature.xyz".toVersion, Left(UnrecognizedPreRelease(List("feature", "xyz"))))
  }

  test("Reject Pre-Release identifiers violating constraints (Default Resolver)") {
    // 1. Versioned classifier missing a number
    assertEquals("1.0.0-dev".toVersion, Left(UnrecognizedPreRelease(List("dev"))))
    assertEquals("1.0.0-alpha".toVersion, Left(UnrecognizedPreRelease(List("alpha"))))

    // 2. Non-versioned classifier having a number
    assertEquals("1.0.0-snapshot.1".toVersion, Left(UnrecognizedPreRelease(List("snapshot", "1"))))

    // 3. Pre-release number is zero (PreReleaseNumber constraint >= 1)
    assertEquals("1.0.0-alpha.0".toVersion, Left(UnrecognizedPreRelease(List("alpha", "0"))))

    // 4. Too many segments for default resolver
    assertEquals("1.0.0-alpha.1.5".toVersion, Left(UnrecognizedPreRelease(List("alpha", "1", "5"))))
  }

  // --- Custom Resolver Test ---

  test("Parser utilizes a custom contextual resolver") {
    // Define a custom resolver that maps "dev" to Snapshot and rejects everything else.
    given customResolver: PreRelease.Resolver = new PreRelease.Resolver:
      def map(identifiers: List[String]): Option[PreRelease] =
        if identifiers == List("dev") then Some(PreRelease.snapshot)
        else None // Does not fallback to default in this test case

    // Should succeed using the custom resolver
    assertEquals("1.0.0-dev".toVersion, Right(V(1, 0, 0).copy(preRelease = Some(PreRelease.snapshot))))

    // Should fail because the custom resolver rejects "alpha.1"
    assertEquals("1.0.0-alpha.1".toVersion, Left(UnrecognizedPreRelease(List("alpha", "1"))))
  }

  // --- Leading v/V Prefix Tests (Spec Section 3.1) ---

  test("Parse version with leading lowercase 'v' prefix") {
    assertEquals("v1.2.3".toVersion, Right(V(1, 2, 3)))
    assertEquals("v0.0.1".toVersion, Right(V(0, 0, 1)))
  }

  test("Parse version with leading uppercase 'V' prefix") {
    assertEquals("V1.2.3".toVersion, Right(V(1, 2, 3)))
    assertEquals("V10.20.30".toVersion, Right(V(10, 20, 30)))
  }

  test("Parse version with v prefix and pre-release") {
    val expected = V(1, 0, 0).copy(preRelease = Some(PreRelease.alpha(PRN(1))))
    assertEquals("v1.0.0-alpha.1".toVersion, Right(expected))
    assertEquals("V1.0.0-alpha.1".toVersion, Right(expected))
  }

  test("Parse version with v prefix and build metadata") {
    val meta = BuildMetadata(List("build", "123"))
    val expected = V(2, 0, 0).copy(buildMetadata = Some(meta))
    assertEquals("v2.0.0+build.123".toVersion, Right(expected))
  }

  test("Parse full version with v prefix") {
    val expected = V(1, 2, 3).copy(
      preRelease = Some(PreRelease.releaseCandidate(PRN(5))),
      buildMetadata = Some(BuildMetadata(List("sha1234567")))
    )
    assertEquals("v1.2.3-rc.5+sha1234567".toVersion, Right(expected))
  }

  test("Reject bare 'v' or 'V' without version") {
    assertEquals("v".toVersion, Left(InvalidVersionFormat("v")))
    assertEquals("V".toVersion, Left(InvalidVersionFormat("V")))
  }

  test("Reject 'v' prefix followed by invalid version") {
    assertEquals("v1.2".toVersion, Left(InvalidVersionFormat("v1.2")))
    assertEquals("Va.b.c".toVersion, Left(InvalidVersionFormat("Va.b.c")))
  }

  // --- Dev Classifier Tests ---

  test("Parse Dev pre-release classifier") {
    val expected = V(1, 0, 0).copy(preRelease = Some(PreRelease.dev(PRN(1))))
    assertEquals("1.0.0-dev.1".toVersion, Right(expected))
  }

  test("Parse Dev with higher number") {
    val expected = V(2, 0, 0).copy(preRelease = Some(PreRelease.dev(PRN(99))))
    assertEquals("2.0.0-dev.99".toVersion, Right(expected))
  }

  test("Dev classifier is case-insensitive") {
    val expected = V(1, 0, 0).copy(preRelease = Some(PreRelease.dev(PRN(5))))
    assertEquals("1.0.0-DEV.5".toVersion, Right(expected))
    assertEquals("1.0.0-Dev.5".toVersion, Right(expected))
  }

  // --- All Classifier Aliases (Comprehensive) ---

  test("Parse all classifier canonical forms") {
    val versions = List(
      ("1.0.0-dev.1", PreRelease.dev(PRN(1))),
      ("1.0.0-milestone.1", PreRelease.milestone(PRN(1))),
      ("1.0.0-alpha.1", PreRelease.alpha(PRN(1))),
      ("1.0.0-beta.1", PreRelease.beta(PRN(1))),
      ("1.0.0-rc.1", PreRelease.releaseCandidate(PRN(1))),
      ("1.0.0-snapshot", PreRelease.snapshot)
    )

    versions.foreach { case (input, expectedPr) =>
      val result = input.toVersion
      assert(result.isRight, s"Failed to parse: $input")
      assertEquals(result.toOption.get.preRelease, Some(expectedPr))
    }
  }

  test("Parse all classifier alias forms") {
    val aliasTests = List(
      // Milestone aliases
      ("1.0.0-m.1", PreRelease.milestone(PRN(1))),
      ("1.0.0-M.1", PreRelease.milestone(PRN(1))),
      // Alpha aliases
      ("1.0.0-a.1", PreRelease.alpha(PRN(1))),
      ("1.0.0-A.1", PreRelease.alpha(PRN(1))),
      // Beta aliases
      ("1.0.0-b.1", PreRelease.beta(PRN(1))),
      ("1.0.0-B.1", PreRelease.beta(PRN(1))),
      // Release candidate aliases
      ("1.0.0-cr.1", PreRelease.releaseCandidate(PRN(1))),
      ("1.0.0-CR.1", PreRelease.releaseCandidate(PRN(1))),
      ("1.0.0-RC.1", PreRelease.releaseCandidate(PRN(1)))
    )

    aliasTests.foreach { case (input, expectedPr) =>
      val result = input.toVersion
      assert(result.isRight, s"Failed to parse: $input")
      assertEquals(result.toOption.get.preRelease, Some(expectedPr), s"Wrong pre-release for: $input")
    }
  }

  test("Parse combined (non-dot-separated) forms for all classifiers") {
    val combinedTests = List(
      ("1.0.0-dev5", PreRelease.dev(PRN(5))),
      ("1.0.0-m10", PreRelease.milestone(PRN(10))),
      ("1.0.0-a3", PreRelease.alpha(PRN(3))),
      ("1.0.0-b7", PreRelease.beta(PRN(7))),
      ("1.0.0-RC15", PreRelease.releaseCandidate(PRN(15))),
      ("1.0.0-cr2", PreRelease.releaseCandidate(PRN(2)))
    )

    combinedTests.foreach { case (input, expectedPr) =>
      val result = input.toVersion
      assert(result.isRight, s"Failed to parse combined form: $input")
      assertEquals(result.toOption.get.preRelease, Some(expectedPr), s"Wrong pre-release for: $input")
    }
  }

  // --- Edge Cases ---

  test("Parse maximum integer values") {
    val max = Int.MaxValue.toString
    val result = s"$max.$max.$max".toVersion
    assert(result.isRight)
    assertEquals(result.toOption.get.major.value, Int.MaxValue)
    assertEquals(result.toOption.get.minor.value, Int.MaxValue)
    assertEquals(result.toOption.get.patch.value, Int.MaxValue)
  }

  test("Reject negative-like (structurally invalid) versions") {
    // Negative sign is not a valid character
    assert("-1.0.0".toVersion.isLeft)
  }

  test("Reject whitespace in version strings") {
    assert(" 1.0.0".toVersion.isLeft)
    assert("1.0.0 ".toVersion.isLeft)
    assert("1. 0.0".toVersion.isLeft)
  }

  test("Reject empty string") {
    assertEquals("".toVersion, Left(InvalidVersionFormat("")))
  }

end VersionParserSuite
