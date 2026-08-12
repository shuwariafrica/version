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

import version.semver.*

// scalafix:off
// Hand-written rather than taken from a codec library: `--emit json` writes one shape and never reads it back, so a
// dependency would cost the native binary more than it saves here.
private[cli] object SemVerJson:

  def toJson(v: SemVer): String =
    val sb = new StringBuilder(128)
    def w(s: String): Unit =
      val _ = sb.append(s)
    def wc(c: Char): Unit =
      val _ = sb.append(c)
    def wl(n: Long): Unit =
      val _ = sb.append(n)
    def wids(ids: List[String]): Unit =
      wc('[')
      var i = 0
      while i < ids.length do
        if i > 0 then wc(',')
        wc('"'); w(escapeJson(ids(i))); wc('"')
        i += 1
      wc(']')
    wc('{')
    w("\"major\":"); wl(v.major.value)
    w(",\"minor\":"); wl(v.minor.value)
    w(",\"patch\":"); wl(v.patch.value)
    v.preRelease.foreach: pr =>
      w(",\"preRelease\":"); wids(pr.identifiers)
    v.metadata.foreach: bm =>
      w(",\"metadata\":"); wids(bm.identifiers)
    wc('}')
    sb.result()
  end toJson

  private def escapeJson(s: String): String =
    // Pre-release and metadata identifiers are ASCII alphanumerics and hyphens only, so this is a safety net against
    // a future scheme rather than a path any SemVer value takes.
    val sb = new StringBuilder(s.length)
    var i = 0
    while i < s.length do
      val c = s.charAt(i)
      c match
        case '"'  => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case _    => sb.append(c)
      i += 1
    sb.result()
end SemVerJson
// scalafix:on
