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

import version.errors.VersionError

/** The version a range of commits asks for, and the reason each directive the scheme refused was refused.
  *
  * Instances may be constructed via [[Derivation$ Derivation]].
  */
final case class Derivation[V](target: V, discarded: List[VersionError])

/** Provides the target calculation that yields a [[Derivation]]. */
object Derivation:

  given [V]: CanEqual[Derivation[V], Derivation[V]] = CanEqual.derived

  /** The version `directives` advance `base` to: one they name outright where the tags admit it, otherwise the highest
    * version any of their requests reaches, otherwise the scheme's own policy for a range that asked for nothing.
    *
    * A directive the scheme refuses is discarded rather than fatal, and its error returned beside the target.
    *
    * @param reachable
    *   the highest version tagged behind `base`, which a named version may not regress below.
    * @param repository
    *   every version tagged anywhere, which bounds a named version whether or not `reachable` exists.
    */
  def target[V](base: V, directives: List[Directive], reachable: Option[V], repository: List[V])(using
    scheme: VersionScheme[V],
    arithmetic: VersionArithmetic[V],
    workflow: ResolvableScheme[V]
  ): Derivation[V] =
    val named = directives.collect { case Directive.Target(raw) => scheme.parse(raw) }
    val requested = directives.collect { case Directive.Emit(request) => arithmetic(base, request) }
    val admitted = admit(named.collect { case Right(v) => v }, reachable, repository)
    val highest = requested.collect { case Right(v) => v }.maxOption(using scheme.precedence)
    Derivation(
      admitted.orElse(highest).getOrElse(workflow.defaultTarget(base)),
      (named ++ requested).collect { case Left(error) => error }
    )

  /** The version `directives` set a project that has released nothing to: one they name outright where the tags admit
    * it, otherwise the version the scheme starts at.
    *
    * Requests are not read: with no release behind it, a project has nothing to advance from.
    *
    * @param repository
    *   every version tagged anywhere, which bounds a named version.
    */
  def target[V](directives: List[Directive], repository: List[V])(using
    scheme: VersionScheme[V],
    workflow: ResolvableScheme[V]
  ): Derivation[V] =
    val named = directives.collect { case Directive.Target(raw) => scheme.parse(raw) }
    Derivation(
      admit(named.collect { case Right(v) => v }, None, repository).getOrElse(workflow.initialVersion),
      named.collect { case Left(error) => error }
    )

  // A named version must outrank every tag that could contradict it. It must exceed a release, but may equal the
  // release a pre-release is working towards, and the repository as a whole applies the same pair of bounds as the
  // reachable history does - so a release cut on another branch cannot be reissued from this one.
  private def admit[V](named: List[V], reachable: Option[V], repository: List[V])(using
    scheme: VersionScheme[V]
  ): Option[V] =
    given Ordering[V] = scheme.precedence
    import scala.math.Ordering.Implicits.infixOrderingOps

    val reachableRelease = reachable.filter(_.stable)
    val repositoryRelease = repository.filter(_.stable).maxOption
    val repositoryHighest = repository.maxOption

    def admissible(candidate: V): Boolean =
      reachableRelease.forall(candidate > _) &&
        reachable.forall(v => v.stable || candidate >= v.release) && {
          (repositoryRelease, repositoryHighest) match
            case (Some(release), _)    => candidate > release
            case (None, Some(highest)) => candidate >= highest.release
            case (None, None)          => true
        }

    named.map(_.release).filter(admissible).maxOption

end Derivation
