/** ************************************************************************** Copyright 2023 Shuwari Africa Ltd. * *
  * Licensed under the Apache License, Version 2.0 (the "License"); * you may not use this file except in compliance
  * with the License. * You may obtain a copy of the License at * * http://www.apache.org/licenses/LICENSE-2.0 * *
  * Unless required by applicable law or agreed to in writing, software * distributed under the License is distributed
  * on an "AS IS" BASIS, * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. * See the License
  * for the specific language governing permissions and * limitations under the License. *
  */
package version.parser

import version.*
import version.errors.*

// scalafix:off
/** Hand-rolled Semantic Version 2.0.0 parser (no regex) to avoid runtime pattern compilation and to mirror the
  * performance style used in [[version.cli.core.parsing.KeywordParser]]. Fully compliant with the project specification
  * and SemVer structural rules enforced previously by the regex implementation.
  *
  * Accepted grammar (informal): core := numeric '.' numeric '.' numeric numeric := '0' | [1-9][0-9]* (no leading zeros)
  * prerelease := '-' ident ('.' ident)* build := '+' ident ('.' ident)* ident := (ALPHA / DIGIT / '-')+ (must not be
  * empty) numeric-ident (subset for pre-release identifiers comprised only of digits) obeys the same leading zero rule
  *
  * Additional library-specific mapping: pre-release identifiers are passed (after reconciliation of combined forms like
  * "RC10") to a contextual [[PreRelease.Resolver]].
  */
