import scala.sys.process.stringToProcess

def sbtLoggerToScalaSysProcessLogger(log: Logger): scala.sys.process.ProcessLogger =
  new scala.sys.process.ProcessLogger {
    def buffer[T](f: => T): T = f
    def err(s: => String): Unit = log.info(s)
    def out(s: => String): Unit = log.info(s)
  }

def git(dir: File)(args: String*)(using log: Logger): String = {
  implicit val pl: scala.sys.process.ProcessLogger = sbtLoggerToScalaSysProcessLogger(log)
  scala.sys.process.Process("git" :: args.toList, dir).!!(pl).trim
}

def assertStartsWith(a: String, prefix: String) =
  assert(a.startsWith(prefix), s"Version '$a' does not start with '$prefix'")

@transient lazy val gitInit = taskKey[Unit]("Initialise git repository")
@transient lazy val gitCommitWithMessage = inputKey[Unit]("Create a commit with message")
@transient lazy val gitTag = inputKey[Unit]("Create an annotated tag")
@transient lazy val checkVersionPrefix = inputKey[Unit]("Check version starts with prefix")

gitCommitWithMessage := {
  given Logger = streams.value.log
  val base = baseDirectory.value
  val msg = complete.Parsers.spaceDelimited("<message>").parsed.mkString(" ")
  IO.writeLines(base / "README.md", List("# Test", java.util.UUID.randomUUID().toString))
  git(base)("add", ".")
  git(base)("commit", "--no-gpg-sign", "-m", msg)
}

gitTag := {
  given Logger = streams.value.log
  val base = baseDirectory.value
  val tagName = complete.Parsers.spaceDelimited("<tag>").parsed.head
  git(base)("tag", "-a", "--no-sign", "-m", s"Release $tagName", tagName)
}

checkVersionPrefix := {
  val prefix = complete.Parsers.spaceDelimited("<prefix>").parsed.head
  val v = resolvedVersion.value.show
  assertStartsWith(v, prefix)
}

gitInit := {
  given Logger = streams.value.log
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
