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
package version.codecs.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

import scala.util.Try

import version.*

given JsonValueCodec[MajorVersion] =
  new JsonValueCodec[MajorVersion]:
    override def decodeValue(in: JsonReader, default: MajorVersion): MajorVersion =
      MajorVersion.make(in.readInt()) match
        case Right(v) => v
        case Left(e)  => in.decodeError("Error decoding MajorVersion instance. See: " + e.message)
    override def encodeValue(x: MajorVersion, out: JsonWriter): Unit = out.writeVal(MajorVersion.unwrap(x))
    // scalafix:off DisableSyntax.null, DisableSyntax.asInstanceOf
    override def nullValue: MajorVersion = null.asInstanceOf[MajorVersion]
    // scalafix:on

given JsonValueCodec[MinorVersion] =
  new JsonValueCodec[MinorVersion]:
    override def decodeValue(in: JsonReader, default: MinorVersion): MinorVersion =
      MinorVersion.make(in.readInt()) match
        case Right(v) => v
        case Left(e)  => in.decodeError("Error decoding MinorVersion instance. " + e.message)
    override def encodeValue(x: MinorVersion, out: JsonWriter): Unit = out.writeVal(MinorVersion.unwrap(x))
    // scalafix:off DisableSyntax.null, DisableSyntax.asInstanceOf
    override def nullValue: MinorVersion = null.asInstanceOf[MinorVersion]
    // scalafix:on

given JsonValueCodec[PatchNumber] =
  new JsonValueCodec[PatchNumber]:
    override def decodeValue(in: JsonReader, default: PatchNumber): PatchNumber =
      PatchNumber.make(in.readInt()) match
        case Right(v) => v
        case Left(e)  => in.decodeError("Error decoding PatchNumber instance. " + e.message)
    override def encodeValue(x: PatchNumber, out: JsonWriter): Unit = out.writeVal(PatchNumber.unwrap(x))
    // scalafix:off DisableSyntax.null, DisableSyntax.asInstanceOf
    override def nullValue: PatchNumber = null.asInstanceOf[PatchNumber]
    // scalafix:on

given JsonValueCodec[PreReleaseNumber] =
  new JsonValueCodec[PreReleaseNumber]:
    override def decodeValue(in: JsonReader, default: PreReleaseNumber): PreReleaseNumber =
      PreReleaseNumber.make(in.readInt()) match
        case Right(v) => v
        case Left(e)  => in.decodeError("Error decoding PreReleaseNumber instance. " + e.message)
    override def encodeValue(x: PreReleaseNumber, out: JsonWriter): Unit = out.writeVal(PreReleaseNumber.unwrap(x))
    // scalafix:off DisableSyntax.null, DisableSyntax.asInstanceOf
    override def nullValue: PreReleaseNumber = null.asInstanceOf[PreReleaseNumber]
    // scalafix:on

given JsonValueCodec[PreReleaseClassifier] = new JsonValueCodec[PreReleaseClassifier]:
  override def decodeValue(in: JsonReader, default: PreReleaseClassifier): PreReleaseClassifier =
    val input = in.readString("")
    input match
      case PreReleaseClassifier(v) => v
      case _                       => in.decodeError("Error decoding PreReleaseClassifier instance.")
  override def encodeValue(x: PreReleaseClassifier, out: JsonWriter): Unit = out.writeVal(x.toString)
  // scalafix:off DisableSyntax.null, DisableSyntax.asInstanceOf
  override def nullValue: PreReleaseClassifier = null.asInstanceOf[PreReleaseClassifier]
  // scalafix:on

given JsonValueCodec[PreRelease] =
  val underlying = JsonCodecMaker.make[PreRelease]
  new JsonValueCodec[PreRelease]:
    override def decodeValue(in: JsonReader, default: PreRelease): PreRelease = Try(underlying.decodeValue(in, default)) match
      case util.Success(v) => v
      case util.Failure(e) => in.decodeError("Error decoding PreRelease instance. " + e.getMessage)
    override def encodeValue(x: PreRelease, out: JsonWriter): Unit = underlying.encodeValue(x, out)
    // scalafix:off DisableSyntax.null, DisableSyntax.asInstanceOf
    override def nullValue: PreRelease = null.asInstanceOf[PreRelease]
    // scalafix:on

given JsonValueCodec[Version] = JsonCodecMaker.make
