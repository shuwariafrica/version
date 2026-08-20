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

/** Shared companion behaviour for the opaque numeric components of a version scheme: lower-bound validation, a total
  * increment, and ordering.
  *
  * Components are carried as `Long`: schemes do put date stamps and build counters in numeric positions, and those
  * overflow an `Int`.
  */
transparent trait VersionComponent[T] extends OpaqueType[T, Long], OpaqueType.Eq[T]:

  type Error = InvalidComponent

  /** Names the component as the subject of an [[InvalidComponent]] message: "Major version must be ...". */
  protected def componentName: String

  protected def minimumValue: Long

  /** Completes an [[InvalidComponent]] message after "must be", for example "a non-negative number (>= 0)". */
  protected def requirement: String

  protected inline def validate(value: Long): Either[InvalidComponent, Long] =
    if value >= minimumValue then Right(value)
    else Left(InvalidComponent(value, componentName, requirement))

  /** The lowest value this component admits. */
  inline def minimum: T = wrap(minimumValue)

  extension (t: T)
    inline def value: Long = unwrap(t)

    /** One step up, saturating at `Long.MaxValue` so that advancement is total. */
    inline def increment: T =
      val current = unwrap(t)
      if current == Long.MaxValue then t else wrap(current + 1)

  given Ordering[T] = Ordering.by(unwrap)

end VersionComponent

/** Extends [[VersionComponent]] for components a scheme returns to a fixed value when a more significant component
  * advances.
  */
transparent trait ResettableVersionComponent[T] extends VersionComponent[T]:
  inline def reset: T = minimum
