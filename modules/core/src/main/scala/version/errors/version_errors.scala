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
package version.errors

import scala.util.control.NoStackTrace

/** Base trait for all errors produced by the version library.
  *
  * Extends [[RuntimeException]] with [[NoStackTrace]] for compatibility with both functional error handling (`Either`)
  * and exception-based validation paths.
  */
trait VersionError extends RuntimeException with NoStackTrace:
  def message: String
  final override def getMessage: String = message

object VersionError:
  given CanEqual[VersionError, VersionError] = CanEqual.derived

// --- Component Validation ---

/** A version component value is outside the valid range for that component.
  *
  * Common across all versioning schemes. The [[componentName]] identifies which component failed.
  */
final case class InvalidComponent(value: Int, componentName: String, requirement: String) extends VersionError:
  override def message: String = s"$componentName must be $requirement. Found: $value"

// --- Qualifier/Pre-Release Combination ---

/** Errors related to inconsistencies between a qualifier classifier and its associated number. */
sealed trait InvalidQualifierCombination extends VersionError

/** A classifier that requires a number was used without one. */
final case class MissingQualifierNumber(classifier: String) extends InvalidQualifierCombination:
  override def message: String =
    s"The classifier '$classifier' requires a qualifier number, but none was provided."

/** A classifier that forbids a number was used with one. */
final case class UnexpectedQualifierNumber(classifier: String, number: Int) extends InvalidQualifierCombination:
  override def message: String =
    s"The classifier '$classifier' cannot have a qualifier number. Found: $number"

// --- Metadata ---

/** Build metadata identifiers contain invalid characters or are empty. */
final case class InvalidMetadata(identifiers: List[String]) extends VersionError:
  override def message: String =
    s"Build metadata identifiers must be non-empty and contain only ASCII alphanumerics and hyphens [0-9A-Za-z-]. Found: '${identifiers.mkString(".")}'"

// --- Qualifier Operation ---

/** An operation requires a versioned classifier but a non-versioned one was provided. */
final case class ClassifierNotVersioned(classifier: String) extends VersionError:
  override def message: String = s"Classifier '$classifier' is not versioned and cannot be used in this operation."

// --- Parsing ---

/** Base for errors that occur during the parsing of version strings. */
sealed trait ParseError extends VersionError

/** The input string does not conform to the expected version format. */
final case class InvalidVersionFormat(input: String) extends ParseError:
  override def message: String = s"The input string '$input' is not a valid version format."

/** A numeric field in the version string cannot be parsed as an integer or is out of range. */
final case class InvalidNumericField(field: String, value: String) extends ParseError:
  override def message: String =
    s"The value '$value' is invalid for the $field field. It must be a valid, non-negative integer within the standard integer range."

/** A qualifier identifier is structurally valid but not recognised by the configured resolver. */
final case class UnrecognisedIdentifier(identifiers: List[String]) extends ParseError:
  override def message: String =
    s"The identifiers '${identifiers.mkString(".")}' are not recognised by the current mapping configuration."
