addSbtPlugin("africa.shuwari.sbt" % "sbt-shuwari" % "0.15.3")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.6")
addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.1.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.11")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.1")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")
addSbtPlugin("com.github.sbt" % "sbt-header" % "5.11.0")

// FIXME: Scala.js temporarily disabled pending sbt 2.x support - tracking scala-js/scala-js#5238.
// Reinstate by uncommenting below and re-enabling the `.jsPlatform` axis in build.sbt.
// addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.20.2")

// Documentation
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.6.1")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.9.0")
addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "2.0.19")
