/*****************************************************************
 * Copyright © Shuwari Africa Ltd. All rights reserved.          *
 *                                                               *
 * Shuwari Africa Ltd. licenses this file to you under the terms *
 * of the Apache License Version 2.0 (the "License"); you may    *
 * not use this file except in compliance with the License. You  *
 * may obtain a copy of the License at:                          *
 *                                                               *
 *     https://www.apache.org/licenses/LICENSE-2.0               *
 *                                                               *
 * Unless required by applicable law or agreed to in writing,    *
 * software distributed under the License is distributed on an   *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,  *
 * either express or implied. See the License for the specific   *
 * language governing permissions and limitations under the      *
 * License.                                                      *
 *****************************************************************/
package africa.shuwari.version

import scala.util.matching.Regex

import africa.shuwari.version.MajorVersion
import africa.shuwari.version.MinorVersion
import africa.shuwari.version.PatchNumber
import africa.shuwari.version.PreReleaseNumber

object Parser:

  def version(version: String): Option[Version] =
    def pattern: Regex =
      """(?i)^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-z-][0-9a-z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-z-][0-9a-z-]*))*))?(?:\+([0-9a-z-]+(?:\.[0-9a-z-]+)*))?$""".r
    version match
      case pattern(major, minor, patch, pre, _) =>
        Some(
          africa.shuwari.version.Version(
            MajorVersion.wrap(major.toInt),
            MinorVersion.wrap(minor.toInt),
            PatchNumber.wrap(patch.toInt),
            Option(pre).map(Parser.preRelease(_))))
      case _ => None

  private inline def preRelease(str: String): PreRelease = str match
    case alphaPreReleasePattern(_, ver)     => PreRelease.alpha(PreReleaseNumber.wrap(ver.toInt))
    case betaPreReleasePattern(_, ver)      => PreRelease.beta(PreReleaseNumber.wrap(ver.toInt))
    case milestonePreReleasePattern(_, ver) => PreRelease.milestone(PreReleaseNumber.wrap(ver.toInt))
    case rcPreReleasePattern(_, ver)        => PreRelease.releaseCandidate(PreReleaseNumber.wrap(ver.toInt))
    case snapshotPattern(_)                 => PreRelease.snapshot
    case genericPreReleasePattern(_)        => PreRelease.unclassified

  private inline def namedPreReleasePattern(classifier: PreReleaseClassifier): Regex =
    classifier.aliases.mkString("""(?i)^(""", "|", """)-?([1-9][0-9]*)$""").r

  private inline def alphaPreReleasePattern: Regex = namedPreReleasePattern(PreReleaseClassifier.Alpha)

  private inline def betaPreReleasePattern: Regex = namedPreReleasePattern(PreReleaseClassifier.Beta)

  private inline def milestonePreReleasePattern: Regex = namedPreReleasePattern(PreReleaseClassifier.Milestone)

  private inline def rcPreReleasePattern: Regex = namedPreReleasePattern(PreReleaseClassifier.ReleaseCandidate)

  private inline def snapshotPattern: Regex = """(?i)^(snapshot)$""".r

  private inline def genericPreReleasePattern = """(.+)""".r
end Parser
