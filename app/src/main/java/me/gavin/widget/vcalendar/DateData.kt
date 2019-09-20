package me.gavin.widget.vcalendar

import java.util.*


class DateData {
    val months = mutableListOf<Month>()
    val weeks = mutableListOf<Week>()

    fun fillMonth(src: Calendar, offset: Int) {
        val cal = src.clone() as Calendar
        cal.add(Calendar.MONTH, offset)
        // 当月天数
        val dayCount = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        // 设置为1号
        cal.set(Calendar.DAY_OF_MONTH, 1)
        // 1号是一周第几天
        val dayOfW = cal.get(Calendar.DAY_OF_WEEK)

        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH)

        // 设置为上月1号
        cal.add(Calendar.MONTH, -1)
        // 上个月天数
        val lastDayCount = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val ly = cal.get(Calendar.YEAR)
        val lm = cal.get(Calendar.MONTH)

        // 设置为下月1号
        cal.add(Calendar.MONTH, 2)
        // 上个月天数
        val ny = cal.get(Calendar.YEAR)
        val nm = cal.get(Calendar.MONTH)
        // 设置为当月最后一天
        cal.add(Calendar.DAY_OF_MONTH, -1)
        val dayNCount = 7 - cal.get(Calendar.DAY_OF_WEEK)

        (2 - dayOfW..dayCount + dayNCount)
                .withIndex()
                .groupBy { it.index / 7 }
                .map { it.value.map { it.value } }
                .map {
                    it.map {
                        when {
                            it < 1 -> Day(ly, lm, lastDayCount + it) // 上月
                            it > dayCount -> Day(ny, nm, it - dayCount) // 下月
                            else -> Day(y, m, it) // 当月
                        }
                    }
                }
                .mapIndexed { i, days -> Week(days, i) }
                .let { Month(it) }
                .let {
                    months.add(it)
                }
    }

    fun fillWeek(src: Calendar) {
        val dayOfM = src[Calendar.DAY_OF_MONTH]
        months[1].weeks.withIndex()
                .let {
                    it.find {
                        it.value.days.last().d >= dayOfM
                    } ?: it.last()
                }
                .let {
                    // 当周
                    weeks.add(it.value)
                    // 上周
                    (months[1].weeks.getOrNull(it.index - 1)
                            ?: months[0].weeks.last())
                            .let {
                                weeks.add(0, it)
                            }
                    // 下周
                    (months[1].weeks.getOrNull(it.index + 1)
                            ?: months[2].weeks.first())
                            .let {
                                weeks.add(it)
                            }
                }
    }

}

val Calendar.dateData
    get() = DateData().also {
        it.fillMonth(this, -1)
        it.fillMonth(this, 0)
        it.fillMonth(this, +1)
        it.fillWeek(this)
    }

data class Day(val y: Int, val m: Int, val d: Int)

data class Week(val days: List<Day>, val line: Int)

data class Month(val weeks: List<Week>, var type: Int = 0) {

    fun lineOfDay(dayOfM: Int): Int {
        return (weeks.find { it.days.last().d >= dayOfM } ?: weeks.last()).line
    }
}