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

import version.errors.InvalidVersionFormat
import version.errors.UnsupportedComponent
import version.semver.SemVer

class DerivationSuite extends FunSuite:

  private val scheme = summon[VersionScheme[SemVer]]

  private def v(input: String): SemVer = SemVer.parseUnsafe(input)

  private def emit(request: Request): Directive = Directive.Emit(request)

  private def named(raw: String): Directive = Directive.Target(raw)

  private def derive(base: String, directives: List[Directive]): Derivation[SemVer] =
    Derivation.target(v(base), directives, None, Nil)

  private def derive(
    base: String,
    directives: List[Directive],
    reachable: Option[String],
    repository: List[String]
  ): Derivation[SemVer] =
    Derivation.target(v(base), directives, reachable.map(v), repository.map(v))

  private def target(base: String, directives: List[Directive]): String =
    scheme.show(derive(base, directives).target)

  private def target(base: String, directives: List[Directive], reachable: Option[String], repository: List[String]): String =
    scheme.show(derive(base, directives, reachable, repository).target)

  test("a range that asks for nothing takes the scheme's own policy for it") {
    assertEquals(target("1.2.3", Nil), "1.2.4")
    assertEquals(target("3.0.0-rc.3", Nil), "3.0.0")
  }

  test("the highest of the requested versions wins, whichever request produced it") {
    assertEquals(target("1.2.3", List(emit(Request.Advance(Intent.Fix)), emit(Request.Advance(Intent.Breaking)))), "2.0.0")
    assertEquals(target("1.2.3", List(emit(Request.Advance(Intent.Feature)), emit(Request.Assign("major", 5)))), "5.0.0")
    assertEquals(target("1.2.3", List(emit(Request.Assign("minor", 7)), emit(Request.Assign("patch", 2)))), "1.7.0")
  }

  test("a request the scheme refuses is reported and the rest still decides") {
    val result = derive("1.2.3", List(emit(Request.Bump("epoch")), emit(Request.Advance(Intent.Fix))))
    assertEquals(scheme.show(result.target), "1.2.4")
    assertEquals(result.discarded, List[errors.VersionError](UnsupportedComponent("semver", "epoch")))
  }

  test("a version named in a spelling the scheme cannot read is reported, not applied") {
    val result = derive("1.2.3", List(named("2.0"), emit(Request.Advance(Intent.Feature))))
    assertEquals(scheme.show(result.target), "1.3.0")
    assertEquals(result.discarded, List[errors.VersionError](InvalidVersionFormat("2.0")))
  }

  test("a range that asks for nothing the scheme accepts still reaches a target") {
    val result = derive("1.2.3", List(emit(Request.Bump("epoch"))))
    assertEquals(scheme.show(result.target), "1.2.4")
    assertEquals(result.discarded.length, 1)
  }

  test("a version named outright outranks what the requests asked for") {
    assertEquals(target("1.2.3", List(named("5.0.0"), emit(Request.Advance(Intent.Breaking)))), "5.0.0")
    assertEquals(target("1.2.3", List(named("1.2.4"), emit(Request.Advance(Intent.Breaking)))), "1.2.4")
  }

  test("a named version is stripped to its release, and the highest of several wins") {
    assertEquals(target("1.2.3", List(named("2.5.0-rc.1"))), "2.5.0")
    assertEquals(
      target("1.4.0", List(named("1.5.0"), named("1.6.0")), Some("1.4.0"), List("1.4.0")),
      "1.6.0"
    )
  }

  test("a named version at or below a release already reachable is refused") {
    assertEquals(target("2.2.5", List(named("2.2.6")), Some("2.2.5"), List("2.2.5")), "2.2.6")
    assertEquals(
      target("2.2.5", List(named("2.2.5"), emit(Request.Advance(Intent.Breaking))), Some("2.2.5"), List("2.2.5")),
      "3.0.0"
    )
    assertEquals(target("2.2.5", List(named("2.2.0")), Some("2.2.5"), List("2.2.5")), "2.2.6")
  }

  test("a named version may equal the release a reachable pre-release is working towards") {
    assertEquals(target("3.1.0-rc.2", List(named("3.1.0")), Some("3.1.0-rc.2"), List("3.1.0-rc.2")), "3.1.0")
    assertEquals(
      target("3.1.0-rc.2", List(named("3.0.9"), emit(Request.Advance(Intent.Fix))), Some("3.1.0-rc.2"), List("3.1.0-rc.2")),
      "3.1.0"
    )
  }

  test("a release tagged anywhere in the repository bounds a named version, reachable or not") {
    assertEquals(target("1.0.0", List(named("2.1.0")), None, List("2.0.0")), "2.1.0")
    assertEquals(target("1.0.0", List(named("1.5.0")), None, List("2.0.0")), "1.0.1")
  }

  test("with no release tagged anywhere, the highest pre-release bounds a named version") {
    assertEquals(target("1.0.0", List(named("2.0.0")), None, List("2.0.0-rc.1")), "2.0.0")
    assertEquals(target("1.0.0", List(named("1.9.0")), None, List("2.0.0-rc.1")), "1.0.1")
  }

  test("a repository with nothing tagged in it admits any named version") {
    assertEquals(target("0.1.0", List(named("9.9.9")), None, Nil), "9.9.9")
  }

  private def first(directives: List[Directive], repository: List[String]): Derivation[SemVer] =
    Derivation.target(directives, repository.map(v))

  test("a project that has released nothing starts where the scheme starts") {
    assertEquals(scheme.show(first(Nil, Nil).target), "0.1.0")
  }

  test("requests do not advance a project that has released nothing") {
    assertEquals(scheme.show(first(List(emit(Request.Advance(Intent.Breaking))), Nil).target), "0.1.0")
    assertEquals(scheme.show(first(List(emit(Request.Assign("major", 3))), Nil).target), "0.1.0")
  }

  test("a version named outright sets the first release, where the tags admit it") {
    assertEquals(scheme.show(first(List(named("1.0.0")), Nil).target), "1.0.0")
    assertEquals(scheme.show(first(List(named("2.1.0")), List("2.0.0")).target), "2.1.0")
    assertEquals(scheme.show(first(List(named("1.5.0")), List("2.0.0")).target), "0.1.0")
  }

  test("a first version named in a spelling the scheme cannot read is reported, not applied") {
    val result = first(List(named("2.0")), Nil)
    assertEquals(scheme.show(result.target), "0.1.0")
    assertEquals(result.discarded, List[errors.VersionError](InvalidVersionFormat("2.0")))
  }

end DerivationSuite
