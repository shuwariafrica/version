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

/** The capability every versioning scheme provides: reading and rendering its values, ordering them, and reporting
  * how any two of them differ.
  *
  * This is the only capability a scheme must have. Advancement ([[VersionArithmetic]]), compatibility
  * ([[CompatibilityPolicy]]) and release workflow ([[ResolvableScheme]]) are separate instances a scheme supplies
  * only where it defines them.
  */
trait VersionScheme[V]:

  /** The identifier this scheme is selected by, in the spelling its ecosystem publishes. */
  def name: String

  /** Reads a value, rejecting anything outside the scheme's grammar rather than coercing it. */
  def parse(input: String): Either[VersionError, V]

  /** The scheme's normative comparison.
    *
    * This may equate structurally distinct values wherever the scheme ranks two spellings alike, so it is not a
    * substitute for equality; identity remains structural.
    */
  def precedence: Ordering[V]

  /** The most significant tier separating `a` from `b`. */
  def difference(a: V, b: V): Difference

  extension (v: V)
    /** The canonical rendering, which [[parse]] reads back as an equal value. */
    def show: String

    /** Whether the value carries no below-release qualifier. */
    def stable: Boolean

    /** The value with every below-release qualifier stripped. */
    def release: V

    /** The leading numeric components, for coarse bucketing. Lossy by construction, and empty for schemes with no
      * numeric positions at all.
      */
    def numbers: IArray[Long]
end VersionScheme
