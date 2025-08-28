package version

import version.PreRelease.Resolver
import version.errors.*
import version.operations.toVersion // Import the extension method

/** Tests the parsing logic, SemVer compliance, and the PreRelease.Resolver mechanism. */
class VersionParserSuite extends munit.FunSuite:

  // Use the default resolver for most tests
  given Resolver = Resolver.default

  // Helpers for constructing expected versions
  private def V(major: Int, minor: Int, patch: Int) =
    Version(MajorVersion.unsafe(major), MinorVersion.unsafe(minor), PatchNumber.unsafe(patch))

  private def PRN(i: Int) = PreReleaseNumber.unsafe(i)

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
    assertEquals("1.0.0-dev".toVersion, Left(UnrecognizedPreRelease(List("dev"))))
    assertEquals("1.0.0-feature.xyz".toVersion, Left(UnrecognizedPreRelease(List("feature", "xyz"))))
  }

  test("Reject Pre-Release identifiers violating constraints (Default Resolver)") {
    // 1. Versioned classifier missing a number
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

end VersionParserSuite
