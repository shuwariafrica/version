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

import boilerplate.OpaqueType

import version.errors.InvalidComponent

/** Capability trait for opaque version components backed by [[Int]].
  *
  * Extends [[boilerplate.OpaqueType]] with version-specific semantics:
  *   - Minimum and maximum value validation
  *   - Increment operation with saturation at upper bound
  *   - Standard ordering
  *
  * Subclasses override [[maximumValue]] for scheme-specific bounds (e.g., .NET components use `65535` for UInt16).
  *
  * @tparam T
  *   The opaque type itself.
  */
transparent trait VersionComponent[T] extends OpaqueType[T, Int], OpaqueType.Eq[T]:

  type Error = InvalidComponent

  /** The component name used in error messages (e.g., "Major version"). */
  protected def componentName: String

  /** The minimum valid value for this component. */
  protected def minimumValue: Int

  /** The maximum valid value for this component. Override for scheme-specific bounds (e.g., `65535` for .NET UInt16). */
  protected def maximumValue: Int = Int.MaxValue

  /** The validation requirement description for error messages (e.g., "a non-negative number (>= 0)"). */
  protected def requirement: String

  protected inline def validate(value: Int): Option[InvalidComponent] =
    if value >= minimumValue && value <= maximumValue then None
    else Some(InvalidComponent(value, componentName, requirement))

  /** The minimum valid value for this component, wrapped in the opaque type. */
  inline def minimum: T = wrap(minimumValue)

  extension (t: T)
    inline def value: Int = unwrap(t)

    /** Returns a new instance incremented by one. Saturates at [[maximumValue]]. */
    inline def increment: T =
      val current = unwrap(t)
      if current >= maximumValue then wrap(current)
      else wrap(current + 1)

  given Ordering[T] = Ordering.by(unwrap)
end VersionComponent

/** Extends [[VersionComponent]] for components that have a defined reset value (typically the minimum value). */
transparent trait ResettableVersionComponent[T] extends VersionComponent[T]:
  inline def reset: T = minimum
