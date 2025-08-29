/** ************************************************************************** Copyright 2023 Shuwari Africa Ltd. * *
  * Licensed under the Apache License, Version 2.0 (the "License"); * you may not use this file except in compliance
  * with the License. * You may obtain a copy of the License at * * http://www.apache.org/licenses/LICENSE-2.0 * *
  * Unless required by applicable law or agreed to in writing, software * distributed under the License is distributed
  * on an "AS IS" BASIS, * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. * See the License
  * for the specific language governing permissions and * limitations under the License. *
  */
package version.codecs.zio

import _root_.zio.json.DeriveJsonCodec
import _root_.zio.json.DeriveJsonDecoder
import _root_.zio.json.DeriveJsonEncoder
import _root_.zio.json.JsonCodec
import _root_.zio.json.JsonDecoder
import _root_.zio.json.JsonError
import _root_.zio.json.internal.RetractReader

import scala.util.Try

import version.BuildMetadata
import version.MajorVersion
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
  JsonCodec.string.transformOrFail[PreReleaseClassifier](decoder, _.toString)

// BuildMetadata as JSON array of strings with validation
given JsonCodec[BuildMetadata] =
  JsonCodec[List[String]].transformOrFail[BuildMetadata](
    ids => BuildMetadata.from(ids).left.map(_.message),
    bm => bm.identifiers
  )

given JsonCodec[PreRelease] =
  val decoder = new JsonDecoder[PreRelease]:
    private val derived = DeriveJsonDecoder.gen[PreRelease]
    override def unsafeDecode(trace: List[JsonError], in: RetractReader): PreRelease = Try(derived.unsafeDecode(trace, in)) match
      case util.Success(v) => v
      case util.Failure(e) => throw JsonDecoder.UnsafeJson(List(JsonError.Message(e.getMessage.nn))) // scalafix:ok
  JsonCodec[PreRelease](DeriveJsonEncoder.gen[PreRelease], decoder)

given JsonCodec[Version] = DeriveJsonCodec.gen[Version]
