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

/** Describes a single version component position within a scheme.
  *
  * Pairs the component's name (e.g., "major", "minor", "patch") with its semantic [[ComponentRole]]. Each scheme
  * declares its layout as an `IArray[ComponentDescriptor]`, making it structurally impossible for names and roles to
  * fall out of sync.
  *
  * @param name
  *   The component name used in commit-message keywords and display.
  * @param role
  *   The semantic role of this component position.
  */
final case class ComponentDescriptor(name: String, role: ComponentRole)

/** Provides instances for [[ComponentDescriptor]]. */
object ComponentDescriptor:
  given CanEqual[ComponentDescriptor, ComponentDescriptor] = CanEqual.derived
