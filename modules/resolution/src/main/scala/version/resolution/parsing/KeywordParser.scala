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
package version.resolution.parsing

import version.ComponentRole
import version.ResolvableScheme
import version.resolution.domain.Keyword
import version.resolution.domain.Keyword.*

// scalafix:off
// Hotpath: single-pass character-level scanning with no intermediate allocations.
// Avoids split/regex/Iterator chains that would allocate per-line during resolution.

/** Commit message keyword parser for version resolution.
  *
  * Extracts version control keywords from commit messages following the specification. Keyword-to-component-index
  * mapping is derived from the scheme's [[version.ResolvableScheme.keywordAliases keywordAliases]].
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
      c == ':' || !isWordChar(c)
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

  private transparent inline def isHexChar(c: Char): Boolean =
    c.isDigit || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')

  private def readShaToken(s: String, i0: Int): (Option[String], Int) =
    var i = i0
    val start = i
    val n = s.length
    while i < n && isHexChar(s.charAt(i)) && (i - start) < 40 do i += 1
    val len = i - start
    if len >= 7 then (Some(s.substring(start, i).toLowerCase), i) else (None, i0)

  private def parseShaList(s: String, i0: Int): (Set[String], Int) =
    import scala.util.boundary, boundary.break
    boundary:
      var i = i0
      val n = s.length
      var shas = Set.empty[String]
      var continue = true
      while continue && i < n do
        val j0 = skipSpaces(s, i)
        val (shaOpt, j1) = readShaToken(s, j0)
        shaOpt match
          case Some(sha) =>
            val j2 = skipSpaces(s, j1)
            if j2 + 1 < n && s.charAt(j2) == '.' && s.charAt(j2 + 1) == '.' then break((Set.empty[String], i0))
            shas = shas + sha
            if j2 < n && s.charAt(j2) == ',' then i = j2 + 1
            else
              i = j1; continue = false
          case None => continue = false
      (shas, i)

  private def parseShaRange(s: String, i0: Int): (Option[(String, String)], Int) =
    val (fromOpt, j1) = readShaToken(s, i0)
    fromOpt match
      case Some(from) =>
        val j2 = skipSpaces(s, j1)
        if j2 + 1 < s.length && s.charAt(j2) == '.' && s.charAt(j2 + 1) == '.' then
          val j3 = skipSpaces(s, j2 + 2)
          val (toOpt, j4) = readShaToken(s, j3)
          toOpt match
            case Some(to) => (Some((from, to)), j4)
            case None     => (None, i0)
        else (None, i0)
      case None => (None, i0)

  // --- Keyword context derived from scheme ---

  /** Precomputed keyword context from a scheme's aliases and layout. */
  final private class KeywordContext(
    val aliases: Map[String, Int],
    val fixIndices: Set[Int],
    val interestingChars: Set[Char]
  )

  private def buildContext[V](scheme: ResolvableScheme[V]): KeywordContext =
    val aliases = scheme.keywordAliases
    val fixIndices = scheme.layout.zipWithIndex.collect { case (d, i) if d.role == ComponentRole.Fix => i }.toSet
    val kwChars = aliases.keys.map(_.head).toSet
    val interestingChars = kwChars ++ Set('v', 't') // version:, target: always interesting
    new KeywordContext(aliases, fixIndices, interestingChars)

  // --- public API ---

  /** Extracts keywords from a commit message using scheme-derived keyword aliases. */
  def parse[V](message: String)(using scheme: ResolvableScheme[V]): List[Keyword] =
    val ctx = buildContext(scheme)
    val lines = message.split('\n')
    var acc = List.empty[Keyword]
    var idx = 0
    val m = lines.length
    while idx < m do
      acc = acc ++ parseLine(lines(idx), ctx)
      idx += 1
    acc

  private def parseLine(line: String, ctx: KeywordContext): List[Keyword] =
    var i = 0
    var out = List.empty[Keyword]
    val n = line.length
    while i < n do
      // Try standalone shorthands: <alias>: <non-empty-text>
      val matched = tryStandaloneShorthand(line, i, ctx)
      if matched.isDefined then
        val (kw, nextI) = matched.get
        kw.foreach(k => out = out :+ k)
        i = nextI
      else if startsWithKW(line, i, "version") then
        val j0 = afterColon(line, i + "version".length)
        if j0 != -1 then
          val (word, j1) = readWord(line, j0)
          val lower = word.toLowerCase
          lower match
            case "ignore" =>
              val j2 = afterColon(line, j1)
              if j2 != -1 then
                val (rangeOpt, j3) = parseShaRange(line, j2)
                rangeOpt match
                  case Some((from, to)) =>
                    out = out :+ IgnoreRange(from, to)
                    i = j3
                  case None =>
                    val (shas, j4) = parseShaList(line, j2)
                    if shas.nonEmpty then
                      out = out :+ IgnoreCommits(shas)
                      i = j4
                    else i = j1
              else
                out = out :+ IgnoreSelf
                i = j1
            case "ignore-merged" =>
              out = out :+ IgnoreMerged
              i = j1
            case _ =>
              // Check if the word is a known alias (version: major, version: breaking, etc.)
              ctx.aliases.get(lower) match
                case Some(idx) =>
                  val j2 = afterColon(line, j1)
                  if j2 != -1 then
                    val (nOpt, j3) = readInt(line, j2)
                    nOpt match
                      case Some(nv) if nv >= 0 =>
                        out = out :+ ComponentSet(idx, nv)
                        i = j3
                      case _ => i = j1
                  else
                    // Relative increment - but Fix-role components are no-ops (patch is default)
                    if !ctx.fixIndices.contains(idx) then out = out :+ ComponentBump(idx)
                    i = j1
                case None =>
                  i = j1
          end match
        else i += 1
        end if
      else if startsWithKW(line, i, "target") then
        val j0 = afterColon(line, i + "target".length)
        if j0 != -1 then
          val (tokOpt, j1) = readSemverToken(line, j0)
          tokOpt.foreach: raw =>
            out = out :+ TargetSet(raw)
          i = j1
        else i += 1
      else
        // Advance to next interesting character for faster scanning
        var j = i + 1
        var found = false
        while j < n && !found do
          val cj = line.charAt(j).toLower
          if ctx.interestingChars.contains(cj) then found = true else j += 1
        i = j
      end if
    end while
    out
  end parseLine

  /** Try matching a standalone shorthand at position i.
    *
    * Returns `None` if no alias matches at this position. Returns `Some((keyword, nextPosition))` if an alias matches.
    * The keyword is `None` for fix-role no-ops (recognised but no keyword emitted).
    */
  private def tryStandaloneShorthand(line: String, i: Int, ctx: KeywordContext): Option[(Option[Keyword], Int)] =
    import scala.util.boundary, boundary.break
    boundary:
      val iter = ctx.aliases.iterator
      while iter.hasNext do
        val (alias, idx) = iter.next()
        if startsWithKW(line, i, alias) then
          if alias == "version" then break(None) // handled separately
          val j0 = afterColon(line, i + alias.length)
          if j0 != -1 && hasNonEmptyText(line, j0) then
            val kw = if ctx.fixIndices.contains(idx) then None else Some(ComponentBump(idx))
            break(Some((kw, j0)))
          else break(Some((None, if j0 != -1 then j0 else i + alias.length)))
      None

  private def hasNonEmptyText(s: String, i: Int): Boolean =
    var j = i
    val n = s.length
    while j < n && isSpace(s.charAt(j)) do j += 1
    j < n && s.charAt(j) != '\n'

end KeywordParser
// scalafix:on
