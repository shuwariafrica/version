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

import munit.FunSuite

/** Unit tests for [[MetadataBuilder]]. */
class MetadataBuilderSuite extends FunSuite:

  test("assemble includes all metadata fields in correct order"):
    val meta = MetadataBuilder.assemble(
      branchOverride = None,
      branchDetected = Some("main"),
      abbreviatedSha = "abc1234",
      commitCount = 5,
      prNumber = Some(42),
      isDirty = true
    )
    assertEquals(meta.branch, Some("main"))
    assertEquals(meta.commitSha, Some("abc1234"))
    assertEquals(meta.commitCount, Some(5))
    assertEquals(meta.prNumber, Some(42))
    assertEquals(meta.isDirty, true)

  test("branch override takes precedence over detected"):
    val meta = MetadataBuilder.assemble(
      branchOverride = Some("override-branch"),
      branchDetected = Some("detected-branch"),
      abbreviatedSha = "abc1234",
      commitCount = 0,
      prNumber = None,
      isDirty = false
    )
    assertEquals(meta.branch, Some("override-branch"))

  test("detached HEAD uses 'detached' when no branch detected"):
    val meta = MetadataBuilder.assemble(
      branchOverride = None,
      branchDetected = None,
      abbreviatedSha = "abc1234",
      commitCount = 0,
      prNumber = None,
      isDirty = false
    )
    assertEquals(meta.branch, Some("detached"))

  test("branch normalisation: lowercase, replace invalid chars, collapse hyphens"):
    val meta = MetadataBuilder.assemble(
      branchOverride = None,
      branchDetected = Some("Feature/ABC_123!!"),
      abbreviatedSha = "abc1234",
      commitCount = 0,
      prNumber = None,
      isDirty = false
    )
    assertEquals(meta.branch, Some("feature-abc-123"))

  test("branch normalisation: empty becomes 'detached'"):
    val meta = MetadataBuilder.assemble(
      branchOverride = None,
      branchDetected = Some("///"),
      abbreviatedSha = "abc1234",
      commitCount = 0,
      prNumber = None,
      isDirty = false
    )
    assertEquals(meta.branch, Some("detached"))

  test("isDirty flag propagated"):
    val clean = MetadataBuilder.assemble(None, Some("main"), "abc", 0, None, isDirty = false)
    val dirty = MetadataBuilder.assemble(None, Some("main"), "abc", 0, None, isDirty = true)
    assertEquals(clean.isDirty, false)
    assertEquals(dirty.isDirty, true)
end MetadataBuilderSuite
