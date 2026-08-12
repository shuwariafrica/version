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

import scala.util.boundary
import scala.util.boundary.break

/** An instruction read out of a commit message.
  *
  * The ignore cases carry commit identifiers as opaque strings, so that a history obtained from any source - a
  * repository, a hosting API, a changelog - can be filtered without this library naming a repository format.
  *
  * Messages are read via [[Directive$ Directive]].
  */
enum Directive derives CanEqual:

  /** A request the message made. */
  case Emit(request: Request)

  /** A version named outright, carried as written for the scheme to read. */
  case Target(raw: String)

  /** Drops the commit that carries it. */
  case IgnoreSelf

  /** Drops the commits a merge brought in. */
  case IgnoreMerged

  /** Drops the commits whose identifiers begin with one of `ids`. */
  case IgnoreCommits(ids: Set[String])

  /** Drops the commits from `from` to `to` inclusive. */
  case IgnoreRange(from: String, to: String)

/** Provides the commit-message grammar that yields [[Directive]] values. */
object Directive:

  /** Reads every directive `message` carries, resolving keywords through the scheme's own vocabulary.
    *
    * Text matching no directive form is prose and yields nothing, so a message is never rejected.
    */
  def parse[V](message: String)(using workflow: ResolvableScheme[V]): List[Directive] =
    read(message, vocabulary(workflow.directives))

  // `version` and `target` head the grammar's own forms, so a scheme that maps either as a keyword of its own cannot
  // shadow the form itself.
  private val reserved: Set[String] = Set("version", "target")

  // scalafix:off
  // Index-arithmetic scan: avoids the per-line allocations a split/regex/Iterator pipeline would incur.

  // `interesting` is every character a directive can begin with, so that the scan skips prose a character at a time
  // without attempting a match at each position.
  final private class Vocabulary(val requests: Map[String, Request], val interesting: Set[Char])

  private def vocabulary(requests: Map[String, Request]): Vocabulary =
    val heads = requests.keys.flatMap(_.headOption.map(c => lower(c))).toSet
    new Vocabulary(requests, heads ++ Set('v', 't', '['))

  private transparent inline def isSpace(c: Char): Boolean =
    c == ' ' || c == '\t' || c == '\r' || c == '\n'

  private transparent inline def isWordChar(c: Char): Boolean =
    (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '-'

  private transparent inline def isDigit(c: Char): Boolean = c >= '0' && c <= '9'

  private transparent inline def eqIc(a: Char, b: Char): Boolean =
    a == b || a.toLower == b.toLower

  private inline def lower(c: Char): Char = if c >= 'A' && c <= 'Z' then (c + 32).toChar else c

  // The grammar's words are drawn from `isWordChar`, so folding case over ASCII alone keeps recognition independent
  // of the JVM's default locale, where `String.toLowerCase` is not.
  private def lower(s: String): String =
    val out = new StringBuilder(s.length)
    var i = 0
    while i < s.length do
      out.append(lower(s.charAt(i))): Unit
      i += 1
    out.result()

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

  private def hasNonEmptyText(s: String, i: Int): Boolean =
    var j = i
    val n = s.length
    while j < n && isSpace(s.charAt(j)) do j += 1
    j < n && s.charAt(j) != '\n'

  private def readWord(s: String, i0: Int): (String, Int) =
    var i = i0
    val start = i
    val n = s.length
    while i < n && isWordChar(s.charAt(i)) do i += 1
    (s.substring(start, i), i)

  private def readNumber(s: String, i0: Int): (Option[Long], Int) =
    var i = i0
    val start = i
    val n = s.length
    while i < n && isDigit(s.charAt(i)) do i += 1
    if i == start then (None, i)
    else
      var acc = 0L
      var j = start
      var ok = true
      while j < i && ok do
        val d = (s.charAt(j) - '0').toLong
        if acc > (Long.MaxValue - d) / 10 then ok = false else acc = acc * 10 + d
        j += 1
      if ok then (Some(acc), i) else (None, i)

  private def readVersionToken(s: String, i0: Int): (Option[String], Int) =
    var i = i0
    val start = i
    val n = s.length
    if i < n && (s.charAt(i) == 'v' || s.charAt(i) == 'V') then i += 1
    var consumed = false
    var keepGoing = true
    while i < n && keepGoing do
      val c = s.charAt(i)
      val ok = isDigit(c) || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '.' || c == '-' || c == '+'
      if ok then
        consumed = true
        i += 1
      else keepGoing = false
    val token = s.substring(start, i)
    if consumed then (Some(token), i) else (None, i0)

  private transparent inline def isHexChar(c: Char): Boolean =
    isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')

  private def readIdToken(s: String, i0: Int): (Option[String], Int) =
    var i = i0
    val start = i
    val n = s.length
    while i < n && isHexChar(s.charAt(i)) && (i - start) < 40 do i += 1
    val len = i - start
    if len >= 7 then (Some(lower(s.substring(start, i))), i) else (None, i0)

  private def readIdList(s: String, i0: Int): (Set[String], Int) =
    boundary:
      var i = i0
      val n = s.length
      var ids = Set.empty[String]
      var continue = true
      while continue && i < n do
        val j0 = skipSpaces(s, i)
        val (idOpt, j1) = readIdToken(s, j0)
        idOpt match
          case Some(id) =>
            val j2 = skipSpaces(s, j1)
            if j2 + 1 < n && s.charAt(j2) == '.' && s.charAt(j2 + 1) == '.' then break((Set.empty[String], i0))
            ids = ids + id
            if j2 < n && s.charAt(j2) == ',' then i = j2 + 1
            else
              i = j1; continue = false
          case None => continue = false
      (ids, i)

  private def readIdRange(s: String, i0: Int): (Option[(String, String)], Int) =
    val (fromOpt, j1) = readIdToken(s, i0)
    fromOpt match
      case Some(from) =>
        val j2 = skipSpaces(s, j1)
        if j2 + 1 < s.length && s.charAt(j2) == '.' && s.charAt(j2 + 1) == '.' then
          val j3 = skipSpaces(s, j2 + 2)
          val (toOpt, j4) = readIdToken(s, j3)
          toOpt match
            case Some(to) => (Some((from, to)), j4)
            case None     => (None, i0)
        else (None, i0)
      case None => (None, i0)

  private def read(message: String, vocab: Vocabulary): List[Directive] =
    val lines = message.split('\n')
    var acc = List.empty[Directive]
    var i = 0
    val n = lines.length
    while i < n do
      acc = acc ++ scan(lines(i), vocab)
      i += 1
    acc

  private def scan(text: String, vocab: Vocabulary): List[Directive] =
    var i = 0
    var out = List.empty[Directive]
    val n = text.length
    while i < n do
      val matched = shorthand(text, i, vocab)
      if matched.isDefined then
        val (directive, next) = matched.get
        directive.foreach(d => out = out :+ d)
        i = next
      else if startsWithKW(text, i, "version") then
        val j0 = afterColon(text, i + "version".length)
        if j0 != -1 then
          val (word, j1) = readWord(text, j0)
          lower(word) match
            case "ignore" =>
              val j2 = afterColon(text, j1)
              if j2 != -1 then
                val (rangeOpt, j3) = readIdRange(text, j2)
                rangeOpt match
                  case Some((from, to)) =>
                    out = out :+ IgnoreRange(from, to)
                    i = j3
                  case None =>
                    val (ids, j4) = readIdList(text, j2)
                    if ids.nonEmpty then
                      out = out :+ IgnoreCommits(ids)
                      i = j4
                    else i = j1
              else
                out = out :+ IgnoreSelf
                i = j1
            case "ignore-merged" =>
              out = out :+ IgnoreMerged
              i = j1
            case name =>
              vocab.requests.get(name) match
                case Some(request) =>
                  val j2 = afterColon(text, j1)
                  if j2 != -1 then
                    val (value, j3) = readNumber(text, j2)
                    // A magnitude addresses a component, so it is read only where the keyword names one; an intent
                    // carries no number and the whole form goes unrecognised.
                    (value, request) match
                      case (Some(v), Request.Bump(component)) =>
                        out = out :+ Emit(Request.Assign(component, v))
                        i = j3
                      case _ => i = j1
                  else
                    out = out :+ Emit(request)
                    i = j1
                case None => i = j1
          end match
        else i += 1
        end if
      else if startsWithKW(text, i, "target") then
        val j0 = afterColon(text, i + "target".length)
        if j0 != -1 then
          val (tokOpt, j1) = readVersionToken(text, j0)
          tokOpt.foreach(raw => out = out :+ Target(raw))
          i = j1
        else i += 1
      else if text.charAt(i) == '[' then
        bracket(text, i, vocab) match
          case Some((directive, next)) =>
            directive.foreach(d => out = out :+ d)
            i = next
          case None => i += 1
      else
        var j = i + 1
        var found = false
        while j < n && !found do if vocab.interesting.contains(lower(text.charAt(j))) then found = true else j += 1
        i = j
      end if
    end while
    out
  end scan

  // A keyword with nothing after its colon is recognised but emits nothing, so that a bare `feat:` is consumed rather
  // than read as a request.
  private def shorthand(text: String, i: Int, vocab: Vocabulary): Option[(Option[Directive], Int)] =
    boundary:
      val iter = vocab.requests.iterator
      while iter.hasNext do
        val (alias, request) = iter.next()
        if startsWithKW(text, i, alias) then
          if reserved.contains(alias) then break(None)
          val j0 = afterColon(text, i + alias.length)
          if j0 != -1 && hasNonEmptyText(text, j0) then break(Some((Some(Emit(request)), j0)))
          else break(Some((None, if j0 != -1 then j0 else i + alias.length)))
      None

  private def leadsWithDirective(s: String, vocab: Vocabulary): Boolean =
    val p = skipSpaces(s, 0)
    def headAt(kw: String): Boolean = startsWithKW(s, p, kw) && afterColon(s, p + kw.length) != -1
    headAt("version") || headAt("target") || vocab.requests.keysIterator.exists(headAt)

  // Boundary alignment on both sides leaves an embedded `foo[breaking]bar` alone. A bracket led by a colon directive
  // declines, so that the colon machinery reads `[version: major]` exactly once rather than both paths firing. Any
  // other bracket is consumed whole as a no-op, so that a directive quoted mid-content (`[see version: major]`) does
  // not leak through the scan of the surrounding text.
  private def bracket(text: String, i: Int, vocab: Vocabulary): Option[(Option[Directive], Int)] =
    if !wordBoundaryBefore(text, i) then None
    else
      val close = text.indexOf(']', i + 1)
      if close < 0 then None
      else if close + 1 < text.length && isWordChar(text.charAt(close + 1)) then None
      else
        val inner = text.substring(i + 1, close)
        val content = lower(inner.trim)
        if content == "ignore" then Some((Some(IgnoreSelf), close + 1))
        else if content == "ignore-merged" then Some((Some(IgnoreMerged), close + 1))
        else
          vocab.requests.get(content) match
            case Some(request) => Some((Some(Emit(request)), close + 1))
            case None          => if leadsWithDirective(inner, vocab) then None else Some((None, close + 1))
  // scalafix:on

end Directive
