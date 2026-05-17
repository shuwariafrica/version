/****************************************************************************
 * Copyright 2023-2026 Shuwari Africa Ltd.                                  *
 *                                                                          *
 * Licensed under the Apache License, Version 2.0 (the "License");          *
 * you may not use this file except in compliance with the License.         *
 * You may obtain a copy of the License at                                  *
 *                                                                          *
 *     http://www.apache.org/licenses/LICENSE-2.0                           *
 *                                                                          *
 * Unless required by applicable law or agreed to in writing, software      *
 * distributed under the License is distributed on an "AS IS" BASIS,        *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. *
 * See the License for the specific language governing permissions and      *
 * limitations under the License.                                           *
 ****************************************************************************/
package version.cli

import munit.FunSuite

import java.nio.file.Files
import java.nio.file.Path

import version.resolution.ResolutionConfig
import version.resolution.VersionCliCore
import version.resolution.openRepository
import version.semver.*

final class CLISuite extends FunSuite with TestRepoSupport:

  override val munitTimeout: scala.concurrent.duration.Duration =
    scala.concurrent.duration.Duration(120, "s")

  private def normalize(o: CliOptions): CliOptions = o.command match
    case rc: ResolveConfig =>
      val style = if rc.consoleStyleExplicit then rc.consoleStyle else if o.ci then ConsoleStyle.Compact else rc.consoleStyle
      val sinks1 = if rc.sinks.isEmpty then List(OutputSink(SinkKind.Console, None)) else rc.sinks
      val sinks2 = sinks1.foldLeft(List.empty[OutputSink]) { (acc, s) =>
        if s.destination.isEmpty && acc.exists(o => o.kind == s.kind && o.destination.isEmpty) then acc else acc :+ s
      }
      o.copy(command = rc.copy(sinks = sinks2, consoleStyle = style))
    case _ => o

  private def parse(args: Seq[String]): Option[CliOptions] =
    scopt.OParser.parse(CliOptions.parser, args, CliOptions.default).map(normalize)

  private def asResolve(o: CliOptions): ResolveConfig = o.command match
    case r: ResolveConfig => r
    case other            => fail(s"Expected ResolveConfig got $other")

  test("Default parse: console sink pretty style"):
    val opts = parse(Nil).getOrElse(fail("parse failed"))
    val rc = asResolve(opts)
    assertEquals(rc.sinks, List(OutputSink(SinkKind.Console, None)))
    assertEquals(rc.consoleStyle, ConsoleStyle.Pretty)

  test("CI default switches to compact style"):
    val opts = parse(Seq("--ci")).getOrElse(fail("parse failed"))
    val rc = asResolve(opts)
    assertEquals(rc.consoleStyle, ConsoleStyle.Compact)

  test("Explicit console-style overrides CI override"):
    val opts = parse(Seq("--ci", "--console-style", "pretty")).getOrElse(fail("parse failed"))
    val rc = asResolve(opts)
    assertEquals(rc.consoleStyle, ConsoleStyle.Pretty)

  test("Multiple emit sinks with file targets"):
    val tmp = Files.createTempDirectory("version-cli-emits-")
    val jsonPath = tmp.resolve("ver.json")
    val opts = parse(
      Seq(
        "--emit",
        "console",
        "--emit",
        "raw",
        "--emit",
        s"json=$jsonPath"
      )).getOrElse(fail("parse failed"))
    val rc = asResolve(opts)
    assert(rc.sinks.exists(_.kind == SinkKind.Console))
    assert(rc.sinks.exists(_.kind == SinkKind.Raw))
    assert(rc.sinks.contains(OutputSink(SinkKind.Json, Some(jsonPath))))

  test("Invalid sink spec fails parse"):
    val parsed = parse(Seq("--emit", "bogus"))
    assert(parsed.isEmpty)

  test("Invalid console style fails parse"):
    val parsed = parse(Seq("--console-style", "fancy"))
    assert(parsed.isEmpty)

  test("Empty emit path fails parse"):
    val parsed = parse(Seq("--emit", "json="))
    assert(parsed.isEmpty)

  test("sha-length lower bound accepted (7)"):
    val parsed = parse(Seq("--sha-length", "7"))
    assert(parsed.nonEmpty)
    assertEquals(parsed.get.shaLength, 7)

  test("sha-length upper bound accepted (64)"):
    val parsed = parse(Seq("--sha-length", "64"))
    assert(parsed.nonEmpty)
    assertEquals(parsed.get.shaLength, 64)

  test("sha-length default (40) accepted"):
    val parsed = parse(Seq("--sha-length", "40"))
    assert(parsed.nonEmpty)
    assertEquals(parsed.get.shaLength, 40)

  test("sha-length below minimum fails parse"):
    val parsed = parse(Seq("--sha-length", "6"))
    assert(parsed.isEmpty)

  test("sha-length above maximum fails parse"):
    val parsed = parse(Seq("--sha-length", "65"))
    assert(parsed.isEmpty)

  test("Explicit console-style marks explicit flag"):
    val parsed = parse(Seq("--console-style", "compact")).getOrElse(fail("parse failed"))
    val rc = asResolve(parsed)
    assert(rc.consoleStyleExplicit)
    assertEquals(rc.consoleStyle, ConsoleStyle.Compact)

  test("Only json emit does not inject console sink"):
    val tmp = Files.createTempDirectory("version-cli-json-only-")
    val jsonPath = tmp.resolve("ver.json")
    val parsed = parse(Seq("--emit", s"json=$jsonPath")).getOrElse(fail("parse failed"))
    val rc = asResolve(parsed)
    assertEquals(rc.sinks.map(_.kind), List(SinkKind.Json))

  test("Duplicate raw sinks with different file destinations are preserved"):
    val tmp = Files.createTempDirectory("version-cli-raw-dupe-")
    val p1 = tmp.resolve("a.txt")
    val p2 = tmp.resolve("b.txt")
    val parsed = parse(Seq("--emit", s"raw=$p1", "--emit", s"raw=$p2")).getOrElse(fail("parse failed"))
    val rc = asResolve(parsed)
    val raws = rc.sinks.filter(_.kind == SinkKind.Raw)
    assertEquals(raws.size, 2)
    assert(raws.exists(_.destination.contains(p1)))
    assert(raws.exists(_.destination.contains(p2)))

  test("Repository option sets repository path"):
    val tmp = Files.createTempDirectory("version-cli-repo-")
    val parsed = parse(Seq("--repository", tmp.toString)).getOrElse(fail("parse failed"))
    assertEquals(parsed.repository, tmp)

  test("Parse release command placeholder with flags"):
    val parsed = scopt.OParser
      .parse(
        CliOptions.parser,
        Seq("release", "--tag-prefix", "v", "--push", "--annotate"),
        CliOptions.default
      )
      .getOrElse(fail("parse failed"))
    parsed.command match
      case r: ReleaseConfig =>
        assertEquals(r.tagPrefix, Some("v"))
        assert(r.push)
        assert(r.annotate)
      case other => fail(s"Expected ReleaseConfig got $other")

  test("Dedupe identical console sinks without destinations"):
    val opts = parse(Seq("--emit", "console", "--emit", "console")).getOrElse(fail("parse failed"))
    val rc = asResolve(opts)
    val consoles = rc.sinks.filter(_.kind == SinkKind.Console)
    assertEquals(consoles.size, 1)

  test("End-to-end: resolve version and emit console/raw/json"):
    withFreshRepo("cli-e2e"): repo =>
      val tmpOut = Files.createTempDirectory("version-cli-out-")
      val jsonPath = tmpOut.resolve("ver.json")
      val args = Seq(
        "--repository",
        repo.toString,
        "--emit",
        "console",
        "--emit",
        "raw",
        "--emit",
        s"json=$jsonPath"
      )
      val opts = parse(args).getOrElse(fail("parse failed"))
      val rc = asResolve(opts)
      val cfg = ResolutionConfig
        .default[SemVer](opts.repository.toString)
        .copy(
          basisCommit = opts.basisCommit,
          prNumber = opts.prNumber,
          branchOverride = opts.branchOverride,
          verbose = opts.verbose
        )
      val v = VersionCliCore.resolve(cfg, openRepository).toOption.getOrElse(fail("resolution failed"))
      val rendered: Map[OutputSink, String] = rc.sinks.map { s =>
        val content = s.kind match
          case SinkKind.Console => if rc.consoleStyle == ConsoleStyle.Pretty then consolePretty(v) else v.show
          case SinkKind.Raw     => v.show
          case SinkKind.Json    => SemVerJson.toJson(v)
        s -> content
      }.toMap
      rc.sinks.foreach { s =>
        s.destination.foreach { p =>
          java.nio.file.Files.createDirectories(p.getParent)
          java.nio.file.Files.writeString(p, rendered(s))
        }
      }
      assert(Files.isRegularFile(jsonPath))
      val rawStrs = rc.sinks.filter(_.kind == SinkKind.Raw).map(rendered)
      assert(rawStrs.forall(_ == v.show))
      val jsonStr = Files.readString(jsonPath)
      assert(jsonStr.contains("\"major\""))

  private def consolePretty(v: SemVer): String =
    val b = new StringBuilder
    b.append("Version:\n")
    b.append(s"  version   : ${v.show}\n")
    b.append(s"  full      : ${SemVer.Formatter.Full.format(v)}\n")
    val pre = v.preRelease.map(_.show).getOrElse("none")
    val meta = v.metadata.map(_.show).getOrElse("none")
    b.append(s"  preRelease: ${pre}\n")
    b.append(s"  metadata  : ${meta}\n")
    b.result()
end CLISuite
