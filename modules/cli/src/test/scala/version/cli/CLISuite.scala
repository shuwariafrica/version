/****************************************************************
 * Copyright © Shuwari Africa Ltd.                              *
 *                                                              *
 * This file is licensed to you under the terms of the Apache   *
 * License Version 2.0 (the "License"); you may not use this    *
 * file except in compliance with the License. You may obtain   *
 * a copy of the License at:                                    *
 *                                                              *
 *     https://www.apache.org/licenses/LICENSE-2.0              *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, *
 * either express or implied. See the License for the specific  *
 * language governing permissions and limitations under the     *
 * License.                                                     *
 ****************************************************************/
package version.cli

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import munit.FunSuite
import org.virtuslab.yaml.*

import version.*
import version.cli.core.VersionCliCore
import version.cli.core.domain.CliConfig
import version.codecs.jsoniter.given
import version.codecs.yaml.given

final class CLISuite extends FunSuite with TestRepoSupport:

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

  test("Default parse: console sink pretty style") {
    val opts = parse(Nil).getOrElse(fail("parse failed"))
    val rc = asResolve(opts)
    assertEquals(rc.sinks, List(OutputSink(SinkKind.Console, None)))
    assertEquals(rc.consoleStyle, ConsoleStyle.Pretty)
  }

  test("CI default switches to compact style") {
    val opts = parse(Seq("--ci")).getOrElse(fail("parse failed"))
    val rc = asResolve(opts)
    assertEquals(rc.consoleStyle, ConsoleStyle.Compact)
  }

  test("Explicit console-style overrides CI override") {
    val opts = parse(Seq("--ci", "--console-style", "pretty")).getOrElse(fail("parse failed"))
    val rc = asResolve(opts)
    assertEquals(rc.consoleStyle, ConsoleStyle.Pretty)
  }

  test("Multiple emit sinks with file targets") {
    val tmp = os.temp.dir(prefix = "version-cli-emits-")
    val jsonPath = tmp / "ver.json"
    val yamlPath = tmp / "ver.yaml"
    val opts = parse(
      Seq(
        "--emit",
        "console",
        "--emit",
        "raw",
        "--emit",
        s"json=$jsonPath",
        "--emit",
        s"yaml=$yamlPath"
      )).getOrElse(fail("parse failed"))
    val rc = asResolve(opts)
    assert(rc.sinks.exists(_.kind == SinkKind.Console))
    assert(rc.sinks.exists(_.kind == SinkKind.Raw))
    assert(rc.sinks.contains(OutputSink(SinkKind.Json, Some(jsonPath))))
    assert(rc.sinks.contains(OutputSink(SinkKind.Yaml, Some(yamlPath))))
  }

  test("Invalid sink spec fails parse") {
    val parsed = parse(Seq("--emit", "bogus"))
    assert(parsed.isEmpty)
  }

  test("Invalid console style fails parse") {
    val parsed = parse(Seq("--console-style", "fancy"))
    assert(parsed.isEmpty)
  }

  test("Empty emit path fails parse") {
    val parsed = parse(Seq("--emit", "json="))
    assert(parsed.isEmpty)
  }

  test("sha-length lower bound accepted (7)") {
    val parsed = parse(Seq("--sha-length", "7"))
    assert(parsed.nonEmpty)
    assertEquals(parsed.get.shaLength, 7)
  }

  test("sha-length upper bound accepted (40)") {
    val parsed = parse(Seq("--sha-length", "40"))
    assert(parsed.nonEmpty)
    assertEquals(parsed.get.shaLength, 40)
  }

  test("sha-length below minimum fails parse") {
    val parsed = parse(Seq("--sha-length", "6"))
    assert(parsed.isEmpty)
  }

  test("sha-length above maximum fails parse") {
    val parsed = parse(Seq("--sha-length", "41"))
    assert(parsed.isEmpty)
  }

  test("Explicit console-style marks explicit flag") {
    val parsed = parse(Seq("--console-style", "compact")).getOrElse(fail("parse failed"))
    val rc = asResolve(parsed)
    assert(rc.consoleStyleExplicit)
    assertEquals(rc.consoleStyle, ConsoleStyle.Compact)
  }

  test("Only json emit does not inject console sink") {
    val tmp = os.temp.dir(prefix = "version-cli-json-only-")
    val jsonPath = tmp / "ver.json"
    val parsed = parse(Seq("--emit", s"json=$jsonPath")).getOrElse(fail("parse failed"))
    val rc = asResolve(parsed)
    assertEquals(rc.sinks.map(_.kind), List(SinkKind.Json))
  }

  test("Duplicate raw sinks with different file destinations are preserved") {
    val tmp = os.temp.dir(prefix = "version-cli-raw-dupe-")
    val p1 = tmp / "a.txt"
    val p2 = tmp / "b.txt"
    val parsed = parse(Seq("--emit", s"raw=$p1", "--emit", s"raw=$p2")).getOrElse(fail("parse failed"))
    val rc = asResolve(parsed)
    val raws = rc.sinks.filter(_.kind == SinkKind.Raw)
    assertEquals(raws.size, 2)
    assert(raws.exists(_.destination.contains(p1)))
    assert(raws.exists(_.destination.contains(p2)))
  }

  test("Repository option sets repository path") {
    val tmp = os.temp.dir(prefix = "version-cli-repo-")
    val parsed = parse(Seq("--repository", tmp.toString)).getOrElse(fail("parse failed"))
    assertEquals(parsed.repository, tmp)
  }

  test("Parse release command placeholder with flags") {
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
  }

  test("Dedupe identical console sinks without destinations") {
    val opts = parse(Seq("--emit", "console", "--emit", "console")).getOrElse(fail("parse failed"))
    val rc = asResolve(opts)
    val consoles = rc.sinks.filter(_.kind == SinkKind.Console)
    assertEquals(consoles.size, 1)
  }

  test("End-to-end: resolve version and emit console/raw/json/yaml") {
    withFreshRepo("cli-e2e") { repo =>
      val tmpOut = os.temp.dir(prefix = "version-cli-out-")
      val jsonPath = tmpOut / "ver.json"
      val yamlPath = tmpOut / "ver.yaml"
      val args = Seq(
        "--repository",
        repo.toString,
        "--emit",
        "console",
        "--emit",
        "raw",
        "--emit",
        s"json=$jsonPath",
        "--emit",
        s"yaml=$yamlPath"
      )
      val opts = parse(args).getOrElse(fail("parse failed"))
      val rc = asResolve(opts)
      val cfg = CliConfig(
        repo = opts.repository,
        basisCommit = opts.basisCommit,
        prNumber = opts.prNumber,
        branchOverride = opts.branchOverride,
        shaLength = opts.shaLength,
        verbose = opts.verbose
      )
      val v = VersionCliCore.resolve(cfg).toOption.getOrElse(fail("resolution failed"))
      val rendered: Map[OutputSink, String] = rc.sinks.map { s =>
        val content = s.kind match
          case SinkKind.Console => if rc.consoleStyle == ConsoleStyle.Pretty then consolePretty(v) else v.toString
          case SinkKind.Raw     => v.toString
          case SinkKind.Json    => writeToString(v)
          case SinkKind.Yaml    => v.asYaml
        s -> content
      }.toMap
      rc.sinks.foreach { s =>
        s.destination.foreach { p =>
          os.makeDir.all(p / os.up)
          os.write.over(p, rendered(s))
        }
      }
      assert(os.isFile(jsonPath))
      assert(os.isFile(yamlPath))
      val rawStrs = rc.sinks.filter(_.kind == SinkKind.Raw).map(rendered)
      assert(rawStrs.forall(_ == v.toString))
      val jsonStr = os.read(jsonPath)
      assert(jsonStr.contains("\"major\""))
      val yamlStr = os.read(yamlPath)
      assert(yamlStr.toLowerCase.contains("major:"))
    }
  }

  private def consolePretty(v: Version): String =
    val b = new StringBuilder
    b.append("Version:\n")
    b.append(s"  full      : ${v.toString}\n")
    b.append(s"  core      : ${v.major.value}.${v.minor.value}.${v.patch.value}\n")
    val pre = v.preRelease.map(_.toString).getOrElse("none")
    val meta = v.buildMetadata.map(_.show).getOrElse("none")
    b.append(s"  preRelease: ${pre}\n")
    b.append(s"  metadata  : ${meta}\n")
    b.result()
end CLISuite
