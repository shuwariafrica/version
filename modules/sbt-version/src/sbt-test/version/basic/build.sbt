import scala.sys.process.Process

def git(dir: File)(args: String*): String =
  Process("git" :: args.toList, dir).!!.trim

def check(actual: String, expected: String): Unit =
  assert(actual == expected, s"Expected '$expected', got '$actual'")

def assertPrefix(actual: String, prefix: String): Unit =
  assert(actual.startsWith(prefix), s"'$actual' does not start with '$prefix'")

@transient lazy val gitInit = taskKey[Unit]("Initialise git repository")
@transient lazy val gitCommit = taskKey[Unit]("Create a commit")
@transient lazy val gitTag = taskKey[Unit]("Create v1.0.0 tag")
@transient lazy val gitTagPreRelease = taskKey[Unit]("Create v2.0.0-rc.1 tag")
@transient lazy val dirty = taskKey[Unit]("Make worktree dirty")
@transient lazy val checkFallback = taskKey[Unit]("No git repo: 0.1.0-SNAPSHOT")
@transient lazy val checkConcreteTag = taskKey[Unit]("Clean tag: exact version")
@transient lazy val checkDirtyTag = taskKey[Unit]("Dirty tag: snapshot with dirty")
@transient lazy val checkAfterCommit = taskKey[Unit]("After commit: snapshot with metadata")
@transient lazy val checkPreRelease = taskKey[Unit]("Pre-release tag: exact pre-release")
@transient lazy val checkMetadata = taskKey[Unit]("Snapshot metadata structure")

gitInit := {
  val base = baseDirectory.value
  IO.writeLines(base / ".gitignore", List("target", ".bsp", "project/target"))
  git(base)("init", "-b", "main")
  git(base)("config", "user.email", "test@example.com")
  git(base)("config", "user.name", "Test")
  git(base)("config", "commit.gpgsign", "false")
  git(base)("add", ".")
  git(base)("commit", "--no-gpg-sign", "-m", "initial")
}

gitCommit := {
  val base = baseDirectory.value
  IO.writeLines(base / "README.md", List(java.util.UUID.randomUUID().toString))
  git(base)("add", ".")
  git(base)("commit", "--no-gpg-sign", "-am", "work")
}

gitTag := {
  val base = baseDirectory.value
  git(base)("tag", "-a", "--no-sign", "-m", "Release 1.0.0", "v1.0.0")
}

gitTagPreRelease := {
  val base = baseDirectory.value
  git(base)("tag", "-a", "--no-sign", "-m", "RC 2.0.0-rc.1", "v2.0.0-rc.1")
}

dirty := {
  IO.write(baseDirectory.value / "dirty.txt", "dirty")
}

checkFallback := check(resolvedVersion.value.show, "0.1.0-SNAPSHOT")

checkConcreteTag := check(resolvedVersion.value.show, "1.0.0")

checkDirtyTag := {
  val v = resolvedVersion.value
  assertPrefix(v.show, "1.0.1-SNAPSHOT")
  assert(v.metadata.exists(_.identifiers.contains("dirty")), "Expected dirty in metadata")
}

checkAfterCommit := {
  val v = resolvedVersion.value
  assertPrefix(v.show, "1.0.1-SNAPSHOT")
  val meta = v.metadata.get.identifiers
  assert(meta.exists(_.startsWith("commits")), s"Expected commits: $meta")
  assert(meta.exists(_.startsWith("sha")), s"Expected sha: $meta")
}

checkPreRelease := check(resolvedVersion.value.show, "2.0.0-rc.1")

checkMetadata := {
  val v = resolvedVersion.value
  assertPrefix(v.show, "0.1.0-SNAPSHOT")
  val meta = v.metadata.get.identifiers
  assert(meta.exists(_.startsWith("branch")), s"Expected branch: $meta")
  assert(meta.exists(_.startsWith("commits")), s"Expected commits: $meta")
  assert(meta.exists(_.startsWith("sha")), s"Expected sha: $meta")
}
