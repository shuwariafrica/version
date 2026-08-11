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

import version.semver.SemVer

class VersionResolverSuite extends FunSuite:

  private val resolver = VersionResolver.withDefaults[SemVer]

  test("the default resolver carries the contextual capability instances") {
    assertEquals(resolver.scheme.name, "semver")
    assertEquals(resolver.workflow.initialVersion.show, "0.1.0")
    assert(resolver.arithmetic(SemVer.parseUnsafe("1.2.3"), Request.Advance(Intent.Fix)).isRight)
  }

  test("the default tag parser accepts a tag with or without the conventional prefix") {
    assertEquals(resolver.tagParser("1.2.3"), Some(SemVer.parseUnsafe("1.2.3")))
    assertEquals(resolver.tagParser("v1.2.3"), Some(SemVer.parseUnsafe("1.2.3")))
    assertEquals(resolver.tagParser("V1.2.3"), Some(SemVer.parseUnsafe("1.2.3")))
  }

  test("the default tag parser rejects a tag that is not a version") {
    assertEquals(resolver.tagParser("release-candidate"), None)
    assertEquals(resolver.tagParser(""), None)
  }

  test("the default resolver renders canonically until given a formatter") {
    assertEquals(resolver.formatter, None)
    assertEquals(resolver.withFormatter(SemVer.Formatter.Standard).formatter, Some(SemVer.Formatter.Standard))
    assertEquals(resolver.withFormatter(SemVer.Formatter.Standard).withoutFormatter.formatter, None)
  }

  test("a supplied tag parser replaces the default") {
    val prefixed = resolver.withTagParser(name => SemVer.parse(name.stripPrefix("release/")).toOption)
    assertEquals(prefixed.tagParser("release/2.0.0"), Some(SemVer.parseUnsafe("2.0.0")))
    assertEquals(resolver.tagParser("release/2.0.0"), None)
  }

end VersionResolverSuite
