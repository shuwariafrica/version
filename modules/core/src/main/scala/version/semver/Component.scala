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
package version.semver

import version.ResettableVersionComponent

/** Represents a major version number. Must be non-negative (>= 0).
  *
  * Instances may be constructed via [[Major$ Major]].
  */
opaque type Major = Int

/** Provides factory methods, instances, and operations for [[Major]]. */
object Major extends ResettableVersionComponent[Major]:
  protected inline def componentName: String = "Major version"
  protected inline def minimumValue: Int = 0
  protected inline def requirement: String = "a non-negative number (>= 0)"
  inline def wrap(value: Int): Major = value
  inline def unwrap(mv: Major): Int = mv
  inline def apply(inline value: Int): Major =
    inline if value < 0 then compiletime.error("Major must be non-negative (>= 0)")
    else wrap(value)
  extension (v: Major)
    /** Returns `true` if the major version is greater than 0, indicating a stable release. */
    inline def isStable: Boolean = unwrap(v) > 0

/** Represents a minor version number. Must be non-negative (>= 0).
  *
  * Instances may be constructed via [[Minor$ Minor]].
  */
opaque type Minor = Int

/** Provides factory methods, instances, and operations for [[Minor]]. */
object Minor extends ResettableVersionComponent[Minor]:
  protected inline def componentName: String = "Minor version"
  protected inline def minimumValue: Int = 0
  protected inline def requirement: String = "a non-negative number (>= 0)"
  inline def wrap(value: Int): Minor = value
  inline def unwrap(mv: Minor): Int = mv
  inline def apply(inline value: Int): Minor =
    inline if value < 0 then compiletime.error("Minor must be non-negative (>= 0)")
    else wrap(value)

/** Represents a patch number. Must be non-negative (>= 0).
  *
  * Instances may be constructed via [[Patch$ Patch]].
  */
opaque type Patch = Int

/** Provides factory methods, instances, and operations for [[Patch]]. */
object Patch extends ResettableVersionComponent[Patch]:
  protected inline def componentName: String = "Patch number"
  protected inline def minimumValue: Int = 0
  protected inline def requirement: String = "a non-negative number (>= 0)"
  inline def wrap(value: Int): Patch = value
  inline def unwrap(pn: Patch): Int = pn
  inline def apply(inline value: Int): Patch =
    inline if value < 0 then compiletime.error("Patch must be non-negative (>= 0)")
    else wrap(value)

/** Represents a pre-release number. Must be positive (>= 1).
  *
  * Instances may be constructed via [[PreReleaseNumber$ PreReleaseNumber]].
  */
opaque type PreReleaseNumber = Int

/** Provides factory methods, instances, and operations for [[PreReleaseNumber]]. */
object PreReleaseNumber extends ResettableVersionComponent[PreReleaseNumber]:
  protected inline def componentName: String = "Pre-release number"
  protected inline def minimumValue: Int = 1
  protected inline def requirement: String = "a positive number (>= 1)"
  inline def wrap(value: Int): PreReleaseNumber = value
  inline def unwrap(prn: PreReleaseNumber): Int = prn
  inline def apply(inline value: Int): PreReleaseNumber =
    inline if value < 1 then compiletime.error("PreReleaseNumber must be positive (>= 1)")
    else wrap(value)
