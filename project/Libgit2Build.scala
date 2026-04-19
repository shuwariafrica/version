import sbt.{Def, *}
import sbt.Keys.*
import xsbti.HashedVirtualFileRef

import scala.scalanative.build.NativeConfig
import scala.scalanative.sbtplugin.ScalaNativePlugin
import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport.nativeConfig
import scala.sys.process.*

object Libgit2Build extends AutoPlugin:

  val buildLibgit2 = taskKey[File]("Build vendored libgit2; returns the build output directory")

  override def trigger = noTrigger

  override def requires: Plugins = ScalaNativePlugin

  override def projectSettings: Seq[Setting[?]] = Seq(
    buildLibgit2 := Def.uncached(cmakeTask.value),
    (Compile / compile) := Def.uncached((Compile / compile).dependsOn(buildLibgit2).value),
    Compile / packageBin / mappings ++= libgitMappings.value,
    nativeConfig := Def.uncached(linkingSetting.value)
  )

  private def linkingSetting: Def.Initialize[Task[NativeConfig]] = Def.task[NativeConfig] {
    val config = nativeConfig.value
    val root = (ThisBuild / baseDirectory).value
    val libgit2Vendor = root / "vendor" / "libgit2"
    val libgit2Build = libgit2Vendor / "build"
    val base = Seq(
      (libgit2Build / "libgit2.a").getAbsolutePath
    )
    val platformFlags = sys.props("os.name").toLowerCase match
      case os if os.contains("lin") => Seq("-lpthread", "-ldl", "-lm")
      case os if os.contains("mac") => Seq("-framework", "Security", "-framework", "CoreFoundation")
      case os if os.contains("win") => Seq("-lws2_32", "-lcrypt32", "-lrpcrt4", "-lole32")
      case _                        => Nil
    val includeFlag = s"-I${(libgit2Vendor / "include").getAbsolutePath}"
    config
      .withLinkingOptions(config.linkingOptions ++ base ++ platformFlags)
      .withCompileOptions(config.compileOptions :+ includeFlag)
  }

  private def cmakeTask: Def.Initialize[Task[File]] = Def.task[File] {
    val log = streams.value.log
    val root = (ThisBuild / baseDirectory).value
    val src = root / "vendor" / "libgit2"
    val build = src / "build"

    if !(src / "CMakeLists.txt").exists then
      sys.error(
        "vendor/libgit2 submodule not initialised. " +
          "Run: git submodule update --init --recursive"
      )

    val stamp = build / ".built-stamp"
    val srcHash = (src / "include").allPaths.get().map(_.lastModified).max
    if !stamp.exists || stamp.lastModified < srcHash then
      log.info("Building vendored libgit2...")
      IO.createDirectory(build)
      val cmakeFlags = Seq(
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
        "-DCMAKE_POSITION_INDEPENDENT_CODE=ON"
      )
      val rc = Process(
        Seq("cmake", "-S", src.getAbsolutePath, "-B", build.getAbsolutePath) ++ cmakeFlags
      ).!(log)
      if rc != 0 then sys.error(s"cmake configure failed: $rc")

      val buildRc = Process(
        Seq("cmake", "--build", build.getAbsolutePath, "--config", "Release", "--parallel")
      ).!(log)
      if buildRc != 0 then sys.error(s"cmake build failed: $buildRc")

      IO.touch(stamp)
    else log.debug("vendored libgit2 is up-to-date")
    end if
    build
  }

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
