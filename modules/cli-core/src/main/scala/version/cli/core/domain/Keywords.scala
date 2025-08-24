package version.cli.core.domain

import version.MajorVersion
import version.MinorVersion
import version.PatchNumber
import version.Version

/** ADT of recognised keywords extracted from commit messages. Pure data. */
sealed trait Keyword derives CanEqual

object Keyword:
  given CanEqual[Keyword, Keyword] = CanEqual.derived

  // Relative Change Keywords (coalesced to at-most one increment per component)
  sealed trait Relative extends Keyword derives CanEqual
  case object MajorChange extends Relative derives CanEqual
  case object MinorChange extends Relative derives CanEqual
  case object PatchChange extends Relative derives CanEqual

  // Absolute Version Set Keywords (highest wins per component)
  sealed trait Absolute extends Keyword derives CanEqual
  final case class MajorSet(value: MajorVersion) extends Absolute derives CanEqual
  final case class MinorSet(value: MinorVersion) extends Absolute derives CanEqual
  final case class PatchSet(value: PatchNumber) extends Absolute derives CanEqual

  // Target Version Set Keyword (full SemVer parsed; only core used by selection stage)
  final case class TargetSet(value: Version) extends Keyword derives CanEqual
end Keyword
