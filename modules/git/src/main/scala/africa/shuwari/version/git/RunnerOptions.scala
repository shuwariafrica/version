package africa.shuwari.version.git

import africa.shuwari.version.BuildInformation

final case class RunnerOptions(debug: Boolean, verbose: Boolean, long: Boolean)

object RunnerOptions:
  private inline def defaultDebug = false
  private inline def defaultVerbose = false
  private inline def defaultLong = false

  def apply(): RunnerOptions = RunnerOptions(defaultDebug, defaultVerbose, defaultLong)

  def parse(args: Seq[String]): Option[RunnerOptions] =
    val p = scopt.OParser.builder[RunnerOptions]
    val name = p.programName(BuildInformation.name)
    val version = p.version(BuildInformation.version)
    val debug = p
      .opt[Boolean]('d', "debug")
      .action((x, c) => c.copy(debug = x))
      .text(s"Enable debugging output. '$defaultDebug' by default.")
    val verbose = p
      .opt[Boolean]('v', "verbose")
      .action((x, c) => c.copy(verbose = x))
      .text(s"Enable verbose output. '$defaultVerbose' by default.")
    val long = p
      .opt[Boolean]('l', "long")
      .action((x, c) => c.copy(long = x))
      .text(s"Enable extended output. '$defaultLong' by default.")

    scopt.OParser.parse(scopt.OParser.sequence(name, version, debug, verbose, long), args, RunnerOptions())

end RunnerOptions
