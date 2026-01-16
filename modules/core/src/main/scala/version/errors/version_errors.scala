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
package version.errors

import scala.util.control.NoStackTrace

import version.PreReleaseClassifier
import version.PreReleaseNumber

/** Base trait for all errors produced by the version library. Modelled as an ADT and extending [[RuntimeException]]
  * with [[NoStackTrace]] for compatibility with both functional error handling (`Either`) and exception throwing in
  * critical validation paths.
  */
sealed trait VersionError extends RuntimeException with NoStackTrace derives CanEqual:
  def message: String
  final override def getMessage: String = message

// --- Validation Errors (for programmatic creation) ---

/** Base trait for errors related to invalid numeric values during component creation. Uses `transparent` to avoid
  * unnecessary type widening in signatures where specific errors are expected.
  */
sealed transparent trait InvalidComponent extends VersionError:
  def value: Int
  def componentName: String
  def requirement: String
  override def message: String = s"$componentName must be $requirement. Found: $value"

final case class InvalidMajorVersion(value: Int) extends InvalidComponent:
  inline val componentName = "Major Version"
  inline val requirement = "a non-negative number (>= 0)"

final case class InvalidMinorVersion(value: Int) extends InvalidComponent:
  inline val componentName = "Minor Version"
  inline val requirement = "a non-negative number (>= 0)"

final case class InvalidPatchNumber(value: Int) extends InvalidComponent:
  inline val componentName = "Patch Number"
  inline val requirement = "a non-negative number (>= 0)"

final case class InvalidPreReleaseNumber(value: Int) extends InvalidComponent:
  inline val componentName = "Pre-Release Number"
  inline val requirement = "a positive number (>= 1)"

/** Errors related to inconsistencies between [[PreReleaseClassifier]] and [[PreReleaseNumber]]. */
sealed transparent trait InvalidPreReleaseCombination extends VersionError

/** Occurs when a classifier that requires a number (e.g., Alpha) is used without one. */
final case class MissingPreReleaseNumber(classifier: PreReleaseClassifier) extends InvalidPreReleaseCombination:
  override def message: String =
    s"The classifier '$classifier' requires a pre-release number, but none was provided."

/** Occurs when a classifier that forbids a number (e.g., Snapshot) is used with one. */
final case class UnexpectedPreReleaseNumber(classifier: PreReleaseClassifier, number: PreReleaseNumber)
    extends InvalidPreReleaseCombination:
  override def message: String =
    s"The classifier '$classifier' cannot have a pre-release number. Found: $number"

/** Occurs when build metadata identifiers contain invalid characters or are empty, violating SemVer 2.0.0. */
final case class InvalidMetadata(identifiers: List[String]) extends VersionError:
  override def message: String =
    s"Build metadata identifiers must be non-empty and contain only ASCII alphanumerics and hyphens [0-9A-Za-z-]. Found: '${identifiers.mkString(".")}'"

// --- Pre-Release Transition Errors (for typed pre-release operations) ---

/** Occurs when attempting to use a pre-release transition on a version that is not a pre-release. */
final case class NotAPreReleaseVersion() extends VersionError:
  override def message: String =
    "Operation requires a pre-release version, but the version has no pre-release component."

/** Occurs when attempting to transition to a pre-release classifier with a lower precedence. */
final case class InvalidPreReleaseTransition(from: PreReleaseClassifier, to: PreReleaseClassifier) extends VersionError:
  override def message: String = s"Cannot transition pre-release from '$from' to lower precedence '$to'."

/** Occurs when an operation requires a versioned classifier but a non-versioned one (e.g., Snapshot) is provided. */
final case class ClassifierNotVersioned(classifier: PreReleaseClassifier) extends VersionError:
  override def message: String = s"Classifier '$classifier' is not versioned and cannot be used in this operation."

// --- Parsing Errors (for creation from strings) ---

/** Base trait for errors that occur during the parsing of version strings. */
sealed transparent trait ParseError extends VersionError

/** Occurs when the input string does not conform to the Semantic Versioning 2.0.0 format specification. */
final case class InvalidVersionFormat(input: String) extends ParseError:
  override def message: String = s"The input string '$input' is not a valid Semantic Version 2.0.0 format."

/** Occurs when a numeric component in the version string cannot be parsed as an integer or is out of range (e.g.
  * exceeds Int.MaxValue).
  */
final case class InvalidNumericField(field: String, value: String) extends ParseError:
  override def message: String =
    s"The value '$value' is invalid for the $field field. It must be a valid, non-negative integer within the standard integer range."

/** Occurs when the pre-release identifier is structurally valid SemVer but is not recognized by the configured
  * [[version.PreRelease.Resolver]].
  */
final case class UnrecognizedPreRelease(identifiers: List[String]) extends ParseError:
  override def message: String =
    s"The pre-release identifiers '${identifiers.mkString(".")}' are not recognized by the current mapping configuration."
