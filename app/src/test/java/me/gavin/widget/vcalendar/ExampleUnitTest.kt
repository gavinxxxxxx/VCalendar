package me.gavin.widget.vcalendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*
import kotlin.system.measureTimeMillis

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun cal() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.MONTH, 9)
        cal.set(Calendar.DAY_OF_MONTH, 10)

        val dayOfM = cal.get(Calendar.DAY_OF_MONTH)
        val dayOfW = cal.get(Calendar.DAY_OF_WEEK)

        val day1OfW = (70 + dayOfW - dayOfM) % 7 + 1
        println(day1OfW)

        println(measureTimeMillis {
//            month(src = cal, offset = -1)
//            month(src = cal, offset = 0)
//            month(src = cal, offset = +1)

            cal.dateData.let {
                println(it.weeks)
                println(it.months[1])
            }
        })

    }

    fun month(src: Calendar, offset: Int) {
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
                .map { Week(it, 0) }
                .let { Month(it) }
                .let {
                    println(it)
                }
    }
}
