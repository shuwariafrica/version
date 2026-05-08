scalaVersion := Libraries.scala3.revision
organization := "africa.shuwari"
description := "Simple utilities and data structures for the management of application versioning."
homepage := Some(url("https://github.com/shuwariafrica/version"))
startYear := Some(2023)
semanticdbEnabled := true
scmInfo := ScmInfo(
  url("https://github.com/shuwariafrica/version"),
  "scm:git:https://github.com/shuwariafrica/version.git",
  Some("scm:git:git@github.com:shuwariafrica/version.git")
).some
formattingSettings
apacheLicensed
Shuwari.organisationSettings

val version =
  projectMatrix
    .in(file("modules/core"))
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(libraryDependencies += Libraries.boilerplate)
    .jvmPlatform(scalaVersions = Seq(Libraries.scala3.revision))
    .nativePlatform(scalaVersions = Seq(Libraries.scala3.revision), settings = nativeSettings)
// FIXME: Re-enable JS axis when scala-js supports sbt 2.x. See scala-js/scala-js#5238.
//    .jsPlatform(scalaVersions = Seq(Libraries.scala3.revision))

val `version-testkit` =
  projectMatrix
    .in(file("modules/testkit"))
    .settings(publish / skip := true)
    .jvmPlatform(scalaVersions = Seq(Libraries.scala3.revision))
    .nativePlatform(scalaVersions = Seq(Libraries.scala3.revision), settings = nativeSettings)

val resolution =
  projectMatrix
    .in(file("modules/resolution"))
    .dependsOn(version)
    .dependsOn(`version-testkit` % Test)
    .settings(publishSettings)
    .settings(unitTestSettings)
    .settings(noticeMappingSettings)
    .jvmPlatform(
      scalaVersions = Seq(Libraries.scala3.revision),
      settings = Seq(libraryDependencies += Libraries.jgit)
    )
    .nativePlatform(
      scalaVersions = Seq(Libraries.scala3.revision),
      axisValues = Nil,
      configure = (p: Project) => p.settings(nativeSettings *).enablePlugins(Libgit2Build)
    )

val `version-cli` =
  projectMatrix
    .in(file("modules/cli"))
    .dependsOn(resolution)
    .dependsOn(`version-testkit` % Test)
    .enablePlugins(BuildInfoPlugin)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(libraryDependencies += Libraries.scopt)
    .settings(Compile / run / mainClass := Some("version.cli.CLI"))
    .settings(publish / skip := true)
    .settings(buildInfoSettings)
    .jvmPlatform(
      scalaVersions = Seq(Libraries.scala3.revision),
      settings = Seq(run / fork := true)
    )
    .nativePlatform(
      scalaVersions = Seq(Libraries.scala3.revision),
      axisValues = Nil,
      configure = (p: Project) => p.settings(nativeSettings *).settings(cliNativeSettings *).enablePlugins(Libgit2Build, ActionsPublish)
    )

val `sbt-version` =
  projectMatrix
    .in(file("modules/sbt-version"))
    .jvmPlatform(scalaVersions = Seq(Libraries.scala3.revision))
    .dependsOn(resolution)
    .dependsOn(`version-testkit` % Test)
    .enablePlugins(SbtPlugin)
    .settings(publishSettings)
    .settings(unitTestSettings)
    .settings(Compile / scalacOptions -= "-deprecation")
    .settings(
      scriptedBufferLog := true,
      scriptedLaunchOpts ++= Seq(
        s"-Dplugin.version=${(LocalRootProject / Keys.version).value}",
        s"-Dsbt.boot.directory=${file(sys.props("user.home")) / ".sbt" / "boot"}"
      )
    )

val `version-jvm` =
  projectMatrix
    .in(file(".jvm"))
    .jvmPlatform(scalaVersions = Seq(Libraries.scala3.revision))
    .settings(publish / skip := true)
    .aggregate(
      version,
      `version-testkit`,
      resolution,
      `version-cli`,
      `sbt-version`
    )

val `version-native` =
  projectMatrix
    .in(file(".native"))
    .nativePlatform(scalaVersions = Seq(Libraries.scala3.revision))
    .defaultAxes(VirtualAxis.native, VirtualAxis.scalaABIVersion(Libraries.scala3.revision))
    .settings(publish / skip := true)
    .aggregate(
      version,
      `version-testkit`,
      resolution,
      `version-cli`
    )

// FIXME: version-js disabled pending scala-js sbt 2.x support (scala-js/scala-js#5238).
//val `version-js` =
//  project
//    .in(file(".js"))
//    .notPublished
//    .aggregate(version.js.get*)

val docs =
  project
    .in(file("docs"))
    .settings(publish / skip := true)
    .enablePlugins(VersionUnidocPlugin)
    .settings(
      ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(
        version.jvm(Libraries.scala3.revision),
        resolution.jvm(Libraries.scala3.revision),
        `sbt-version`.jvm(Libraries.scala3.revision)
      )
    )

