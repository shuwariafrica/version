package version.codecs.yaml

import org.virtuslab.yaml.*

import scala.util.Try

import version.*
import version.errors.VersionError

/** A helper method to create a `YamlCodec` for opaque types that wrap an `Int` and have failable constructors.
  *
  * @param unwrap
  *   A function to extract the `Int` from the opaque type `T`.
  * @param make
  *   A failable constructor that takes an `Int` and returns an `Either[VersionError, T]`.
  * @return
  *   A `YamlCodec[T]`.
  */
private def versionNumberFieldCodec[T](
  unwrap: T => Int,
  make: Int => Either[VersionError, T]
): YamlCodec[T] =
  val encoder: YamlEncoder[T] = YamlEncoder.forInt.mapContra(unwrap)
  val decoder: YamlDecoder[T] = new YamlDecoder[T]:
    override def construct(node: Node)(implicit settings: LoadSettings): Either[ConstructError, T] =
      YamlDecoder.forInt.construct(node).flatMap { intValue =>
        make(intValue).left.map(err => ConstructError.from(err.message, node))
      }
  YamlCodec.make(using decoder, encoder)

// New API: use `.value` to unwrap and `.from` as smart constructor
given YamlCodec[MajorVersion] = versionNumberFieldCodec(_.value, MajorVersion.from)
given YamlCodec[MinorVersion] = versionNumberFieldCodec(_.value, MinorVersion.from)
given YamlCodec[PatchNumber] = versionNumberFieldCodec(_.value, PatchNumber.from)
given YamlCodec[PreReleaseNumber] = versionNumberFieldCodec(_.value, PreReleaseNumber.from)

given YamlCodec[PreReleaseClassifier] =
  val encoder: YamlEncoder[PreReleaseClassifier] = YamlEncoder.forString.mapContra(_.toString)
  val decoder: YamlDecoder[PreReleaseClassifier] = new YamlDecoder[PreReleaseClassifier]:
    override def construct(node: Node)(implicit settings: LoadSettings): Either[ConstructError, PreReleaseClassifier] =
      YamlDecoder.forString.construct(node).flatMap {
        case PreReleaseClassifier(classifier) => Right(classifier)
        case other                            => Left(ConstructError.from(s"Unknown PreReleaseClassifier: $other", node))
      }
  YamlCodec.make(using decoder, encoder)

/** A custom `YamlCodec` for `PreRelease` to handle the validation logic within its constructor. */
given YamlCodec[PreRelease] =
  // Let the macro generate the basic encoder and a decoder for the case class structure.
  val derivedCodec = YamlCodec.derived[PreRelease]

  // Create a new decoder that wraps the derived one to catch validation exceptions.
  val validatingDecoder: YamlDecoder[PreRelease] = new YamlDecoder[PreRelease]:
    override def construct(node: Node)(implicit settings: LoadSettings): Either[ConstructError, PreRelease] =
      // First, attempt to decode using the derived decoder.
      // Then, wrap the result in a Try to catch exceptions from the PreRelease constructor.
      Try(derivedCodec.construct(node)).toEither.flatten.left.map {
        case ce: ConstructError    => ce // Propagate existing construct errors
        case otherError: Throwable => ConstructError.from(otherError.getMessage.nn, node)
      }
  // The derived encoder is safe to use as is.
  YamlCodec.make(using validatingDecoder, derivedCodec)

// BuildMetadata is an opaque List[String]; encode/decode as YAML sequence of strings
given YamlCodec[BuildMetadata] =
  val enc: YamlEncoder[BuildMetadata] = YamlEncoder.forSeq[String].mapContra(_.identifiers)
  val dec: YamlDecoder[BuildMetadata] = new YamlDecoder[BuildMetadata]:
    override def construct(node: Node)(implicit settings: LoadSettings): Either[ConstructError, BuildMetadata] =
      YamlDecoder.forSeq[String].construct(node).flatMap { seq =>
        BuildMetadata.from(seq.toList).left.map(err => ConstructError.from(err.message, node))
      }
  YamlCodec.make(using dec, enc)

/** The `YamlCodec` for `Version` can be safely derived as it has no complex constructor validation. */
given YamlCodec[Version] = YamlCodec.derived
