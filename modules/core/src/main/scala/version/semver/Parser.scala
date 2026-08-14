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

import scala.annotation.tailrec

import version.errors.InvalidNumericField
import version.errors.InvalidVersionFormat
import version.errors.ParseError

private[semver] object Parser:

  def parse(input: String): Either[ParseError, SemVer] =
    val body =
      if input.length > 1 && (input.charAt(0) == 'v' || input.charAt(0) == 'V') then input.substring(1)
      else input
    if body.isEmpty then Left(InvalidVersionFormat(input))
    else
      val (beforeBuild, build) = section(body, '+')
      val (core, pre) = section(beforeBuild, '-')
      coreComponents(core, input).flatMap { (major, minor, patch) =>
        for
          preRelease <- optional(pre)(ids => PreRelease.of(ids))
          metadata <- optional(build)(ids => Metadata.of(ids))
        yield SemVer(Major.wrap(major), Minor.wrap(minor), Patch.wrap(patch), preRelease, metadata)
      }

  private def coreComponents(core: String, input: String): Either[ParseError, (Long, Long, Long)] =
    segments(core) match
      case major :: minor :: patch :: Nil =>
        for
          a <- number(major, "Major", input)
          b <- number(minor, "Minor", input)
          c <- number(patch, "Patch", input)
        yield (a, b, c)
      case _ => Left(InvalidVersionFormat(input))

  private def number(segment: String, field: String, input: String): Either[ParseError, Long] =
    if !Identifier.numeric(segment) || Identifier.leadingZero(segment) then Left(InvalidVersionFormat(input))
    else segment.toLongOption.toRight(InvalidNumericField(field, segment))

  private def optional[A](raw: Option[String])(
    construct: List[String] => Either[ParseError, A]
  ): Either[ParseError, Option[A]] =
    raw match
      case None       => Right(None)
      case Some(part) => construct(segments(part)).map(Some(_))

  private def section(value: String, separator: Char): (String, Option[String]) =
    value.indexOf(separator.toInt) match
      case -1 => (value, None)
      case i  => (value.substring(0, i), Some(value.substring(i + 1)))

  // Empty segments are retained rather than dropped, so that a leading, doubled, or trailing separator reaches
  // identifier validation and is rejected there.
  def segments(value: String): List[String] =
    @tailrec def loop(from: Int, acc: List[String]): List[String] =
      value.indexOf('.'.toInt, from) match
        case -1 => (value.substring(from) :: acc).reverse
        case i  => loop(i + 1, value.substring(from, i) :: acc)
    loop(0, Nil)

end Parser
