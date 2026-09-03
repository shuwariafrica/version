import sbt.*

object Libraries:
  val scala3 = "org.scala-lang" %% "scala3-library" % "3.9.0"
  val boilerplate: ModuleID = "africa.shuwari" %% "boilerplate" % "0.13.0"
  val boilerplateEffect: ModuleID = "africa.shuwari" %% "boilerplate-effect" % "0.13.0"
  val jgit: ModuleID = "org.eclipse.jgit" % "org.eclipse.jgit" % "7.7.1.202607240634-r"
  val munit: ModuleID = "org.scalameta" %% "munit" % "1.3.5"
  val scopt: ModuleID = "com.github.scopt" %% "scopt" % "4.1.1-M3"
