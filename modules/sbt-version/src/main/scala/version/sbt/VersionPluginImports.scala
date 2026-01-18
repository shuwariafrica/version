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
package version.sbt

import sbt.SettingKey
import sbt.settingKey
import sjsonnew.IsoString

import version.cli.core.domain.CliConfig

/** Auto-imported types and settings for the version sbt plugin.
  *
  * All members are automatically available in `build.sbt` when the plugin is enabled.
  *
  * Provides [[sjsonnew.IsoString IsoString]] instances for [[Version]] and related types to enable sbt 2.x task
  * caching. These instances are automatically derived by sjsonnew's [[sjsonnew.BasicJsonProtocol BasicJsonProtocol]]
  * into [[sjsonnew.JsonFormat JsonFormat]] and [[sjsonnew.HashWriter HashWriter]] instances.
  */
object VersionPluginImports:

  /** Alias for [[version.Version]] to enable unqualified use in build definitions. */
  type Version = version.Version

  /** Companion object for [[Version]], providing factory methods and type class instances. */
  val Version: version.Version.type = version.Version

  /** Alias for [[version.VersionError]] to enable unqualified use in build definitions. */
  type VersionError = version.errors.VersionError

  /** Companion object for [[VersionError]], providing error case classes. */
  val VersionError: version.errors.VersionError.type = version.errors.VersionError

  /** Alias for [[version.cli.core.domain.CliConfig]] for advanced configuration. */
  type VersionConfig = CliConfig

  /** Companion object for [[VersionConfig]]. */
  val VersionConfig: CliConfig.type = CliConfig

  /** Alias for [[version.PreRelease]] to enable unqualified use in build definitions. */
  type PreRelease = version.PreRelease

  /** Companion object for [[PreRelease]], providing factory methods and the [[PreRelease.Resolver]] type. */
  val PreRelease: version.PreRelease.type = version.PreRelease

  // --- sjsonnew IsoString instances for sbt 2.x task caching ---

  /** [[sjsonnew.IsoString IsoString]] instance for [[Version]] enabling sbt 2.x task caching.
    *
    * Uses [[Version.Show.Full]] for serialisation and [[Version.Read.ReadString]] with the default
    * [[version.PreRelease.Resolver PreRelease.Resolver]] for deserialisation.
    *
    * This instance is used exclusively for sbt's internal cache serialisation, not for user-facing version strings.
    * The [[versionShow]] and [[versionRead]] settings control external rendering and git tag parsing respectively,
    * while this `IsoString` handles lossless round-trip serialisation for task caching.
    *
    * Custom `versionRead` resolvers (e.g., mapping `"nightly"` to [[version.PreReleaseClassifier.Snapshot Snapshot]])
    * are applied when parsing git tags. Once parsed, the [[Version]] contains canonical classifiers that `Show.Full`
    * renders in standard format, ensuring the default resolver can deserialise cached values.
    */
  given IsoString[version.Version] = IsoString.iso(
    version.Version.Show.Full.show,
    version.Version.Read.ReadString.toVersionUnsafe
  )

  /** Optional branch name override for build metadata derivation.
    *
    * Use when CI checkouts do not preserve branch information.
    */
  val versionBranchOverride: SettingKey[Option[String]] =
    settingKey("Optional branch override used when deriving build metadata.")

  /** The [[Version.Read]] instance for parsing version tags.
    *
    * Override to support non-standard tag formats:
    * {{{
    * versionRead := {
    *   // Custom Read implementation
    *   Version.Read.ReadString
    * }
    * }}}
    *
    * @see [[versionResolver]] for customising pre-release identifier mapping.
    */
  val versionRead: SettingKey[Version.Read[String]] =
    settingKey(
      "The Version.Read[String] instance used for parsing version tags. Defaults to Version.Read.ReadString."
    )

  /** The [[PreRelease.Resolver]] instance for mapping pre-release identifiers.
    *
    * Override to support non-standard pre-release formats:
    * {{{
    * versionResolver := new PreRelease.Resolver:
    *   extension (ids: List[String])
    *     def resolve: Option[PreRelease] = ids match
    *       case List("nightly") => Some(PreRelease.snapshot)
    *       case _               => PreRelease.Resolver.given_Resolver.resolve(ids)
    * }}}
    */
  val versionResolver: SettingKey[PreRelease.Resolver] =
    settingKey(
      "The PreRelease.Resolver instance used for mapping pre-release identifiers. Defaults to the standard resolver."
    )

  /** Optional [[Version.Show]] instance for customising the `version` setting output.
    *
    * When `None`, uses [[Version.Show.Standard]] (excludes build metadata).
    */
  val versionShow: SettingKey[Option[Version.Show]] =
    settingKey("Optional Version.Show instance for customising the version string. Defaults to Version.Show.Standard.")

  /** The resolved [[Version]] for the current repository state.
    *
    * Includes full 40-character SHA in build metadata for maximum flexibility. Use [[Version.Show]] instances for
    * rendering.
    */
  val resolvedVersion: SettingKey[Version] =
    settingKey("Resolved semantic version for the current repository state. Use Version.Show instances for rendering.")
end VersionPluginImports
