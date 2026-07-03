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

import version.testkit.GpgKeyring
import version.testkit.Process

final class CLISuite extends FunSuite with TestRepoSupport:

  override val munitTimeout: scala.concurrent.duration.Duration =
    scala.concurrent.duration.Duration(120, "s")

  private def parse(args: String*): Option[CliOptions] =
    scopt.OParser.parse(CliOptions.parser, args, CliOptions.default)

  private def command(args: String*): CommandConfig =
    parse(args*).getOrElse(fail(s"parse failed: ${args.mkString(" ")}")).command

  test("default command is show-current"):
    command() match
      case ShowConfig(ShowKind.Current, _, _, _) => ()
      case other                                 => fail(s"expected ShowConfig(Current), got $other")

  test("target with no argument is show-target"):
    command("target") match
      case ShowConfig(ShowKind.Target, _, _, _) => ()
      case other                                => fail(s"got $other")

  test("target --set records a target directive"):
    assertEquals(command("target", "--set", "2.0.0"), TargetConfig(Some("2.0.0"), None, noSign = false, dryRun = false))
    assertEquals(command("target", "-s", "2.0.0"), TargetConfig(Some("2.0.0"), None, noSign = false, dryRun = false))

  test("target --increment records an increment directive with flags"):
    assertEquals(
      command("target", "--increment", "minor", "--no-sign", "--dry-run"),
      TargetConfig(None, Some("minor"), noSign = true, dryRun = true)
    )
    assertEquals(command("target", "-i", "major"), TargetConfig(None, Some("major"), noSign = false, dryRun = false))

  test("target rejects both --set and --increment"):
    assert(parse("target", "--set", "2.0.0", "--increment", "minor").isEmpty)

  test("tag with no argument"):
    assertEquals(command("tag"), TagConfig(None, None, noSign = false, dryRun = false))

  test("tag with version, message, no-sign, dry-run"):
    assertEquals(
      command("tag", "1.2.3", "-m", "Cut 1.2.3", "--no-sign", "--dry-run"),
      TagConfig(Some("1.2.3"), Some("Cut 1.2.3"), noSign = true, dryRun = true)
    )

  test("sha-length bounds"):
    assertEquals(parse("--sha-length", "7").get.shaLength, 7)
    assertEquals(parse("--sha-length", "64").get.shaLength, 64)
    assert(parse("--sha-length", "6").isEmpty)
    assert(parse("--sha-length", "65").isEmpty)

  test("emit sinks parse onto the show command"):
    val json = Files.createTempDirectory("cli-emit-").resolve("v.json")
    command("--emit", "raw", "--emit", s"json=$json") match
      case ShowConfig(_, sinks, _, _) =>
        assert(sinks.exists(_.kind == SinkKind.Raw))
        assert(sinks.contains(OutputSink(SinkKind.Json, Some(json))))
      case other => fail(s"got $other")

  test("invalid sink, style, and empty path fail parse"):
    assert(parse("--emit", "bogus").isEmpty)
    assert(parse("--console-style", "fancy").isEmpty)
    assert(parse("--emit", "json=").isEmpty)

  test("a global flag applies after a subcommand (scopt keeps root options matchable)"):
    command("target", "--console-style", "compact") match
      case ShowConfig(ShowKind.Target, _, ConsoleStyle.Compact, true) => ()
      case other                                                      => fail(s"got $other")

  test("target --increment --no-sign creates an empty commit carrying the directive"):
    withFreshRepo("cli-increment"): repo =>
      val before = git(repo, "rev-parse", "HEAD").trim
      assertEquals(CLI.run(Array("target", "--increment", "minor", "--no-sign", "--repository", repo.toString)), 0)
      val after = git(repo, "rev-parse", "HEAD").trim
      assertNotEquals(after, before)
      assert(git(repo, "log", "-1", "--format=%B").contains("version: minor"))
      assert(git(repo, "diff", "--name-only", s"$before..$after").trim.isEmpty, "commit should change no files")

  test("target --set --no-sign records the target directive"):
    withFreshRepo("cli-set"): repo =>
      assertEquals(CLI.run(Array("target", "--set", "3.0.0", "--no-sign", "--repository", repo.toString)), 0)
      assert(git(repo, "log", "-1", "--format=%B").contains("target: 3.0.0"))

  test("target --increment rejects an unknown keyword"):
    withFreshRepo("cli-increment-bad"): repo =>
      assertEquals(CLI.run(Array("target", "--increment", "bogus", "--no-sign", "--repository", repo.toString)), 1)

  test("a mutating command without a signing key and without --no-sign is refused"):
    withFreshRepo("cli-nosign-required"): repo =>
      val before = git(repo, "rev-parse", "HEAD").trim
      assertEquals(CLI.run(Array("target", "--increment", "minor", "--repository", repo.toString)), 1)
      assertEquals(git(repo, "rev-parse", "HEAD").trim, before, "no commit should be created")

  test("--dry-run performs no mutation"):
    withFreshRepo("cli-dryrun"): repo =>
      val before = git(repo, "rev-parse", "HEAD").trim
      assertEquals(CLI.run(Array("target", "--increment", "minor", "--no-sign", "--dry-run", "--repository", repo.toString)), 0)
      assertEquals(git(repo, "rev-parse", "HEAD").trim, before, "dry-run should not commit")

  test("tag --no-sign creates an annotated tag at HEAD"):
    withFreshRepo("cli-tag"): repo =>
      assertEquals(CLI.run(Array("tag", "9.9.9", "--no-sign", "--repository", repo.toString)), 0)
      assert(git(repo, "tag", "-l").linesIterator.contains("9.9.9"), "tag 9.9.9 should exist")
      assertEquals(git(repo, "cat-file", "-t", "9.9.9").trim, "tag", "should be an annotated tag")

  test("show current resolves and exits zero"):
    withFreshRepo("cli-show"): repo =>
      assertEquals(CLI.run(Array("--repository", repo.toString, "--emit", "raw")), 0)

  test("list with no flags is a ListConfig"):
    assertEquals(command("list"), ListConfig(None, finalOnly = false, None, None, details = false))

  test("list flags populate the ListConfig"):
    assertEquals(
      command("list", "--limit", "5", "--final", "--since", "1.0.0", "--until", "2.0.0", "--details"),
      ListConfig(Some(5), finalOnly = true, Some("1.0.0"), Some("2.0.0"), details = true)
    )

  test("list -n is the limit alias"):
    assertEquals(command("list", "-n", "3"), ListConfig(Some(3), finalOnly = false, None, None, details = false))

  test("options are scoped to their command"):
    assert(parse("target", "--increment", "minor", "--limit", "5").isEmpty, "--limit is a list-only option, invalid for target")
    assert(parse("list", "--no-sign").isEmpty, "--no-sign is a mutating-only option, invalid for list")
    assert(parse("list", "--limit", "5").isDefined, "--limit is valid for list")
    assert(parse("target", "--increment", "minor", "--no-sign").isDefined, "--no-sign is valid for target")

  private def capture(args: String*): (Int, String) =
    val out = new java.io.ByteArrayOutputStream()
    val code = Console.withOut(out)(CLI.run(args.toArray))
    (code, out.toString("UTF-8"))

  test("list shows the annotated release history newest first, with the release date"):
    withFreshRepo("cli-list"): repo =>
      val (code, output) = capture("list", "--repository", repo.toString)
      assertEquals(code, 0)
      val lines = output.linesIterator.toList
      assertEquals(lines.size, 6, clues(output))
      assert(lines.head.startsWith("4.3.0 "), clues(lines.head))
      assert(lines.last.startsWith("1.0.0 "), clues(lines.last))
      // default line is `<version>  <release date> UTC` - one date, no tag
      assert(lines.forall(l => l.contains(" UTC") && !l.contains("(v")), clues(output))

  test("list --details adds the tag and the source-commit date"):
    withFreshRepo("cli-list-details"): repo =>
      val (code, output) = capture("list", "--details", "--limit", "1", "--repository", repo.toString)
      assertEquals(code, 0)
      val line = output.linesIterator.toList.head
      assert(line.startsWith("4.3.0 ") && line.contains("v4.3.0"), clues(line))
      assertEquals(line.split(" UTC", -1).length - 1, 2, clues(line)) // release date + commit date

  test("list --final excludes pre-releases"):
    withFreshRepo("cli-list-final"): repo =>
      val (code, output) = capture("list", "--final", "--repository", repo.toString)
      assertEquals(code, 0)
      assertEquals(output.linesIterator.size, 4, clues(output))
      assert(!output.contains("rc.1"), clues(output))

  test("list --limit caps the number of entries"):
    withFreshRepo("cli-list-limit"): repo =>
      val (code, output) = capture("list", "--limit", "2", "--repository", repo.toString)
      assertEquals(code, 0)
      assertEquals(output.linesIterator.size, 2, clues(output))

  test("list --since and --until bound the version range"):
    withFreshRepo("cli-list-range"): repo =>
      val (code, output) = capture("list", "--since", "1.0.1", "--until", "2.0.0", "--repository", repo.toString)
      assertEquals(code, 0)
      assert(output.contains("2.0.0") && output.contains("1.0.1"), clues(output))
      assert(!output.contains("4.3.0") && !output.contains("1.0.0"), clues(output))

  private val gpgHome: Option[String] = GpgKeyring.home

  private lazy val keyFingerprint: String = GpgKeyring.prepare(gpgHome.get)

  override def afterAll(): Unit = gpgHome.foreach(GpgKeyring.killAgent)

  gpgHome match
    case None =>
      test("CLI signing test skipped - GNUPGHOME not configured".ignore)(())
    case Some(_) =>
      test("target --increment without --no-sign produces a git-verifiable signed commit when a key is configured"):
        withFreshRepo("cli-signed-increment"): repo =>
          git(repo, "config", "user.signingkey", keyFingerprint): Unit
          val before = git(repo, "rev-parse", "HEAD").trim
          assertEquals(CLI.run(Array("target", "--increment", "patch", "--repository", repo.toString)), 0)
          val after = git(repo, "rev-parse", "HEAD").trim
          assertNotEquals(after, before)
          assert(git(repo, "log", "-1", "--format=%B").contains("version: patch"))
          val verify = Process.run(Seq("git", "verify-commit", after), repo)
          assert(verify.successful, clues(verify.stderr))
  end match
end CLISuite
