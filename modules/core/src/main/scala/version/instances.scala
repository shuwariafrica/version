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

/** Default [[Version.Read]] instance for `String`.
   *
   * Available via `import version.{given, *}` or `import version.given`.
   *
   * Parses SemVer strings using the contextual [[PreRelease.Resolver]] for mapping pre-release identifiers. Override by
   * providing a custom `given Version.Read[String]` in scope.
   */
given Version.Read[String] = Version.Read.ReadString
