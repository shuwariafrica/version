// Test fallback behaviour when not in a Git repository

lazy val root = project
  .in(file("."))
  .enablePlugins(VersionPlugin)
  .settings(
    name := "no-repo-test",
    scalaVersion := "3.7.4",
    // Task to verify fallback version is used
    TaskKey[Unit]("checkFallback") := {
      val v = resolvedVersion.value
      assert(v.show == "0.1.0-SNAPSHOT", s"Expected 0.1.0-SNAPSHOT, got ${v.show}")
    }
  )
