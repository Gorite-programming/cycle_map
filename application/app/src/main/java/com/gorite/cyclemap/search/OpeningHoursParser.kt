package com.gorite.cyclemap.search

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

enum class OpenStatus {
    Open,
    Closed,
    Unknown
}

object OpeningHoursParser {

    private val dayAbbrs = mapOf(
        "Mo" to DayOfWeek.MONDAY,
        "Tu" to DayOfWeek.TUESDAY,
        "We" to DayOfWeek.WEDNESDAY,
        "Th" to DayOfWeek.THURSDAY,
        "Fr" to DayOfWeek.FRIDAY,
        "Sa" to DayOfWeek.SATURDAY,
        "Su" to DayOfWeek.SUNDAY
    )

    private val dayOrder = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY
    )

    data class DayRule(
        val days: Set<DayOfWeek>,
        val isOff: Boolean,
        val timeRanges: List<Pair<Int, Int>> // (startMinutes, endMinutes), e.g. 09:00 = 540
    )

    /**
     * OSM opening_hours 文字列を解析し、指定時刻の営業状況を返す。
     * 解析不能または空文字列の場合は安全側に倒して [OpenStatus.Unknown] を返す。
     */
    fun evaluate(raw: String?, dateTime: LocalDateTime = LocalDateTime.now()): OpenStatus {
        if (raw.isNullOrBlank()) return OpenStatus.Unknown
        val clean = raw.trim().replace("\\s+".toRegex(), " ")

        if (clean.equals("24/7", ignoreCase = true)) {
            return OpenStatus.Open
        }
        if (clean.equals("closed", ignoreCase = true) || clean.equals("off", ignoreCase = true)) {
            return OpenStatus.Closed
        }

        return try {
            val rules = parseRules(clean)
            if (rules.isEmpty()) return OpenStatus.Unknown

            val currentDay = dateTime.dayOfWeek
            val prevDay = currentDay.minus(1)
            val currentMinutes = dateTime.hour * 60 + dateTime.minute

            // 1. 前日深夜からの日またぎ営業の判定 (例: 22:00-02:00)
            for (rule in rules) {
                if (prevDay in rule.days && !rule.isOff) {
                    for ((start, end) in rule.timeRanges) {
                        if (end < start && currentMinutes < end) {
                            return OpenStatus.Open
                        }
                    }
                }
            }

            // 2. 当日の営業ルール判定
            val matchingRule = rules.find { currentDay in it.days }
            if (matchingRule != null) {
                if (matchingRule.isOff) return OpenStatus.Closed
                for ((start, end) in matchingRule.timeRanges) {
                    if (end < start) {
                        // 日またぎ: start以降なら本日営業中
                        if (currentMinutes >= start) return OpenStatus.Open
                    } else {
                        // 同日内
                        if (currentMinutes in start..end) return OpenStatus.Open
                    }
                }
                return OpenStatus.Closed
            }

            // 当日の指定がない場合、ルールが曜日指定なし(毎日適用)のルールか確認
            val generalRule = rules.find { it.days.isEmpty() }
            if (generalRule != null) {
                if (generalRule.isOff) return OpenStatus.Closed
                for ((start, end) in generalRule.timeRanges) {
                    if (end < start) {
                        if (currentMinutes >= start) return OpenStatus.Open
                    } else {
                        if (currentMinutes in start..end) return OpenStatus.Open
                    }
                }
                return OpenStatus.Closed
            }

            OpenStatus.Unknown
        } catch (_: Exception) {
            OpenStatus.Unknown
        }
    }

    private fun parseRules(ruleText: String): List<DayRule> {
        val statements = ruleText.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableListOf<DayRule>()

        for (stmt in statements) {
            val parts = stmt.split(" ").filter { it.isNotEmpty() }
            if (parts.isEmpty()) continue

            // 曜日パートの抽出
            var dayTokens: String? = null
            var timeTokens: String? = null

            if (parts.size == 1) {
                // "09:00-18:00" または "24/7"
                timeTokens = parts[0]
            } else if (parts[0].any { it.isLetter() } && dayAbbrs.keys.any { parts[0].contains(it) }) {
                dayTokens = parts[0]
                timeTokens = parts.drop(1).joinToString(" ")
            } else {
                timeTokens = stmt
            }

            val days = if (dayTokens != null) parseDays(dayTokens) else emptySet()

            if (timeTokens.equals("off", ignoreCase = true) || timeTokens.equals("closed", ignoreCase = true)) {
                result.add(DayRule(days = days, isOff = true, timeRanges = emptyList()))
            } else {
                val ranges = parseTimeRanges(timeTokens ?: "")
                if (ranges.isNotEmpty()) {
                    result.add(DayRule(days = days, isOff = false, timeRanges = ranges))
                }
            }
        }

        return result
    }

    private fun parseDays(token: String): Set<DayOfWeek> {
        val days = mutableSetOf<DayOfWeek>()
        val segments = token.split(",")
        for (seg in segments) {
            if (seg.contains("-")) {
                val sub = seg.split("-")
                val start = dayAbbrs[sub[0].trim()]
                val end = dayAbbrs[sub[1].trim()]
                if (start != null && end != null) {
                    val sIdx = dayOrder.indexOf(start)
                    val eIdx = dayOrder.indexOf(end)
                    if (sIdx <= eIdx) {
                        for (i in sIdx..eIdx) days.add(dayOrder[i])
                    } else {
                        for (i in sIdx until dayOrder.size) days.add(dayOrder[i])
                        for (i in 0..eIdx) days.add(dayOrder[i])
                    }
                }
            } else {
                dayAbbrs[seg.trim()]?.let { days.add(it) }
            }
        }
        return days
    }

    private fun parseTimeRanges(token: String): List<Pair<Int, Int>> {
        val list = mutableListOf<Pair<Int, Int>>()
        val ranges = token.split(",")
        for (range in ranges) {
            val parts = range.trim().split("-")
            if (parts.size == 2) {
                val startMin = parseMinute(parts[0].trim())
                val endMin = parseMinute(parts[1].trim())
                if (startMin != null && endMin != null) {
                    list.add(Pair(startMin, endMin))
                }
            }
        }
        return list
    }

    private fun parseMinute(timeStr: String): Int? {
        val parts = timeStr.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return (h * 60 + m).coerceIn(0, 1440)
    }
}
