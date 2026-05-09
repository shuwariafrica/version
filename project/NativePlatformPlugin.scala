import sbt.*
import sbt.Keys.*

import scala.scalanative.build.{LTO, Mode}
import scala.scalanative.sbtplugin.ScalaNativePlugin
import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport.nativeConfig

object NativePlatformPlugin extends AutoPlugin:

  override def trigger: PluginTrigger = noTrigger
  override def requires: Plugins = ScalaNativePlugin

  enum Os:
    case Linux, MacOs, Windows

  enum Arch:
    case X86_64, Aarch64, Other

  enum Libc:
    case Glibc, Musl

  private val rawArch: String = sys.props.getOrElse("os.arch", "").toLowerCase

  val os: Os =
    val n = sys.props.getOrElse("os.name", "").toLowerCase
    if n.contains("win") then Os.Windows
    else if n.contains("mac") || n.contains("darwin") then Os.MacOs
    else Os.Linux

  val arch: Arch = rawArch match
    case a if a == "x86_64" || a == "amd64"  => Arch.X86_64
    case a if a == "aarch64" || a == "arm64" => Arch.Aarch64
    case _                                   => Arch.Other

  // Defaults to Glibc off-Linux so callers composing path strings stay total.
  val libc: Libc = os match
    case Os.Linux =>
      val a = arch match
        case Arch.X86_64  => "x86_64"
        case Arch.Aarch64 => "aarch64"
        case Arch.Other   => rawArch
      if new java.io.File(s"/lib/ld-musl-$a.so.1").exists() then Libc.Musl else Libc.Glibc
    case _ => Libc.Glibc

  // libc segment is Linux-only because musl and glibc binaries are
  // ABI-incompatible; the tag must distinguish them.
  val hostTag: String =
    val osPart = os match
      case Os.Linux =>
        val libcPart = libc match
          case Libc.Glibc => "glibc"
          case Libc.Musl  => "musl"
        s"linux-$libcPart"
      case Os.MacOs   => "macos"
      case Os.Windows => "windows"
    val archPart = arch match
      case Arch.X86_64  => "x86_64"
      case Arch.Aarch64 => "aarch64"
      case Arch.Other   => rawArch
    s"$osPart-$archPart"

  val isCi: Boolean = sys.props.get("sbt.ci").contains("true")

  val isStaticLink: Boolean = sys.props.get("release.binary.static").contains("true")

  // LTO.thin on macOS drops libunwind __unw_* symbols the system linker
  // still references, breaking the link.
  val lto: LTO = if os == Os.MacOs then LTO.full else LTO.thin

  // Linux only: macOS clang rejects _FORTIFY_SOURCE without a sysroot, and
  // clang-cl applies /GS automatically under /MT.
  val cHardening: Seq[String] =
    if os == Os.Linux then Seq("-fstack-protector-strong", "-D_FORTIFY_SOURCE=2") else Nil

  // releaseFast + thin LTO is required: Mode.debug overflows musl's 8MB
  // main-thread stack along the resolver call chain.
  val nativeSettings: List[Setting[?]] = List(
    Test / parallelExecution := true,
    Compile / unmanagedResourceDirectories +=
      (Compile / sourceDirectory).value / "resources-native",
    Test / unmanagedResourceDirectories +=
      (Test / sourceDirectory).value / "resources-native",
    libraryDependencySchemes += "org.scala-native" % "test-interface_native0.5_3" % "always",
    nativeConfig := Def.uncached(
      nativeConfig.value
        .withMultithreading(false)
        .withMode(Mode.releaseFast)
        .withLTO(lto)
    )
  )

  val applicationSettings: List[Setting[?]] = nativeSettings ++ List(
    nativeConfig := Def.uncached {
      val prev = nativeConfig.value
      val mode = if isCi then Mode.releaseFull else Mode.releaseFast
      val base = prev
        .withBaseName("version")
        .withMode(mode)
        .withLTO(lto)
        .withCompileOptions(prev.compileOptions ++ cHardening)
      if isStaticLink then base.withLinkingOptions(base.linkingOptions :+ "-static")
      else base
    }
  )

end NativePlatformPlugin
