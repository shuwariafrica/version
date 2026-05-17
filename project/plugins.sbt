import BuildLibraries.*

libraryDependencies ++= List(
//  BuildLibraries.`sbt-scala-js`.value, TODO: Scala.js temporarily disabled pending sbt 2.x support
  BuildLibraries.`sbt-scala-native`.value,
  BuildLibraries.`sbt-shuwari`.value,
  BuildLibraries.`sbt-scalafix`.value,
  BuildLibraries.`sbt-dynver`.value,
  BuildLibraries.`sbt-scalafmt`.value,
  BuildLibraries.`sbt-pgp`.value,
  BuildLibraries.`sbt-buildinfo`.value,
  BuildLibraries.`sbt-header`.value,
  BuildLibraries.`sbt-unidoc`.value,
  BuildLibraries.`sbt-mdoc`.value,
  BuildLibraries.`sbt-bloop`.value
)

enablePlugins(BuildInfoPlugin)
buildInfoObject := "Info"
buildInfoKeys := Seq[BuildInfoKey](
  "scalaNativeVersion" -> `sbt-scala-native`.value.revision
)
