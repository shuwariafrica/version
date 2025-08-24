package version.parser

import scala.util.matching.Regex

import version.BuildMetadata
import version.MajorVersion
import version.MinorVersion
import version.PatchNumber
import version.PreRelease
import version.Version
import version.errors.*

/** Provides functionality to parse version strings into structured [[Version]] objects according to SemVer 2.0.0. */
object VersionParser:

  // SemVer 2.0.0 compliant Regex (from https://semver.org/)
  // Captures: 1=Major, 2=Minor, 3=Patch, 4=PreRelease, 5=BuildMetadata
  // Enforces rules like no leading zeros in numeric components (unless the component is just "0").
  // Moved to object scope for optimization (compiled once).
  private def SemVerRegex: Regex = ("""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""" +
    """(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?""" +
    """(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$""").r

  // Pattern to detect combined formats like "RC1" or "Alpha5" within a single identifier segment.
  private def CombinedIdentifierPattern: Regex = """^([a-zA-Z-]+)(\d+)$""".r

  /** Parses a version string into a [[Version]] object.
    *
    * This method validates the input against the SemVer 2.0.0 specification and utilizes a contextual
    * [[version.PreRelease.Resolver]] to interpret the pre-release identifier.
    *
    * @param input
    *   The version string to parse.
    * @param resolver
    *   The contextual resolver. Requires a `given version.PreRelease.Resolver` in scope.
    * @return
    *   `Right(Version)` if parsing is successful, `Left(ParseError)` otherwise.
    */
  def parse(input: String)(using resolver: PreRelease.Resolver): Either[ParseError, Version] =
    input match
      case SemVerRegex(majorStr, minorStr, patchStr, preReleaseStr, buildMetadataStr) =>
        // Use a for-comprehension for clean composition of Either results
        for
          // 1. Parse numeric fields.
          majorInt <- parseNumeric(majorStr, "Major")
          minorInt <- parseNumeric(minorStr, "Minor")
          patchInt <- parseNumeric(patchStr, "Patch")

          // 2. Parse and map the pre-release part.
          preRelease <- parsePreRelease(preReleaseStr)

          // 3. Parse build metadata.
          buildMetadata <- parseBuildMetadata(buildMetadataStr)

        // 4. Construct the final Version.
        // The regex and parseNumeric ensure the values are valid (non-negative, within Int range),
        // so we can safely use the `unsafe` constructors here.
        yield Version(
          MajorVersion.unsafe(majorInt),
          MinorVersion.unsafe(minorInt),
          PatchNumber.unsafe(patchInt),
          preRelease,
          buildMetadata
        )

      case _ => Left(InvalidVersionFormat(input))
  end parse

  /** Parses a numeric component string into an Int, handling potential exceptions (e.g., overflow). */
  private def parseNumeric(value: String, fieldName: String): Either[ParseError, Int] =
    scala.util.Try(value.toInt).toEither.left.map(_ => InvalidNumericField(fieldName, value))

  /** Parses the pre-release identifier string using the provided [[version.PreRelease.Resolver]]. */
  private def parsePreRelease(
    identifierStr: String
  )(using resolver: PreRelease.Resolver): Either[ParseError, Option[PreRelease]] =
    // The regex capture group might be null if the segment is absent.
    Option(identifierStr) match
      case None        => Right(None) // No pre-release part present
      case Some(idStr) =>
        // SemVer requires dot separation for identifiers.
        val rawIdentifiers = idStr.split('.').toList
        // Attempt to reconcile common non-standard formats (e.g., "RC1" -> List("RC", "1"))
        val identifiers = reconcileIdentifiers(rawIdentifiers)

        resolver.map(identifiers) match
          case Some(pr) => Right(Some(pr))
          // The identifier is structurally valid SemVer but not recognized by the resolver.
          case None => Left(UnrecognizedPreRelease(identifiers))

  /** Parses the build metadata string. */
  private def parseBuildMetadata(metadataStr: String): Either[ParseError, Option[BuildMetadata]] =
    Option(metadataStr) match
      case None          => Right(None)
      case Some(metaStr) =>
        val identifiers = metaStr.split('.').toList
        // BuildMetadata.from validates the identifiers (non-empty, allowed characters).
        BuildMetadata.from(identifiers) match
          case Right(meta) => Right(Some(meta))
          // If validation fails, we treat it as an InvalidVersionFormat in the context of parsing the whole string.
          case Left(err) => Left(InvalidVersionFormat(s"Invalid build metadata: ${err.message}"))

  /** Attempts to reconcile commonly used non-dot-separated pre-release formats found within a single segment.
    *
    * If a single identifier segment contains both letters and numbers (e.g., "RC1"), it splits them into two
    * identifiers (e.g., List("RC", "1")), allowing the resolver to process them correctly even if they violate strict
    * SemVer dot separation. This only applies if the input list has exactly one element.
    */
  private def reconcileIdentifiers(identifiers: List[String]): List[String] =
    identifiers match
      case List(singleId) =>
        singleId match
          case CombinedIdentifierPattern(prefix, suffix) => List(prefix, suffix)
          case _                                         => identifiers // Not a combined format
      case _ => identifiers // Already separated, empty, or multiple segments
end VersionParser
