package version.codecs.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

import scala.util.Try

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

given JsonValueCodec[Version] = JsonCodecMaker.make[Version]
