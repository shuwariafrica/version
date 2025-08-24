package version

import scala.util.Random

import version.PreReleaseClassifier.*
import version.errors.*

/** Tests for the core composite data structures: PreReleaseClassifier, PreRelease, and Version (including Ordering and
  * construction).
  */
class VersionSuite extends munit.FunSuite:

  extension [A](list: List[A]) private def shuffle: List[A] = Random.shuffle(list)

  // --- PreReleaseClassifier Tests ---

  test("PreReleaseClassifier Ordering (Precedence)") {
    // Defined order: Milestone < Alpha < Beta < ReleaseCandidate < Snapshot
    val expectedOrder = List(Milestone, Alpha, Beta, ReleaseCandidate, Snapshot)
    assertEquals(PreReleaseClassifier.values.toList.shuffle.sorted, expectedOrder)
  }

  test("PreReleaseClassifier.versioned status") {
    assert(Milestone.versioned)
    assert(Alpha.versioned)
    assert(Beta.versioned)
    assert(ReleaseCandidate.versioned)
    assert(!Snapshot.versioned)
  }

  test("PreReleaseClassifier.fromAlias (and unapply)") {
    assertEquals(PreReleaseClassifier.fromAlias("m"), Some(Milestone))
    assertEquals(PreReleaseClassifier.fromAlias("Milestone"), Some(Milestone))
    assertEquals(PreReleaseClassifier.fromAlias("A"), Some(Alpha))
    assertEquals(PreReleaseClassifier.fromAlias("RC"), Some(ReleaseCandidate))
    assertEquals(PreReleaseClassifier.fromAlias("cr"), Some(ReleaseCandidate))
    assertEquals(PreReleaseClassifier.fromAlias("snapshot"), Some(Snapshot))
    assertEquals(PreReleaseClassifier.fromAlias("unknown"), None)

    "rc" match
      case PreReleaseClassifier(c) => assertEquals(c, ReleaseCandidate)
      case _                       => fail("Extractor failed")
  }

  // --- PreRelease Tests ---

  private val N1 = PreReleaseNumber.unsafe(1)
  private val N5 = PreReleaseNumber.unsafe(5)

  test("PreRelease construction constraints (Missing Number)") {
    // Versioned classifiers must have a number
    val ex1 = intercept[MissingPreReleaseNumber](PreRelease(Alpha, None))
    assertEquals(ex1.classifier, Alpha)

    val ex2 = intercept[MissingPreReleaseNumber](PreRelease(ReleaseCandidate, None))
    assertEquals(ex2.classifier, ReleaseCandidate)
  }

  test("PreRelease construction constraints (Unexpected Number)") {
    // Non-versioned classifiers must NOT have a number
    val ex = intercept[UnexpectedPreReleaseNumber](PreRelease(Snapshot, Some(N1)))
    assertEquals(ex.classifier, Snapshot)
    assertEquals(ex.number, N1)
  }

  test("PreRelease.increment") {
    val a1 = PreRelease.alpha(N1)
    val a2 = PreRelease.alpha(PreReleaseNumber.unsafe(2))
    assertEquals(a1.increment, a2)
    // Snapshot increment should be idempotent
    assertEquals(PreRelease.snapshot.increment, PreRelease.snapshot)
  }

  test("PreRelease.toString (SemVer format)") {
    assertEquals(PreRelease.alpha(N5).toString, "alpha.5")
    assertEquals(PreRelease.snapshot.toString, "snapshot")
  }

  test("PreRelease Ordering") {
    val expectedOrder = List(
      PreRelease.milestone(N1),
      PreRelease.milestone(N5),
      PreRelease.alpha(N1),
      PreRelease.alpha(N5),
      PreRelease.beta(N1),
      PreRelease.releaseCandidate(N1),
      PreRelease.snapshot
    )
    assertEquals(expectedOrder.shuffle.sorted, expectedOrder)
  }

  // --- Version Tests ---

  private val V1_0_0 = Version(MajorVersion.unsafe(1), MinorVersion.unsafe(0), PatchNumber.unsafe(0))
  private val V1_2_3 = Version(MajorVersion.unsafe(1), MinorVersion.unsafe(2), PatchNumber.unsafe(3))
  private val V2_0_0 = Version(MajorVersion.unsafe(2), MinorVersion.unsafe(0), PatchNumber.unsafe(0))

  test("Version.toString (SemVer format)") {
    assertEquals(V1_2_3.toString, "1.2.3")

    val vPre = V1_2_3.copy(preRelease = Some(PreRelease.alpha(N1)))
    assertEquals(vPre.toString, "1.2.3-alpha.1")

    val meta = BuildMetadata(List("build"))
    val vMeta = V1_2_3.copy(buildMetadata = Some(meta))
    assertEquals(vMeta.toString, "1.2.3+build")

    val vFull = vPre.copy(buildMetadata = Some(meta))
    assertEquals(vFull.toString, "1.2.3-alpha.1+build")
  }

  // --- Version Ordering (SemVer Precedence) ---

  test("Version Ordering (Core Components)") {
    assert(V1_0_0 < V1_2_3)
    assert(V1_2_3 < V2_0_0)
    assert(V1_0_0 < V2_0_0)
  }

  test("Version Ordering (Pre-Release vs Release)") {
    // Rule: Release versions have higher precedence than pre-release versions (1.0.0 > 1.0.0-alpha)
    val release = V1_0_0
    val pre = V1_0_0.copy(preRelease = Some(PreRelease.alpha(N1)))
    assert(release > pre)
    assert(pre < release)
  }

  test("Version Ordering (Pre-Release comparison)") {
    // Rule: Pre-releases are compared based on classifier precedence and number
    val vA1 = V1_0_0.copy(preRelease = Some(PreRelease.alpha(N1)))
    val vA5 = V1_0_0.copy(preRelease = Some(PreRelease.alpha(N5)))
    val vB1 = V1_0_0.copy(preRelease = Some(PreRelease.beta(N1)))

    assert(vA1 < vA5) // Number precedence
    assert(vA5 < vB1) // Classifier precedence
  }

  test("Version Ordering (Build Metadata Ignored)") {
    // Rule: Build metadata MUST be ignored when determining version precedence
    val v1 = V1_0_0
    val v1MetaA = V1_0_0.copy(buildMetadata = Some(BuildMetadata(List("A"))))
    val v1MetaB = V1_0_0.copy(buildMetadata = Some(BuildMetadata(List("B"))))

    // They are considered equal in precedence (compare == 0)
    assertEquals(v1.compare(v1MetaA), 0)
    assertEquals(v1MetaA.compare(v1MetaB), 0)

    // Note: They are not structurally equal (case class equality differs)
    assertNotEquals(v1, v1MetaA)
  }

  test("Version comprehensive Ordering test") {
    // Based on SemVer spec examples and internal hierarchy
    // We use the parser here to easily generate the expected Version objects.
    // This implicitly trusts the parser, but focuses the test on the Ordering implementation.
    import PreRelease.Resolver.default // Ensure default resolver is in scope for parsing

    val expectedOrder = List(
      "0.9.0",
      "1.0.0-milestone.1",
      "1.0.0-alpha.1",
      "1.0.0-alpha.5",
      "1.0.0-beta.1",
      "1.0.0-rc.1",
      "1.0.0-snapshot",
      "1.0.0",
      "1.0.0+build.123", // Equal precedence to 1.0.0
      "1.0.1",
      "1.1.0",
      "2.0.0"
    ).map(s => version.parser.VersionParser.parse(s).toOption.get)

    val shuffled = Random.shuffle(expectedOrder)
    val sorted = shuffled.sorted

    // Verify the order by checking that every element is less than or equal to the next one
    sorted.sliding(2).foreach {
      case Seq(a, b) => assert(a <= b, s"Ordering failed: $a should be <= $b")
      case _         => // End of list
    }

    // Specific check for the metadata case ensuring they are adjacent/equivalent in precedence
    val baseIndex = sorted.indexWhere(_.toString.equals("1.0.0"))
    val metaIndex = sorted.indexWhere(_.toString.equals("1.0.0+build.123"))
    assert(baseIndex >= 0 && metaIndex >= 0)
    assertEquals(sorted(baseIndex).compare(sorted(metaIndex)), 0)
  }

end VersionSuite
