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

import boilerplate.TypedError

/** The root of every error this library produces.
  *
  * Left deliberately open: a scheme implemented outside this library rejects input for reasons only it knows, and
  * extends this root to say so.
  */
abstract class VersionError(message: String, cause: Option[Throwable]) extends TypedError(message, cause)

/** A component was given a value outside the range that component admits. */
final case class InvalidComponent(value: Long, componentName: String, requirement: String)
    extends VersionError(s"$componentName must be $requirement. Found: $value", None)

/** A request addressed a component under a name the scheme does not use. */
final case class UnsupportedComponent(scheme: String, component: String)
    extends VersionError(s"The '$scheme' scheme has no component named '$component'.", None)

/** Two versions read by different schemes were compared, which no rule ranks. */
final case class SchemeMismatch(left: String, right: String)
    extends VersionError(s"A '$left' version and a '$right' version cannot be compared with one another.", None)

/** A classifier and a qualifier number contradict one another. */
sealed trait InvalidQualifierCombination extends VersionError

/** A classifier that is numbered was used without a number. */
final case class MissingQualifierNumber(classifier: String)
    extends VersionError(s"The classifier '$classifier' requires a qualifier number, but none was provided.", None)
    with InvalidQualifierCombination

/** A classifier that is not numbered was used with a number. */
final case class UnexpectedQualifierNumber(classifier: String, number: Long)
    extends VersionError(s"The classifier '$classifier' cannot have a qualifier number. Found: $number", None)
    with InvalidQualifierCombination

/** An operation that needs a numbered classifier was given one that is not numbered. */
final case class ClassifierNotVersioned(classifier: String)
    extends VersionError(s"Classifier '$classifier' is not versioned and cannot be used in this operation.", None)

/** Input does not conform to the grammar of the scheme reading it. */
sealed trait ParseError extends VersionError

/** The overall shape of the input is not a version of this scheme. */
final case class InvalidVersionFormat(input: String)
    extends VersionError(s"The input string '$input' is not a valid version format.", None)
    with ParseError

/** A numeric field is not a number, or names one too large to carry. */
final case class InvalidNumericField(field: String, value: String)
    extends VersionError(
      s"The value '$value' is invalid for the $field field. It must be a non-negative integer no greater than ${Long.MaxValue}.",
      None
    )
    with ParseError

/** Pre-release identifiers are empty, contain characters outside `[0-9A-Za-z-]`, or carry a leading zero on a purely
  * numeric identifier.
  */
final case class InvalidPreRelease(identifiers: List[String])
    extends VersionError(
      s"Pre-release identifiers must be non-empty, contain only ASCII alphanumerics and hyphens [0-9A-Za-z-], and carry no leading zero when numeric. Found: '${identifiers.mkString(".")}'",
      None
    )
    with ParseError

/** Build metadata identifiers are empty or contain characters outside `[0-9A-Za-z-]`. */
final case class InvalidMetadata(identifiers: List[String])
    extends VersionError(
      s"Build metadata identifiers must be non-empty and contain only ASCII alphanumerics and hyphens [0-9A-Za-z-]. Found: '${identifiers.mkString(".")}'",
      None
    )
    with ParseError
