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

import version.resolution.domain.CommitSha

/** Runs the shared [[GitRepositorySuite]] against the JGit backend. */
class JvmGitRepositorySuite extends GitRepositorySuite, JvmGitRepositoryTestSupport:

  test("a backend failure carries the exception JGit raised"):
    withFreshRepo("jvm-backend-cause"): repo =>
      val blob = git(repo, "rev-parse", "HEAD:README.md").trim
      val session = openTestRepository(repo)
      try
        session.loadCommit(CommitSha(blob)) match
          case Left(GitError.BackendFailure(_, cause)) =>
            assert(cause.isDefined, "a JGit failure should carry what it raised")
          case other => fail(s"expected a backend failure for a blob, got $other")
      finally session.close()
