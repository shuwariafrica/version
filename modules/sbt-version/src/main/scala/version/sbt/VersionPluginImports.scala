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

import version.cli.core.domain.CliConfig

/** Auto-imported types and settings for the version sbt plugin.
  *
  * All members are automatically available in `build.sbt` when the plugin is enabled.
  */
object VersionPluginImports:

  /** Alias for [[version.Version]] to enable unqualified use in build definitions. */
  type Version = version.Version

  /** Companion object for [[Version]], providing factory methods and type class instances. */
  val Version: version.Version.type = version.Version

  /** Alias for [[version.cli.core.domain.CliConfig]] for advanced configuration. */
  type VersionConfig = CliConfig

  /** Companion object for [[VersionConfig]]. */
  val VersionConfig: CliConfig.type = CliConfig

  /** Optional branch name override for build metadata derivation.
    *
    * Use when CI checkouts do not preserve branch information.
    */
  val versionBranchOverride: SettingKey[Option[String]] =
    settingKey("Optional branch override used when deriving build metadata.")

  /** The [[Version.Read]] instance for parsing version tags.
    *
    * Override to support non-standard pre-release formats:
    * {{{
    * versionRead := {
    *   given PreRelease.Resolver with
    *     extension (ids: List[String])
    *       def resolve: Option[PreRelease] = ids match
    *         case List("nightly") => Some(PreRelease.snapshot)
    *         case _ => summon[PreRelease.Resolver].resolve(ids)
    *   Version.Read.ReadString
    * }
    * }}}
    */
  val versionRead: SettingKey[Version.Read[String]] =
    settingKey(
      "The Version.Read[String] instance used for parsing version tags. Defaults to Version.Read.ReadString."
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
