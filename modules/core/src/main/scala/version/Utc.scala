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
package version

/** Renders a Unix epoch-seconds instant as a UTC timestamp without a `java.time` dependency - Scala Native ships only an
  * `Instant` stub - using Howard Hinnant's civil-from-days algorithm.
  */
object Utc:

  /** A 12-character `yyyymmddhhmm` identifier. The fixed width keeps lexicographic order aligned with chronological
    * order, so rendered strings of the same base sort by time.
    */
  def compact(epochSeconds: Long): String =
    val c = civil(epochSeconds)
    val sb = StringBuilder(12)
    appendYear(sb, c.year)
    appendPad2(sb, c.month)
    appendPad2(sb, c.day)
    appendPad2(sb, c.hour)
    appendPad2(sb, c.minute)
    sb.result()

  /** `yyyy-MM-dd HH:mm`, to the minute, in UTC. */
  def dateTime(epochSeconds: Long): String =
    val c = civil(epochSeconds)
    val sb = StringBuilder(16)
    appendYear(sb, c.year)
    sb.append('-')
    appendPad2(sb, c.month)
    sb.append('-')
    appendPad2(sb, c.day)
    sb.append(' ')
    appendPad2(sb, c.hour)
    sb.append(':')
    appendPad2(sb, c.minute)
    sb.result()

  final private case class Civil(year: Long, month: Int, day: Int, hour: Int, minute: Int)

  private def civil(epochSeconds: Long): Civil =
    val secondsPerDay = 86400L
    val days = Math.floorDiv(epochSeconds, secondsPerDay)
    val secondsOfDay = Math.floorMod(epochSeconds, secondsPerDay).toInt
    val z = days + 719468L
    val era = if z >= 0 then z / 146097L else (z - 146096L) / 146097L
    val doe = (z - era * 146097L).toInt
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val baseYear = yoe + era * 400L
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if mp < 10 then mp + 3 else mp - 9
    val year = if month <= 2 then baseYear + 1 else baseYear
    val hour = secondsOfDay / 3600
    val minute = (secondsOfDay % 3600) / 60
    Civil(year, month, day, hour, minute)

  private inline def appendPad2(sb: StringBuilder, n: Int): Unit =
    if n < 10 then sb.append('0').append(n): Unit else sb.append(n): Unit

  private inline def appendYear(sb: StringBuilder, y: Long): Unit =
    if y < 0 then sb.append(y): Unit
    else if y < 10 then sb.append("000").append(y): Unit
    else if y < 100 then sb.append("00").append(y): Unit
    else if y < 1000 then sb.append('0').append(y): Unit
    else sb.append(y): Unit

end Utc
