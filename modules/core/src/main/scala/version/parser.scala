/** ************************************************************** Copyright © Shuwari Africa Ltd. * * This file is
  * licensed to you under the terms of the Apache * License Version 2.0 (the "License"); you may not use this * file
  * except in compliance with the License. You may obtain * a copy of the License at: * *
  * https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable law or agreed to in writing, *
  * software distributed under the License is distributed on an * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  * KIND, * either express or implied. See the License for the specific * language governing permissions and limitations
  * under the * License. *
  */
package version

import scala.util.matching.Regex

/** Provides methods to parse version strings into `Version` objects. */
object VersionParser:

  /** Parses a version string into an `Option[Version]`.
    *
    * @param version
    *   the version string to parse.
    * @return
    *   an `Option[Version]` if the string is valid, None otherwise.
    */
  def version(version: String): Option[Version] =
    def pattern: Regex =
      """(?i)^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-z-][0-9a-z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-z-][0-9a-z-]*))*))?(?:\+([0-9a-z-]+(?:\.[0-9a-z-]+)*))?$""".r
    version match
      case pattern(major, minor, patch, pre, _) =>
        Some(
          Version(
            MajorVersion(major.toInt),
            MinorVersion(minor.toInt),
            PatchNumber(patch.toInt),
            Option(pre).map(VersionParser.preRelease)))
      case _ => None

  // FIXME: Handle validation of non supported pre-release classifiers/patterns

  /** Parses a pre-release string into a `PreRelease` object.
    *
    * @param str
    *   the pre-release string to parse.
    * @return
    *   a `PreRelease` object.
    */
  private inline def preRelease(str: String): PreRelease = str match
    case alphaPreReleasePattern(_, ver)     => PreRelease.alpha(PreReleaseNumber(ver.toInt))
    case betaPreReleasePattern(_, ver)      => PreRelease.beta(PreReleaseNumber(ver.toInt))
    case milestonePreReleasePattern(_, ver) => PreRelease.milestone(PreReleaseNumber(ver.toInt))
    case rcPreReleasePattern(_, ver)        => PreRelease.releaseCandidate(PreReleaseNumber(ver.toInt))
    case snapshotPattern(_)                 => PreRelease.snapshot
    case genericPreReleasePattern(_)        => PreRelease.unclassified

  /** Creates a regex pattern for a named pre-release classifier.
    *
    * @param classifier
    *   the pre-release classifier.
    * @return
    *   a regex pattern for the classifier.
    */
  private inline def namedPreReleasePattern(classifier: PreReleaseClassifier): Regex =
    classifier.aliases.mkString("""(?i)^(""", "|", """)-?([1-9][0-9]*)$""").r

  private inline def alphaPreReleasePattern: Regex = namedPreReleasePattern(PreReleaseClassifier.Alpha)

  private inline def betaPreReleasePattern: Regex = namedPreReleasePattern(PreReleaseClassifier.Beta)

  private inline def milestonePreReleasePattern: Regex = namedPreReleasePattern(PreReleaseClassifier.Milestone)

  private inline def rcPreReleasePattern: Regex = namedPreReleasePattern(PreReleaseClassifier.ReleaseCandidate)

  private inline def snapshotPattern: Regex = """(?i)^(snapshot)$""".r

  private inline def genericPreReleasePattern = """(.+)""".r
end VersionParser
