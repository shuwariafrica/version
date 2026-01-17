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
package version

import boilerplate.OpaqueType

import version.Metadata.fromUnsafe
import version.errors.InvalidMetadata

/** Represents build metadata as defined by the Semantic Versioning 2.0.0 specification.
 *
 * Identifiers must comprise only ASCII alphanumerics and hyphens `[0-9A-Za-z-]` and must not be empty. Build metadata
 * does not affect version precedence.
 *
 * Instances may be constructed via [[Metadata$ Metadata]].
 */
opaque type Metadata = List[String]

/** Provides factory methods and operations for [[Metadata]].
 *
 * Extends [[boilerplate.OpaqueType]] with build-metadata-specific validation and semantics.
 *
 * @see
 *   [[Metadata]] opaque type for representation details.
 */
object Metadata extends OpaqueType[Metadata]:
  type Type = List[String]
  type Error = InvalidMetadata

  inline def wrap(ids: List[String]): Metadata = ids
  inline def unwrap(bm: Metadata): List[String] = bm

  protected inline def validate(ids: List[String]): Option[Error] =
    if ids.nonEmpty && ids.forall(isValidIdentifier) then None
    else Some(InvalidMetadata(ids))

  /** Checks if an identifier is valid according to SemVer 2.0.0 rules. */
  private inline def isValidIdentifier(id: String): Boolean =
    // Checks for non-empty and allowed characters [0-9A-Za-z-]
    id.nonEmpty && id.forall { c =>
      c.isLetterOrDigit || ("-".indexOf(c) >= 0)
    }

  /** Creates [[Metadata]] from a list of identifiers.
   *
   * @throws InvalidMetadata
   *   if any identifier is empty or contains invalid characters.
   */
  def apply(identifiers: List[String]): Metadata = fromUnsafe(identifiers)

  /** Semantic alias for [[unwrap]]. Returns the list of metadata identifiers. */
  extension (metadata: Metadata)
    inline def identifiers: List[String] = unwrap(metadata)
    /** Returns the SemVer-compliant metadata string (including leading '+'). */
    inline def show: String = s"+${unwrap(metadata).mkString(".")}"
end Metadata
