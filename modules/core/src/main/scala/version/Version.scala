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

/** Family marker for any value that represents a version under some scheme.
  *
  * The only contract carried at this layer is a canonical string representation. All other operations
  * (parsing, ordering, component manipulation, Git-based resolution, configurable rendering) live on
  * [[VersionScheme]], [[VersionArithmetic]], [[ResolvableScheme]], and [[Formatter]] and are dispatched
  * by the per-scheme instance.
  */
trait Version:
  /** Canonical string representation in the scheme's conventions. */
  def show: String

/** Provides companion aliases for [[Version]]. */
object Version:
  /** Canonical-string accessor: companion alias for [[Version!.show]]. */
  def show[V <: Version](v: V): String = v.show
