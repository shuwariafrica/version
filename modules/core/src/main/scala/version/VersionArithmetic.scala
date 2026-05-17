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

/** Component manipulation for version schemes that support programmatic increment and set operations.
  *
  * Separated from [[ResolvableScheme]] because not all arithmetic-capable schemes support Git-based resolution (e.g.,
  * .NET Official supports increment/set but has no pre-release concept for resolution).
  *
  * @tparam V
  *   The version type.
  */
trait VersionArithmetic[V <: Version] extends VersionScheme[V]:

  /** Advance the version by incrementing the component at the given index.
    *
    * Reset semantics for lower-precedence components are scheme-specific. For example, incrementing the major component
    * in SemVer resets minor and patch to zero.
    *
    * @param index
    *   Zero-based component position in scheme order.
    */
  extension (v: V) def incrementComponent(index: Int): V

  /** Set the component at the given index to a specific value.
    *
    * Reset semantics for lower-precedence components are scheme-specific, matching [[incrementComponent]]. For SemVer:
    * setting major resets minor and patch; setting minor resets patch.
    *
    * @param index
    *   Zero-based component position in scheme order.
    * @param value
    *   The value to set.
    * @return
    *   `Right(V)` on success, `Left(VersionError)` if the value is out of bounds for the scheme.
    */
  extension (v: V) def setComponent(index: Int, value: Int): Either[VersionError, V]
end VersionArithmetic

object VersionArithmetic:
  /** Summons the contextual [[VersionArithmetic]] instance. */
  inline def apply[V <: Version](using va: VersionArithmetic[V]): VersionArithmetic[V] = va
