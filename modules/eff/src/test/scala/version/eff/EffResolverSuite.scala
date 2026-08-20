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
package version.eff

import boilerplate.effect.Eff
import boilerplate.effect.RetryPolicy
import cats.effect.unsafe.implicits.global
import munit.FunSuite

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration.*

import version.resolution.GitError
import version.resolution.GitRepository
import version.resolution.ResolutionConfig
import version.resolution.ResolutionError
import version.resolution.openRepository
import version.semver.SemVer
import version.testkit.TestRepoSupport

class EffResolverSuite extends FunSuite, TestRepoSupport:

  override val munitTimeout: Duration = 120.seconds

  private def config(repo: Path): ResolutionConfig[SemVer] = ResolutionConfig.default[SemVer](repo.toString)

  private def ran[E <: Throwable, A](eff: Eff[E, A])(using scala.reflect.TypeTest[Throwable, E]): Either[E, A] =
    eff.either.absolve.unsafeRunSync()

  // Records whether the bracket closed the repository it opened; every other operation is the real backend's.
  final private class Tracked(underlying: GitRepository) extends GitRepository:
    export underlying.{close as _, *}
    private val flag = AtomicBoolean(false)
    def close(): Unit =
      flag.set(true)
      underlying.close()
    def closed: Boolean = flag.get()

  final private class Opener(failures: Int):
    private val attempts = AtomicInteger(0)
    private val tracked = java.util.Collections.synchronizedList(java.util.ArrayList[Tracked]())

    def open(path: String): Either[GitError, GitRepository] =
      if attempts.getAndIncrement() < failures then Left(GitError.RepositoryNotFound(path))
      else
        openRepository(path).map { repo =>
          val wrapper = Tracked(repo)
          tracked.add(wrapper): Unit
          wrapper
        }

    def attempted: Int = attempts.get()
    def opened: Int = tracked.size
    def allClosed: Boolean =
      import scala.jdk.CollectionConverters.*
      tracked.asScala.forall(_.closed)

  test("the engine's answer arrives through the typed channel, suspended rather than computed on the caller") {
    withFreshRepo("eff-resolve") { repo =>
      checkout(repo, "v1.0.0")
      val opener = Opener(0)
      val result = ran(Resolver.resolve(config(repo), opener.open))
      assertEquals(result.map(_.show), Right("1.0.0"))
    }
  }

  test("the full result and the release history come through the same channel") {
    withFreshRepo("eff-all") { repo =>
      checkout(repo, "v1.0.0")
      val opener = Opener(0)
      assert(ran(Resolver.resolveAll(config(repo), opener.open)).isRight)
      val history = ran(Resolver.releaseHistory(config(repo), opener.open))
      assert(history.exists(_.nonEmpty), clues(history))
    }
  }

  test("a repository the caller holds is read without being closed") {
    withFreshRepo("eff-borrowed") { repo =>
      checkout(repo, "v1.0.0")
      val held = Tracked(openRepository(repo.toString).toOption.get)
      try
        assertEquals(ran(Resolver.resolve(config(repo), held)).map(_.show), Right("1.0.0"))
        assertEquals(held.closed, false)
      finally held.close()
    }
  }

  test("the bracket closes the repository it opened on the successful path") {
    withFreshRepo("eff-release-success") { repo =>
      checkout(repo, "v1.0.0")
      val opener = Opener(0)
      assert(ran(Resolver.resolve(config(repo), opener.open)).isRight)
      assertEquals(opener.opened, 1)
      assert(opener.allClosed, "the opened repository was not closed")
    }
  }

  test("the bracket closes the repository it opened on a typed failure") {
    withFreshRepo("eff-release-failure") { repo =>
      val opener = Opener(0)
      val failing = config(repo).copy(basisCommit = Some("no-such-revision"))
      val result = ran(Resolver.resolve(failing, opener.open))
      assert(result.isLeft, clues(result))
      assertEquals(opener.opened, 1)
      assert(opener.allClosed, "the opened repository was not closed after a typed failure")
    }
  }

  test("a failure to open is reported as a resolution failure rather than raised") {
    withFreshRepo("eff-open-failure") { repo =>
      val opener = Opener(1)
      val result = ran(Resolver.resolve(config(repo), opener.open))
      assertEquals(result, Left(ResolutionError.GitFailure(GitError.RepositoryNotFound(repo.toString))))
      assertEquals(opener.opened, 0)
    }
  }

  test("a typed failure is recovered on the channel rather than escaping the effect") {
    withFreshRepo("eff-recover") { repo =>
      val opener = Opener(1)
      val recovered = Resolver
        .resolve(config(repo), opener.open)
        .catchAll(error => Eff.succeed(SemVer.parseUnsafe("0.0.0-" + error.getClass.getSimpleName.toLowerCase)))
      assertEquals(recovered.absolve.unsafeRunSync().show, "0.0.0-gitfailure")
    }
  }

  test("absolving the channel raises the typed error itself at the end of the world") {
    withFreshRepo("eff-absolve") { repo =>
      val opener = Opener(1)
      val raised = intercept[ResolutionError.GitFailure](Resolver.resolve(config(repo), opener.open).absolve.unsafeRunSync())
      assertEquals(raised, ResolutionError.GitFailure(GitError.RepositoryNotFound(repo.toString)))
    }
  }

  test("a retried resolution re-acquires the repository each attempt and closes every one it opened") {
    withFreshRepo("eff-retry") { repo =>
      checkout(repo, "v1.0.0")
      val opener = Opener(2)
      val policy = RetryPolicy.constant(1.milli).withMaxAttempts(5)
      val result = ran(Eff.retry(Resolver.resolve(config(repo), opener.open), policy))
      assertEquals(result.map(_.show), Right("1.0.0"))
      assertEquals(opener.attempted, 3)
      assertEquals(opener.opened, 1)
      assert(opener.allClosed, "the opened repository was not closed")
    }
  }

  test("a repository resource yields a usable repository and reports a bad path on the channel") {
    withFreshRepo("eff-resource") { repo =>
      val head = ran(Repository.open(repo.toString).use(r => Eff.blocking(r.head)))
      assert(head.exists(_.isDefined), clues(head))
      val missing = ran(Repository.open(repo.resolve("absent").toString).use(r => Eff.blocking(r.head)))
      assert(missing.isLeft, clues(missing))
    }
  }

  test("a repository resource discovers the repository above a nested directory") {
    withFreshRepo("eff-discover") { repo =>
      val nested = repo.resolve("a/b/c")
      Files.createDirectories(nested): Unit
      val found = ran(Repository.discover(nested.toString).use(r => Eff.blocking(Right(r.gitDir))))
      assert(found.exists(_.nonEmpty), clues(found))
    }
  }

end EffResolverSuite
