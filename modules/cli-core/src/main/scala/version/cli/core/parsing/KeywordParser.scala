/****************************************************************************
 * Copyright 2023 Shuwari Africa Ltd.                                       *
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
package version.cli.core.parsing

import version.*
import version.cli.core.domain.Keyword
import version.cli.core.domain.Keyword.*
import version.errors.ParseError

// scalafix:off
/** Commit message keyword parser for version resolution.
  *
  * Extracts version control keywords from commit messages following the specification in
  * `docs/version-resolution-technical-specification.md`. Performs single-pass scanning with
  * case-insensitive keyword detection and proper token boundary enforcement.
  *
  * Recognised keyword forms:
  *   - `version: major | breaking | minor | feature | feat | patch | fix` — relative increment
  *   - `version: major: <N> | minor: <N> | patch: <N>` — absolute set (synonyms apply)
  *   - `version: ignore` — exclude commit from version calculation
  *   - `<bump-token>: <text>` — standalone shorthand (e.g., `breaking: Remove API`)
  *   - `target: <SEMVER>` — target version directive (optional leading `v` or `V`)
  *
  * @see [[version.cli.core.VersionCliCore$ VersionCliCore]] for the public resolution API
  */
object KeywordParser:

  // --- character predicates ---

  private transparent inline def isSpace(c: Char): Boolean =
    c == ' ' || c == '\t' || c == '\r' || c == '\n'

  private transparent inline def isWordChar(c: Char): Boolean =
    (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '-'

  private transparent inline def eqIc(a: Char, b: Char): Boolean =
    a == b || a.toLower == b.toLower

  // --- low-level scanning utilities ---

  private def skipSpaces(s: String, i0: Int): Int =
    var i = i0
    val n = s.length
    while i < n && isSpace(s.charAt(i)) do i += 1
    i

  private def wordBoundaryBefore(s: String, i: Int): Boolean =
    i == 0 || !isWordChar(s.charAt(i - 1))

  private def wordBoundaryAfter(s: String, i: Int, kwLen: Int): Boolean =
    val j = i + kwLen
    j >= s.length || {
      val c = s.charAt(j)
      c == ':' || !isWordChar(c) // must be ':' or a non-word boundary
    }

  private def startsWithKW(s: String, i: Int, kw: String): Boolean =
    var k = 0
    var j = i
    val n = s.length
    while k < kw.length && j < n && eqIc(s.charAt(j), kw.charAt(k)) do
      k += 1; j += 1
    k == kw.length && wordBoundaryBefore(s, i) && wordBoundaryAfter(s, i, kw.length)

  private def afterColon(s: String, i0: Int): Int =
    val i = skipSpaces(s, i0)
    if i < s.length && s.charAt(i) == ':' then skipSpaces(s, i + 1) else -1

  private def readWord(s: String, i0: Int): (String, Int) =
    var i = i0
    val start = i
    val n = s.length
    while i < n && isWordChar(s.charAt(i)) do i += 1
    (s.substring(start, i), i)

  private def readInt(s: String, i0: Int): (Option[Int], Int) =
    var i = i0
    val start = i
    val n = s.length
    while i < n && s.charAt(i).isDigit do i += 1
    if i == start then (None, i)
    else
      // Overflow-aware parse into a Long then bound-check
      var acc: Long = 0L
      var j = start
      var ok = true
      while j < i && ok do
        val d = (s.charAt(j) - '0').toLong
        acc = acc * 10 + d
        if acc > Int.MaxValue then ok = false
        j += 1
      if ok then (Some(acc.toInt), i) else (None, i)

  private def readSemverToken(s: String, i0: Int): (Option[String], Int) =
    // Read contiguous token allowed in SemVer: [0-9A-Za-z-+.], allow optional leading v/V
    var i = i0
    val start = i
    val n = s.length
    if i < n && (s.charAt(i) == 'v' || s.charAt(i) == 'V') then i += 1
    var consumed = false
    var keepGoing = true
    while i < n && keepGoing do
      val c = s.charAt(i)
      val ok = c.isDigit || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '.' || c == '-' || c == '+'
      if ok then
        consumed = true
        i += 1
      else keepGoing = false
    val token = s.substring(start, i)
    if consumed then (Some(token), i) else (None, i0)

  // --- public API ---

  /** Extracts keywords from a commit message.
    *
    * Scans the full message (subject and body) and returns all recognised version control
    * keywords. Invalid or unrecognised directives are silently ignored.
    */
  def parse(message: String)(using reader: Version.Read[String]): List[Keyword] =
    // Process line by line for predictable boundaries with a single pass.
    val lines = message.split('\n')
    var acc = List.empty[Keyword]
    var idx = 0
    val m = lines.length
    while idx < m do
      acc = acc ++ parseLine(lines(idx), reader)
      idx += 1
    acc

  // Internal: parse a single line.
  private def parseLine(line: String, reader: Version.Read[String]): List[Keyword] =
    var i = 0
    var out = List.empty[Keyword]
    val n = line.length
    while i < n do
      // Standalone shorthands: <bump-token>: <non-empty-text>
      // These must have non-empty text after the colon to be valid
      if startsWithKW(line, i, "breaking") then
        val j0 = afterColon(line, i + "breaking".length)
        if j0 != -1 && hasNonEmptyText(line, j0) then out = out :+ MajorChange
        i = if j0 != -1 then j0 else i + "breaking".length
      else if startsWithKW(line, i, "major") && !startsWithKW(line, i, "majorversion") then
        val j0 = afterColon(line, i + "major".length)
        if j0 != -1 && hasNonEmptyText(line, j0) then out = out :+ MajorChange
        i = if j0 != -1 then j0 else i + "major".length
      else if startsWithKW(line, i, "feature") then
        val j0 = afterColon(line, i + "feature".length)
        if j0 != -1 && hasNonEmptyText(line, j0) then out = out :+ MinorChange
        i = if j0 != -1 then j0 else i + "feature".length
      else if startsWithKW(line, i, "feat") && !startsWithKW(line, i, "feature") then
        // `feat` is a Conventional Commits abbreviation for feature (minor increment)
        val j0 = afterColon(line, i + "feat".length)
        if j0 != -1 && hasNonEmptyText(line, j0) then out = out :+ MinorChange
        i = if j0 != -1 then j0 else i + "feat".length
      else if startsWithKW(line, i, "minor") && !startsWithKW(line, i, "minorversion") then
        val j0 = afterColon(line, i + "minor".length)
        if j0 != -1 && hasNonEmptyText(line, j0) then out = out :+ MinorChange
        i = if j0 != -1 then j0 else i + "minor".length
      else if startsWithKW(line, i, "fix") then
        val j0 = afterColon(line, i + "fix".length)
        if j0 != -1 && hasNonEmptyText(line, j0) then out = out :+ PatchChange
        i = if j0 != -1 then j0 else i + "fix".length
      else if startsWithKW(line, i, "patch") && !startsWithKW(line, i, "patchversion") then
        val j0 = afterColon(line, i + "patch".length)
        if j0 != -1 && hasNonEmptyText(line, j0) then out = out :+ PatchChange
        i = if j0 != -1 then j0 else i + "patch".length
      else if startsWithKW(line, i, "version") then
        val j0 = afterColon(line, i + "version".length)
        if j0 != -1 then
          val (word, j1) = readWord(line, j0)
          word.toLowerCase match
            case "ignore" =>
              // version: ignore - exclude commit from version calculation
              out = out :+ Ignore
              i = j1
            case w if w == "major" || w == "breaking" =>
              // Check for absolute set (version: major: N)
              val j2 = afterColon(line, j1)
              if j2 != -1 then
                val (nOpt, j3) = readInt(line, j2)
                nOpt match
                  case Some(nv) =>
                    version.MajorVersion.from(nv).foreach(v => out = out :+ MajorSet(v))
                    i = j3
                  case None => i = j1 // malformed absolute, ignore
              else
                // Relative increment: version: major
                out = out :+ MajorChange
                i = j1
            case w if w == "minor" || w == "feature" || w == "feat" =>
              val j2 = afterColon(line, j1)
              if j2 != -1 then
                val (nOpt, j3) = readInt(line, j2)
                nOpt match
                  case Some(nv) =>
                    version.MinorVersion.from(nv).foreach(v => out = out :+ MinorSet(v))
                    i = j3
                  case None => i = j1
              else
                out = out :+ MinorChange
                i = j1
            case w if w == "patch" || w == "fix" =>
              val j2 = afterColon(line, j1)
              if j2 != -1 then
                val (nOpt, j3) = readInt(line, j2)
                nOpt match
                  case Some(nv) =>
                    version.PatchNumber.from(nv).foreach(v => out = out :+ PatchSet(v))
                    i = j3
                  case None => i = j1
              else
                out = out :+ PatchChange
                i = j1
            case _ =>
              // Unrecognised word after version:, skip
              i = j1
          end match
        else i += 1
        end if
      else if startsWithKW(line, i, "target") then
        val j0 = afterColon(line, i + "target".length)
        if j0 != -1 then
          val (tokOpt, j1) = readSemverToken(line, j0)
          tokOpt.foreach { s =>
            val norm = if s.startsWith("v") || s.startsWith("V") then s.drop(1) else s
            reader.toVersion(norm) match
              case Right(v)            => out = out :+ TargetSet(v)
              case Left(_: ParseError) => () // ignore malformed targets
          }
          i = j1
        else i += 1
      else
        // Advance to next interesting initial character for faster scanning
        // Interesting chars: b(reaking), m(ajor/inor), f(eature/ix), p(atch), v(ersion), t(arget)
        var j = i + 1
        var found = false
        while j < n && !found do
          val cj = line.charAt(j).toLower
          val interesting = cj == 'b' || cj == 'm' || cj == 'f' || cj == 'p' || cj == 'v' || cj == 't'
          if interesting then found = true else j += 1
        i = j
    end while
    out
  end parseLine

  /** Checks if there is non-empty, non-whitespace text starting at position i. */
  private def hasNonEmptyText(s: String, i: Int): Boolean =
    var j = i
    val n = s.length
    // Skip whitespace and check if there's actual content
    while j < n && isSpace(s.charAt(j)) do j += 1
    j < n && s.charAt(j) != '\n'

end KeywordParser
// scalafix:on
