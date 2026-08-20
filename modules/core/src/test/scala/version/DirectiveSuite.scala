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
package version

import munit.FunSuite

import version.Directive.*
import version.semver.SemVer

class DirectiveSuite extends FunSuite:

  private def parse(message: String): List[Directive] = Directive.parse[SemVer](message)

  private def bump(component: String): Directive = Emit(Request.Bump(component))

  private def assign(component: String, value: Long): Directive = Emit(Request.Assign(component, value))

  private def advance(intent: Intent): Directive = Emit(Request.Advance(intent))

  test("a keyword names the request its scheme maps it to") {
    assertEquals(parse("version: major"), List(bump("major")))
    assertEquals(parse("version: minor"), List(bump("minor")))
    assertEquals(parse("version: patch"), List(bump("patch")))
    assertEquals(parse("version: breaking"), List(advance(Intent.Breaking)))
    assertEquals(parse("version: feature"), List(advance(Intent.Feature)))
    assertEquals(parse("version: feat"), List(advance(Intent.Feature)))
    assertEquals(parse("version: fix"), List(advance(Intent.Fix)))
    assertEquals(parse("version: stable"), List(advance(Intent.Stable)))
  }

  test("a keyword followed by text is the same request in shorthand") {
    assertEquals(parse("breaking: Remove deprecated API"), List(advance(Intent.Breaking)))
    assertEquals(parse("feat: x"), List(advance(Intent.Feature)))
    assertEquals(parse("feat: Add caching support"), List(advance(Intent.Feature)))
    assertEquals(parse("feature: New feature"), List(advance(Intent.Feature)))
    assertEquals(parse("fix: handle edge case"), List(advance(Intent.Fix)))
    assertEquals(parse("fix: Handle edge case"), List(advance(Intent.Fix)))
    assertEquals(parse("stable: promote"), List(advance(Intent.Stable)))
    assertEquals(parse("major: rework the public API"), List(bump("major")))
  }

  test("a keyword is read wherever it stands in a line, needing no position of its own") {
    assertEquals(parse("* fix: y"), List(advance(Intent.Fix)))
    assertEquals(parse("  - fix: y"), List(advance(Intent.Fix)))
    assertEquals(parse("Rework the parser, breaking: drop old API"), List(advance(Intent.Breaking)))
  }

  test("a keyword parted from its colon is prose") {
    assertEquals(parse("feat(api): x"), Nil)
    assertEquals(parse("fix!: x"), Nil)
    assertEquals(parse("chore(deps): x"), Nil)
    assertEquals(parse("BREAKING CHANGE: drops X"), Nil)
    assertEquals(parse("Breaking change: this one is described below"), Nil)
  }

  test("a shorthand with nothing after its colon asks for nothing") {
    assertEquals(parse("breaking:"), Nil)
    assertEquals(parse("feat:"), Nil)
  }

  test("a magnitude addresses the component its keyword names") {
    assertEquals(parse("version: major: 3"), List(assign("major", 3)))
    assertEquals(parse("version: minor: 5"), List(assign("minor", 5)))
    assertEquals(parse("version: patch: 2"), List(assign("patch", 2)))
  }

  test("a magnitude after a change word is not a directive, since intents carry no value") {
    assertEquals(parse("version: breaking: 5"), Nil)
    assertEquals(parse("version: fix: 7"), Nil)
  }

  test("a magnitude is read to the full range its component admits") {
    assertEquals(parse("version: major: 4294967296"), List(assign("major", 4294967296L)))
  }

  test("a magnitude that is not a decimal number is not a directive") {
    assertEquals(parse("version: major: -1"), Nil)
    assertEquals(parse("version: major: 99999999999999999999"), Nil)
    assertEquals(parse("version: major: ٣"), Nil)
  }

  test("a target names its version as written, for the scheme to read") {
    assertEquals(parse("target: 2.0.0"), List(Target("2.0.0")))
    assertEquals(parse("target: v2.0.0"), List(Target("v2.0.0")))
  }

  test("an ignore excludes the commit that carries it") {
    assertEquals(parse("version: ignore"), List(IgnoreSelf))
    assertEquals(parse("[ignore] docs only"), List(IgnoreSelf))
    assertEquals(parse("version: ignore-merged"), List(IgnoreMerged))
    assertEquals(parse("[ignore-merged]"), List(IgnoreMerged))
  }

  test("an ignore names other commits by identifier prefix or by range") {
    assertEquals(parse("version: ignore: abc1234"), List(IgnoreCommits(Set("abc1234"))))
    assertEquals(parse("version: ignore: abc1234, def5678"), List(IgnoreCommits(Set("abc1234", "def5678"))))
    assertEquals(parse("version: ignore: abc1234..def5678"), List(IgnoreRange("abc1234", "def5678")))
  }

  test("an identifier too short to be unambiguous excludes nothing at all") {
    assertEquals(parse("version: ignore: abc12"), Nil)
    assertEquals(parse("version: ignore: abc1234.."), Nil)
  }

  test("keywords are matched without regard to case") {
    assertEquals(parse("VERSION: MAJOR"), List(bump("major")))
    assertEquals(parse("Version: Minor"), List(bump("minor")))
    assertEquals(parse("[BREAKING]"), List(advance(Intent.Breaking)))
  }

  test("a keyword inside a longer word is prose") {
    assertEquals(parse("reversion: 1.0.0"), Nil)
    assertEquals(parse("retarget: 2.0.0"), Nil)
    assertEquals(parse("prefixbreaking: text"), Nil)
    assertEquals(parse("version: majorx"), Nil)
  }

  test("every line of a message is read") {
    assertEquals(parse("version: major\nversion: minor"), List(bump("major"), bump("minor")))
  }

  test("a bracketed keyword is the same request as its colon form") {
    assertEquals(parse("[major]"), List(bump("major")))
    assertEquals(parse("[minor] add helper"), List(bump("minor")))
    assertEquals(parse("[breaking] Remove deprecated API"), List(advance(Intent.Breaking)))
    assertEquals(parse("[feat] add caching"), List(advance(Intent.Feature)))
    assertEquals(parse("[fix] correct typo"), List(advance(Intent.Fix)))
    assertEquals(parse("[ breaking ]"), List(advance(Intent.Breaking)))
  }

  test("a bracket led by a colon directive yields that directive once") {
    assertEquals(parse("[version: major]"), List(bump("major")))
    assertEquals(parse("[version: major: 3]"), List(assign("major", 3)))
    assertEquals(parse("[target: 2.0.0]"), List(Target("2.0.0")))
    assertEquals(parse("[breaking: drop the legacy API]"), List(advance(Intent.Breaking)))
    assertEquals(parse("[feat: add caching]"), List(advance(Intent.Feature)))
    assertEquals(parse("[version: major rollout]"), List(bump("major")))
  }

  test("a bracket that does not lead with a directive is prose, and hides what it contains") {
    assertEquals(parse("[skip ci]"), Nil)
    assertEquals(parse("[ci skip]"), Nil)
    assertEquals(parse("[WIP]"), Nil)
    assertEquals(parse("[JIRA-123] fix login"), Nil)
    assertEquals(parse("[major refactor]"), Nil)
    assertEquals(parse("[see version: major]"), Nil)
    assertEquals(parse("[foo version: major]"), Nil)
  }

  test("a directive outside an opaque bracket still fires") {
    assertEquals(parse("[foo] version: major"), List(bump("major")))
    assertEquals(parse("[see version: major]x"), List(bump("major")))
  }

  test("an unterminated bracket is prose") {
    assertEquals(parse("[breaking but no close"), Nil)
  }

  test("a bracket glued to a word on either side is prose") {
    assertEquals(parse("somebracketin[breaking]inline"), Nil)
    assertEquals(parse("foo[breaking]"), Nil)
    assertEquals(parse("[breaking]bar"), Nil)
    assertEquals(parse("-[breaking]"), Nil)
    assertEquals(parse("[breaking]-x"), Nil)
  }

  test("punctuation around a bracket leaves it a directive") {
    assertEquals(parse("Remove old API [breaking]."), List(advance(Intent.Breaking)))
    assertEquals(parse("[breaking], and more"), List(advance(Intent.Breaking)))
    assertEquals(parse("done [feat]"), List(advance(Intent.Feature)))
    assertEquals(parse("[breaking].more"), List(advance(Intent.Breaking)))
    assertEquals(parse("[breaking]\r"), List(advance(Intent.Breaking)))
  }

  test("each bracket of a run is read on its own") {
    assertEquals(parse("[core][breaking] Text"), List(advance(Intent.Breaking)))
    assertEquals(parse("[major][minor]"), List(bump("major"), bump("minor")))
    assertEquals(parse("[breaking][feature] Rework the parser"), List(advance(Intent.Breaking), advance(Intent.Feature)))
    assertEquals(parse("Add request caching [breaking]"), List(advance(Intent.Breaking)))
    assertEquals(parse("[fix][breaking]"), List(advance(Intent.Fix), advance(Intent.Breaking)))
    assertEquals(parse("[ignore][feat]"), List(IgnoreSelf, advance(Intent.Feature)))
    assertEquals(parse("[][breaking]"), List(advance(Intent.Breaking)))
    assertEquals(parse("[ ][breaking]"), List(advance(Intent.Breaking)))
  }

  test("a stray bracket neither fires nor blocks the one beside it") {
    assertEquals(parse("][breaking]"), List(advance(Intent.Breaking)))
    assertEquals(parse("[breaking]["), List(advance(Intent.Breaking)))
    assertEquals(parse("[major]]"), List(bump("major")))
  }

  test("a keyword spelled outside the ASCII word charset is prose") {
    assertEquals(parse("[breáking]"), Nil)
    assertEquals(parse("[breaking] 修正"), List(advance(Intent.Breaking)))
  }

  // A second vocabulary, to keep the grammar honest about naming no keyword of its own.
  private val custom: ResolvableScheme[SemVer] = new ResolvableScheme[SemVer]:
    def initialVersion: SemVer = SemVer.resolvable.initialVersion

    def developmentVersion(release: SemVer, metadata: DevelopmentMetadata): SemVer =
      SemVer.resolvable.developmentVersion(release, metadata)

    def defaultTarget(base: SemVer): SemVer = SemVer.resolvable.defaultTarget(base)

    def directives: Map[String, Request] = SemVer.resolvable.directives ++ Map(
      "epoch" -> Request.Bump("epoch"),
      "version" -> Request.Bump("minor"),
      "target" -> Request.Advance(Intent.Fix)
    )

    extension (v: SemVer) def snapshot: Boolean = SemVer.resolvable.snapshot(v)

  test("the grammar reads the words its scheme maps, and names none of its own") {
    assertEquals(Directive.parse[SemVer]("version: epoch")(using custom), List(bump("epoch")))
    assertEquals(Directive.parse[SemVer]("[epoch]")(using custom), List(bump("epoch")))
    assertEquals(parse("version: epoch"), Nil)
    assertEquals(parse("[epoch]"), Nil)
  }

  test("the grammar's own heads stay its own, whatever a scheme maps them to") {
    assertEquals(Directive.parse[SemVer]("version: major")(using custom), List(bump("major")))
    assertEquals(Directive.parse[SemVer]("target: 2.0.0")(using custom), List(Target("2.0.0")))
  }

  test("a message with nothing to say yields nothing") {
    assertEquals(parse(""), Nil)
    assertEquals(parse("Correct the spelling of a log line\n\nNo directive here."), Nil)
  }

end DirectiveSuite
