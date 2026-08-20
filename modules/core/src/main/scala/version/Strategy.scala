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

/** What a caller wants of a range rewritten around a version.
  *
  * The choice is the caller's throughout: a scheme interprets a strategy against its own grammar, and never picks one.
  */
enum Strategy derives CanEqual:

  /** Name the version outright, discarding whatever the range allowed besides it. */
  case Pin

  /** Move the range's floor up to the version, keeping the construct and the precision the author wrote. */
  case Raise

  /** Leave a range that already admits the version alone, and otherwise reshape it as little as will admit it. */
  case Replace

  /** Leave a range that already admits the version alone, and otherwise extend it to reach the version without
    * dropping anything it already admitted.
    */
  case Widen
