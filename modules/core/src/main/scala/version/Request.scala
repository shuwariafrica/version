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

/** A reified request to advance a version, as emitted by a directive vocabulary and interpreted by
  * [[VersionArithmetic]].
  *
  * [[Request.Advance Advance]] is subject to the scheme's advancement policy and may be redirected by it.
  * [[Request.Bump Bump]] and [[Request.Assign Assign]] address a component by the scheme's own name for it and are
  * exempt from that policy, so an explicit `major` moves the major even where an intent would not.
  */
enum Request derives CanEqual:

  /** Advance by the significance of the change, leaving the choice of component to the scheme. */
  case Advance(intent: Intent)

  /** Advance the named component by one step. */
  case Bump(component: String)

  /** Set the named component, resetting every component below it. */
  case Assign(component: String, value: Long)
