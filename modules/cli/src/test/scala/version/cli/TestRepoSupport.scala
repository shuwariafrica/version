package version.cli

import scala.util.control.NonFatal

trait TestRepoSupport:
  def withFreshRepo[A](name: String)(f: os.Path => A): A =
    val tmp = os.temp.dir(prefix = s"version-cli-$name-")
    val script = Option(System.getenv("CREATE_TEST_REPO")).map(os.Path(_)).getOrElse(os.pwd / "scripts" / "create-test-repo.sh")
    try
      os.proc("bash", script.toString, tmp.toString).call(check = true): Unit
      f(tmp)
    finally
      try os.remove.all(tmp)
      catch case NonFatal(_) => ()
