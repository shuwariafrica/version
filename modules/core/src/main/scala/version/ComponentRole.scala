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

/** Describes the semantic role of a version component position within a scheme.
  *
  * Drives predictable keyword behaviour across schemes. For example, `version: breaking` in a commit message resolves to
  * the component(s) with [[Breaking]] role, regardless of which scheme is in use.
  *
  * Each scheme declares a `layout: IArray[ComponentRole]` mapping positions to roles. For multiple same-role positions
  * (e.g., PVP has two [[Breaking]]), role-based keywords resolve to the last position with that role. Users who need a
  * specific position use the component name directly.
  */
enum ComponentRole:

  /** Breaking changes. Keywords: major, breaking. */
  case Breaking

  /** Non-breaking additions. Keywords: minor, feature, feat. */
  case Feature

  /** Bug fixes. Keywords: patch, fix. */
  case Fix

  /** Date-based components (CalVer: year, month, day). */
  case Temporal

  /** Scheme-specific component with no cross-scheme keyword mapping. */
  case Supplementary

object ComponentRole:
  given CanEqual[ComponentRole, ComponentRole] = CanEqual.derived
