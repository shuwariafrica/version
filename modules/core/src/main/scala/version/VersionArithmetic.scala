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

/** Interpretation of an advancement [[Request]], for schemes that define one.
  *
  * A scheme whose advancement is a matter of policy rather than of its own algebra supplies no instance, so code that
  * needs to advance a version says so in its context bounds and the absence is answered at compile time.
  */
trait VersionArithmetic[V]:

  /** `Left` where the scheme cannot express the request: an unknown component name, or a value the addressed
    * component does not admit.
    */
  def apply(v: V, request: Request): Either[VersionError, V]
