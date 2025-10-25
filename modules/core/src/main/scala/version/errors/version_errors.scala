/** ************************************************************** Copyright © Shuwari Africa Ltd. * * This file is
  * licensed to you under the terms of the Apache * License Version 2.0 (the "License"); you may not use this * file
  * except in compliance with the License. You may obtain * a copy of the License at: * *
  * https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable law or agreed to in writing, *
  * software distributed under the License is distributed on an * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  * KIND, * either express or implied. See the License for the specific * language governing permissions and limitations
  * under the * License. *
  */
package version.errors

import scala.util.control.NoStackTrace

import version.PreReleaseClassifier
import version.PreReleaseNumber

sealed trait VersionError extends RuntimeException with NoStackTrace with Product with Serializable:
  def message: String
  override def getMessage: String = message

sealed trait InvalidNumericField extends VersionError:
  def value: Int

final case class InvalidMajorVersion(value: Int) extends InvalidNumericField:
  override def message: String = s"Major Version must be a non-negative number. Found: $value"

final case class InvalidMinorVersion(value: Int) extends InvalidNumericField:
  override def message: String = s"Minor Version must be a non-negative number. Found: $value"

final case class InvalidPatchNumber(value: Int) extends InvalidNumericField:
  override def message: String = s"Patch Number must be a non-negative number. Found: $value"

final case class InvalidPreReleaseNumber(value: Int) extends InvalidNumericField:
  override def message: String = s"Pre-Release Number must be a positive number. Found: $value"

sealed trait InvalidPreRelease extends VersionError:
  def classifier: PreReleaseClassifier

final case class InvalidNumberedPreRelease(classifier: PreReleaseClassifier) extends InvalidPreRelease:
  override def message: String =
    s"Only Snapshot PreRelease instances cannot have a number defined. Found: Classifier: '$classifier', PreRelease Number: Empty"

final case class InvalidSnapshotPreRelease(classifier: PreReleaseClassifier, number: PreReleaseNumber) extends InvalidPreRelease:
  override def message: String =
    s"Snapshot PreRelease instances cannot have a number defined. Found: Classifier: $classifier, PreRelease Number: $number"
