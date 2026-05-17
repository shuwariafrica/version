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

/** Bundles every scheme-typed piece of configuration the version pipeline needs: the [[ResolvableScheme]] instance,
  * a Git tag parser, and the optional rendering [[Formatter]].
  *
  * Bundling guarantees the three pieces share a single `V` parameter at the type level, so consumers cannot pair a
  * scheme with a mismatched tag parser or formatter.
  *
  * Instances may be constructed via [[VersionResolver$ VersionResolver]].
  */
final case class VersionResolver[V <: Version](
  scheme: ResolvableScheme[V],
  tagParser: String => Option[V],
  formatter: Option[Formatter[V]]
)

/** Provides factory methods and combinators for [[VersionResolver]]. */
object VersionResolver:
  import scala.annotation.targetName

  given [V <: Version]: CanEqual[VersionResolver[V], VersionResolver[V]] = CanEqual.derived

  /** Default resolver for the in-scope scheme: scheme-supplied parsing with the conventional `v`/`V` tag prefix
    * stripped, and no rendering formatter (consumers fall back to [[Version!.show]]).
    */
  def withDefaults[V <: Version](using scheme: ResolvableScheme[V]): VersionResolver[V] =
    VersionResolver(
      scheme = scheme,
      tagParser = name =>
        val raw = if name.startsWith("v") || name.startsWith("V") then name.drop(1) else name
        scheme.parse(raw).toOption
      ,
      formatter = None
    )

  /** Companion alias for the multi-parameter [[withTagParser]] extension. */
  inline def withTagParser[V <: Version](r: VersionResolver[V], parser: String => Option[V]): VersionResolver[V] =
    r.withTagParser(parser)

  /** Companion alias for the multi-parameter [[withFormatter]] extension. */
  inline def withFormatter[V <: Version](r: VersionResolver[V], f: Formatter[V]): VersionResolver[V] =
    r.withFormatter(f)

  extension [V <: Version](r: VersionResolver[V])
    @targetName("ext_withTagParser")
    inline def withTagParser(parser: String => Option[V]): VersionResolver[V] = r.copy(tagParser = parser)

    @targetName("ext_withFormatter")
    inline def withFormatter(f: Formatter[V]): VersionResolver[V] = r.copy(formatter = Some(f))

    inline def withoutFormatter: VersionResolver[V] = r.copy(formatter = None)
end VersionResolver
