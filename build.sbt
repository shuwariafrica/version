val libraries = new {
  val boilerplate = Def.setting("io.github.arashi01" %%% "boilerplate" % "0.3.2")
  val `jsoniter-scala` =
    Def.setting("com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-core" % "2.37.6")
  val `jsoniter-scala-macros` = `jsoniter-scala`(_.withName("jsoniter-scala-macros"))
  val `os-lib` = Def.setting("com.lihaoyi" %%% "os-lib" % "0.11.6")
  val munit = Def.setting("org.scalameta" %%% "munit" % "1.2.1")
  val `scala-yaml` = Def.setting("org.virtuslab" %%% "scala-yaml" % "0.3.1")
  val scopt = Def.setting("com.github.scopt" %%% "scopt" % "4.1.1-M3")
  val `zio-json` = Def.setting("dev.zio" %%% "zio-json" % "0.8.0")
  val `zio-prelude` = Def.setting("dev.zio" %%% "zio-prelude" % "1.0.0-RC39")
}

lazy val `version-root` =
  project
    .in(file("."))
    .shuwariProject
    .notPublished
    .apacheLicensed
    .aggregate(jvmProjects, jsProjects, nativeProjects)
    .settings(sonatypeProfileSetting)

lazy val jvmProjects =
  project
    .in(file(".jvm"))
    .notPublished
    .aggregate(
      version.jvm,
      `version-zio-prelude`.jvm,
      `version-codecs-jsoniter`.jvm,
      `version-codecs-zio`.jvm,
      `version-codecs-yaml`.jvm,
      `version-cli-core`.jvm,
      `version-cli`.jvm,
      `sbt-version`
    )

lazy val nativeProjects =
  project
    .in(file(".native"))
    .notPublished
    .aggregate(
      version.native,
      `version-zio-prelude`.native,
      `version-codecs-jsoniter`.native,
      `version-codecs-zio`.native,
      `version-codecs-yaml`.native,
      `version-cli-core`.native,
      `version-cli`.native
    )

lazy val jsProjects =
  project
    .in(file(".js"))
    .notPublished
    .aggregate(
      version.js,
      `version-zio-prelude`.js,
      `version-codecs-jsoniter`.js,
      `version-codecs-zio`.js,
      `version-codecs-yaml`.js
    )

lazy val version =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/core"))
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(libraryDependency(libraries.boilerplate))
    .nativeSettings(nativeSettings)

lazy val `version-zio-prelude` =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/zio-prelude"))
    .dependsOn(version)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(libraryDependency(libraries.`zio-prelude`))
    .nativeSettings(nativeSettings)

lazy val `version-codecs-jsoniter` =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/codecs-jsoniter"))
    .settings(unitTestSettings)
    .settings(publishSettings)
    .dependsOn(version)
    .settings(libraryDependency(libraries.`jsoniter-scala`))
    .settings(libraryDependency(libraries.`jsoniter-scala-macros`(_.withConfigurations(Some(Provided.name)))))
    .nativeSettings(nativeSettings)

lazy val `version-codecs-zio` =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/codecs-zio"))
    .settings(unitTestSettings)
    .settings(publishSettings)
    .dependsOn(version)
    .settings(libraryDependency(libraries.`zio-json`))
    .nativeSettings(nativeSettings)

lazy val `version-codecs-yaml` =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/codecs-yaml"))
    .settings(unitTestSettings)
    .dependsOn(version)
    .settings(libraryDependency(libraries.`scala-yaml`))
    .nativeSettings(nativeSettings)

