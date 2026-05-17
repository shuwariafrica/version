import sbt.*
import sbt.Keys.*
//import sbt.librarymanagement.ModuleID

object BuildLibraries extends AutoPlugin:
  val `sbt-shuwari`: Def.Initialize[ModuleID] = sbtPlugin("africa.shuwari.sbt" % "sbt-shuwari" % "0.15.3")
  val `sbt-scalafix` = sbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.6")
  val `sbt-dynver` = sbtPlugin("com.github.sbt" % "sbt-dynver" % "5.1.1")
  val `sbt-scalafmt` = sbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.0")
  val `sbt-scala-native` = sbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.11")
  val `sbt-pgp` = sbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.1")
  val `sbt-buildinfo` = sbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")
  val `sbt-header` = sbtPlugin("com.github.sbt" % "sbt-header" % "5.11.0")

  // FIXME: Scala.js temporarily disabled pending sbt 2.x support - tracking scala-js/scala-js#5238.
  // val `sbt-scalajs` = sbtPlugin("org.scala-js" % "sbt-scalajs" % scalaJSVersion)

  val `sbt-unidoc` = sbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.6.1")
  val `sbt-mdoc` = sbtPlugin("org.scalameta" % "sbt-mdoc" % "2.9.0")
  val `sbt-bloop` = sbtPlugin("ch.epfl.scala" % "sbt-bloop" % "2.0.19")

  def sbtPlugin(module: ModuleID): Def.Initialize[ModuleID] = Def.setting {
    val sbtV = (pluginCrossBuild / sbtBinaryVersion).value
    val scalaV = (update / scalaBinaryVersion).value
    Defaults.sbtPluginExtra(module, sbtV, scalaV)
  }
