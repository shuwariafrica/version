val libraries = new {
  val scalaVersion = "3.3.1"
  val `jsoniter-scala` =
    Def.setting("com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-core" % "2.27.5")
  val `jsoniter-scala-macros` =
    Def.setting("com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-macros" % "2.27.5" % Provided)
  val munit = Def.setting("org.scalameta" %%% "munit" % "1.0.0-M10")
  val `zio-json` = Def.setting("dev.zio" %%% "zio-json" % "0.6.2")
  val `zio-prelude` = Def.setting("dev.zio" %%% "zio-prelude" % "1.0.0-RC21")
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
    .aggregate(version.jvm, `version-codecs-jsoniter`.jvm, `version-codecs-zio`.jvm)

lazy val nativeProjects =
  project
    .in(file(".native"))
    .notPublished
    .aggregate(version.native, `version-codecs-jsoniter`.native, `version-codecs-zio`.native)

lazy val jsProjects =
  project
    .in(file(".js"))
    .notPublished
    .aggregate(version.js, `version-codecs-jsoniter`.js, `version-codecs-zio`.js)

lazy val version =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/core"))
    .jsConfigure(disableStaticAnalysisPlugins)
    .nativeConfigure(disableStaticAnalysisPlugins)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(libraryDependency(libraries.`zio-prelude`))

lazy val `version-codecs-jsoniter` =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/codecs-jsoniter"))
    .jsConfigure(disableStaticAnalysisPlugins)
    .nativeConfigure(disableStaticAnalysisPlugins)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .dependsOn(version)
    .settings(libraryDependency(libraries.`jsoniter-scala`))
    .settings(libraryDependency(libraries.`jsoniter-scala-macros`))

lazy val `version-codecs-zio` =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/codecs-zio"))
    .jsConfigure(disableStaticAnalysisPlugins)
    .nativeConfigure(disableStaticAnalysisPlugins)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .dependsOn(version)
    .settings(libraryDependency(libraries.`zio-json`))

inThisBuild(
  List(
    Keys.version := VersionPlugin.versionSetting.value,
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := List(libraries.scalaVersion),
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

def unitTestSettings: List[Setting[?]] = List(
  libraryDependencies += libraries.munit.value % Test,
  testFrameworks += new TestFramework("munit.Framework")
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
    "Implementation-Version" -> VersionPlugin.implementationVersionSetting.value,
    "Implementation-Vendor-Id" -> organization.value,
    "Implementation-Vendor" -> organizationName.value
  ),
  publishTo := sonatypePublishToBundle.value,
  pomIncludeRepository := (_ => false),
  publishMavenStyle := true,
  sonatypeProfileSetting
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
