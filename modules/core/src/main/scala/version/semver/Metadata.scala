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
package version.semver

import boilerplate.OpaqueType

import version.errors.InvalidMetadata

/** Build metadata as defined by the Semantic Versioning 2.0.0 specification.
  *
  * Identifiers must comprise only ASCII alphanumerics and hyphens `[0-9A-Za-z-]` and must not be empty. Build metadata
  * does not affect version precedence.
  *
  * Instances may be constructed via [[Metadata$ Metadata]].
  */
opaque type Metadata = List[String]

/** Provides factory methods and operations for [[Metadata]]. */
object Metadata extends OpaqueType[Metadata, List[String]], OpaqueType.Eq[Metadata]:
  type Error = InvalidMetadata

  def wrap(ids: List[String]): Metadata = ids
  def unwrap(bm: Metadata): List[String] = bm
  inline def apply(inline identifiers: List[String]): Metadata = fromUnsafe(identifiers)

  protected inline def validate(ids: List[String]): Option[Error] =
    if ids.nonEmpty && ids.forall(isValidIdentifier) then None
    else Some(InvalidMetadata(ids))

  private inline def isValidIdentifier(id: String): Boolean =
    id.nonEmpty && id.forall { c =>
      c.isLetterOrDigit || ("-".indexOf(c) >= 0)
    }

  extension (metadata: Metadata)
    inline def identifiers: List[String] = unwrap(metadata)
    inline def show: String = unwrap(metadata).mkString(".")
end Metadata
