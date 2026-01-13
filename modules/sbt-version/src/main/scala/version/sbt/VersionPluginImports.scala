/****************************************************************
 * Copyright © Shuwari Africa Ltd.                              *
 *                                                              *
 * This file is licensed to you under the terms of the Apache   *
 * License Version 2.0 (the "License"); you may not use this    *
 * file except in compliance with the License. You may obtain   *
 * a copy of the License at:                                    *
 *                                                              *
 *     https://www.apache.org/licenses/LICENSE-2.0              *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, *
 * either express or implied. See the License for the specific  *
 * language governing permissions and limitations under the     *
 * License.                                                     *
 ****************************************************************/
package version.sbt

import sbt.SettingKey
import sbt.settingKey

import version.cli.core.domain.CliConfig

object VersionPluginImports:
  type Version = version.Version
  val Version: version.Version.type = version.Version

  type VersionConfig = CliConfig
  val VersionConfig: CliConfig.type = CliConfig

  val versionBranchOverride: SettingKey[Option[String]] =
    settingKey("Optional branch override used when deriving build metadata.")

  val versionShow: SettingKey[Option[Version.Show]] =
    settingKey("Optional Version.Show instance for customising the version string. Defaults to Version.Show.Standard.")

  val resolvedVersion: SettingKey[Version] =
    settingKey("Resolved semantic version for the current repository state. Use Version.Show instances for rendering.")