lazy val `version-cli-core` =
  crossProject(JVMPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/cli-core"))
    .dependsOn(version)
    .settings(unitTestSettings)
    .settings(libraryDependency(libraries.`os-lib`))
    .nativeSettings(nativeSettings)

lazy val `version-cli` =
  crossProject(JVMPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/cli"))
    .dependsOn(`version-codecs-yaml`)
    .dependsOn(`version-codecs-jsoniter`)
    .dependsOn(`version-cli-core`)
    .settings(unitTestSettings)
    .settings(libraryDependency(libraries.scopt))
    .settings(Compile / run / mainClass := Some("version.cli.CLI"))
    .settings(publish / skip := true)
    .jvmSettings(run / fork := true)
    .enablePlugins(BuildInfoPlugin)
    .settings(buildInfoSettings)
    .nativeSettings(nativeSettings)

lazy val `sbt-version` =
  project
    .in(file("modules/sbt-version"))
    .enablePlugins(SbtPlugin)
    .settings(sbtVersion := "2.0.0-RC8")
    .settings(Compile / scalacOptions --= Seq("-deprecation"))
    .settings(
      ScriptedPlugin.autoImport.scriptedLaunchOpts ++= {
        val pluginVersion = (LocalRootProject / Keys.version).value
        Seq(s"-Dplugin.version=$pluginVersion")
      }
    )
    .settings(unitTestSettings)
    .dependsOn(`version-cli-core`.jvm)

inThisBuild(
  List(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := List("3.7.4"),
    organization := "africa.shuwari",
    description := "Simple utilities and data structures for the management of application versioning.",
    homepage := Some(url("https://github.com/shuwarifrica/version")),
    startYear := Some(2023),
    semanticdbEnabled := true,
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    publishCredentials,
    scmInfo := ScmInfo(
      url("https://github.com/shuwariafrica/version"),
      "scm:git:https://github.com/shuwariafrica/version.git",
      Some("scm:git:git@github.com:shuwariafrica/version.git")
    ).some
  ) ++ formattingSettings
)

def nativeSettings = List(
  Test / parallelExecution := false
)

def unitTestSettings: List[Setting[?]] = List(
  libraryDependencies += libraries.munit.value % Test,
  testFrameworks += new TestFramework("munit.Framework"),
  Test / envVars ++= {
    val p = (LocalRootProject / baseDirectory).value / "scripts" / "create-test-repo.sh"
    Map("CREATE_TEST_REPO" -> p.getAbsolutePath)
  }
)

def formattingSettings =
  List(
    scalafmtDetailedError := true,
    scalafmtPrintDiff := true
  )

def libraryDependency(library: Def.Initialize[ModuleID]) = libraryDependencies += library.value

def publishCredentials = credentials := List(
  Credentials(
    "Sonatype Nexus Repository Manager",
    sonatypeCredentialHost.value,
    System.getenv("PUBLISH_USER"),
    System.getenv("PUBLISH_USER_PASSPHRASE")
  )
)

def publishSettings = publishCredentials +: pgpSettings ++: List(
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
  publishTo := sonatypePublishToBundle.value,
  pomIncludeRepository := (_ => false),
  publishMavenStyle := true,
  sonatypeProfileSetting,
  headerLicense := Some(HeaderLicense.ALv2("2023", "Shuwari Africa Ltd.", HeaderLicenseStyle.Detailed))
)

def sonatypeProfileSetting = sonatypeProfileName := "africa.shuwari"

def pgpSettings = List(
  PgpKeys.pgpSelectPassphrase :=
    sys.props
      .get("SIGNING_KEY_PASSPHRASE")
      .map(_.toCharArray),
  usePgpKeyHex(System.getenv("SIGNING_KEY_ID"))
)

def disableStaticAnalysisPlugins(p: Project) = p.disablePlugins(HeaderPlugin, ScalafixPlugin, ScalafmtPlugin)

addCommandAlias("format", "scalafixAll; scalafmtAll; scalafmtSbt; headerCreateAll")

addCommandAlias("staticCheck", "scalafixAll --check; scalafmtCheckAll; scalafmtSbtCheck; headerCheckAll")

def buildInfoSettings = List(
  buildInfoKeys := List[BuildInfoKey](name, Keys.version),
  buildInfoPackage := "version.internal"
)
