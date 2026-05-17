/****************************************************************************
 * Copyright 2023-2026 Shuwari Africa Ltd.                                  *
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

import version.errors.VersionError

/** Core contract for a versioning scheme.
  *
  * Every version type provides an instance of this trait in its companion object, enabling scheme selection via Scala 3
  * implicit scope. When a type `V` appears as a type parameter, the compiler finds `VersionScheme[V]` in `V`'s
  * companion - no explicit import of givens required.
  *
  * The [[ordering]] member is a plain `def` (not `given`) because implicit search does not recursively extract given
  * members from given instances. Each scheme companion provides a standalone `given Ordering[V]` for direct implicit
  * resolution, and this `def` for extraction by generic code.
  *
  * @tparam V
  *   The version type.
  */
trait VersionScheme[V <: Version]:

  /** Canonical scheme identifier (e.g., "semver", "dotnet-official"). */
  def name: String

  /** Component descriptors in scheme order, pairing each position's name with its semantic role.
    *
    * For example, SemVer declares:
    * {{{
    * IArray(
    *   ComponentDescriptor("major", ComponentRole.Breaking),
    *   ComponentDescriptor("minor", ComponentRole.Feature),
    *   ComponentDescriptor("patch", ComponentRole.Fix)
    * )
    * }}}
    */
  def layout: IArray[ComponentDescriptor]

  /** Parse a version string into the scheme's type. */
  def parse(input: String): Either[VersionError, V]

  /** Ordering relation for this scheme. Plain `def`, not `given` - see trait documentation. */
  def ordering: Ordering[V]

  /** Numeric core components in scheme order.
    *
    * Pre-release and metadata state is NOT represented here - use [[isFinal]], [[core]], or scheme-specific typed
    * accessors for that information.
    */
  extension (v: V) def components: IArray[Int]

  /** Whether this represents a final/stable release (no development or pre-release markers). */
  extension (v: V) def isFinal: Boolean

  /** The core version without development/pre-release markers.
    *
    * For schemes without pre-release concepts, returns unchanged.
    */
  extension (v: V) def core: V
end VersionScheme

object VersionScheme:
  /** Summons the contextual [[VersionScheme]] instance. */
  inline def apply[V <: Version](using vs: VersionScheme[V]): VersionScheme[V] = vs
