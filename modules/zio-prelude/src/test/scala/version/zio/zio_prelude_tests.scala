package version.zio

import munit.FunSuite
import zio.prelude.given

import version.*
import version.zio.prelude.given

class PreludeSpec extends FunSuite:

  test("Equal[MajorVersion] should correctly compare MajorVersion instances") {
    assert(MajorVersion.unsafe(1) === MajorVersion.unsafe(1))
    assert(MajorVersion.unsafe(1) !== MajorVersion.unsafe(2))
  }
  test("Equal[MinorVersion] should correctly compare MinorVersion instances") {
    assert(MinorVersion.unsafe(1) === MinorVersion.unsafe(1))
    assert(MinorVersion.unsafe(1) !== MinorVersion.unsafe(2))
  }
  test("Equal[PatchNumber] should correctly compare PatchNumber instances") {
    assert(PatchNumber.unsafe(1) === PatchNumber.unsafe(1))
    assert(PatchNumber.unsafe(1) !== PatchNumber.unsafe(2))
  }
  test("Equal[PreReleaseNumber] should correctly compare PreReleaseNumber instances") {
    assert(PreReleaseNumber.unsafe(1) === PreReleaseNumber.unsafe(1))
    assert(PreReleaseNumber.unsafe(1) !== PreReleaseNumber.unsafe(2))
  }
  test("Equal[PreReleaseClassifier] should correctly compare PreReleaseClassifier instances") {
    assert(PreReleaseClassifier.Alpha === PreReleaseClassifier.Alpha)
    assert(PreReleaseClassifier.Alpha !== PreReleaseClassifier.Beta)
  }
  test("Equal[PreRelease] should correctly compare PreRelease instances") {
    assert(PreRelease.alpha(PreReleaseNumber.unsafe(1)) === PreRelease.alpha(PreReleaseNumber.unsafe(1)))
    assert(PreRelease.beta(PreReleaseNumber.unsafe(1)) !== PreRelease.alpha(PreReleaseNumber.unsafe(1)))
    assert(PreRelease.alpha(PreReleaseNumber.unsafe(2)) !== PreRelease.alpha(PreReleaseNumber.unsafe(1)))
  }

  test("Ord[MajorVersion] should correctly compare MajorVersion instances") {
    assert(MajorVersion.unsafe(1) === MajorVersion.unsafe(1))
    assert(!(MajorVersion.unsafe(1) === MajorVersion.unsafe(2)))
  }
  test("Ord[MinorVersion] should correctly compare MinorVersion instances") {
    assertEquals(MinorVersion.unsafe(1), MinorVersion.unsafe(1))
    assert(MinorVersion.unsafe(1) < MinorVersion.unsafe(2))
  }

  test("Ord[PatchNumber] should correctly compare PatchNumber instances") {
    assertEquals(PatchNumber.unsafe(1), PatchNumber.unsafe(1))
    assert(PatchNumber.unsafe(1) < PatchNumber.unsafe(2))
  }

  test("Ord[PreReleaseNumber] should correctly compare PreReleaseNumber instances") {
    assertEquals(PreReleaseNumber.unsafe(1), PreReleaseNumber.unsafe(1))
    assert(PreReleaseNumber.unsafe(1) < PreReleaseNumber.unsafe(2))
  }

  test("Ord[PreReleaseClassifier] should correctly compare PreReleaseClassifier instances") {
    assertEquals(PreReleaseClassifier.Alpha, PreReleaseClassifier.Alpha)
    assert(PreReleaseClassifier.Alpha < PreReleaseClassifier.Beta)
  }

  test("Ord[PreRelease] should correctly compare PreRelease instances") {
    assertEquals(PreRelease.alpha(PreReleaseNumber.unsafe(1)), PreRelease.alpha(PreReleaseNumber.unsafe(1)))
    assert(PreRelease.alpha(PreReleaseNumber.unsafe(1)) < PreRelease.beta(PreReleaseNumber.unsafe(1)))
  }

  test("Ord[Version] should correctly compare Version instances") {
    assertEquals(
      Version(MajorVersion.unsafe(1), MinorVersion.unsafe(0), PatchNumber.unsafe(0)),
      Version(MajorVersion.unsafe(1), MinorVersion.unsafe(0), PatchNumber.unsafe(0))
    )
    assert(
      Version(MajorVersion.unsafe(1), MinorVersion.unsafe(0), PatchNumber.unsafe(0)) <
        Version(MajorVersion.unsafe(2), MinorVersion.unsafe(0), PatchNumber.unsafe(0))
    )
  }

  test("Commutative[MajorVersion] should correctly combine MajorVersion instances") {
    val expected = MajorVersion.unsafe(3)
    val a = MajorVersion.unsafe(1)
    val b = MajorVersion.unsafe(2)
    assertEquals(a <> b, expected)
    assertEquals(b <> a, expected)
  }

  test("Commutative[MinorVersion] should correctly combine MinorVersion instances") {
    val expected = MinorVersion.unsafe(3)
    val a = MinorVersion.unsafe(1)
    val b = MinorVersion.unsafe(2)
    assertEquals(a <> b, expected)
    assertEquals(b <> a, expected)
  }

  test("Commutative[PatchNumber] should correctly combine PatchNumber instances") {
    val expected = PatchNumber.unsafe(3)
    val a = PatchNumber.unsafe(1)
    val b = PatchNumber.unsafe(2)
    assertEquals(a <> b, expected)
    assertEquals(b <> a, expected)
  }

  test("Commutative[PreReleaseNumber] should correctly combine PreReleaseNumber instances") {
    val expected = PreReleaseNumber.unsafe(3)
    val a = PreReleaseNumber.unsafe(1)
    val b = PreReleaseNumber.unsafe(2)
    assertEquals(a <> b, expected)
    assertEquals(b <> a, expected)
  }
end PreludeSpec
