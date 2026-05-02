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

import version.DevelopmentMetadata

// scalafix:off
/** Assembles [[DevelopmentMetadata]] from pre-gathered resolution values.
  *
  * Pure function - no Git dependency. The resolver gathers the values via [[GitRepository]], then passes them here for
  * assembly.
  */
object MetadataBuilder:

  /** Assemble development metadata from pre-gathered values.
    *
    * Branch normalisation: lowercase, replace non-`[0-9a-z-]` with `-`, collapse consecutive `-`, trim leading/trailing
    * `-`, empty becomes `"detached"`.
    */
  def assemble(
    branchOverride: Option[String],
    branchDetected: Option[String],
    abbreviatedSha: String,
    commitCount: Int,
    prNumber: Option[Int],
    isDirty: Boolean
  ): DevelopmentMetadata =
    val branch = branchOverride
      .map(normalise)
      .orElse(branchDetected.map(normalise))
      .getOrElse("detached")
    DevelopmentMetadata(
      branch = Some(branch),
      commitSha = Some(abbreviatedSha),
      commitCount = Some(commitCount),
      prNumber = prNumber,
      isDirty = isDirty
    )

  /** Branch normalisation per spec ss7. */
  private[resolution] def normalise(name: String): String =
    // Hotpath: single-pass normalisation avoids regex/replaceAll intermediate string allocations.
    val lower = name.toLowerCase
    val sb = new StringBuilder(lower.length)
    var prevHyphen = false
    lower.foreach { ch =>
      val ok = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '-'
      if ok then
        if ch == '-' then
          if !prevHyphen then
            sb.append('-'); prevHyphen = true
        else
          sb.append(ch); prevHyphen = false
      else if !prevHyphen then
        sb.append('-'); prevHyphen = true
    }
    var s = sb.result()
    s = s.dropWhile(_ == '-')
    s = s.reverse.dropWhile(_ == '-').reverse
    if s.isEmpty then "detached" else s
end MetadataBuilder
// scalafix:on
