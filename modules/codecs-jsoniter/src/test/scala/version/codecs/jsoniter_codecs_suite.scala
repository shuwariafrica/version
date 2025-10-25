/** ************************************************************** Copyright © Shuwari Africa Ltd. * * This file is
  * licensed to you under the terms of the Apache * License Version 2.0 (the "License"); you may not use this * file
  * except in compliance with the License. You may obtain * a copy of the License at: * *
  * https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable law or agreed to in writing, *
  * software distributed under the License is distributed on an * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  * KIND, * either express or implied. See the License for the specific * language governing permissions and limitations
  * under the * License. *
  */
package version.codecs

import com.github.plokhotnyuk.jsoniter_scala.core.*

import version.MajorVersion
import version.MinorVersion
import version.PatchNumber
import version.PreRelease
import version.PreReleaseClassifier
import version.PreReleaseNumber
import version.Version
import version.codecs.jsoniter.given

class JsoniterCodecsSuite extends munit.FunSuite:

  val validPreReleasePairs: List[(String, PreRelease)] = List(
    """{"classifier":"snapshot"}""" -> PreRelease.snapshot,
    """{"classifier":"m","number":1}""" -> PreRelease.milestone(PreReleaseNumber(1)),
    """{"classifier":"alpha","number":1}""" -> PreRelease.alpha(PreReleaseNumber(1)),
    """{"classifier":"beta","number":1}""" -> PreRelease.beta(PreReleaseNumber(1)),
    """{"classifier":"rc","number":1}""" -> PreRelease.releaseCandidate(PreReleaseNumber(1))
  )

  val validVersionPairs: List[(String, Version)] =
    def appendPreRelease(kv: (String, PreRelease)) =
      s"""{"major":1,"minor":10,"patch":1,"preRelease":${kv._1}}""" -> Version(
        MajorVersion(1),
        MinorVersion(10),
        PatchNumber(1),
        Some(kv._2))
    val finalVersion =
      s"""{"major":1,"minor":10,"patch":1}""" -> Version(MajorVersion(1), MinorVersion(10), PatchNumber(1))
    finalVersion +: validPreReleasePairs.map(appendPreRelease)

  test("Jsoniter decoding of MajorVersion instances") {
    val validMajorVersion1 = "1" -> MajorVersion(1)
    val invalidVersionNumber = "-1"
    assertEquals(readFromString[MajorVersion](s"${validMajorVersion1._1}"), validMajorVersion1._2)
    intercept[JsonReaderException](readFromString[MajorVersion](invalidVersionNumber))
  }

  test("Jsoniter encoding of MajorVersion instances") {
    def validString = "1"
    assertEquals(writeToString(MajorVersion(1)), validString)
  }

  test("Jsoniter decoding of Minor instances") {
    val validMajorVersion1 = "1" -> MinorVersion(1)
    val invalidVersionNumber = "-1"
    assertEquals(readFromString[MinorVersion](s"${validMajorVersion1._1}"), validMajorVersion1._2)
    intercept[JsonReaderException](readFromString[MinorVersion](invalidVersionNumber))
  }

  test("Jsoniter encoding of MinorVersion instances") {
    def validString = "1"
    assertEquals(writeToString(MinorVersion(1)), validString)
  }

  test("Jsoniter decoding of PatchNumber instances") {
    val validMajorVersion1 = "1" -> PatchNumber(1)
    val invalidVersionNumber = "-1"
    assertEquals(readFromString[PatchNumber](s"${validMajorVersion1._1}"), validMajorVersion1._2)
    intercept[JsonReaderException](readFromString[PatchNumber](invalidVersionNumber))
  }

  test("Jsoniter encoding of PatchNumber instances") {
    def validString = "1"
    assertEquals(writeToString(PatchNumber(1)), validString)
  }

  test("Jsoniter decoding of PreReleaseNumber instances") {
    val validMajorVersion1 = "1" -> PreReleaseNumber(1)
    val invalidVersionNumber = "-1"
    assertEquals(readFromString[PreReleaseNumber](s"${validMajorVersion1._1}"), validMajorVersion1._2)
    intercept[JsonReaderException](readFromString[PreReleaseNumber](invalidVersionNumber))
  }

  test("Jsoniter encoding of PreReleaseNumber instances") {
    def validString = "1"
    assertEquals(writeToString(PreReleaseNumber(1)), validString)
  }

  test("Jsoniter decoding of PreReleaseClassifier instances") {
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
      .map(readFromString[PreReleaseClassifier](_))
      .foreach(assertEquals(_, PreReleaseClassifier.Snapshot))

    validMilestonePreReleaseClassifiers
      .map(readFromString[PreReleaseClassifier](_))
      .foreach(assertEquals(_, PreReleaseClassifier.Milestone))

    validAlphaPreReleaseClassifiers
      .map(readFromString[PreReleaseClassifier](_))
      .foreach(assertEquals(_, PreReleaseClassifier.Alpha))

    validBetaPreReleaseClassifiers
      .map(readFromString[PreReleaseClassifier](_))
      .foreach(assertEquals(_, PreReleaseClassifier.Beta))

    validReleaseCandidatePreReleaseClassifiers
      .map(readFromString[PreReleaseClassifier](_))
      .foreach(assertEquals(_, PreReleaseClassifier.ReleaseCandidate))

    intercept[JsonReaderException](readFromString[PreReleaseNumber](s"$invalidInput"))
  }

  test("Jsoniter encoding of PreReleaseClassifier instances") {
    val classifiers = List(
      PreReleaseClassifier.Snapshot -> "\"snapshot\"",
      PreReleaseClassifier.Milestone -> "\"m\"",
      PreReleaseClassifier.Alpha -> "\"alpha\"",
      PreReleaseClassifier.Beta -> "\"beta\"",
      PreReleaseClassifier.ReleaseCandidate -> "\"rc\""
    )
    classifiers.foreach((kv: (PreReleaseClassifier, String)) => assertEquals(writeToString(kv._1), kv._2))
  }

  test("Jsoniter decoding of PreRelease instances") {
    validPreReleasePairs.foreach(kv => assertEquals(readFromString[PreRelease](kv._1), kv._2))
    val invalidInput = """{"classifier":"snapshot","number":1}"""
    intercept[JsonReaderException](readFromString[PreRelease](invalidInput))
  }

  test("Jsoniter encoding of PreRelease instances") {
    validPreReleasePairs.foreach(kv => assertEquals(writeToString(kv._2), kv._1))
  }

  test("Jsoniter decoding of Version instances") {
    validVersionPairs.foreach(kv => assertEquals(readFromString[Version](kv._1), kv._2))
    val invalidInput =
      s"""{"major":1,"minor":10,"patch":1,"preRelease":{"classifier":"snapshot","number":1}}"""
    intercept[JsonReaderException](readFromString[Version](invalidInput))
  }

  test("Jsoniter encoding of Version instances") {
    validVersionPairs.foreach(kv => assertEquals(writeToString(kv._2), kv._1))
  }
end JsoniterCodecsSuite
