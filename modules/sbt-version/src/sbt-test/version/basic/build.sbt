import scala.sys.process.stringToProcess

// Helper to run git commands in a directory
def runGit(dir: File)(args: String*): String =
  scala.sys.process.Process("git" :: args.toList, dir).!!.trim

def check(a: String, e: String): Unit =
  assert(a == e, s"Version mismatch: Expected '$e', Got '$a'")

def assertStartsWith(a: String, prefix: String): Unit =
  assert(a.startsWith(prefix), s"Version '$a' does not start with '$prefix'")

def assertContains(a: String, substring: String): Unit =
  assert(a.contains(substring), s"Version '$a' does not contain '$substring'")

// Task keys
@transient lazy val gitInit = taskKey[Unit]("Initialise git repository")
@transient lazy val gitCommit = taskKey[Unit]("Create a commit")
@transient lazy val gitTag = taskKey[Unit]("Create an annotated tag")
@transient lazy val gitTagPreRelease = taskKey[Unit]("Create a pre-release tag")
@transient lazy val dirty = taskKey[Unit]("Make working directory dirty")
@transient lazy val checkOnTag = taskKey[Unit]("Check version at tagged commit (clean)")
@transient lazy val checkOnTagDirty = taskKey[Unit]("Check version at tagged commit (dirty)")
@transient lazy val checkOnTagAndCommit = taskKey[Unit]("Check version after tagged commit")
@transient lazy val checkPreReleaseTag = taskKey[Unit]("Check version at pre-release tag")
@transient lazy val checkSnapshotMetadata = taskKey[Unit]("Check SNAPSHOT metadata structure")
@transient lazy val checkFallback = taskKey[Unit]("Check fallback version when no git repo")
@transient lazy val checkExtendedShowDirect = taskKey[Unit]("Regression: direct use of Extended.show with resolvedVersion.value")
@transient lazy val checkNestedVersionAccess = taskKey[Unit]("Regression: nested task accessing resolvedVersion")

// Git operations
gitInit := {
  val log = streams.value.log
  val base = baseDirectory.value
  log.info(s"gitInit: Initialising git in $base")
  IO.writeLines(base / ".gitignore", List("target", ".bsp", "project/target"))
  runGit(base)("init", "-b", "main")
  runGit(base)("config", "user.email", "test@example.com")
  runGit(base)("config", "user.name", "Test")
  runGit(base)("config", "commit.gpgsign", "false")
  runGit(base)("add", ".")
  runGit(base)("commit", "--no-gpg-sign", "-m", "initial")
  log.info(s"gitInit: Done - HEAD is ${runGit(base)("rev-parse", "HEAD")}")
}

gitCommit := {
  val log = streams.value.log
  val base = baseDirectory.value
  IO.writeLines(base / "README.md", List("# Test", java.util.UUID.randomUUID().toString))
  runGit(base)("add", ".")
  runGit(base)("commit", "--no-gpg-sign", "-am", "commit")
  log.info(s"gitCommit: Done - HEAD is ${runGit(base)("rev-parse", "HEAD")}")
}

gitTag := {
  val log = streams.value.log
  val base = baseDirectory.value
  runGit(base)("tag", "-a", "--no-sign", "-m", "Release 1.0.0", "v1.0.0")
  log.info(s"gitTag: Created v1.0.0 at ${runGit(base)("rev-parse", "HEAD")}")
}

gitTagPreRelease := {
  val log = streams.value.log
  val base = baseDirectory.value
  runGit(base)("tag", "-a", "--no-sign", "-m", "Pre-release 2.0.0-rc.1", "v2.0.0-rc.1")
  log.info(s"gitTagPreRelease: Created v2.0.0-rc.1 at ${runGit(base)("rev-parse", "HEAD")}")
}

dirty := {
  import java.nio.file.*, StandardOpenOption.*
  import scala.jdk.CollectionConverters.*
  Files.write(baseDirectory.value.toPath.resolve("dirty.txt"), Seq("dirty").asJava, CREATE, APPEND): Unit
}

// Check implementations
checkOnTag := {
  val v = resolvedVersion.value.show
  check(v, "1.0.0")
}

