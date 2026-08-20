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

/** Auto-imported types and settings for the version sbt plugin.
  *
  * All members are automatically available in `build.sbt` when the plugin is enabled.
  */
object VersionPluginImports:

  /** A version paired with the scheme that reads it, which is how the plugin's typed keys carry one. */
  type Versioned = version.Versioned

  /** Companion of [[Versioned]], for constructing and comparing carried versions. */
  val Versioned: version.Versioned.type = version.Versioned

  /** Bundled scheme + tag parser + formatter. */
  type VersionResolver[V] = version.resolution.VersionResolver[V]

  /** Companion of [[VersionResolver]] for `withDefaults[V]` and combinators. */
  val VersionResolver: version.resolution.VersionResolver.type = version.resolution.VersionResolver

  /** Rendering strategy for a version of scheme `V`. */
  type Formatter[V] = version.Formatter[V]

  /** Alias for [[version.semver.SemVer]] to enable unqualified use in build definitions. */
  type SemVer = version.semver.SemVer

  /** Companion object for [[SemVer]], providing factory methods and type class instances. */
  val SemVer: version.semver.SemVer.type = version.semver.SemVer

  /** Alias for [[version.errors.VersionError]] to enable unqualified use in build definitions. */
  type VersionError = version.errors.VersionError

  /** Alias for [[version.semver.PreRelease]] to enable unqualified use in build definitions. */
  type PreRelease = version.semver.PreRelease

  /** Companion object for [[PreRelease]], providing factory methods and operations. */
  val PreRelease: version.semver.PreRelease.type = version.semver.PreRelease

  /** [[sjsonnew.IsoString IsoString]] instance for [[SemVer]] enabling sbt 2.x task caching.
    *
    * Uses [[SemVer.Formatter$.Full Formatter.Full]] for serialisation and [[SemVer.parseUnsafe]] for
    * deserialisation. Used exclusively for sbt's internal cache serialisation, not for user-facing version strings.
    */
  given IsoString[version.semver.SemVer] = IsoString.iso(
    version.semver.SemVer.Formatter.Full.format,
    version.semver.SemVer.parseUnsafe
  )

  /** Optional branch name override for build metadata derivation.
    *
    * Use when CI checkouts do not preserve branch information.
    */
  val versionBranchOverride: SettingKey[Option[String]] =
    settingKey("Optional branch override used when deriving build metadata.")

  /** Bundle of scheme, tag parser, and rendering formatter for version resolution.
    *
    * The default is `VersionResolver.withDefaults[SemVer].withFormatter(SemVer.Formatter.Standard)`: the SemVer
    * scheme, a `v`/`V`-stripping tag parser, and the rendering that keeps `version` a stable `<core>-SNAPSHOT`
    * publishing coordinate. Override the formatter to publish build metadata instead:
    * {{{
    * versionResolver := VersionResolver.withDefaults[SemVer]
    *   .withFormatter(SemVer.Formatter.Full.withShaLength(7))
    * }}}
    */
  val versionResolver: SettingKey[VersionResolver[?]] =
    settingKey("Version resolver bundle (scheme + tag parser + formatter).")

  /** The resolved version for the current repository state.
    *
    * Rendered with `.show`; match `.value` to reach the scheme's own accessors:
    * {{{
    * resolvedVersion.value.value match
    *   case v: SemVer => s"${v.major.value}.${v.minor.value}.${v.patch.value}"
    * }}}
    */
  val resolvedVersion: SettingKey[Versioned] =
    settingKey("Resolved version for the current repository state.")

  /** The target release version the working tree is heading toward.
    *
    * On a clean release tag this equals [[resolvedVersion]] - the tag itself. Otherwise it is the next release the
    * resolution computed: the version a release cut from the current state would carry, without development metadata
    * (for example `1.0.1` while [[resolvedVersion]] renders `1.0.1-SNAPSHOT+...`).
    */
  val versionTarget: SettingKey[Versioned] =
    settingKey("Target release version the working tree is heading toward.")
end VersionPluginImports
