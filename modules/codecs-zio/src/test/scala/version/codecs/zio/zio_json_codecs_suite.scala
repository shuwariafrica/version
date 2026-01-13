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
package version.codecs.zio

import _root_.zio.json.*

import version.MajorVersion
import version.MinorVersion
import version.PatchNumber
import version.PreRelease
import version.PreReleaseClassifier
import version.PreReleaseNumber
import version.Version
import version.codecs.zio.given

class ZioJsonCodecsSuite extends munit.FunSuite:

  private val validPreReleasePairs: List[(String, PreRelease)] = List(
    """{"classifier":"SNAPSHOT"}""" -> PreRelease.snapshot,
    """{"classifier":"milestone","number":1}""" -> PreRelease.milestone(PreReleaseNumber.fromUnsafe(1)),
    """{"classifier":"alpha","number":1}""" -> PreRelease.alpha(PreReleaseNumber.fromUnsafe(1)),
    """{"classifier":"beta","number":1}""" -> PreRelease.beta(PreReleaseNumber.fromUnsafe(1)),
    """{"classifier":"rc","number":1}""" -> PreRelease.releaseCandidate(PreReleaseNumber.fromUnsafe(1))
  )

  private val validVersionPairs: List[(String, Version)] =
    def appendPreRelease(kv: (String, PreRelease)) =
      s"""{"major":1,"minor":10,"patch":1,"preRelease":${kv._1}}""" -> Version(
        MajorVersion.fromUnsafe(1),
        MinorVersion.fromUnsafe(10),
        PatchNumber.fromUnsafe(1),
        Some(kv._2))

    val finalVersion =
      s"""{"major":1,"minor":10,"patch":1}""" -> Version(MajorVersion.fromUnsafe(1), MinorVersion.fromUnsafe(10), PatchNumber.fromUnsafe(1))
    finalVersion +: validPreReleasePairs.map(appendPreRelease)

  test("zio-json decoding of MajorVersion instances") {
    val validMajorVersion1 = "1" -> MajorVersion.fromUnsafe(1)
    val invalidVersionNumber = "-1"
    assertEquals(validMajorVersion1._1.fromJson[MajorVersion], Right(validMajorVersion1._2))
    assert(leftExpected(invalidVersionNumber.fromJson[MajorVersion]))
  }

  test("zio-json encoding of MajorVersion instances") {
    def validString = "1"
    assertEquals(MajorVersion.fromUnsafe(1).toJson, validString)
  }

  test("zio-json decoding of MinorVersion instances") {
    val validMinorVersion1 = "1" -> MinorVersion.fromUnsafe(1)
    val invalidVersionNumber = "-1"
    assertEquals(validMinorVersion1._1.fromJson[MinorVersion], Right(validMinorVersion1._2))
    assert(leftExpected(invalidVersionNumber.fromJson[MinorVersion]))
  }

  test("zio-json encoding of MinorVersion instances") {
    def validString = "1"
    assertEquals(MinorVersion.fromUnsafe(1).toJson, validString)
  }

  test("zio-json decoding of PatchNumber instances") {
    val validPatchNumber1 = "1" -> PatchNumber.fromUnsafe(1)
    val invalidVersionNumber = "-1"
    assertEquals(validPatchNumber1._1.fromJson[PatchNumber], Right(validPatchNumber1._2))
    assert(leftExpected(invalidVersionNumber.fromJson[PatchNumber]))
  }

  test("zio-json encoding of PatchNumber instances") {
    def validString = "1"
    assertEquals(PatchNumber.fromUnsafe(1).toJson, validString)
  }

  test("zio-json decoding of PreReleaseNumber instances") {
    val validPreReleaseNumber1 = "1" -> PreReleaseNumber.fromUnsafe(1)
    val invalidVersionNumber = "-1"
    assertEquals(validPreReleaseNumber1._1.fromJson[PreReleaseNumber], Right(validPreReleaseNumber1._2))
    assert(leftExpected(invalidVersionNumber.fromJson[PreReleaseNumber]))
  }

  test("zio-json encoding of PreReleaseNumber instances") {
    def validString = "1"
    assertEquals(PreReleaseNumber.fromUnsafe(1).toJson, validString)
  }

  test("zio-json decoding of PreReleaseClassifier instances") {
    val validSnapshotPrereleaseClassifiers = List("\"snapshot\"", "\"Snapshot\"", "\"SNAPSHOT\"")

    val validMilestonePreReleaseClassifiers = List(
      "\"milestone\"",
      "\"Milestone\"",
      "\"MILESTONE\"",
      "\"m\"",
      "\"M\""
    )

    val validAlphaPreReleaseClassifiers = List("\"alpha\"", "\"Alpha\"", "\"ALPHA\"", "\"a\"", "\"A\"")
    val validBetaPreReleaseClassifiers = List("\"beta\"", "\"Beta\"", "\"BETA\"", "\"b\"", "\"B\"")
    val validReleaseCandidatePreReleaseClassifiers = List("\"rc\"", "\"Rc\"", "\"RC\"", "\"cr\"", "\"Cr\"", "\"CR\"")
    val invalidInput = "\"-1\""

    validSnapshotPrereleaseClassifiers
      .map(_.fromJson[PreReleaseClassifier])
      .foreach(assertEquals(_, Right(PreReleaseClassifier.Snapshot)))

    validMilestonePreReleaseClassifiers
      .map(_.fromJson[PreReleaseClassifier])
      .foreach(assertEquals(_, Right(PreReleaseClassifier.Milestone)))

    validAlphaPreReleaseClassifiers
      .map(_.fromJson[PreReleaseClassifier])
      .foreach(assertEquals(_, Right(PreReleaseClassifier.Alpha)))

    validBetaPreReleaseClassifiers
      .map(_.fromJson[PreReleaseClassifier])
      .foreach(assertEquals(_, Right(PreReleaseClassifier.Beta)))

    validReleaseCandidatePreReleaseClassifiers
      .map(_.fromJson[PreReleaseClassifier])
      .foreach(assertEquals(_, Right(PreReleaseClassifier.ReleaseCandidate)))

    assert(leftExpected(invalidInput.fromJson[PreReleaseClassifier]))
  }

  test("zio-json encoding of PreReleaseClassifier instances") {
    val classifiers = List(
      PreReleaseClassifier.Snapshot -> "\"SNAPSHOT\"",
      PreReleaseClassifier.Milestone -> "\"milestone\"",
      PreReleaseClassifier.Alpha -> "\"alpha\"",
      PreReleaseClassifier.Beta -> "\"beta\"",
      PreReleaseClassifier.ReleaseCandidate -> "\"rc\""
    )
    classifiers.foreach((kv: (PreReleaseClassifier, String)) => assertEquals(kv._1.toJson, kv._2))
  }

  test("zio-json decoding of PreRelease instances") {
    validPreReleasePairs.foreach(kv => assertEquals(kv._1.fromJson[PreRelease], Right(kv._2)))
    // Validation: Snapshot with a number should fail
    val snapshotWithNumber = """{"classifier":"snapshot","number":1}"""
    assert(leftExpected(snapshotWithNumber.fromJson[PreRelease]), "Expected failure for snapshot with number")
    // Validation: Versioned classifier without a number should fail
    val rcWithoutNumber = """{"classifier":"rc"}"""
    assert(leftExpected(rcWithoutNumber.fromJson[PreRelease]), "Expected failure for RC without number")
  }

  test("zio-json encoding of PreRelease instances") {
    validPreReleasePairs.foreach(kv => assertEquals(kv._2.toJson, kv._1))
  }

  test("zio-json decoding of Version instances") {
    validVersionPairs.foreach(kv => assertEquals(kv._1.fromJson[Version], Right(kv._2)))
    val invalidInput =
      s"""{"major":1,"minor":10,"patch":1,"preRelease":{"classifier":"snapshot","number":1}}"""
    leftExpected(invalidInput.fromJson[Version]) // TODO: Validation of expected error messages
  }

  test("zio-json encoding of Version instances") {
    validVersionPairs.foreach(kv => assertEquals(kv._2.toJson, kv._1))
  }

  private inline def leftExpected[A](value: Either[String, A]): Boolean = leftExpected(value, _ => true)

  private inline def leftExpected[A](v: Either[String, A], errorMatches: String => Boolean): Boolean = v match
    case Left(err) => if errorMatches(err) then true else false
    case _         => false
end ZioJsonCodecsSuite
