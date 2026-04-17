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
package version

/** Policy for determining API/binary compatibility between two versions.
  *
  * Compatibility is a policy applied to versions, not an inherent property of the version itself. Multiple policies can
  * exist for the same version type (following sbt's pattern: Strict, SemVer, EarlySemVer, PackVer).
  *
  * Each scheme provides a default policy in its companion (found via implicit scope). Users override locally:
  * {{{
  * given CompatibilityPolicy[SemVer] = SemVer.compatibility.early
  * v1.isCompatibleWith(v2)  // uses early policy
  * }}}
  *
  * @tparam V
  *   The version type.
  */
trait CompatibilityPolicy[V]:

  /** Whether `v2` is considered compatible with `v1` under this policy. */
  extension (v1: V) def isCompatibleWith(v2: V): Boolean

/** Provides universal policy constructors for [[CompatibilityPolicy]]. */
object CompatibilityPolicy:

  /** Exact match only - versions are compatible only if equal. */
  def strict[V](using CanEqual[V, V]): CompatibilityPolicy[V] =
    new CompatibilityPolicy[V]:
      extension (v1: V) def isCompatibleWith(v2: V): Boolean = v1 == v2
