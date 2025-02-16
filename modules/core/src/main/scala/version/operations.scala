/****************************************************************
 * Copyright © Shuwari Africa Ltd.                              *
 *                                                              *
 * This file is licensed to you under the terms of the Apache   *
 * License Version 2.0 (the "License"); you may not use this    *
 * file except in compliance with the License. You may obtain   *
 * a copy of the License at:                                    *
 *                                                              *
 *     https://www.apache.org/licenses/LICENSE-2.0              *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, *
 * either express or implied. See the License for the specific  *
 * language governing permissions and limitations under the     *
 * License.                                                     *
 ****************************************************************/
package version

import scala.annotation.targetName

extension (v: MajorVersion)
  /** Retrieves the value of the major version. */
  @targetName("majorVersionValue") def value: Int = MajorVersion.unwrap(v)

  /** Increments the major version by one.
    *
    * @return
    *   a new `MajorVersion` incremented by one.
    */
  @targetName("majorVersionIncrement") def increment: MajorVersion = MajorVersion.increment(v)

  /** Checks if the major version indicates a pre-release.
    * @return
    *   true if the major version is 0, false otherwise.
    */
  def preRelease: Boolean = v == MajorVersion(0) // scalafix:ok

extension (v: MinorVersion)
  /** Checks if the major version indicates a pre-release.
    * @return
    *   true if the major version is 0, false otherwise.
    */
  @targetName("minorVersionValue") def value: Int = MinorVersion.unwrap(v)

  /** Increments the minor version by one.
    *
    * @return
    *   a new `MinorVersion` incremented by one.
    */
  @targetName("minorVersionIncrement") def increment: MinorVersion = MinorVersion.increment(v)

extension (v: PatchNumber)
  /** Retrieves the value of the patch number.
    * @return
    *   the integer value of the patch number.
    */
  @targetName("patchNumberValue") def value: Int = PatchNumber.unwrap(v)

  /** Increments the patch number by one.
    *
    * @return
    *   a new `PatchNumber` incremented by one.
    */
  @targetName("patchNumberIncrement") def increment: PatchNumber = PatchNumber.increment(v)

extension (v: PreReleaseNumber)
  /** Retrieves the value of the pre-release number.
    * @return
    *   the integer value of the pre-release number.
    */
  @targetName("preReleaseNumberValue") def value: Int = PreReleaseNumber.unwrap(v)

  /** Increments the pre-release number by one.
    *
    * @return
    *   a new `PreReleaseNumber` incremented by one.
    */
  @targetName("preReleaseNumberIncrement") def increment: PreReleaseNumber = PreReleaseNumber.increment(v)

extension (v: PreReleaseClassifier)
  /** Retrieves the list of aliases for the pre-release classifier. */
  def aliases: List[String] = PreReleaseClassifier.aliases(v)

  /** Checks if the pre-release classifier requires a [[PreReleaseNumber]] */
  def versioned: Boolean = PreReleaseClassifier.versioned(v)

extension (v: Version)
  /** Increments the patch number of the version.
    * @return
    *   a new `Version` with the patch number incremented.
    */
  def incrementPatchNumber: Version = Version(v.major, v.minor, v.patch.increment)

  /** Increments the minor version of the version.
    *
    * @return
    *   a new `Version` with the minor version incremented and patch number reset.
    */
  def incrementMinorVersion: Version = Version(v.major, v.minor.increment, PatchNumber.initial)

  /** Increments the major version of the version.
    *
    * @return
    *   a new `Version` with the major version incremented and minor and patch numbers reset.
    */
  def incrementMajorVersion: Version = Version(v.major.increment, MinorVersion.initial, PatchNumber.initial)
