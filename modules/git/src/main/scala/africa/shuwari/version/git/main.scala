package africa.shuwari.version.git

import java.nio.file.Path

import africa.shuwari.version.git.executor.readVersion

@main
def main(): Unit =
  println("Hello, world!")
  val workdir = Path.of("").nn.toAbsolutePath.nn
  println(s"Working directory: $workdir")
  println(s"Git version: ${readVersion(workdir, None)}")
