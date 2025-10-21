val libraries = new {
  val `jsoniter-scala` =
    Def.setting("com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-core" % "2.33.2")
  val `jsoniter-scala-macros` =
    Def.setting("com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-macros" % "2.33.2" % Provided)
  val munit = Def.setting("org.scalameta" %%% "munit" % "1.1.0")
  val `zio-json` = Def.setting("dev.zio" %%% "zio-json" % "0.7.23")
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
    .aggregate(version.jvm, `version-zio-prelude`.jvm, `version-codecs-jsoniter`.jvm, `version-codecs-zio`.jvm)

lazy val nativeProjects =
  project
    .in(file(".native"))
    .notPublished
    .aggregate(version.native, `version-zio-prelude`.native, `version-codecs-jsoniter`.native, `version-codecs-zio`.native)

lazy val jsProjects =
  project
    .in(file(".js"))
    .notPublished
    .aggregate(version.js, `version-zio-prelude`.js, `version-codecs-jsoniter`.js, `version-codecs-zio`.js)

lazy val version =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/core"))
    .settings(unitTestSettings)
    .settings(publishSettings)

lazy val `version-zio-prelude` =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/zio-prelude"))
    .dependsOn(version)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(libraryDependency(libraries.`zio-prelude`))

lazy val `version-codecs-jsoniter` =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/codecs-jsoniter"))
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
    .settings(unitTestSettings)
    .settings(publishSettings)
    .dependsOn(version)
    .settings(libraryDependency(libraries.`zio-json`))

inThisBuild(
  List(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := List("3.3.7"),
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
    "Implementation-Version" -> fullVersion.value,
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
