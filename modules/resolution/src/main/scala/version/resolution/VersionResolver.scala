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
package version.resolution

import scala.annotation.targetName

import version.Formatter
import version.ResolvableScheme
import version.VersionArithmetic
import version.VersionScheme

/** Every scheme-typed piece a version pipeline needs, held behind a single `V` so that a scheme cannot be paired with
  * a tag parser, formatter, or capability instance belonging to another.
  *
  * Instances may be constructed via [[VersionResolver$ VersionResolver]].
  */
final case class VersionResolver[V](
  scheme: VersionScheme[V],
  arithmetic: VersionArithmetic[V],
  workflow: ResolvableScheme[V],
  tagParser: String => Option[V],
  formatter: Option[Formatter[V]]
)

/** Provides factory methods and combinators for [[VersionResolver]]. */
object VersionResolver:

  given [V]: CanEqual[VersionResolver[V], VersionResolver[V]] = CanEqual.derived

  /** Reads a tag name as a version, tolerating the conventional `v` or `V` prefix that Git tags carry and the scheme
    * does not.
    */
  def defaultTagParser[V](using scheme: VersionScheme[V]): String => Option[V] =
    name =>
      val raw = if name.startsWith("v") || name.startsWith("V") then name.drop(1) else name
      scheme.parse(raw).toOption

  /** The resolver over the contextual capabilities: tags are read by [[defaultTagParser]], and nothing is rendered
    * other than canonically.
    */
  def withDefaults[V](using
    scheme: VersionScheme[V],
    arithmetic: VersionArithmetic[V],
    workflow: ResolvableScheme[V]
  ): VersionResolver[V] =
    VersionResolver(
      scheme = scheme,
      arithmetic = arithmetic,
      workflow = workflow,
      tagParser = defaultTagParser[V],
      formatter = None
    )

  inline def withTagParser[V](r: VersionResolver[V], parser: String => Option[V]): VersionResolver[V] =
    r.withTagParser(parser)

  inline def withFormatter[V](r: VersionResolver[V], f: Formatter[V]): VersionResolver[V] =
    r.withFormatter(f)

  extension [V](r: VersionResolver[V])
    @targetName("ext_withTagParser")
    inline def withTagParser(parser: String => Option[V]): VersionResolver[V] = r.copy(tagParser = parser)

    @targetName("ext_withFormatter")
    inline def withFormatter(f: Formatter[V]): VersionResolver[V] = r.copy(formatter = Some(f))

    inline def withoutFormatter: VersionResolver[V] = r.copy(formatter = None)
end VersionResolver
