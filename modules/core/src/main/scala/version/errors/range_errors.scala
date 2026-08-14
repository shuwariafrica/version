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

import version.Strategy

/** A range operation the scheme refused: input outside its grammar, or a rewrite no spelling of the range performs. */
sealed trait RangeError extends VersionError

/** Input outside the range grammar of the scheme reading it, carrying the part of `input` that failed. */
final case class InvalidRangeFormat(input: String, fragment: String)
    extends VersionError(s"The range '$input' is not valid: '$fragment' is outside the grammar reading it.", None)
    with RangeError

/** No rewrite of this range under this strategy admits the version it was asked to admit. */
final case class UnsupportedRewrite(range: String, strategy: Strategy)
    extends VersionError(s"No $strategy rewrite of the range '$range' admits the requested version.", None)
    with RangeError
