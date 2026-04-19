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
package version.sbt

import sbt.SettingKey
import sbt.settingKey
import sjsonnew.IsoString

import version.resolution.ResolutionConfig

/** Auto-imported types and settings for the version sbt plugin.
  *
  * All members are automatically available in `build.sbt` when the plugin is enabled.
  */
object VersionPluginImports:

  /** Alias for [[version.semver.SemVer]] to enable unqualified use in build definitions. */
  type SemVer = version.semver.SemVer

  /** Companion object for [[SemVer]], providing factory methods and type class instances. */
  val SemVer: version.semver.SemVer.type = version.semver.SemVer

  /** Alias for [[version.VersionError]] to enable unqualified use in build definitions. */
  type VersionError = version.errors.VersionError

  /** Companion object for [[VersionError]], providing error case classes. */
  val VersionError: version.errors.VersionError.type = version.errors.VersionError

  /** Alias for [[version.resolution.ResolutionConfig]] pinned to SemVer. */
  type VersionConfig = ResolutionConfig[version.semver.SemVer]

  /** Companion object for [[version.resolution.ResolutionConfig ResolutionConfig]]. */
  val VersionConfig: ResolutionConfig.type = ResolutionConfig

  /** Alias for [[version.semver.PreRelease]] to enable unqualified use in build definitions. */
  type PreRelease = version.semver.PreRelease

  /** Companion object for [[PreRelease]], providing factory methods and the [[PreRelease.Resolver]] type. */
  val PreRelease: version.semver.PreRelease.type = version.semver.PreRelease

  /** Type alias for tag parser functions. */
  type TagParser = String => Option[version.semver.SemVer]

  // --- sjsonnew IsoString instances for sbt 2.x task caching ---

  /** [[sjsonnew.IsoString IsoString]] instance for [[SemVer]] enabling sbt 2.x task caching.
    *
    * Uses [[SemVer.Formatter$.full Formatter.full]] for serialisation and [[SemVer.parseUnsafe]] for deserialisation.
    * Used exclusively for sbt's internal cache serialisation, not for user-facing version strings.
    */
  given IsoString[version.semver.SemVer] = IsoString.iso(
    version.semver.SemVer.Formatter.full.format,
    version.semver.SemVer.parseUnsafe
  )

  /** Optional branch name override for build metadata derivation.
    *
    * Use when CI checkouts do not preserve branch information.
    */
  val versionBranchOverride: SettingKey[Option[String]] =
    settingKey("Optional branch override used when deriving build metadata.")

  /** Tag parser function for converting Git tag names to [[SemVer]] values.
    *
    * The default strips `v`/`V` prefixes and parses via [[SemVer$.parse SemVer.parse]]. Override to support
    * non-standard tag formats:
    * {{{
    * versionTagParser := name =>
    *   SemVer.parse(name.stripPrefix("release-")).toOption
    * }}}
    */
  val versionTagParser: SettingKey[TagParser] =
    settingKey("Tag parser for converting Git tag names to SemVer values. Defaults to v-prefix stripping.")

  /** Optional [[SemVer.Formatter]] for customising the `version` setting output.
    *
    * When `None`, uses `v.show` (standard SemVer format, excludes build metadata).
    */
  val versionFormatter: SettingKey[Option[SemVer.Formatter]] =
    settingKey("Optional SemVer.Formatter for customising the version string. Defaults to standard (v.show).")

  /** The resolved [[SemVer]] for the current repository state.
    *
    * Includes full 40-character SHA in build metadata for maximum flexibility. Use [[SemVer.Formatter]] for custom
    * rendering.
    */
  val resolvedVersion: SettingKey[SemVer] =
    settingKey("Resolved semantic version for the current repository state.")
end VersionPluginImports
