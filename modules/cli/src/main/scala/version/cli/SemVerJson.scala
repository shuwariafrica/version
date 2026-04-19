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
/** Minimal JSON serialisation for [[SemVer]] used by the CLI's `--emit json` output.
  *
  * Write-only. No external codec dependency required.
  */
private[cli] object SemVerJson:

  /** Render a SemVer value as a JSON object string. */
  def toJson(v: SemVer): String =
    val sb = new StringBuilder(128)
    def w(s: String): Unit =
      val _ = sb.append(s)
    def wc(c: Char): Unit =
      val _ = sb.append(c)
    def wi(n: Int): Unit =
      val _ = sb.append(n)
    wc('{')
    w("\"major\":"); wi(v.major.value)
    w(",\"minor\":"); wi(v.minor.value)
    w(",\"patch\":"); wi(v.patch.value)
    v.preRelease.foreach: pr =>
      w(",\"preRelease\":{\"classifier\":\""); w(escapeJson(pr.classifier.show)); wc('"')
      pr.number.foreach { n => w(",\"number\":"); wi(n.value) }
      wc('}')
    v.metadata.foreach: bm =>
      w(",\"metadata\":[")
      val ids = bm.identifiers
      var i = 0
      while i < ids.length do
        if i > 0 then wc(',')
        wc('"'); w(escapeJson(ids(i))); wc('"')
        i += 1
      wc(']')
    wc('}')
    sb.result()
  end toJson

  private def escapeJson(s: String): String =
    // Metadata identifiers and classifier names are ASCII alphanumeric + hyphen only,
    // so escaping is a safety net rather than a hot path.
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
