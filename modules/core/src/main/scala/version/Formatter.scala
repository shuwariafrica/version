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

/** A rendering of a version other than its canonical one.
  *
  * Schemes name their renderings, and any configuration a rendering takes, in their own companions - see
  * [[version.semver.SemVer$.Formatter SemVer.Formatter]]. The canonical rendering needs no formatter: it is
  * [[VersionScheme]]'s `show`.
  */
trait Formatter[V]:
  def format(v: V): String
