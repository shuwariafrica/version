package version.zio

import zio.prelude.*

import version.MajorVersion
import version.MinorVersion
import version.PatchNumber
import version.PreRelease
import version.PreReleaseClassifier
import version.PreReleaseNumber
import version.Version

object prelude:

  given Equal[MajorVersion] = Equal.default
  given Equal[MinorVersion] = Equal.default
  given Equal[PatchNumber] = Equal.default
  given Equal[PreReleaseNumber] = Equal.default
  given Equal[PreReleaseClassifier] = Equal.default
  given Equal[PreRelease] = Equal.default
  given Equal[Version] = Equal.default

  given Ord[MajorVersion] = Ord.default
  given Ord[MinorVersion] = Ord.default
  given Ord[PatchNumber] = Ord.default
  given Ord[PreReleaseNumber] = Ord.default
  given Ord[PreReleaseClassifier] = Ord.default
  given Ord[PreRelease] = Ord.default
  given Ord[Version] = Ord.default

  // Combine by numeric addition, wrapping back into the opaque types
  given Commutative[MajorVersion] = Commutative.make((l, r) => MajorVersion.unsafe(l.value + r.value))
  given Commutative[MinorVersion] = Commutative.make((l, r) => MinorVersion.unsafe(l.value + r.value))
  given Commutative[PatchNumber] = Commutative.make((l, r) => PatchNumber.unsafe(l.value + r.value))
  given Commutative[PreReleaseNumber] = Commutative.make((l, r) => PreReleaseNumber.unsafe(l.value + r.value))
