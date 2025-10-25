/** ************************************************************** Copyright © Shuwari Africa Ltd. * * This file is
  * licensed to you under the terms of the Apache * License Version 2.0 (the "License"); you may not use this * file
  * except in compliance with the License. You may obtain * a copy of the License at: * *
  * https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable law or agreed to in writing, *
  * software distributed under the License is distributed on an * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  * KIND, * either express or implied. See the License for the specific * language governing permissions and limitations
  * under the * License. *
  */
package version

import scala.annotation.targetName

extension (v: MajorVersion)
  /** Retrieves the value of the major version. */
  @targetName("majorVersionValue") inline def value: Int = MajorVersion.unwrap(v)
  /** Returns a new [[MinorVersion]] incremented by one. */
  @targetName("majorVersionIncrement") inline def increment: MajorVersion = MajorVersion.increment(v)
  /** Returns false if the major version is 0, true otherwise. */
  inline def isStable: Boolean = MajorVersion.isStable(v)

extension (v: MinorVersion)
  /** Retrieves the value of the minor version. */
  @targetName("minorVersionValue") inline def value: Int = MinorVersion.unwrap(v)

  /** Returns a new [[MinorVersion]] incremented by one. */
  @targetName("minorVersionIncrement") inline def increment: MinorVersion = MinorVersion.increment(v)

extension (v: PatchNumber)
  /** Retrieves the value of the patch number. */
  @targetName("patchNumberValue") inline def value: Int = PatchNumber.unwrap(v)

  /** Returns a new [[PatchNumber]] incremented by one. */
  @targetName("patchNumberIncrement") inline def increment: PatchNumber = PatchNumber.increment(v)

extension (v: PreReleaseNumber)
  /** Retrieves the value of the pre-release number. */
  @targetName("preReleaseNumberValue") inline def value: Int = PreReleaseNumber.unwrap(v)

  /** Returns a new [[PreReleaseNumber]] incremented by one. */
  @targetName("preReleaseNumberIncrement") inline def increment: PreReleaseNumber = PreReleaseNumber.increment(v)

extension (v: PreReleaseClassifier)
  /** Retrieves the list of aliases for the pre-release classifier. */
  inline def aliases: List[String] = PreReleaseClassifier.aliases(v)

  /** Checks if the pre-release classifier requires a [[PreReleaseNumber]]. */
  inline def versioned: Boolean = PreReleaseClassifier.versioned(v)

extension (v: PreRelease)
  // scalafix:off DisableSyntax.==
  inline def milestone: Boolean = v.classifier == PreReleaseClassifier.Milestone
  inline def alpha: Boolean = v.classifier == PreReleaseClassifier.Alpha
  inline def beta: Boolean = v.classifier == PreReleaseClassifier.Beta
  inline def rc: Boolean = v.classifier == PreReleaseClassifier.ReleaseCandidate
  inline def snapshot: Boolean = v.classifier == PreReleaseClassifier.Snapshot
  inline def unclassified: Boolean = v.classifier == PreReleaseClassifier.Unclassified
  // scalafix:on DisableSyntax.==

extension (v: Version)
  /** Returns a new [[Version]] with the patch number incremented. */
  inline def incrementPatchNumber: Version = Version(v.major, v.minor, v.patch.increment)

  /** Returns a new [[Version]] with the minor version incremented and patch number reset. */
  inline def incrementMinorVersion: Version = Version(v.major, v.minor.increment, PatchNumber.reset)

  /** Returns a new [[Version]] with the major version incremented and minor and patch numbers reset. */
  inline def incrementMajorVersion: Version = Version(v.major.increment, MinorVersion.reset, PatchNumber.reset)

  /** Returns true if the major version is non-zero, false otherwise. */
  inline def isStable: Boolean = Version.isStable(v)

  /** Returns true if the version has pre-release information available, false otherwise. */
  inline def isPreRelease: Boolean = v.preRelease.nonEmpty
