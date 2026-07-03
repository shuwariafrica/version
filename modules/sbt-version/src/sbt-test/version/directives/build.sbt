import scala.sys.process.Process

def git(dir: File)(args: String*): String =
  Process("git" :: args.toList, dir).!!.trim

def assertPrefix(actual: String, prefix: String): Unit =
  assert(actual.startsWith(prefix), s"'$actual' does not start with '$prefix'")

@transient lazy val gitInit = taskKey[Unit]("Initialise git repository with v1.0.0")
@transient lazy val gitCommitMsg = inputKey[Unit]("Create a commit with given message")
@transient lazy val gitTag = inputKey[Unit]("Create an annotated tag")
@transient lazy val checkPrefix = inputKey[Unit]("Assert version starts with prefix")
@transient lazy val checkHistory = inputKey[Unit]("Assert versionHistory holds the given space-separated versions")

// Splice the plugin's versionHistory Initialize into a setting, exactly as a mima consumer would.
@transient lazy val historyShown = settingKey[Set[String]]("Rendered versionHistory for assertions")
historyShown := VersionPlugin.versionHistory.value.map(_.show)

gitInit := {
  val base = baseDirectory.value
  IO.writeLines(base / ".gitignore", List("target", ".bsp", "project/target"))
  git(base)("init", "-b", "main")
  git(base)("config", "user.email", "test@example.com")
  git(base)("config", "user.name", "Test")
  git(base)("config", "commit.gpgsign", "false")
  git(base)("add", ".")
  git(base)("commit", "--no-gpg-sign", "-m", "initial")
  git(base)("tag", "-a", "--no-sign", "-m", "v1.0.0", "v1.0.0")
}

gitCommitMsg := {
  val base = baseDirectory.value
  val msg = complete.Parsers.spaceDelimited("<msg>").parsed.mkString(" ")
  IO.writeLines(base / "README.md", List(java.util.UUID.randomUUID().toString))
  git(base)("add", ".")
  git(base)("commit", "--no-gpg-sign", "-m", msg)
}

gitTag := {
  val base = baseDirectory.value
  val tag = complete.Parsers.spaceDelimited("<tag>").parsed.head
  git(base)("tag", "-a", "--no-sign", "-m", tag, tag)
}

checkPrefix := {
  val prefix = complete.Parsers.spaceDelimited("<prefix>").parsed.head
  assertPrefix(resolvedVersion.value.show, prefix)
}

checkHistory := {
  val expected = complete.Parsers.spaceDelimited("<versions>").parsed.toSet
  val actual = historyShown.value
  assert(actual == expected, s"versionHistory $actual != expected $expected")
}
