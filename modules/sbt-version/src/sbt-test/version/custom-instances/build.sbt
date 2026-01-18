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
@transient lazy val gitInit = taskKey[Unit]("Initialise git repository with custom tag format")
@transient lazy val checkBothCustomInstances = taskKey[Unit]("Check both custom Read and Resolver work together")
@transient lazy val checkExtendedShow = taskKey[Unit]("Check Extended Show includes metadata after commit")

// Custom Read instance that accepts 'release-' prefix instead of 'v'
ThisBuild / versionRead := {
  new Version.Read[String]:
    private val delegate = Version.Read.ReadString
    extension (s: String)
      def toVersion(using _root_.version.PreRelease.Resolver): Either[_root_.version.errors.ParseError, _root_.version.Version] =
        val normalised = if s.startsWith("release-") then s.stripPrefix("release-") else s
        delegate.toVersion(normalised)
      def toVersionUnsafe(using _root_.version.PreRelease.Resolver): _root_.version.Version =
        toVersion match
          case Right(v) => v
          case Left(e)  => throw e
}

// Custom Resolver instance that maps "nightly" to Snapshot
ThisBuild / versionResolver := {
  new PreRelease.Resolver:
    extension (identifiers: List[String])
      def resolve: Option[_root_.version.PreRelease] =
        identifiers match
          case List("nightly") => Some(_root_.version.PreRelease.snapshot)
          case _               => PreRelease.Resolver.given_Resolver.resolve(identifiers)
}

gitInit := {
  val log = streams.value.log
  val base = baseDirectory.value
  log.info(s"gitInit: Initialising git in $base with custom tag format")
  IO.writeLines(base / ".gitignore", List("target", ".bsp", "project/target"))
  runGit(base)("init", "-b", "main")
  runGit(base)("config", "user.email", "test@example.com")
  runGit(base)("config", "user.name", "Test")
  runGit(base)("config", "commit.gpgsign", "false")
  runGit(base)("add", ".")
  runGit(base)("commit", "--no-gpg-sign", "-m", "initial")
  // Create a tag with BOTH custom prefix AND custom pre-release:
  // - 'release-' prefix requires custom Read to strip it
  // - 'nightly' pre-release requires custom Resolver to map it to SNAPSHOT
  runGit(base)("tag", "-a", "--no-sign", "-m", "Release 2.5.0 nightly", "release-2.5.0-nightly")
  log.info(s"gitInit: Created release-2.5.0-nightly tag at ${runGit(base)("rev-parse", "HEAD")}")
}

checkBothCustomInstances := {
  // This test verifies BOTH custom Read AND custom Resolver work together:
  // - Custom Read strips 'release-' prefix → "2.5.0-nightly"
  // - Custom Resolver maps 'nightly' → SNAPSHOT classifier
  // Result: Version(2, 5, 0, Some(PreRelease(Snapshot, None)))
  val v = resolvedVersion.value
  val showStd = v.show

  // Verify the rendered version
  check(showStd, "2.5.0-SNAPSHOT")

  // Verify parsed components
  assert(v.major.value == 2, s"Expected major=2, got ${v.major.value}")
  assert(v.minor.value == 5, s"Expected minor=5, got ${v.minor.value}")
  assert(v.patch.value == 0, s"Expected patch=0, got ${v.patch.value}")

  // Verify pre-release is SNAPSHOT (proves custom Resolver worked)
  assert(v.preRelease.isDefined, "Should have preRelease (proves custom Resolver mapped 'nightly')")
  assert(
    v.preRelease.get.classifier == _root_.version.PreReleaseClassifier.Snapshot,
    s"Expected SNAPSHOT classifier, got ${v.preRelease.get.classifier}"
  )

  // This is a snapshot since SNAPSHOT is a pre-release
  assert(v.snapshot, "Should be a snapshot (SNAPSHOT is pre-release)")
}

checkExtendedShow := {
  // After a commit, version should be a development version
  // The base version (2.5.0-SNAPSHOT) should bump to 2.5.0-SNAPSHOT+metadata
  // (No patch bump since base is already a pre-release)
  val v = resolvedVersion.value
  val extended = Version.Show.Extended.show(v)
  val standard = Version.Show.Standard.show(v)

  // Standard should not include metadata
  check(standard, "2.5.0-SNAPSHOT")

  // Extended should include metadata
  assertStartsWith(extended, "2.5.0-SNAPSHOT+")
  assertContains(extended, "branch")
  assertContains(extended, ".commits")
  assertContains(extended, ".sha")
}
