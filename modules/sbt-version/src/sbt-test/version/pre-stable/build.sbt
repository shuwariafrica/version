import scala.sys.process.Process

def git(dir: File)(args: String*): String =
  Process("git" :: args.toList, dir).!!.trim

def assertPrefix(actual: String, prefix: String): Unit =
  assert(actual.startsWith(prefix), s"'$actual' does not start with '$prefix'")

@transient lazy val gitInit = taskKey[Unit]("Initialise git repository tagged v0.93.9")
@transient lazy val gitCommitMsg = inputKey[Unit]("Create a commit with the given message")
@transient lazy val checkPrefix = inputKey[Unit]("Assert version starts with prefix")

gitInit := {
  val base = baseDirectory.value
  IO.writeLines(base / ".gitignore", List("target", ".bsp", "project/target"))
  git(base)("init", "-b", "main")
  git(base)("config", "user.email", "test@example.com")
  git(base)("config", "user.name", "Test")
  git(base)("config", "commit.gpgsign", "false")
  git(base)("add", ".")
  git(base)("commit", "--no-gpg-sign", "-m", "initial")
  git(base)("tag", "-a", "--no-sign", "-m", "v0.93.9", "v0.93.9")
}

gitCommitMsg := {
  val base = baseDirectory.value
  val msg = complete.Parsers.spaceDelimited("<msg>").parsed.mkString(" ")
  IO.writeLines(base / "README.md", List(java.util.UUID.randomUUID().toString))
  git(base)("add", ".")
  git(base)("commit", "--no-gpg-sign", "-m", msg)
}

checkPrefix := {
  val prefix = complete.Parsers.spaceDelimited("<prefix>").parsed.head
  assertPrefix(resolvedVersion.value.show, prefix)
}