checkOnTagDirty := {
  val v = resolvedVersion.value
  val showStd = v.show
  assertStartsWith(showStd, "1.0.1-SNAPSHOT")
  // Check metadata contains dirty marker
  assert(v.metadata.isDefined, s"Expected metadata to be defined, got None")
  val meta = v.metadata.get.identifiers
  assert(meta.contains("dirty"), s"Expected 'dirty' in metadata: $meta")
}

checkOnTagAndCommit := {
  val v = resolvedVersion.value
  val showStd = v.show
  assertStartsWith(showStd, "1.0.1-SNAPSHOT")
  // Check metadata contains commit count and sha
  assert(v.metadata.isDefined, s"Expected metadata to be defined, got None")
  val meta = v.metadata.get.identifiers
  assert(meta.exists(_.startsWith("commits")), s"Expected commits in metadata: $meta")
  assert(meta.exists(_.startsWith("sha")), s"Expected sha in metadata: $meta")
}

checkPreReleaseTag := {
  val v = resolvedVersion.value.show
  check(v, "2.0.0-rc.1")
}

checkSnapshotMetadata := {
  val v = resolvedVersion.value
  // Standard show excludes metadata, check fields directly
  val showStd = v.show
  assertStartsWith(showStd, "0.1.0-SNAPSHOT")
  // Verify metadata is present in the resolved version
  assert(v.metadata.isDefined, s"Expected metadata to be defined, got None")
  val meta = v.metadata.get.identifiers
  assert(meta.exists(_.startsWith("branch")), s"Expected branch in metadata: $meta")
  assert(meta.exists(_.startsWith("commits")), s"Expected commits in metadata: $meta")
  assert(meta.exists(_.startsWith("sha")), s"Expected sha in metadata: $meta")
}

checkFallback := {
  val v = resolvedVersion.value.show
  check(v, "0.1.0-SNAPSHOT")
}

// Regression tests for sbt 2.x caching integration (HashWriter/IsoString)
// These tests verify that resolvedVersion.value can be used in various contexts
// without causing caching issues.

checkExtendedShowDirect := {
  // Directly use Version.Show.Extended.show with resolvedVersion.value
  // This is a common pattern that must work correctly with sbt caching
  val v = resolvedVersion.value
  val extended = Version.Show.Extended.show(v)
  assertStartsWith(extended, "0.1.0-SNAPSHOT+")
  assertContains(extended, "branch")
  assertContains(extended, ".commits")
  assertContains(extended, ".sha")
  // Verify Full show preserves complete SHA
  val full = Version.Show.Full.show(v)
  assertStartsWith(full, "0.1.0-SNAPSHOT+")
  // Full SHA should be 40 chars, so sha identifier should be 43 chars (sha + 40)
  val meta = v.metadata.get.identifiers
  val shaId = meta.find(_.startsWith("sha")).get
  assert(shaId.length == 43, s"Expected full SHA (43 chars), got ${shaId.length}: $shaId")
}

// Helper task that returns a computed string from resolvedVersion
@transient lazy val computeVersionString = taskKey[String]("Compute version string")
computeVersionString := {
  val v = resolvedVersion.value
  s"Version: ${v.major.value}.${v.minor.value}.${v.patch.value}, snapshot=${v.snapshot}"
}

checkNestedVersionAccess := {
  // Access resolvedVersion through a nested task dependency
  // This tests that the Version type works correctly with sbt's task graph caching
  val computed = computeVersionString.value
  assert(computed.contains("Version: 0.1.0"), s"Unexpected computed string: $computed")
  assert(computed.contains("snapshot=true"), s"Expected snapshot=true: $computed")

  // Also verify we can use resolvedVersion directly after the nested call
  val v = resolvedVersion.value
  assert(v.snapshot, "Expected snapshot version")
  assert(v.major.value == 0, s"Expected major=0, got ${v.major.value}")
  assert(v.minor.value == 1, s"Expected minor=1, got ${v.minor.value}")
  assert(v.patch.value == 0, s"Expected patch=0, got ${v.patch.value}")
}
