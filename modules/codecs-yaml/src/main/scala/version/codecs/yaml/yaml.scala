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
package version.codecs.yaml

import org.virtuslab.yaml.*

import version.*
import version.errors.VersionError

// scala-yaml's Tag type does not provide CanEqual; required for null tag detection
private given CanEqual[Tag, Tag] = CanEqual.derived

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

/** Custom validating codec for [[PreRelease]].
  *
  * Decodes the classifier and optional number fields, then validates via [[PreRelease.from]]. This ensures domain
  * invariants (e.g., Snapshot must not have a number) are enforced at the codec boundary.
  */
given YamlCodec[PreRelease] =
  val encoder: YamlEncoder[PreRelease] = new YamlEncoder[PreRelease]:
    override def asNode(pr: PreRelease): Node =
      val classifierNode = Node.ScalarNode(pr.classifier.show)
      val numberNode = pr.number match
        case Some(n) => Node.ScalarNode(n.value.toString)
        case None    => Node.ScalarNode("null")
      Node.MappingNode(
        Node.ScalarNode("classifier") -> classifierNode,
        Node.ScalarNode("number") -> numberNode
      )
  val decoder: YamlDecoder[PreRelease] = new YamlDecoder[PreRelease]:
    override def construct(node: Node)(implicit settings: LoadSettings): Either[ConstructError, PreRelease] =
      node match
        case Node.MappingNode(mappings, _) =>
          def findField(name: String): Option[Node] =
            mappings.collectFirst { case (Node.ScalarNode(n, _), v) if n == name => v }
          for
            classifierNode <- findField("classifier").toRight(ConstructError.from("missing required field: classifier", node))
            classifier <- summon[YamlDecoder[PreReleaseClassifier]].construct(classifierNode)
            number <- findField("number") match
              case None                                                => Right(None)
              case Some(Node.ScalarNode(_, tag)) if tag == Tag.nullTag => Right(None)
              case Some(n)                                             => summon[YamlDecoder[PreReleaseNumber]].construct(n).map(Some(_))
            result <- PreRelease.from(classifier, number).left.map(err => ConstructError.from(err.message, node))
          yield result
        case _ => Left(ConstructError.from("expected mapping node for PreRelease", node))
  YamlCodec.make(using decoder, encoder)
end given

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
