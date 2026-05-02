import sbt.{Def, *}
import sbt.Keys.*
import sbt.io.IO

import scala.scalanative.sbtplugin.ScalaNativePlugin
import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport.nativeLink
import scala.sys.process.Process

/** Stages the Scala Native CLI binary plus shell completions and licence files into a platform-appropriate release
  * archive (`tar.gz` on Unix-style targets, `zip` on Windows) and - when running under GitHub Actions - writes the
  * produced archive path to `$GITHUB_OUTPUT` for downstream upload steps to consume.
  *
  * Usage in CI:
  * {{{
  *   sbt -Dactions.publish.target=<id> \
  *       -Drelease.binary=true [-Drelease.binary.static=true] \
  *       version-cliNative/releaseArchive
  * }}}
  *
  * Single source of truth for "what gets shipped, in what shape" lives here in Scala so the release pipeline does not
  * duplicate the staging logic across YAML branches.
  */
object ActionsPublish extends AutoPlugin:

  val releaseArchiveTarget = settingKey[String](
    "Platform identifier embedded in the release archive name (e.g. linux-x86_64, " +
      "macos-aarch64, windows-x86_64). Read from `-Dactions.publish.target=<id>`."
  )

  val releaseArchive = taskKey[File](
    "Stage the native binary, shell completions and licence files; produce a tar.gz " +
      "or zip; emit `archive=<path>` and `binary=<path>` to $GITHUB_OUTPUT when set."
  )

  override def trigger = noTrigger
  override def requires: Plugins = ScalaNativePlugin

  override def projectSettings: Seq[Setting[?]] = Seq(
    // Empty default so non-release reloads don't trip on a missing system property.
    // `releaseArchive` validates non-emptiness when it actually runs.
    releaseArchiveTarget := sys.props.getOrElse("actions.publish.target", ""),
    releaseArchive := Def.uncached(stageAndArchive.value)
  )

  private def stageAndArchive: Def.Initialize[Task[File]] = Def.task[File] {
    val log = streams.value.log
    val converter = fileConverter.value
    // ScalaNativePluginInternal scopes `nativeLink` inside `inConfig(Compile)(...)`,
    // so the un-scoped key is undefined; reach in explicitly.
    val binaryRef = (Compile / nativeLink).value
    val binary = converter.toPath(binaryRef).toFile
    val rootBase = (ThisBuild / baseDirectory).value
    val outDir = target.value
    val targetId = releaseArchiveTarget.value
    if targetId.isEmpty then
      sys.error(
        "ActionsPublish: required system property `-Dactions.publish.target=<id>` is not set"
      )
    val ver = version.value

    val isWindows = targetId.startsWith("windows-")
    val archiveExt = if isWindows then "zip" else "tar.gz"
    val archiveBase = s"version-$ver-$targetId"
    val archive = outDir / s"$archiveBase.$archiveExt"
    val staging = outDir / "release-staging" / archiveBase

    log.info(s"Staging release archive at ${staging.getAbsolutePath}")
    IO.delete(staging)
    IO.delete(archive)
    IO.createDirectory(staging / "completions")

    val binaryDest = if isWindows then staging / "version.exe" else staging / "version"
    IO.copyFile(binary, binaryDest)
    if !isWindows then binaryDest.setExecutable(true)

    val licenseExt = if isWindows then ".txt" else ""
    IO.copyFile(rootBase / "LICENSE", staging / s"LICENSE$licenseExt")
    IO.copyFile(rootBase / "NOTICE", staging / s"NOTICE$licenseExt")

    val completionsSrc = rootBase / "packaging" / "completions"
    val completionFiles =
      if isWindows then Seq("version.ps1")
      else Seq("version.bash", "_version", "version.fish")
    completionFiles.foreach { name =>
      IO.copyFile(completionsSrc / name, staging / "completions" / name)
    }

    if isWindows then writeZip(staging, archive)
    else writeTarGz(staging, archive, log)

    sys.env.get("GITHUB_OUTPUT").foreach { ghOutput =>
      val outputs = Seq(
        s"archive=${archive.getAbsolutePath}",
        s"binary=${binary.getAbsolutePath}",
        ""
      ).mkString("\n")
      IO.append(new File(ghOutput), outputs)
    }

    log.info(s"Release archive: ${archive.getAbsolutePath}")
    archive
  }

  /** Pure-JVM zip via sbt.io.IO.zip. Entries are pathed relative to the staging directory's parent so the archive
    * contains `<archiveBase>/...`, matching the tar.gz layout convention.
    */
  private def writeZip(staging: File, archive: File): Unit =
    val parent = staging.getParentFile.toPath
    val entries =
      (PathFinder(staging) ** AllPassFilter)
        .get()
        .filter(_.isFile)
        .map { f =>
          val rel = parent.relativize(f.toPath).toString.replace(java.io.File.separatorChar, '/')
          f -> rel
        }
    IO.zip(entries, archive, None)

  /** Tarball + gzip via the host `tar`. Linux/macOS runners ship tar natively; Windows runners get it via Git Bash.
    * Single-call invocation for portability.
    */
  private def writeTarGz(staging: File, archive: File, log: Logger): Unit =
    val parent = staging.getParentFile
    val baseName = staging.getName
    val cmd = Seq("tar", "-czf", archive.getAbsolutePath, "-C", parent.getAbsolutePath, baseName)
    val rc = Process(cmd).!(log)
    if rc != 0 then sys.error(s"tar exited with rc=$rc; command: ${cmd.mkString(" ")}")

end ActionsPublish
