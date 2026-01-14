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
package version.codecs.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

import version.*

given JsonValueCodec[MajorVersion] =
  new JsonValueCodec[MajorVersion]:
    override inline def decodeValue(in: JsonReader, default: MajorVersion): MajorVersion =
      MajorVersion.from(in.readInt()) match
        case Right(v) => v
        case Left(e)  => in.decodeError("Error decoding MajorVersion instance. See: " + e.message)
    override inline def encodeValue(x: MajorVersion, out: JsonWriter): Unit = out.writeVal(x.value)
    // scalafix:off DisableSyntax.null, DisableSyntax.asInstanceOf
    override def nullValue: MajorVersion = null.asInstanceOf[MajorVersion]
    // scalafix:on

given JsonValueCodec[MinorVersion] =
  new JsonValueCodec[MinorVersion]:
    override def decodeValue(in: JsonReader, default: MinorVersion): MinorVersion =
      MinorVersion.from(in.readInt()) match
        case Right(v) => v
        case Left(e)  => in.decodeError("Error decoding MinorVersion instance. " + e.message)
    override def encodeValue(x: MinorVersion, out: JsonWriter): Unit = out.writeVal(x.value)
    // scalafix:off DisableSyntax.null, DisableSyntax.asInstanceOf
    override def nullValue: MinorVersion = null.asInstanceOf[MinorVersion]
    // scalafix:on

given JsonValueCodec[PatchNumber] =
  new JsonValueCodec[PatchNumber]:
    override def decodeValue(in: JsonReader, default: PatchNumber): PatchNumber =
      PatchNumber.from(in.readInt()) match
        case Right(v) => v
        case Left(e)  => in.decodeError("Error decoding PatchNumber instance. " + e.message)
    override def encodeValue(x: PatchNumber, out: JsonWriter): Unit = out.writeVal(x.value)
    // scalafix:off DisableSyntax.null, DisableSyntax.asInstanceOf
    override def nullValue: PatchNumber = null.asInstanceOf[PatchNumber]
    // scalafix:on

given JsonValueCodec[PreReleaseNumber] =
  new JsonValueCodec[PreReleaseNumber]:
    override def decodeValue(in: JsonReader, default: PreReleaseNumber): PreReleaseNumber =
      PreReleaseNumber.from(in.readInt()) match
        case Right(v) => v
        case Left(e)  => in.decodeError("Error decoding PreReleaseNumber instance. " + e.message)
    override def encodeValue(x: PreReleaseNumber, out: JsonWriter): Unit = out.writeVal(x.value)
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

// BuildMetadata is an opaque type over List[String]; encode/decode as a JSON array of strings
given JsonValueCodec[BuildMetadata] =
  // Use a codec for the underlying representation List[String]
  val listCodec = JsonCodecMaker.make[List[String]]
  new JsonValueCodec[BuildMetadata]:
    override def decodeValue(in: JsonReader, default: BuildMetadata): BuildMetadata =
      val ids = listCodec.decodeValue(in, Nil)
      BuildMetadata.from(ids) match
        case Right(v) => v
        case Left(e)  => in.decodeError("Error decoding BuildMetadata instance. " + e.message)
    override def encodeValue(x: BuildMetadata, out: JsonWriter): Unit =
      // Use identifiers extension to unwrap
      listCodec.encodeValue(x.identifiers, out)
    // scalafix:off DisableSyntax.null, DisableSyntax.asInstanceOf
    override def nullValue: BuildMetadata = null.asInstanceOf[BuildMetadata]
    // scalafix:on

/** Custom validating codec for [[PreRelease]].
  *
  * Decodes the classifier and optional number fields, then validates via [[PreRelease.from]]. This ensures domain
  * invariants (e.g., Snapshot must not have a number) are enforced at the codec boundary.
  */
given JsonValueCodec[PreRelease] =
  new JsonValueCodec[PreRelease]:
    // scalafix:off DisableSyntax.var, DisableSyntax.while
    override def decodeValue(in: JsonReader, default: PreRelease): PreRelease =
      var classifier: PreReleaseClassifier | Null = null // scalafix:ok DisableSyntax.null
      var number: Option[PreReleaseNumber] = None
      var classifierSeen = false
      var numberSeen = false

      if in.isNextToken('{') then
        if !in.isNextToken('}') then
          in.rollbackToken()
          while
            val fieldName = in.readKeyAsString()
            if fieldName == "classifier" then
              if classifierSeen then in.duplicatedKeyError(fieldName.length)
              classifierSeen = true
              val str = in.readString("")
              classifier = str match
                case PreReleaseClassifier(c) => c
                case _                       => in.decodeError(s"Invalid PreReleaseClassifier: $str")
            else if fieldName == "number" then
              if numberSeen then in.duplicatedKeyError(fieldName.length)
              numberSeen = true
              if in.isNextToken('n') then
                in.readNullOrError((), "expected null or number")
                number = None
              else
                in.rollbackToken()
                number = Some(
                  summon[JsonValueCodec[PreReleaseNumber]].decodeValue(in, null.asInstanceOf[PreReleaseNumber])
                ) // scalafix:ok DisableSyntax.null, DisableSyntax.asInstanceOf
            else in.skip()
            in.isNextToken(',')
          do ()
        end if
      else in.decodeError("expected '{'")
      end if
      // scalafix:on

      if !classifierSeen then in.decodeError("missing required field: classifier")

      PreRelease.from(classifier.nn, number) match
        case Right(pr) => pr
        case Left(err) => in.decodeError(err.message)
    end decodeValue

    override def encodeValue(x: PreRelease, out: JsonWriter): Unit =
      out.writeObjectStart()
      out.writeKey("classifier")
      out.writeVal(x.classifier.show)
      x.number.foreach { n =>
        out.writeKey("number")
        out.writeVal(n.value)
      }
      out.writeObjectEnd()

    // scalafix:off DisableSyntax.null, DisableSyntax.asInstanceOf
    override def nullValue: PreRelease = null.asInstanceOf[PreRelease]
    // scalafix:on

