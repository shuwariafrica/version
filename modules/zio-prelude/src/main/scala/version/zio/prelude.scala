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
package version.zio

import zio.prelude.*

import version.MajorVersion
import version.MinorVersion
import version.PatchNumber
import version.PreRelease
import version.PreReleaseClassifier
import version.PreReleaseNumber
import version.Version

object prelude:

  given Equal[MajorVersion] = Equal.default
  given Equal[MinorVersion] = Equal.default
  given Equal[PatchNumber] = Equal.default
  given Equal[PreReleaseNumber] = Equal.default
  given Equal[PreReleaseClassifier] = Equal.default
  given Equal[PreRelease] = Equal.default
  given Equal[Version] = Equal.default

  given Ord[MajorVersion] = Ord.default
  given Ord[MinorVersion] = Ord.default
  given Ord[PatchNumber] = Ord.default
  given Ord[PreReleaseNumber] = Ord.default
  given Ord[PreReleaseClassifier] = Ord.default
  given Ord[PreRelease] = Ord.default
  given Ord[Version] = Ord.default

  // Combine by numeric addition, wrapping back into the opaque types
  given Commutative[MajorVersion] = Commutative.make((l, r) => MajorVersion.fromUnsafe(l.value + r.value))
  given Commutative[MinorVersion] = Commutative.make((l, r) => MinorVersion.fromUnsafe(l.value + r.value))
  given Commutative[PatchNumber] = Commutative.make((l, r) => PatchNumber.fromUnsafe(l.value + r.value))
  given Commutative[PreReleaseNumber] = Commutative.make((l, r) => PreReleaseNumber.fromUnsafe(l.value + r.value))
