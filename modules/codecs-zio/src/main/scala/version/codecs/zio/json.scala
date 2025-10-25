/** ************************************************************** Copyright © Shuwari Africa Ltd. * * This file is
  * licensed to you under the terms of the Apache * License Version 2.0 (the "License"); you may not use this * file
  * except in compliance with the License. You may obtain * a copy of the License at: * *
  * https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable law or agreed to in writing, *
  * software distributed under the License is distributed on an * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  * KIND, * either express or implied. See the License for the specific * language governing permissions and limitations
  * under the * License. *
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

import version.MajorVersion
import version.MinorVersion
import version.PatchNumber
import version.PreRelease
import version.PreReleaseClassifier
import version.PreReleaseNumber
import version.Version
import version.VersionNumberField

private inline def numberCodec[A](companion: VersionNumberField[A])(using JsonCodec[Int]) =
  JsonCodec[Int].transformOrFail[A](companion.make(_).left.map(_.message), companion.unwrap)

given JsonCodec[MajorVersion] = numberCodec(MajorVersion)
given JsonCodec[MinorVersion] = numberCodec(MinorVersion)
given JsonCodec[PatchNumber] = numberCodec(PatchNumber)
given JsonCodec[PreReleaseNumber] = numberCodec(PreReleaseNumber)

given JsonCodec[PreReleaseClassifier] =
  val decoder = (str: String) =>
    str match
      case PreReleaseClassifier(classifier) => Right(classifier)
      case _                                => Left("Error decoding PreReleaseClassifier instance from provided input: " + s"\"$str\"")
  JsonCodec.string.transformOrFail[PreReleaseClassifier](decoder, _.toString)

given JsonCodec[PreRelease] =
  val decoder = new JsonDecoder[PreRelease]:
    private val derived = DeriveJsonDecoder.gen[PreRelease]
    override def unsafeDecode(trace: List[JsonError], in: RetractReader): PreRelease = Try(derived.unsafeDecode(trace, in)) match
      case util.Success(v) => v
      case util.Failure(e) => throw JsonDecoder.UnsafeJson(List(JsonError.Message(e.getMessage.nn))) // scalafix:ok
  JsonCodec[PreRelease](DeriveJsonEncoder.gen[PreRelease], decoder)

given JsonCodec[Version] = DeriveJsonCodec.gen[Version]
