package version.cli.core.parsing

import version.*
import version.cli.core.domain.Keyword
import version.cli.core.domain.Keyword.*
import version.errors.ParseError
import version.parser.VersionParser

// scalafix:off
/** Manual-scanning keyword parser for commit messages.
  *
  * Properties:
  *   - Case-insensitive keyword detection
  *   - Whitespace tolerance around colons
  *   - Token boundaries enforced (no substring matches within larger words)
  *   - Efficient single pass across each input string; avoids regex
  *
  * Extracted keywords:
  *   - change: major | breaking
  *   - change: minor | feature
  *   - change: patch | fix
  *   - version: major: <N>
  *   - version: minor: <N>
  *   - version: patch: <N>
  *   - target: <SEMVER> (optional leading v/V accepted)
  *
  * NOTE: This is a hot path. `while`/`do`, local vars, and direct Char ops are intentional to minimise allocations and
  * enable tight JIT-optimised loops. These are encapsulated within the boundaries of this object.
  */
object KeywordParser:

  // --- character predicates ---

  private inline def isSpace(c: Char): Boolean =
    c == ' ' || c == '\t' || c == '\r' || c == '\n'

  private inline def isWordChar(c: Char): Boolean =
    (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '-'

  private inline def eqIc(a: Char, b: Char): Boolean =
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

  /** Extracts recognised keywords from a full commit message (subject + body). */
  def parse(message: String)(using PreRelease.Resolver): List[Keyword] =
    // Process line by line for predictable boundaries with a single pass.
    val lines = message.split('\n')
    var acc = List.empty[Keyword]
    var idx = 0
    val m = lines.length
    while idx < m do
      acc = acc ++ parseLine(lines(idx))
      idx += 1
    acc

  // Internal: parse a single line.
  private def parseLine(line: String)(using PreRelease.Resolver): List[Keyword] =
    var i = 0
    var out = List.empty[Keyword]
    val n = line.length
    while i < n do
      if startsWithKW(line, i, "change") then
        val j0 = afterColon(line, i + "change".length)
        if j0 != -1 then
          val (word, j1) = readWord(line, j0)
          word.toLowerCase match
            case w if w == "major" || w == "breaking" =>
              out = out :+ MajorChange; i = j1
            case w if w == "minor" || w == "feature" =>
              out = out :+ MinorChange; i = j1
            case w if w == "patch" || w == "fix" =>
              out = out :+ PatchChange; i = j1
            case _ => i += 1
        else i += 1
      else if startsWithKW(line, i, "breaking") then
        // Allow shorthand "BREAKING: ..." to imply a major change
        out = out :+ MajorChange
        val j0 = afterColon(line, i + "breaking".length)
        i = if j0 != -1 then j0 else i + "breaking".length
      else if startsWithKW(line, i, "feature") then
        // Allow shorthand "feature: ..." to imply a minor change
        out = out :+ MinorChange
        val j0 = afterColon(line, i + "feature".length)
        i = if j0 != -1 then j0 else i + "feature".length
      else if startsWithKW(line, i, "fix") then
        // Allow shorthand "fix: ..." to imply a patch change
        out = out :+ PatchChange
        val j0 = afterColon(line, i + "fix".length)
        i = if j0 != -1 then j0 else i + "fix".length
      else if startsWithKW(line, i, "version") then
        val j0 = afterColon(line, i + "version".length)
        if j0 != -1 then
          val (comp, j1) = readWord(line, j0)
          val j2 = afterColon(line, j1)
          if j2 != -1 then
            val (nOpt, j3) = readInt(line, j2)
            nOpt match
              case Some(nv) =>
                comp.toLowerCase match
                  case "major" =>
                    version.MajorVersion.from(nv).foreach(v => out = out :+ MajorSet(v))
                  case "minor" =>
                    version.MinorVersion.from(nv).foreach(v => out = out :+ MinorSet(v))
                  case "patch" =>
                    version.PatchNumber.from(nv).foreach(v => out = out :+ PatchSet(v))
                  case _ => ()
                i = j3
              case None => i += 1
          else i += 1
        else i += 1
      else if startsWithKW(line, i, "target") then
        val j0 = afterColon(line, i + "target".length)
        if j0 != -1 then
          val (tokOpt, j1) = readSemverToken(line, j0)
          tokOpt.foreach { s =>
            val norm = if s.startsWith("v") || s.startsWith("V") then s.drop(1) else s
            VersionParser.parse(norm) match
              case Right(v)            => out = out :+ TargetSet(v)
              case Left(_: ParseError) => () // ignore malformed targets
          }
          i = j1
        else i += 1
      else
        // advance to next interesting initial 'c'/'v'/'t' for faster scanning
        var j = i + 1
        var found = false
        while j < n && !found do
          val cj = line.charAt(j)
          val interesting = cj == 'c' || cj == 'C' || cj == 'v' || cj == 'V' || cj == 't' || cj == 'T'
          if interesting then found = true else j += 1
        i = j
    end while
    out
  end parseLine
end KeywordParser
// scalafix:on
