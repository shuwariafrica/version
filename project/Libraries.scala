import sbt.*

object Libraries:
  val scala3 = "org.scala-lang" %% "scala3-library" % "3.8.3"
  val boilerplate: ModuleID = "io.github.arashi01" %% "boilerplate" % "0.7.0"
  val jgit: ModuleID = "org.eclipse.jgit" % "org.eclipse.jgit" % "7.6.0.202603022253-r"
  val `os-lib`: ModuleID = "com.lihaoyi" %% "os-lib" % "0.11.7"
  val munit: ModuleID = "org.scalameta" %% "munit" % "1.3.0"
  val scopt: ModuleID = "com.github.scopt" %% "scopt" % "4.1.1-M3"
