/****************************************************************************
 * Copyright 2023 Shuwari Africa Ltd.                                       *
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
package version.cli.core

import munit.FunSuite

import version.*
import version.cli.core.domain.*

final class BuildMetadataBuilderSuite extends FunSuite with TestRepoSupport:

  test("Branch normalisation via Resolver includes 'branch<normalised>' and canonical ordering") {
    withFreshRepo("metadata-branch") {
      repo =>
        // Create a funky branch name and commit
        os.proc("git", "checkout", "-b", "Feature/ABC_123").call(cwd = repo, check = true): Unit
        os.write.append(repo / "README.md", "\nfeature work\n"): Unit
        os.proc("git", "add", "README.md").call(cwd = repo, check = true): Unit
        os.proc("git", "commit", "--no-gpg-sign", "-m", "version: patch").call(cwd = repo, check = true): Unit

      val result =
        VersionCliCore.resolve(
          CliConfig(repo = repo, basisCommit = "HEAD", prNumber = Some(42), branchOverride = None, shaLength = 12, verbose = false))
      assert(result.isRight)
      val v = result.toOption.get
      val meta = v.buildMetadata.map(_.show).getOrElse("")
      // Canonical order: pr, branch, commits, sha, dirty
      assert(meta.startsWith("+pr42.branchfeature-abc-123.commits"), s"metadata was: $meta")
      assert(meta.contains(".sha"), s"metadata was: $meta")
    }
  }

  test("SHA length bounds enforced") {
    withFreshRepo("sha-bounds") {
      repo =>
        // Force development mode (not exact tag resolution)
        os.write.append(repo / "README.md", "\nDIRTY\n"): Unit
        val tooShort =
          VersionCliCore.resolve(
            CliConfig(repo = repo, basisCommit = "HEAD", prNumber = None, branchOverride = None, shaLength = 6, verbose = false))
        assert(tooShort.isLeft)

      val ok = VersionCliCore.resolve(
        CliConfig(repo = repo, basisCommit = "HEAD", prNumber = None, branchOverride = None, shaLength = 40, verbose = false))
      assert(ok.isRight)
    }
  }

  test("Branch normalisation edge cases and detached head -> branchdetached") {
    withFreshRepo("branch-detached") { repo =>
      // Create a branch with odd characters
      os.proc("git", "checkout", "-b", "Rêf/Weird__Name!!!").call(cwd = repo, check = true): Unit
      os.write.append(repo / "README.md", "\nweird\n"): Unit
      os.proc("git", "add", "README.md").call(cwd = repo, check = true): Unit
      os.proc("git", "commit", "--no-gpg-sign", "-m", "version: patch").call(cwd = repo, check = true): Unit

      val res1 =
        VersionCliCore.resolve(
          CliConfig(repo = repo, basisCommit = "HEAD", prNumber = None, branchOverride = None, shaLength = 12, verbose = false))
      assert(res1.isRight)
      val v1 = res1.toOption.get
      val m1 = v1.buildMetadata.map(_.show).getOrElse("")
      assert(m1.contains("+branchr-f-weird-name"))

      // Detached HEAD should render branchdetached
      val head = os.proc("git", "rev-parse", "HEAD").call(cwd = repo, check = true).out.text().trim
      // Switch to detached HEAD quietly; advice is disabled in script, but keep -q for safety
      os.proc("git", "checkout", "-q", head).call(cwd = repo, check = true): Unit
      val res2 =
        VersionCliCore.resolve(
          CliConfig(repo = repo, basisCommit = "HEAD", prNumber = None, branchOverride = None, shaLength = 12, verbose = false))
      assert(res2.isRight)
      val v2 = res2.toOption.get
      val m2 = v2.buildMetadata.map(_.show).getOrElse("")
      assert(m2.contains("+branchdetached"))
    }
  }

  test("commits<number> excludes merge commits and counts along first-parent from base") {
    withFreshRepo("meta-commits") { repo =>
      // On main tip, base should be v2.0.0 and after that we have a merge commit (excluded) and one post-merge commit
      val res = VersionCliCore.resolve(
        CliConfig(repo = repo, basisCommit = "HEAD", prNumber = None, branchOverride = None, shaLength = 12, verbose = false))
      assert(res.isRight)
      val v = res.toOption.get
      val ids = v.buildMetadata.toList.flatMap(_.identifiers)
      val commitsId = ids.find(_.startsWith("commits")).getOrElse("")
      val n = commitsId.stripPrefix("commits")
      assertEquals(n, "1", clues(ids.mkString(",")))
    }
  }
end BuildMetadataBuilderSuite
