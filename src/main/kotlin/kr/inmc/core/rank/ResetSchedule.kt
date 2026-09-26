package kr.inmc.core.rank

import kr.inmc.core.util.Durations
import org.bukkit.configuration.ConfigurationSection
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** When a season ends and the leaderboard is wiped. */
enum class ResetPolicy(val display: String, val help: String) {
    NONE("초기화 없음", "랭킹이 영구 누적됩니다. 순위 보상은 수동으로만 정산됩니다."),
    DAILY("매일", "매일 지정한 시각에 초기화됩니다."),
    WEEKLY("매주", "매주 지정한 요일·시각에 초기화됩니다."),
    MONTHLY("매월", "매월 지정한 날짜·시각에 초기화됩니다."),
    INTERVAL("주기", "마지막 초기화로부터 지정한 시간이 지나면 초기화됩니다."),
    MANUAL("수동", "관리자가 [지금 시즌 종료]를 누를 때만 초기화됩니다.");

    val automatic: Boolean get() = this != NONE && this != MANUAL

    companion object {
        fun parse(raw: String?): ResetPolicy =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: WEEKLY
    }
}

/**
 * The reset clock for one game's leaderboard.
 *
 * [nextAfter] always returns an instant **strictly after** the one it is given. That single
 * property is what makes a long server outage safe: the ticker fires one reset, asks for the
 * next time from *now*, and a week of downtime therefore produces one reset rather than seven.
 */
class ResetSchedule(
    var policy: ResetPolicy = ResetPolicy.WEEKLY,
    hour: Int = 4,
    minute: Int = 0,
    var weekday: DayOfWeek = DayOfWeek.MONDAY,
    dayOfMonth: Int = 1,
    var intervalSeconds: Long = 7L * 86400L,
    /** Empty string means the server's own time zone. */
    var zoneId: String = "",
) {

    var hour: Int = hour.coerceIn(0, 23)
        set(value) {
            field = value.coerceIn(0, 23)
        }

    var minute: Int = minute.coerceIn(0, 59)
        set(value) {
            field = value.coerceIn(0, 59)
        }

    /** 1..31; months shorter than this clamp to their last day rather than skipping. */
    var dayOfMonth: Int = dayOfMonth.coerceIn(1, 31)
        set(value) {
            field = value.coerceIn(1, 31)
        }

    fun zone(): ZoneId =
        if (zoneId.isBlank()) ZoneId.systemDefault()
        else runCatching { ZoneId.of(zoneId) }.getOrElse { ZoneId.systemDefault() }

    /** Epoch millis of the next reset strictly after [fromMillis]; 0 when there is none. */
    fun nextAfter(fromMillis: Long): Long {
        if (!policy.automatic) return 0L
        if (policy == ResetPolicy.INTERVAL) {
            val step = intervalSeconds.coerceAtLeast(60L)
            return fromMillis + step * 1000L
        }

        val zone = zone()
        val from = LocalDateTime.ofInstant(Instant.ofEpochMilli(fromMillis), zone)

        var candidate = when (policy) {
            ResetPolicy.DAILY -> from.toLocalDate().atTime(hour, minute)
            ResetPolicy.WEEKLY -> from.toLocalDate().atTime(hour, minute)
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(weekday))
            ResetPolicy.MONTHLY -> atDayOfMonth(from.toLocalDate(), hour, minute)
            else -> return 0L
        }

        // Walk forward until strictly after `from`, so a reset never fires twice for one period.
        var guard = 0
        while (!candidate.isAfter(from) && guard++ < 64) {
            candidate = when (policy) {
                ResetPolicy.DAILY -> candidate.plusDays(1)
                ResetPolicy.WEEKLY -> candidate.plusWeeks(1)
                ResetPolicy.MONTHLY -> atDayOfMonth(
                    candidate.toLocalDate().withDayOfMonth(1).plusMonths(1), hour, minute
                )
                else -> return 0L
            }
        }
        return candidate.atZone(zone).toInstant().toEpochMilli()
    }

    private fun atDayOfMonth(date: LocalDate, hour: Int, minute: Int): LocalDateTime =
        date.withDayOfMonth(dayOfMonth.coerceAtMost(date.lengthOfMonth())).atTime(hour, minute)

    fun describe(): String {
        val clock = String.format("%02d:%02d", hour, minute)
        return when (policy) {
            ResetPolicy.NONE -> "초기화 없음"
            ResetPolicy.MANUAL -> "수동 초기화"
            ResetPolicy.DAILY -> "매일 $clock"
            ResetPolicy.WEEKLY -> "매주 ${weekdayName()} $clock"
            ResetPolicy.MONTHLY -> "매월 ${dayOfMonth}일 $clock"
            ResetPolicy.INTERVAL -> "${Durations.formatShort(intervalSeconds)}마다"
        }
    }

    fun weekdayName(): String = WEEKDAY_NAMES[weekday] ?: weekday.name

    fun copyOf(): ResetSchedule = ResetSchedule(
        policy, hour, minute, weekday, dayOfMonth, intervalSeconds, zoneId,
    )

    fun save(section: ConfigurationSection) {
        section.set("policy", policy.name)
        section.set("hour", hour)
        section.set("minute", minute)
        section.set("weekday", weekday.name)
        section.set("day-of-month", dayOfMonth)
        section.set("interval", Durations.format(intervalSeconds))
        section.set("timezone", zoneId)
    }

    companion object {

        val WEEKDAY_NAMES: Map<DayOfWeek, String> = mapOf(
            DayOfWeek.MONDAY to "월요일",
            DayOfWeek.TUESDAY to "화요일",
            DayOfWeek.WEDNESDAY to "수요일",
            DayOfWeek.THURSDAY to "목요일",
            DayOfWeek.FRIDAY to "금요일",
            DayOfWeek.SATURDAY to "토요일",
            DayOfWeek.SUNDAY to "일요일",
        )

        fun parseWeekday(raw: String?): DayOfWeek {
            val text = raw?.trim().orEmpty()
            WEEKDAY_NAMES.entries.firstOrNull { it.value == text }?.let { return it.key }
            return runCatching { DayOfWeek.valueOf(text.uppercase()) }.getOrDefault(DayOfWeek.MONDAY)
        }

        fun load(section: ConfigurationSection?): ResetSchedule {
            if (section == null) return ResetSchedule()
            return ResetSchedule(
                policy = ResetPolicy.parse(section.getString("policy")),
                hour = section.getInt("hour", 4),
                minute = section.getInt("minute", 0),
                weekday = parseWeekday(section.getString("weekday")),
                dayOfMonth = section.getInt("day-of-month", 1),
                intervalSeconds = Durations.parse(section.getString("interval"), 7L * 86400L),
                zoneId = section.getString("timezone").orEmpty(),
            )
        }
    }
}
