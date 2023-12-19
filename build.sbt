val libraries = new {
  val scalaVersion = "3.3.1"
  val munit = Def.setting("org.scalameta" %%% "munit" % "1.0.0-M10")
  val `zio-prelude` = Def.setting("dev.zio" %%% "zio-prelude" % "1.0.0-RC21")
}

lazy val `version-root` =
  project
    .in(file("."))
    .shuwariProject
    .apacheLicensed
    .aggregate(version.jvm, version.js, version.native)

lazy val version =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/core"))
    .jsConfigure(_.disablePlugins(ScalafmtPlugin, ScalafixPlugin, HeaderPlugin))
    .settings(unitTestSettings)
    .settings(libraryDependency(libraries.`zio-prelude`))

inThisBuild(
  List(
    Keys.version := VersionPlugin.versionSetting.value,
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := List(libraries.scalaVersion),
    semanticdbEnabled := true
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

addCommandAlias("format", "scalafixAll; scalafmtAll; scalafmtSbt; headerCreateAll")

addCommandAlias("staticCheck", "scalafixAll --check; scalafmtCheckAll; scalafmtSbtCheck; headerCheckAll")
