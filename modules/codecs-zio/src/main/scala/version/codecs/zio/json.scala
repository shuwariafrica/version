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

import _root_.zio.json.DeriveJsonCodec
import _root_.zio.json.JsonCodec

import version.MajorVersion
import version.Metadata
import version.MinorVersion
import version.PatchNumber
import version.PreRelease
import version.PreReleaseClassifier
import version.PreReleaseNumber
import version.Version

// Helper to create codecs for Int-backed opaque types using new API
private inline def numberCodec[A](unwrap: A => Int, make: Int => Either[version.errors.VersionError, A])(using
  JsonCodec[Int]
) =
  JsonCodec[Int].transformOrFail[A](i => make(i).left.map(_.message), unwrap)

given JsonCodec[MajorVersion] = numberCodec(_.value, MajorVersion.from)
given JsonCodec[MinorVersion] = numberCodec(_.value, MinorVersion.from)
given JsonCodec[PatchNumber] = numberCodec(_.value, PatchNumber.from)
given JsonCodec[PreReleaseNumber] = numberCodec(_.value, PreReleaseNumber.from)

given JsonCodec[PreReleaseClassifier] =
  val decoder = (str: String) =>
    str match
      case PreReleaseClassifier(classifier) => Right(classifier)
      case _                                => Left("Error decoding PreReleaseClassifier instance from provided input: " + s"\"$str\"")
  JsonCodec.string.transformOrFail[PreReleaseClassifier](decoder, _.show)

// Metadata as JSON array of strings with validation
given JsonCodec[Metadata] =
  JsonCodec[List[String]].transformOrFail[Metadata](
    ids => Metadata.from(ids).left.map(_.message),
    bm => bm.identifiers
  )

/** Intermediate representation for [[PreRelease]] decoding, allowing field-by-field parsing before validation. */
private case class PreReleaseRaw(classifier: PreReleaseClassifier, number: Option[PreReleaseNumber])
private object PreReleaseRaw:
  given JsonCodec[PreReleaseRaw] = DeriveJsonCodec.gen[PreReleaseRaw]

/** Custom validating codec for [[PreRelease]].
  *
  * Decodes the classifier and optional number fields, then validates via [[PreRelease.from]]. This ensures domain
  * invariants (e.g., Snapshot must not have a number) are enforced at the codec boundary.
  */
given JsonCodec[PreRelease] =
  JsonCodec[PreReleaseRaw].transformOrFail[PreRelease](
    raw => PreRelease.from(raw.classifier, raw.number).left.map(_.message),
    pr => PreReleaseRaw(pr.classifier, pr.number)
  )

/** Intermediate representation for [[Version]] decoding. */
private case class VersionRaw(
  major: MajorVersion,
  minor: MinorVersion,
  patch: PatchNumber,
  preRelease: Option[PreRelease],
  metadata: Option[Metadata]
)
private object VersionRaw:
  given JsonCodec[VersionRaw] = DeriveJsonCodec.gen[VersionRaw]

/** Custom codec for [[Version]] that validates the [[PreRelease]] component if present. */
given JsonCodec[Version] =
  JsonCodec[VersionRaw].transform[Version](
    raw => Version(raw.major, raw.minor, raw.patch, raw.preRelease, raw.metadata),
    v => VersionRaw(v.major, v.minor, v.patch, v.preRelease, v.metadata)
  )
