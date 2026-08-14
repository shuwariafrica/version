---
title: Ranges
---

# Ranges

A range is what a manifest writes where a version is expected: `^1.2.3`, `>=1.2.3 <2.0.0`, `1.x`. It is a value in its
own right, of a type the scheme owns, and it is read, rendered, tested and rewritten through `RangeScheme[V, R]`.

```scala
import version.semver.*

val range = SemVerRange.parse("^1.2.3")  // Right(^1.2.3)
```

The capability is keyed on the version type and the range type together, so a range value selects it without the call
site naming either, and a scheme whose ecosystem writes no ranges simply supplies no instance.

```scala
def widest[V, R](candidates: List[V], constraint: R)(using RangeScheme[V, R], VersionScheme[V]): Option[V] =
  constraint.highest(candidates)
```

## A range carries the form it was written in

The value holds the constructs and the spellings the grammar offers a choice of - which construct (caret, tilde,
hyphen, x-range, or a primitive comparator), which wildcard character, how many positions were written, the operator,
the pre-release. It does not hold whitespace.

```scala
SemVerRange.parse("^1.2").map(_.show)      // Right("^1.2")   - not "^1.2.0"
SemVerRange.parse("1.X").map(_.show)       // Right("1.X")    - not "1.x"
SemVerRange.parse(">= 1.2.3").map(_.show)  // Right(">=1.2.3")
```

`show` renders canonically within that form and `parse` reads the result back as an equal value. A leading `v` and
build metadata inside a bound are accepted and discarded: neither can bound anything, because precedence cannot see
them.

This is what makes a rewrite able to hand back the construct its author chose. A consumer needing the manifest text
byte for byte keeps its own copy of the original and compares it against `show`, which tells a pure layout difference
from a real one.

## Membership is the scheme's rule, not ordered containment

`admits` asks the scheme, and the answer is not "does this version lie between the bounds".

```scala
val caret = SemVerRange.parse("^1.2.3").toOption.get

caret.admits(SemVer.parseUnsafe("1.9.9"))      // true
caret.admits(SemVer.parseUnsafe("1.3.0-rc.1")) // false
```

`1.3.0-rc.1` ranks above `1.2.3` and below `2.0.0-0`, which are the bounds `^1.2.3` stands for, and the range still
refuses it. SemVer admits a pre-release only where some comparator of the same conjunction carries a pre-release at
identical numbers - the version that opts in is named, and nothing else at that precision comes along with it.

```scala
val opted = SemVerRange.parse("^1.2.3-pr.1").toOption.get

opted.admits(SemVer.parseUnsafe("1.2.3-pr.2"))           // true  - opted in at 1.2.3
opted.admits(SemVer.parseUnsafe("1.2.4-alpha.notready")) // false - a different pre-release line
```

The comparator that opts in may be either end: `>=1.0.0 <2.0.0-rc.1` admits `2.0.0-rc.0`.

## Choosing from a set of candidates

```scala
val published = List("1.0.0", "1.2.3", "1.9.9", "2.0.0", "1.6.0-rc.1").map(SemVer.parseUnsafe)

caret.highest(published) // Some(1.9.9)
caret.lowest(published)  // Some(1.2.3)
```

Both select under the scheme's own precedence and consider only what the range admits, so the pre-release above is
never returned. Neither picks a direction for you: a bounded range and a floating one want opposite ends, and which
one a caller wants is the caller's business.

`exact` answers whether the range names a single version outright, which is what tells a caller a dependency is
pinned rather than bounded.

```scala
SemVerRange.parse("1.2.3").toOption.flatMap(_.exact) // Some(1.2.3)
SemVerRange.parse("^1.2.3").toOption.flatMap(_.exact) // None
```

## The primitive form behind a construct

`desugar` replaces every sugar construct with the comparators it stands for, within the same type. It is idempotent
and admits exactly what the range it came from admits.

