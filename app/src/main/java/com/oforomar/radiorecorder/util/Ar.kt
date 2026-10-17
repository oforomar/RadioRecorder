package com.oforomar.radiorecorder.util

import com.oforomar.radiorecorder.model.ALL_DAYS
import com.oforomar.radiorecorder.model.bit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Arabic (Egypt) formatting with Arabic-Indic digits, independent of the phone's language. */
object Ar {
    val locale: Locale = Locale.forLanguageTag("ar-EG")

    /** Egyptian week order, Saturday first. */
    val week = listOf(
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    fun dayName(d: DayOfWeek): String = when (d) {
        DayOfWeek.SATURDAY -> "السبت"
        DayOfWeek.SUNDAY -> "الأحد"
        DayOfWeek.MONDAY -> "الإثنين"
        DayOfWeek.TUESDAY -> "الثلاثاء"
        DayOfWeek.WEDNESDAY -> "الأربعاء"
        DayOfWeek.THURSDAY -> "الخميس"
        DayOfWeek.FRIDAY -> "الجمعة"
    }

    private val months = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )

    fun digits(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in '0'..'9') '٠' + (c - '0') else c)
    }

    fun num(n: Int): String = digits(n.toString())

    /** 21:05 → "٩:٠٥ م" */
    fun time(hour: Int, minute: Int): String {
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val suffix = if (hour < 12) "ص" else "م"
        return digits("$h12:${minute.toString().padStart(2, '0')}") + " " + suffix
    }

    fun time(epochMs: Long): String {
        val t = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
        return time(t.hour, t.minute)
    }

    fun minutes(n: Int): String = when {
        n == 1 -> "دقيقة واحدة"
        n == 2 -> "دقيقتان"
        n in 3..10 -> "${num(n)} دقائق"
        else -> "${num(n)} دقيقة"
    }

    fun duration(totalMin: Int): String {
        if (totalMin < 60) return minutes(totalMin)
        val h = totalMin / 60
        val m = totalMin % 60
        val hours = when (h) {
            1 -> "ساعة"
            2 -> "ساعتان"
            in 3..10 -> "${num(h)} ساعات"
            else -> "${num(h)} ساعة"
        }
        return when (m) {
            0 -> hours
            30 -> "$hours ونصف"
            else -> "$hours و${minutes(m)}"
        }
    }

    fun days(mask: Int): String {
        if ((mask and ALL_DAYS) == ALL_DAYS) return "كل يوم"
        val selected = week.filter { (mask and it.bit()) != 0 }
        if (selected.isEmpty()) return "لا توجد أيام"
        return selected.joinToString("، ") { dayName(it) }
    }

    /** "اليوم" / "غداً" / day name, relative to today. */
    fun relativeDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "اليوم"
        today.plusDays(1) -> "غداً"
        else -> dayName(date.dayOfWeek)
    }

    /** "السبت ١٨ أكتوبر ٢٠٢٦" */
    fun date(epochMs: Long): String {
        val d = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
        return "${dayName(d.dayOfWeek)} ${num(d.dayOfMonth)} ${months[d.monthValue - 1]} ${num(d.year)}"
    }

    fun size(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb < 1) "أقل من ١ م.ب" else digits(String.format(Locale.US, "%.0f", mb)) + " م.ب"
    }

    /** ISO date with Western digits, used in file names so they sort correctly. */
    val fileDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
}
