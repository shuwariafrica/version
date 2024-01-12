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
package africa.shuwari.version.codecs

import _root_.zio.json.DeriveJsonCodec
import _root_.zio.json.DeriveJsonDecoder
import _root_.zio.json.DeriveJsonEncoder
import _root_.zio.json.JsonCodec
import _root_.zio.json.JsonDecoder
import _root_.zio.json.JsonError
import _root_.zio.json.internal.RetractReader
import _root_.zio.prelude.Newtype
import _root_.zio.prelude.ZValidation.Failure
import _root_.zio.prelude.ZValidation.Success

import scala.util.Try

import africa.shuwari.version.*

package object zio:

  private inline def codec[A <: Newtype[B], B](t: A)(using JsonCodec[B]) =
    def decodeType(t: A, v: B): Either[String, t.Type] = t.make(v) match
      case Success(_, res) => Right(res)
      case Failure(_, err) => Left(err.mkString)
    JsonCodec[B].transformOrFail[t.Type](decodeType(t, _), t.unwrap)

  given JsonCodec[MajorVersion] = codec(MajorVersion)
  given JsonCodec[MinorVersion] = codec(MinorVersion)
  given JsonCodec[PatchNumber] = codec(PatchNumber)
  given JsonCodec[PreReleaseNumber] = codec(PreReleaseNumber)

  given JsonCodec[PreReleaseClassifier] =
    val decoder = (str: String) =>
      str match
        case PreReleaseClassifier(classifier) => Right(classifier)
        case _ => Left("Error decoding PreReleaseClassifier instance from provided input: " + s"\"$str\"")
    JsonCodec.string.transformOrFail[PreReleaseClassifier](decoder, _.toString)

  given JsonCodec[PreRelease] =
    val decoder = new JsonDecoder[PreRelease]:
      private val derived = DeriveJsonDecoder.gen[PreRelease]
      override def unsafeDecode(trace: List[JsonError], in: RetractReader): PreRelease = Try(
        derived.unsafeDecode(trace, in)) match
        case util.Success(v) => v
        case util.Failure(e) => throw JsonDecoder.UnsafeJson(List(JsonError.Message(e.getMessage.nn))) // scalafix:ok
    JsonCodec[PreRelease](DeriveJsonEncoder.gen[PreRelease], decoder)

  given JsonCodec[Version] = DeriveJsonCodec.gen[Version]

end zio