/** Custom validating codec for [[Version]].
  *
  * Decodes all fields and validates the [[PreRelease]] component via [[PreRelease.from]] if present.
  */
given JsonValueCodec[Version] =
  new JsonValueCodec[Version]:
    // scalafix:off DisableSyntax.var, DisableSyntax.while, DisableSyntax.null, DisableSyntax.asInstanceOf
    override def decodeValue(in: JsonReader, default: Version): Version =
      var major: MajorVersion | Null = null
      var minor: MinorVersion | Null = null
      var patch: PatchNumber | Null = null
      var preRelease: Option[PreRelease] = None
      var buildMetadata: Option[BuildMetadata] = None
      var majorSeen, minorSeen, patchSeen, preReleaseSeen, buildMetadataSeen = false

      if in.isNextToken('{') then
        if !in.isNextToken('}') then
          in.rollbackToken()
          while
            val fieldName = in.readKeyAsString()
            if fieldName == "major" then
              if majorSeen then in.duplicatedKeyError(fieldName.length)
              majorSeen = true
              major = summon[JsonValueCodec[MajorVersion]].decodeValue(in, null.asInstanceOf[MajorVersion])
            else if fieldName == "minor" then
              if minorSeen then in.duplicatedKeyError(fieldName.length)
              minorSeen = true
              minor = summon[JsonValueCodec[MinorVersion]].decodeValue(in, null.asInstanceOf[MinorVersion])
            else if fieldName == "patch" then
              if patchSeen then in.duplicatedKeyError(fieldName.length)
              patchSeen = true
              patch = summon[JsonValueCodec[PatchNumber]].decodeValue(in, null.asInstanceOf[PatchNumber])
            else if fieldName == "preRelease" then
              if preReleaseSeen then in.duplicatedKeyError(fieldName.length)
              preReleaseSeen = true
              if in.isNextToken('n') then
                in.readNullOrError((), "expected null or PreRelease object")
                preRelease = None
              else
                in.rollbackToken()
                preRelease = Some(summon[JsonValueCodec[PreRelease]].decodeValue(in, null.asInstanceOf[PreRelease]))
            else if fieldName == "buildMetadata" then
              if buildMetadataSeen then in.duplicatedKeyError(fieldName.length)
              buildMetadataSeen = true
              if in.isNextToken('n') then
                in.readNullOrError((), "expected null or BuildMetadata array")
                buildMetadata = None
              else
                in.rollbackToken()
                buildMetadata = Some(summon[JsonValueCodec[BuildMetadata]].decodeValue(in, null.asInstanceOf[BuildMetadata]))
            else in.skip()
            end if
            in.isNextToken(',')
          do ()
          end while
        end if
      else in.decodeError("expected '{'")
      end if
      // scalafix:on

      if !majorSeen then in.decodeError("missing required field: major")
      if !minorSeen then in.decodeError("missing required field: minor")
      if !patchSeen then in.decodeError("missing required field: patch")

      Version(major.nn, minor.nn, patch.nn, preRelease, buildMetadata)
    end decodeValue

    override def encodeValue(x: Version, out: JsonWriter): Unit =
      out.writeObjectStart()
      out.writeKey("major")
      out.writeVal(x.major.value)
      out.writeKey("minor")
      out.writeVal(x.minor.value)
      out.writeKey("patch")
      out.writeVal(x.patch.value)
      x.preRelease.foreach { pr =>
        out.writeKey("preRelease")
        summon[JsonValueCodec[PreRelease]].encodeValue(pr, out)
      }
      x.buildMetadata.foreach { bm =>
        out.writeKey("buildMetadata")
        summon[JsonValueCodec[BuildMetadata]].encodeValue(bm, out)
      }
      out.writeObjectEnd()

    // scalafix:off DisableSyntax.null, DisableSyntax.asInstanceOf
    override def nullValue: Version = null.asInstanceOf[Version]
    // scalafix:on
