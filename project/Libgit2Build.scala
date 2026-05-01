import sbt.{Def, *}
import sbt.Keys.*
import xsbti.HashedVirtualFileRef

import scala.scalanative.build.NativeConfig
import scala.scalanative.sbtplugin.ScalaNativePlugin
import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport.nativeConfig
import scala.sys.process.*

object Libgit2Build extends AutoPlugin:

  val buildLibgit2 = taskKey[File]("Build vendored libgit2; returns the build output directory")
  val libgit2StaticLib = taskKey[File]("Absolute path to the built libgit2 static archive for the current host")

  override def trigger = noTrigger

  override def requires: Plugins = ScalaNativePlugin

  override def buildSettings: Seq[Setting[?]] = Seq(
    ThisBuild / buildLibgit2 := Def.uncached(cmakeTask.value),
    ThisBuild / libgit2StaticLib := Def.uncached(resolveStaticLib.value)
  )

  override def projectSettings: Seq[Setting[?]] = Seq(
    Compile / compile := Def.uncached((Compile / compile).dependsOn(ThisBuild / buildLibgit2).value),
    Compile / packageBin / mappings ++= libgitMappings.value,
    nativeConfig := Def.uncached(linkingSetting.value)
  )

  private def linkingSetting: Def.Initialize[Task[NativeConfig]] = Def.task[NativeConfig] {
    val config = nativeConfig.value
    val staticLib = (ThisBuild / libgit2StaticLib).value
    val libgit2Vendor = (ThisBuild / baseDirectory).value / "vendor" / "libgit2"
    val platformFlags = hostOs match
      case Os.Linux   => Seq("-lpthread", "-ldl", "-lm")
      case Os.MacOs   => Seq("-framework", "Security", "-framework", "CoreFoundation")
      case Os.Windows => Seq("-lws2_32", "-lcrypt32", "-lrpcrt4", "-lole32", "-lsecur32", "-lbcrypt")
    val includeFlag = s"-I${(libgit2Vendor / "include").getAbsolutePath}"
    config
      .withLinkingOptions(config.linkingOptions ++ (staticLib.getAbsolutePath +: platformFlags))
      .withCompileOptions(config.compileOptions :+ includeFlag)
  }

  private def cmakeTask: Def.Initialize[Task[File]] = Def.task[File] {
    val log = streams.value.log
    val root = (ThisBuild / baseDirectory).value
    val src = root / "vendor" / "libgit2"
    val build = buildDirFor(root)

    if !(src / "CMakeLists.txt").exists then
      sys.error("vendor/libgit2 submodule not initialised. Run: git submodule update --init --recursive")

    val stamp = build / ".built-stamp"
    val srcHash = (src / "include").allPaths.get().map(_.lastModified).max
    if !stamp.exists || stamp.lastModified < srcHash then
      log.info(s"Building vendored libgit2 (${hostTag}) ...")
      IO.createDirectory(build)
      val configureCmd = Seq("cmake", "-S", src.getAbsolutePath, "-B", build.getAbsolutePath) ++ cmakeFlags
      val rc = Process(configureCmd).!(log)
      if rc != 0 then sys.error(s"cmake configure failed: $rc")

      val buildRc = Process(
        Seq("cmake", "--build", build.getAbsolutePath, "--config", "Release", "--parallel")
      ).!(log)
      if buildRc != 0 then sys.error(s"cmake build failed: $buildRc")

      IO.touch(stamp)
    else log.debug(s"vendored libgit2 (${hostTag}) is up-to-date")
    end if
    build
  }

  private def resolveStaticLib: Def.Initialize[Task[File]] = Def.task[File] {
    val build = (ThisBuild / buildLibgit2).value
    val candidates =
      build.allPaths
        .get()
        .toSeq
        .filter(_.isFile)
        .filter(f => libNameMatches(f.getName))
    candidates.headOption.getOrElse(
      sys.error(s"Expected a libgit2 static archive (libgit2.a or git2.lib) under ${build.getAbsolutePath}; none found. " +
        s"Contents: ${build.allPaths.get().map(_.getName).mkString(", ")}"))
  }

  private def cmakeFlags: Seq[String] = Seq(
    "-DBUILD_SHARED_LIBS=OFF",
    "-DBUILD_TESTS=OFF",
    "-DBUILD_CLI=OFF",
    "-DUSE_HTTPS=OFF",
    "-DUSE_SSH=OFF",
    "-DUSE_NTLMCLIENT=OFF",
    "-DUSE_GSSAPI=OFF",
    "-DUSE_ICONV=OFF",
    "-DREGEX_BACKEND=builtin",
    "-DUSE_BUNDLED_ZLIB=ON",
    "-DCMAKE_BUILD_TYPE=Release",
    "-DCMAKE_POSITION_INDEPENDENT_CODE=ON",
    // scala-native's clang/lld toolchain links libucrt/libvcruntime (static CRT) by default
    // on Windows. libgit2 must match (/MT) or the linker reports LNK2005 multiply-defined
    // symbols between libucrt.lib and ucrt.lib.
    "-DSTATIC_CRT=ON"
  )

  private def libNameMatches(fname: String): Boolean =
    val lower = fname.toLowerCase
    lower == "libgit2.a" || lower == "git2.lib"

  private def buildDirFor(root: File): File =
    root / "vendor" / "libgit2" / "build" / hostTag

  private def hostTag: String =
    val os = hostOs match
      case Os.Linux   => "linux"
      case Os.MacOs   => "macos"
      case Os.Windows => "windows"
    val arch = sys.props.getOrElse("os.arch", "unknown").toLowerCase.replace("_", "-") match
      case a if a == "x86_64" || a == "amd64"  => "x86_64"
      case a if a == "aarch64" || a == "arm64" => "aarch64"
      case a                                   => a
    s"$os-$arch"

  private enum Os:
    case Linux, MacOs, Windows

  private def hostOs: Os =
    val n = sys.props.getOrElse("os.name", "").toLowerCase
    if n.contains("win") then Os.Windows
    else if n.contains("mac") || n.contains("darwin") then Os.MacOs
    else Os.Linux

  private def libgitSource(base: File) = base / "vendor" / "libgit2"

  def libgitMappings: Def.Initialize[Task[Seq[(HashedVirtualFileRef, String)]]] =
    Def.task {
      val converter = fileConverter.value
      val source = libgitSource((ThisBuild / baseDirectory).value)
      Seq(
        source / "COPYING" -> "META-INF/licenses/libgit2-COPYING",
        source / "AUTHORS" -> "META-INF/licenses/libgit2-AUTHORS",
        source / "deps" / "zlib" / "LICENSE" -> "META-INF/licenses/zlib-LICENSE"
      ).collect {
        case (f, p) if f.exists => converter.toVirtualFile(f.toPath) -> p
      }
    }
end Libgit2Build