val `version-root` =
  projectMatrix
    .in(file("."))
    .settings(publish / skip := true)
    .aggregate(`version-jvm`, `version-native`)

def nativeSettings: List[Setting[?]] = {
  import scala.scalanative.build.Mode
  List(
    Test / parallelExecution := true,
    Compile / unmanagedResourceDirectories +=
      (Compile / sourceDirectory).value / "resources-native",
    Test / unmanagedResourceDirectories +=
      (Test / sourceDirectory).value / "resources-native",
    libraryDependencySchemes += "org.scala-native" % "test-interface_native0.5_3" % "always",
    // Force optimised codegen for every native artefact, including test binaries.
    // Mode.debug keeps every Scala call as a real frame; the resolver path then
    // exceeds musl's default 8MB main-thread stack on Alpine. releaseFast inlines
    // sufficiently to keep stack usage. releaseFull is used for the shipped binary.
    nativeConfig := Def.uncached(
      nativeConfig.value
        .withMultithreading(false)
        .withMode(Mode.releaseFast)
    )
  )
}

def cliNativeSettings: List[Setting[?]] = {
  import scala.scalanative.build.{LTO, Mode}
  val isStaticLink = sys.props.get("release.binary.static").contains("true")
  // releaseFull is what we ship; releaseFast keeps local iteration tractable
  // while still exercising optimised codegen end-to-end. The shipping CI lanes
  // pass `-Dsbt.ci=true` so the binary tested matches the binary shipped.
  val isCi = sys.props.get("sbt.ci").contains("true")
  val mode = if (isCi) Mode.releaseFull else Mode.releaseFast
  val osName = sys.props.getOrElse("os.name", "").toLowerCase
  val isMacOS = osName.contains("mac") || osName.contains("darwin")
  val isLinux = osName.contains("linux")
  // scala-native's own Validator warns LTO.thin is unstable on macOS: thin LTO
  // drops the platform/posix/libunwind/__unw_* symbols the system linker still
  // references, breaking the link. LTO.full performs whole-program optimisation
  // and keeps those symbols reachable.
  val lto = if (isMacOS) LTO.full else LTO.thin
  // Stack protection and source fortification apply to C compilation
  // (resources-native shims). Linux-only because clang on macOS Apple
  // toolchain rejects -D_FORTIFY_SOURCE without a sysroot setting and
  // clang-cl on Windows uses /GS, which is enabled by default for /MT builds.
  val hardening = if (isLinux) Seq("-fstack-protector-strong", "-D_FORTIFY_SOURCE=2") else Nil
  List(
    nativeConfig := Def.uncached {
      val prev = nativeConfig.value
      val base = prev
        .withBaseName("version")
        .withMode(mode)
        .withLTO(lto)
        .withCompileOptions(prev.compileOptions ++ hardening)
      if (isStaticLink) base.withLinkingOptions(base.linkingOptions :+ "-static")
      else base
    }
  )
}

def noticeMappingSettings: Seq[Setting[?]] = List(
  Compile / packageBin / mappings += {
    val converter = fileConverter.value
    val notice = (ThisBuild / baseDirectory).value / "NOTICE"
    converter.toVirtualFile(notice.toPath) -> "META-INF/NOTICE"
  }
)

def unitTestSettings: List[Setting[?]] = List(
  libraryDependencies += Libraries.munit % Test,
  testFrameworks += new TestFramework("munit.Framework")
)

def formattingSettings =
  List(
    scalafmtDetailedError := true,
    scalafmtPrintDiff := true
  )

def publishSettings = pgpSettings ++: List(
  packageOptions += Package.ManifestAttributes(
    "Created-By" -> "Simple Build Tool",
    "Built-By" -> System.getProperty("user.name"),
    "Build-Jdk" -> System.getProperty("java.version"),
    "Specification-Title" -> name.value,
    "Specification-Version" -> Keys.version.value,
    "Specification-Vendor" -> organizationName.value
  ),
  publishTo := {
    if (Keys.version.value.toLowerCase.contains("snapshot"))
      Some("central-snapshots".at("https://central.sonatype.com/repository/maven-snapshots/"))
    else localStaging.value
  },
  pomIncludeRepository := (_ => false),
  publishMavenStyle := true,
  headerLicense := Some(HeaderLicense.ALv2("2023-2026", "Shuwari Africa Ltd.", HeaderLicenseStyle.Detailed))
)

def pgpSettings = List(
  PgpKeys.pgpSelectPassphrase :=
    sys.props
      .get("SIGNING_KEY_PASSPHRASE")
      .map(_.toCharArray),
  usePgpKeyHex(System.getenv("SIGNING_KEY_ID"))
)

def buildInfoSettings = List(
  buildInfoKeys := List[BuildInfoKey](name, Keys.version),
  buildInfoPackage := "version.internal"
)

addCommandAlias("format", "scalafixAll; scalafmtAll; scalafmtSbt; headerCreateAll")

addCommandAlias("check", "scalafixAll --check; scalafmtCheckAll; scalafmtSbtCheck; headerCheckAll")
