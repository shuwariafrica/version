package version.cli.core

import os.*

import scala.util.control.NonFatal

/** Utility to create and manage ephemeral test Git repositories. */
trait TestRepoSupport:

  /** Create a fresh repository by running the bash fixture script. */
  def withFreshRepo[A](testName: String)(f: os.Path => A): A =
    val tmp = os.temp.dir(prefix = s"version-cli-core-$testName-")
    val scriptPath =
      Option(System.getenv("CREATE_TEST_REPO")).map(os.Path(_)).getOrElse(os.pwd / "scripts" / "create-test-repo.sh")
    try
      os.proc("bash", scriptPath.toString, tmp.toString).call(check = true): Unit
      f(tmp)
    finally
      try os.remove.all(tmp)
      catch case NonFatal(_) => ()

  /** Helper to run a git command in the repo, returning stdout text. */
  def git(repo: os.Path, args: String*): String =
    val quoted = ("git" +: args.toSeq)
      .map { a =>
        val s = a.replace("\\", "\\\\").replace("\"", "\\\"")
        s"\"$s\""
      }
      .mkString(" ")
    os.proc("bash", "-lc", quoted).call(cwd = repo, check = true).out.text()

  /** Checkout a branch or tag (porcelain is acceptable in tests). */
  def checkout(repo: os.Path, ref: String): Unit =
    // Quiet checkout; script disables detached head advice so no extra notes are printed.
    os.proc("git", "checkout", "-q", ref).call(cwd = repo, check = true): Unit
end TestRepoSupport
