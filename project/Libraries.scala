import sbt.*

object Libraries:
  // sbt 2.0.x compiles build definitions with Scala 3.8, so the sbt plugin and everything it depends on (the libraries
  // here and boilerplate) must be built with 3.8 and with dependencies built no later than 3.8.
  val scala3 = "org.scala-lang" %% "scala3-library" % "3.8.4"
  // The CLI is an application nothing else reads the TASTy of.
  val cliScala3 = "org.scala-lang" %% "scala3-library" % "3.10.1"
  val jdkRelease: Int = 21
  val boilerplate: ModuleID = "africa.shuwari" %% "boilerplate" % "0.13.0"
  val boilerplateEffect: ModuleID = "africa.shuwari" %% "boilerplate-effect" % "0.13.0"
  val jgit: ModuleID = "org.eclipse.jgit" % "org.eclipse.jgit" % "7.8.1.202611041320-r"
  val munit: ModuleID = "org.scalameta" %% "munit" % "1.3.6"
  val scopt: ModuleID = "com.github.scopt" %% "scopt" % "4.2.0"
