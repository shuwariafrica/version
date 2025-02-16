//package africa.shuwari.version

//package africa.shuwari.version
//
//import scala.sys.process.ProcessLogger
//
//sealed trait VersionRunnerError extends RuntimeException:
//  def message: String
//
//object VersionRunnerError:
//  case object InvalidRunnerOptions extends VersionRunnerError:
//    override def message: String = "Invalid runner options."
//  case object GitExecutableNotFound extends VersionRunnerError:
//    override def message: String = "Git executable not found on system PATH."
//  final case class RuntimeError(message: String) extends VersionRunnerError
//
//object GitRunner:
//
//  // Case class to hold process execution output
//  final case class Output(stdout: String, stderr: String)
//
//  private type Result[A] = Either[VersionRunnerError, A]
//
//
