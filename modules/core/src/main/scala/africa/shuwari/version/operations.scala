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

import zio.prelude.*

import scala.annotation.targetName

extension [A <: Newtype[Int]](a: A) def increment(v: a.Type): a.Type = a.wrap(a.unwrap(v) + 1)

extension [A <: VersionNumberField](a: A) def zero: a.Type = a(0)

extension (num: MajorVersion)
  @targetName("majorVersionValue") def value: Int = MajorVersion.unwrap(num)
  @targetName("majorVersionIncrement") def increment: MajorVersion = MajorVersion.increment(num)
  def preRelease: Boolean = num === MajorVersion(0)

extension (num: MinorVersion)
  @targetName("minorVersionValue") def value: Int = MinorVersion.unwrap(num)
  @targetName("minorVersionIncrement") def increment: MinorVersion = MinorVersion.increment(num)

extension (num: PatchNumber)
  @targetName("patchNumberValue") def value: Int = PatchNumber.unwrap(num)
  @targetName("patchNumberIncrement") def increment: PatchNumber = PatchNumber.increment(num)

extension (num: PreReleaseNumber)
  @targetName("preReleaseNumberValue") def value: Int = PreReleaseNumber.unwrap(num)
  @targetName("preReleaseNumberIncrement") def increment: PreReleaseNumber = PreReleaseNumber.increment(num)

extension (ver: Version)
  def incrementPatchNumber: Version = Version(ver.majorVersion, ver.minorVersion, ver.patchNumber.increment)
  def incrementMinorVersion: Version = Version(ver.majorVersion, ver.minorVersion.increment, PatchNumber.zero)
  def incrementMajorVersion: Version = Version(ver.majorVersion.increment, MinorVersion.zero, PatchNumber.zero)