object VersionParser:

  // --- low-level helpers --------------------------------------------------

  private inline def isIdentChar(c: Char): Boolean =
    (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '-'

  private def parseUnsignedInt(segment: String, field: String): Either[ParseError, Int] =
    // Overflow-aware manual parse (no allocation beyond substring already captured)
    var i = 0
    val n = segment.length
    var acc: Long = 0L
    while i < n do
      val c = segment.charAt(i)
      val d = c - '0'
      acc = acc * 10 + d
      if acc > Int.MaxValue then return Left(InvalidNumericField(field, segment))
      i += 1
    Right(acc.toInt)

  private def isAllDigits(s: String): Boolean = s.nonEmpty && s.forall(c => c >= '0' && c <= '9')

  // Pattern-style reconciliation (manual to avoid regex); returns optional (prefix, digits)
  private def splitAlphaNumCombined(id: String): Option[(String, String)] =
    // Accept forms like RC10, alpha5, SNAPSHOT (no digits) not split.
    var i = 0
    val n = id.length
    if n == 0 then return None
    // prefix letters or '-'
    while i < n && ((id.charAt(i) >= 'A' && id.charAt(i) <= 'Z') || (id.charAt(i) >= 'a' && id.charAt(i) <= 'z') || id.charAt(i) == '-') do
      i += 1
    if i == 0 || i == n then return None // either no leading alpha/hyphen or no digits part
    // remaining must be digits
    var j = i
    while j < n && id.charAt(j).isDigit do j += 1
    if j == n && i > 0 && i < n then Some(id.substring(0, i) -> id.substring(i)) else None

  // --- main parse ---------------------------------------------------------

  def parse(input: String)(using resolver: PreRelease.Resolver): Either[ParseError, Version] =
    if input == null || input.isEmpty then return Left(InvalidVersionFormat(input))

    val len = input.length
    var i = 0

    // 1. major
    val majorStart = i
    while i < len && input.charAt(i).isDigit do i += 1
    if i == majorStart || i == len || input.charAt(i) != '.' then return Left(InvalidVersionFormat(input))
    val majorStr = input.substring(majorStart, i)
    if majorStr.length > 1 && majorStr.charAt(0) == '0' then return Left(InvalidVersionFormat(input))
    val majorInt = parseUnsignedInt(majorStr, "Major") match
      case Left(err) => return Left(err)
      case Right(v)  => v
    i += 1 // skip '.'

    // 2. minor
    val minorStart = i
    while i < len && input.charAt(i).isDigit do i += 1
    if i == minorStart || i == len || input.charAt(i) != '.' then return Left(InvalidVersionFormat(input))
    val minorStr = input.substring(minorStart, i)
    if minorStr.length > 1 && minorStr.charAt(0) == '0' then return Left(InvalidVersionFormat(input))
    val minorInt = parseUnsignedInt(minorStr, "Minor") match
      case Left(err) => return Left(err)
      case Right(v)  => v
    i += 1

    // 3. patch
    val patchStart = i
    while i < len && input.charAt(i).isDigit do i += 1
    if i == patchStart then return Left(InvalidVersionFormat(input))
    val patchStr = input.substring(patchStart, i)
    if patchStr.length > 1 && patchStr.charAt(0) == '0' then return Left(InvalidVersionFormat(input))
    val patchInt = parseUnsignedInt(patchStr, "Patch") match
      case Left(err) => return Left(err)
      case Right(v)  => v

    // 4. optional pre-release
    var preRelease: Option[PreRelease] = None
    if i < len && input.charAt(i) == '-' then
      i += 1
      if i >= len then return Left(InvalidVersionFormat(input)) // trailing '-'
      val ids = scala.collection.mutable.ListBuffer.empty[String]
      var identStart = i
      var continue = true
      while continue && i <= len do
        if i == len || input.charAt(i) == '+' || input.charAt(i) == '.' then
          val end = i
          if end == identStart then return Left(InvalidVersionFormat(input)) // empty identifier
          val id = input.substring(identStart, end)
          if !id.forall(isIdentChar) then return Left(InvalidVersionFormat(input))
          if isAllDigits(id) && id.length > 1 && id.charAt(0) == '0' then return Left(InvalidVersionFormat(input))
          ids += id
          if i == len || input.charAt(i) == '+' then continue = false
          else
            i += 1
            if i >= len then return Left(InvalidVersionFormat(input)) // dot at end
            identStart = i
        else i += 1
      end while
      val raw = ids.toList
      val reconciled = raw match
        case single :: Nil => splitAlphaNumCombined(single).map { case (a, b) => List(a, b) }.getOrElse(raw)
        case _             => raw
      // If reconciliation produced a numeric identifier with a leading zero (length>1), reject structurally per SemVer.
      reconciled match
        case _ :: num :: Nil if isAllDigits(num) && num.length > 1 && num.charAt(0) == '0' =>
          return Left(InvalidVersionFormat(input))
        case _ => ()
      resolver.map(reconciled) match
        case Some(pr) => preRelease = Some(pr)
        case None     => return Left(UnrecognizedPreRelease(reconciled))
    end if

    // 5. optional build metadata
    var buildMetadata: Option[BuildMetadata] = None
    if i < len && input.charAt(i) == '+' then
      i += 1
      if i >= len then return Left(InvalidVersionFormat(input)) // trailing '+'
      val ids = scala.collection.mutable.ListBuffer.empty[String]
      var identStart = i
      var continue = true
      while continue && i <= len do
        if i == len || input.charAt(i) == '.' then
          val end = i
          if end == identStart then return Left(InvalidVersionFormat(input)) // empty identifier (structural)
          val id = input.substring(identStart, end)
          if !id.forall(isIdentChar) then
            // invalid character -> wrap as InvalidVersionFormat (different message pattern preserved below)
            return Left(InvalidVersionFormat(s"Invalid build metadata: ${InvalidBuildMetadata(List(id)).message}"))
          ids += id
          if i == len then continue = false
          else
            i += 1
            if i >= len then return Left(InvalidVersionFormat(input)) // trailing '.'
            identStart = i
        else i += 1
      end while
      val list = ids.toList
      // All identifiers non-empty structurally. Validate collectively using BuildMetadata.from to reuse logic.
      BuildMetadata.from(list) match
        case Right(meta) => buildMetadata = Some(meta)
        case Left(err)   => return Left(InvalidVersionFormat(s"Invalid build metadata: ${err.message}"))
    end if

    // 6. must have consumed all characters
    if i != len then return Left(InvalidVersionFormat(input))

    Right(
      Version(
        MajorVersion.unsafe(majorInt),
        MinorVersion.unsafe(minorInt),
        PatchNumber.unsafe(patchInt),
        preRelease,
        buildMetadata
      )
    )
  end parse
end VersionParser
//scalafix:on