| Written        | Stands for              |
|----------------|-------------------------|
| `^1.2.3`       | `>=1.2.3 <2.0.0-0`      |
| `^0.2.3`       | `>=0.2.3 <0.3.0-0`      |
| `^0.0.3`       | `>=0.0.3 <0.0.4-0`      |
| `~1.2.3`       | `>=1.2.3 <1.3.0-0`      |
| `~0.2`         | `>=0.2.0 <0.3.0-0`      |
| `1.x`          | `>=1.0.0 <2.0.0-0`      |
| `1.2.x`        | `>=1.2.0 <1.3.0-0`      |
| `<=0.7.x`      | `<0.8.0-0`              |
| `1.2.3 - 2.3.4`| `>=1.2.3 <=2.3.4`       |
| `*`            | (constrains nothing)    |

Every synthesised ceiling ends in `-0`, the lowest pre-release there is, so `<2.0.0-0` excludes `2.0.0-alpha` as well
as `2.0.0`.

## Rewriting a range around a new version

`rewrite` takes the strategy from the caller and preserves the written form. The scheme never picks the strategy.

| Strategy  | What it does                                                                                  |
|-----------|-----------------------------------------------------------------------------------------------|
| `Pin`     | Names the version outright                                                                     |
| `Raise`   | Moves the floor up to the version, keeping the construct and the precision                     |
| `Replace` | Leaves a range that already admits alone, and otherwise reshapes it as little as will admit    |
| `Widen`   | Leaves a range that already admits alone, and otherwise reaches the version without losing any |

```scala
import version.Strategy

val target = SemVer.parseUnsafe("2.5.0")

SemVerRange.parse("^1.2").toOption.get.rewrite(Strategy.Raise, target)   // Right(^2.5)
SemVerRange.parse("1.x").toOption.get.rewrite(Strategy.Replace, target)  // Right(2.x)
SemVerRange.parse("<2.0.0").toOption.get.rewrite(Strategy.Replace, target) // Right(<3.0.0)
SemVerRange.parse("^1.0.0").toOption.get.rewrite(Strategy.Widen, target) // Right(^1.0.0 || ^2.5.0)
```

Three consequences worth knowing:

- **Arity and wildcard survive.** `^1.2` becomes `^1.5`, never `^1.5.0`; `1.*` becomes `2.*`, never `2.x`.
- **An exclusive ceiling moves at the precision its trailing zeros mark.** The author who wrote `<2.0.0` meant a major
  boundary, so `2.5.0` gives `<3.0.0`; who wrote `<2.3.1` meant a patch one, so `2.5.0` gives `<2.5.1`.
- **Only the last alternative is rewritten.** Authors append, so `^1.0.0 || ^2.0.0` widened for `3.0.0` becomes
  `^1.0.0 || ^2.0.0 || ^3.0.0` rather than repeating itself.

A `Right` is guaranteed to admit the version it was rewritten for. Where no rewrite of that written form under that
strategy can, the result is `Left(UnsupportedRewrite(range, strategy))` - `>0.9.0` asked to admit `0.9.0` says
strictly-above and the request contradicts it, and no spelling of the any-range admits a pre-release.

## Reading a range

The grammar is the one npm publishes for Semantic Versioning: alternatives separated by `||`, each of them a hyphen
range or a conjunction of comparators.

```scala
SemVerRange.parse("~>1.2.3")
// Left(InvalidRangeFormat("~>1.2.3", "~>1.2.3"))
```

`InvalidRangeFormat` names the fragment that failed as well as the whole input, because a range is compound and the
failure is usually one comparator of several. `~>` is rejected outright: the published grammar spells the tilde with
nothing after it, npm silently reads `~>` as `~`, and other tooling reads it as a distinct operator, so accepting it
would mean choosing one of two live readings without saying so.

## Supplying ranges for another scheme

Implement `RangeScheme[V, R]` in the companion of `R`. Beyond the signatures, the contract is:

- `parse` is strict, and rejects with a `RangeError`.
- `show` is canonical within the written form, and `parse` must read it back as an equal value.
- `desugar` is idempotent and preserves membership.
- `admits` is the ecosystem's own membership rule, whatever ordering would say.
- `rewrite` returns `Right` only where the result admits the version, and names the range and the strategy in the
  `Left` where none can.

`highest` and `lowest` are derived from `admits` and the scheme's `precedence` and are not yours to write.
