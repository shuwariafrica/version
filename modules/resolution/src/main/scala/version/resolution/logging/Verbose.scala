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
package version.resolution.logging

import boilerplate.OpaqueType

/** Controls whether verbose/debug logging is enabled during version resolution.
  *
  * Replaces raw `Boolean` as a contextual parameter to prevent ambiguity with other `given Boolean` instances.
  *
  * Instances may be constructed via [[Verbose$ Verbose]].
  */
opaque type Verbose = Boolean

/** Provides factory methods and operations for [[Verbose]]. */
object Verbose extends OpaqueType[Verbose, Boolean], OpaqueType.Eq[Verbose]:
  type Error = Nothing

  def wrap(value: Boolean): Verbose = value
  def unwrap(value: Verbose): Boolean = value
  protected inline def validate(value: Boolean): Option[Nothing] = None
  inline def apply(inline value: Boolean): Verbose = wrap(value)

  val enabled: Verbose = wrap(true)
  val disabled: Verbose = wrap(false)

  extension (v: Verbose)
    /** Whether verbose logging is enabled. */
    inline def isEnabled: Boolean = unwrap(v)
