inThisBuild(
  List(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := List("3.8.3"),
    organization := "africa.shuwari",
    description := "Simple utilities and data structures for the management of application versioning.",
    homepage := Some(url("https://github.com/shuwarifrica/version")),
    startYear := Some(2023),
    semanticdbEnabled := true,
    scmInfo := ScmInfo(
      url("https://github.com/shuwariafrica/version"),
      "scm:git:https://github.com/shuwariafrica/version.git",
      Some("scm:git:git@github.com:shuwariafrica/version.git")
    ).some
  ) ++ formattingSettings
)

val libraries = new {
  val boilerplate = Def.setting("io.github.arashi01" %%% "boilerplate" % "0.7.0")
  val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % "7.6.0.202603022253-r"
  val `os-lib` = Def.setting("com.lihaoyi" %%% "os-lib" % "0.11.7")
  val munit = Def.setting("org.scalameta" %%% "munit" % "1.2.1")
  val scopt = Def.setting("com.github.scopt" %%% "scopt" % "4.1.1-M3")
}

val version =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/core"))
    .settings(fatalWarningsSetting)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(libraryDependency(libraries.boilerplate))
    .nativeSettings(nativeSettings)

val `version-testkit` =
  crossProject(JVMPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/testkit"))
    .settings(fatalWarningsSetting)
    .settings(libraryDependency(libraries.`os-lib`))
    .settings(publish / skip := true)
    .nativeSettings(nativeSettings)

val resolution =
  crossProject(JVMPlatform, NativePlatform)
    .crossType(CrossType.Full)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/resolution"))
    .dependsOn(version)
    .dependsOn(`version-testkit` % Test)
    .settings(fatalWarningsSetting)
    .settings(publishSettings)
    .settings(unitTestSettings)
    .nativeSettings(nativeSettings)
    .settings(libraryDependencies += libraries.`os-lib`.value % Test)
    .jvmSettings(libraryDependencies += libraries.jgit)
    .nativeConfigure(_.enablePlugins(Libgit2Build))
    .settings(noticeMappingSettings)

val `version-cli` =
  crossProject(JVMPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/cli"))
    .dependsOn(resolution)
    .dependsOn(`version-testkit` % Test)
    .settings(fatalWarningsSetting)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(libraryDependency(libraries.scopt))
    .settings(libraryDependencies += libraries.`os-lib`.value % Test)
    .settings(Compile / run / mainClass := Some("version.cli.CLI"))
    .settings(publish / skip := true)
    .jvmSettings(run / fork := true)
    .enablePlugins(BuildInfoPlugin)
    .settings(buildInfoSettings)
    .nativeSettings(nativeSettings)
    .nativeConfigure(_.enablePlugins(Libgit2Build))

val `sbt-version` =
  project
    .in(file("modules/sbt-version"))
    .dependsOn(resolution.jvm)
    .dependsOn(`version-testkit`.jvm % Test)
    .enablePlugins(SbtPlugin)
    .settings(fatalWarningsSetting)
    .settings(publishSettings)
    .settings(unitTestSettings)
    .settings(sbtVersion := "2.0.0-RC12")
    .settings(Compile / scalacOptions -= "-deprecation")
    .settings(
      scriptedBufferLog := true,
      scriptedLaunchOpts ++= Seq(
        s"-Dplugin.version=${(LocalRootProject / Keys.version).value}",
        s"-Dsbt.boot.directory=${file(sys.props("user.home")) / ".sbt" / "boot"}"
      )
    )

val `version-jvm` =
  project
    .in(file(".jvm"))
    .notPublished
    .aggregate(
      version.jvm,
      `version-testkit`.jvm,
      resolution.jvm,
      `version-cli`.jvm,
      `sbt-version`
    )

val `version-native` =
  project
    .in(file(".native"))
    .notPublished
    .aggregate(
      version.native,
      `version-testkit`.native,
      resolution.native,
      `version-cli`.native
    )

val `version-js` =
  project
    .in(file(".js"))
    .notPublished
    .aggregate(
      version.js
    )

val `version-root` =
  project
    .in(file("."))
    .shuwariProject
    .notPublished
    .apacheLicensed
    .aggregate(`version-jvm`, `version-js`, `version-native`)
    .enablePlugins(VersionUnidocPlugin)
    .settings(
      ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(
        version.jvm,
        resolution.jvm,
        `sbt-version`
      )
    )

def nativeSettings = List(
  Test / parallelExecution := true
)

def noticeMappingSettings: Seq[Setting[?]] = List(
  Compile / packageBin / mappings += ((ThisBuild / baseDirectory).value / "NOTICE" -> "META-INF/NOTICE"))

def unitTestSettings: List[Setting[?]] = List(
  libraryDependencies += libraries.munit.value % Test,
  testFrameworks += new TestFramework("munit.Framework")
)

def formattingSettings =
  List(
    scalafmtDetailedError := true,
    scalafmtPrintDiff := true
  )

// Workaround: Scala 3.8+ deprecated -Xfatal-warnings in favour of -Werror
// Must be applied at project level AFTER sbt-shuwari's projectSettings add their options
def fatalWarningsSetting: List[Setting[?]] = List(
  ScalaCompiler.compilerOptions ~= { opts =>
    opts.filterNot(_.option == "-Xfatal-warnings")
  },
  Compile / compile / scalacOptions += "-Werror"
)

def libraryDependency(library: Def.Initialize[ModuleID]) = libraryDependencies += library.value

def publishSettings = pgpSettings ++: List(
  packageOptions += Package.ManifestAttributes(
    "Created-By" -> "Simple Build Tool",
    "Built-By" -> System.getProperty("user.name"),
    "Build-Jdk" -> System.getProperty("java.version"),
    "Specification-Title" -> name.value,
    "Specification-Version" -> Keys.version.value,
    "Specification-Vendor" -> organizationName.value,
    "Implementation-Title" -> name.value,
    "Implementation-Version" -> fullVersion.value,
    "Implementation-Vendor-Id" -> organization.value,
    "Implementation-Vendor" -> organizationName.value
  ),
  publishTo := {
    if (Keys.version.value.toLowerCase.contains("snapshot"))
      Some("central-snapshots".at("https://central.sonatype.com/repository/maven-snapshots/"))
    else localStaging.value
  },
  pomIncludeRepository := (_ => false),
  publishMavenStyle := true,
  headerLicense := Some(HeaderLicense.ALv2("2023", "Shuwari Africa Ltd.", HeaderLicenseStyle.Detailed))
)

def pgpSettings = List(
  PgpKeys.pgpSelectPassphrase :=
    sys.props
      .get("SIGNING_KEY_PASSPHRASE")
      .map(_.toCharArray),
  usePgpKeyHex(System.getenv("SIGNING_KEY_ID"))
)

addCommandAlias("format", "scalafixAll; scalafmtAll; scalafmtSbt; headerCreateAll")

addCommandAlias("check", "scalafixAll --check; scalafmtCheckAll; scalafmtSbtCheck; headerCheckAll")

def buildInfoSettings = List(
  buildInfoKeys := List[BuildInfoKey](name, Keys.version),
  buildInfoPackage := "version.internal"
)
