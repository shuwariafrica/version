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
package version.semver

import scala.util.boundary
import scala.util.boundary.break

import version.errors.*

// scalafix:off
// Index-arithmetic walk with boundary/break: avoids the per-component closure and Either allocation a chained flatMap would produce.

/** SemVer 2.0.0 string parser.
  *
  * Parses version strings into [[ParsedVersion]] named tuples. Handles optional `v`/`V` prefix, core version
  * (`MAJOR.MINOR.PATCH`), pre-release identifiers (mapped via [[PreRelease.Resolver]]), build metadata, and combined
  * classifier forms (e.g., `rc3` -> `rc.3`).
  *
  * Package-private to `version`. External consumers should use [[SemVer$.parse SemVer.parse]].
  */
private[version] object Parser:

  type ParsedVersion = (
    major: Major,
    minor: Minor,
    patch: Patch,
    preRelease: Option[PreRelease],
    metadata: Option[Metadata]
  )

  private inline def isIdentChar(c: Char): Boolean =
    (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '-'

  private def parseUnsignedInt(segment: String, field: String): Either[ParseError, Int] =
    boundary:
      var i = 0
      val n = segment.length
      var acc: Long = 0L
      while i < n do
        val c = segment.charAt(i)
        val d = c - '0'
        acc = acc * 10 + d
        if acc > Int.MaxValue then break(Left(InvalidNumericField(field, segment)))
        i += 1
      Right(acc.toInt)

  private def isAllDigits(s: String): Boolean = s.nonEmpty && s.forall(c => c >= '0' && c <= '9')

  private def splitAlphaNumCombined(id: String): Option[(String, String)] =
    boundary:
      var i = 0
      val n = id.length
      if n == 0 then break(None)
      while i < n && ((id.charAt(i) >= 'A' && id.charAt(i) <= 'Z') || (id.charAt(i) >= 'a' && id.charAt(i) <= 'z') || id.charAt(i) == '-')
      do i += 1
      if i == 0 || i == n then break(None)
      var j = i
      while j < n && id.charAt(j).isDigit do j += 1
      if j == n && i > 0 && i < n then Some(id.substring(0, i) -> id.substring(i)) else None

  def parse(input: String)(using resolver: PreRelease.Resolver): Either[ParseError, ParsedVersion] =
    boundary:
      if input == null || input.isEmpty then break(Left(InvalidVersionFormat(input)))

      val normalised =
        if input.length > 1 && (input.charAt(0) == 'v' || input.charAt(0) == 'V') then input.substring(1)
        else input

      if normalised.isEmpty then break(Left(InvalidVersionFormat(input)))

      val len = normalised.length
      var i = 0

      // 1. major
      val majorStart = i
      while i < len && normalised.charAt(i).isDigit do i += 1
      if i == majorStart || i == len || normalised.charAt(i) != '.' then break(Left(InvalidVersionFormat(input)))
      val majorStr = normalised.substring(majorStart, i)
      if majorStr.length > 1 && majorStr.charAt(0) == '0' then break(Left(InvalidVersionFormat(input)))
      val majorInt = parseUnsignedInt(majorStr, "Major") match
        case Left(err) => break(Left(err))
        case Right(v)  => v
      i += 1

      // 2. minor
      val minorStart = i
      while i < len && normalised.charAt(i).isDigit do i += 1
      if i == minorStart || i == len || normalised.charAt(i) != '.' then break(Left(InvalidVersionFormat(input)))
      val minorStr = normalised.substring(minorStart, i)
      if minorStr.length > 1 && minorStr.charAt(0) == '0' then break(Left(InvalidVersionFormat(input)))
      val minorInt = parseUnsignedInt(minorStr, "Minor") match
        case Left(err) => break(Left(err))
        case Right(v)  => v
      i += 1

      // 3. patch
      val patchStart = i
      while i < len && normalised.charAt(i).isDigit do i += 1
      if i == patchStart then break(Left(InvalidVersionFormat(input)))
      val patchStr = normalised.substring(patchStart, i)
      if patchStr.length > 1 && patchStr.charAt(0) == '0' then break(Left(InvalidVersionFormat(input)))
      val patchInt = parseUnsignedInt(patchStr, "Patch") match
        case Left(err) => break(Left(err))
        case Right(v)  => v

      // 4. optional pre-release
      var preRelease: Option[PreRelease] = None
      if i < len && normalised.charAt(i) == '-' then
        i += 1
        if i >= len then break(Left(InvalidVersionFormat(input)))
        val ids = scala.collection.mutable.ListBuffer.empty[String]
        var identStart = i
        var continue = true
        while continue && i <= len do
          if i == len || normalised.charAt(i) == '+' || normalised.charAt(i) == '.' then
            val end = i
            if end == identStart then break(Left(InvalidVersionFormat(input)))
            val id = normalised.substring(identStart, end)
            if !id.forall(isIdentChar) then break(Left(InvalidVersionFormat(input)))
            if isAllDigits(id) && id.length > 1 && id.charAt(0) == '0' then break(Left(InvalidVersionFormat(input)))
            ids += id
            if i == len || normalised.charAt(i) == '+' then continue = false
            else
              i += 1
              if i >= len then break(Left(InvalidVersionFormat(input)))
              identStart = i
          else i += 1
        end while
        val raw = ids.toList
        val reconciled = raw match
          case single :: Nil => splitAlphaNumCombined(single).map { case (a, b) => List(a, b) }.getOrElse(raw)
          case _             => raw
        reconciled match
          case _ :: num :: Nil if isAllDigits(num) && num.length > 1 && num.charAt(0) == '0' =>
            break(Left(InvalidVersionFormat(input)))
          case _ => ()
        reconciled.resolve match
          case Some(pr) => preRelease = Some(pr)
          case None     => break(Left(UnrecognisedIdentifier(reconciled)))
      end if

      // 5. optional build metadata
      var metadata: Option[Metadata] = None
      if i < len && normalised.charAt(i) == '+' then
        i += 1
        if i >= len then break(Left(InvalidVersionFormat(input)))
        val ids = scala.collection.mutable.ListBuffer.empty[String]
        var identStart = i
        var continue = true
        while continue && i <= len do
          if i == len || normalised.charAt(i) == '.' then
            val end = i
            if end == identStart then break(Left(InvalidVersionFormat(input)))
            val id = normalised.substring(identStart, end)
            if !id.forall(isIdentChar) then
              break(Left(InvalidVersionFormat(s"Invalid build metadata: ${InvalidMetadata(List(id)).message}")))
            ids += id
            if i == len then continue = false
            else
              i += 1
              if i >= len then break(Left(InvalidVersionFormat(input)))
              identStart = i
          else i += 1
        end while
        val list = ids.toList
        Metadata.from(list) match
          case Right(meta) => metadata = Some(meta)
          case Left(err)   => break(Left(InvalidVersionFormat(s"Invalid build metadata: ${err.message}")))
      end if

      // 6. must have consumed all characters
      if i != len then break(Left(InvalidVersionFormat(input)))

      Right(
        (
          major = Major.fromUnsafe(majorInt),
          minor = Minor.fromUnsafe(minorInt),
          patch = Patch.fromUnsafe(patchInt),
          preRelease = preRelease,
          metadata = metadata
        )
      )
  end parse
end Parser
// scalafix:on
