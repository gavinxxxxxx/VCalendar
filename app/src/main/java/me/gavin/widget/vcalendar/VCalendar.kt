package me.gavin.widget.vcalendar

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.animation.addListener
import androidx.core.util.Consumer
import androidx.core.view.ViewCompat
import me.gavin.R
import java.util.*
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@SuppressLint("Recycle")
class VCalendar(context: Context, attrs: AttributeSet?)
    : View(context, attrs), CoordinatorLayout.AttachedBehavior {

    private val itemHeight: Int
    private val textSizeWeekday: Int
    private val textSize: Int

    private val textColorWeekday: Int
    private val textColorHint: Int
    private val textColor: Int

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 32f
    }
    private var diffY = (paint.descent() + paint.ascent()) / 2f

    private val rect = Rect()
    private var itemHeightF = 0f
    private var maxHeight = 0
    private val maxFoldHeight get() = maxHeight - minHeight

    private var foldHeight = 0f

    val preHeight get() = maxHeight - foldHeight
    val minHeight get() = rect.top + itemHeightF

    private var scrollState = SCROLL_NONE
    private val scrollSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    private val flingSlop by lazy { ViewConfiguration.get(context).scaledMinimumFlingVelocity * 3 }

    private val cal = Calendar.getInstance()
            .apply {
                //                set(Calendar.MONTH, 9)
//                set(Calendar.DAY_OF_MONTH, 30)
            }
    private var dateData: DateData? = null

    private val isWeekMode get() = height - minHeight < 2

    init {
        context.obtainStyledAttributes(attrs, R.styleable.VCalendar).apply {
            itemHeight = getDimensionPixelSize(R.styleable.VCalendar_cal_itemHeight, 0)
            textSizeWeekday = getDimensionPixelSize(R.styleable.VCalendar_cal_textSizeWeekday, 12)
            textSize = getDimensionPixelSize(R.styleable.VCalendar_android_textSize, 16)
            textColorWeekday = getColor(R.styleable.VCalendar_cal_textColorWeekday, 0xFF666666.toInt())
            textColorHint = getColor(R.styleable.VCalendar_cal_textColorHint, 0xFF999999.toInt())
            textColor = getColor(R.styleable.VCalendar_android_textColor, 0xFF333333.toInt())
        }.recycle()

        dateData = cal.dateData
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        itemHeightF = if (itemHeight > 0) itemHeight.toFloat() else width / 7f
        maxHeight = rect.top + (itemHeightF * (dateData?.months?.get(1)?.weeks?.size ?: 5)).toInt()
        setMeasuredDimension(width, preHeight.roundToInt())
        rect.set(0, 120, measuredWidth, measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        drawWeekday(canvas)

        dateData?.let {
            if (isWeekMode) {
                val y0 = rect.top + itemHeightF * 0.5f - diffY
                drawWeek(canvas, it.weeks[0], width * -1, y0)
                drawWeek(canvas, it.weeks[1], width * 0, y0)
                drawWeek(canvas, it.weeks[2], width * +1, y0)
            } else {
                val dayOfM = cal[Calendar.DAY_OF_MONTH]
                drawMonth(canvas, it.months[0], dayOfM, width * -1)
                drawMonth(canvas, it.months[1], dayOfM, width * 0)
                drawMonth(canvas, it.months[2], dayOfM, width * +1)
            }
        }
    }

    private fun drawWeekday(canvas: Canvas) {
        val y = rect.top * 0.5f - diffY
        arrayOf("日", "一", "二", "三", "四", "五", "六").forEachIndexed { h, s ->
            val x = rect.left + rect.width() / 7f * (h + 0.5f)
            canvas.drawText(s, x, y, paint)
        }
    }

    private fun drawMonth(canvas: Canvas, month: Month, dayOfM: Int, offset: Int) {
        val dayOfM2 = dayOfM.coerceAtMost(month.weeks.last().days.map { it.d }.max() ?: 1)
        val line = month.lineOfDay(dayOfM2)

        if (line > 0) {
            canvas.save()
            canvas.clipRect(offset, rect.top, offset + width, height)
        }

        month.weeks.forEach {
            val y0 = rect.top + itemHeightF * 0.5f - diffY
            val y1 = y0 + itemHeightF * it.line - foldHeight.coerceAtLeast(0f)
            val y2 = if (it.line == line) y1.coerceAtLeast(y0) else y1

            if (it.line == line + 1) {
                // 比选中日期更大的周不能绘制在第一行
                canvas.save()
                canvas.clipRect(offset, rect.top + itemHeightF.roundToInt(), offset + width, height)
            }

            drawWeek(canvas, it, offset, y2)

            if (it.line == line - 1) {
                canvas.restore()
            }
        }

        if (month.weeks.last().line > line) {
            // 比选中日期更大的周不能绘制在第一行
            canvas.restore()
        }
    }

    private fun drawWeek(canvas: Canvas, week: Week, offset: Int, y: Float) {
        week.days.forEachIndexed { h, day ->
            val x = rect.left + rect.width() / 7f * (h + 0.5f)
            drawDay(canvas, day, x + offset, y)
        }
    }

    private fun drawDay(canvas: Canvas, day: Day, x: Float, y: Float) {
        if (x > 0 && x < width && day.d == cal[Calendar.DAY_OF_MONTH] && day.m == cal[Calendar.MONTH]) {
            paint.color = textColorHint
            canvas.drawCircle(x, y + diffY, itemHeightF / 4, paint)
            paint.color = textColor
        }
        canvas.drawText(day.d.toString(), x, y, paint)
    }

    private var lastX = 0f
    private var lastY = 0f
    private lateinit var velocityTracker: VelocityTracker
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (xAnimator.isRunning) xAnimator.cancel()
                if (yAnimator.isRunning) yAnimator.cancel()
                if (scrollX != 0) {
                    scrollState = SCROLL_HORI
                    velocityTracker = VelocityTracker.obtain()
                } else if (!isWeekMode && foldHeight.absoluteValue > 0) {
                    scrollState = SCROLL_VERT
                    velocityTracker = VelocityTracker.obtain()
                }
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (scrollState == SCROLL_NONE) {
                    if (abs(event.y - lastY) > scrollSlop) {
                        scrollState = SCROLL_VERT
                        velocityTracker = VelocityTracker.obtain()
                    } else if (abs(event.x - lastX) > scrollSlop) {
                        scrollState = SCROLL_HORI
                        velocityTracker = VelocityTracker.obtain()
                    }
                }

                if (scrollState == SCROLL_HORI) {
                    velocityTracker.addMovement(event)
                    scrollX = (scrollX + lastX - event.x).roundToInt().coerceIn(-width, width)
                    lastX = event.x
                } else if (scrollState == SCROLL_VERT) {
                    velocityTracker.addMovement(event)
                    foldHeight = (foldHeight + lastY - event.y).coerceIn(minOf(foldHeight, 0f), maxFoldHeight)
                    requestLayout()
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                if (scrollState != SCROLL_NONE) {
                    velocityTracker.addMovement(event)
                    velocityTracker.computeCurrentVelocity(1000)
                }

                if (scrollState == SCROLL_HORI) {
                    snapX(velocityTracker.xVelocity)
                    velocityTracker.recycle()
                } else if (scrollState == SCROLL_VERT) {
                    snapY(velocityTracker.yVelocity)
                    velocityTracker.recycle()
                } else {
                    selectDateByPoint(event.x, event.y)
                }

                scrollState = SCROLL_NONE
            }
        }
        return true
    }

    private fun snapX(xv: Float) {
        if (xv.absoluteValue < flingSlop) {
            smoothScrollXBy(if (abs(scrollX) < width / 2) 0 else if (scrollX > 0) width else -width)
        } else {
            smoothScrollXBy(if (scrollX * xv > 0) 0 else if (scrollX > 0) width else -width)
        }
    }

    fun snapY(yv: Float) {
        if (isWeekMode || foldHeight == 0f) return
        if (yv.absoluteValue < flingSlop) {
            smoothScrollYBy(if (foldHeight < maxFoldHeight / 2) 0f else maxFoldHeight)
        } else {
            smoothScrollYBy(if (yv > 0) 0f else maxFoldHeight)
        }
    }

    private val xAnimator by lazy {
        ValueAnimator.ofInt(0, 0)
                .setDuration(256)
                .apply {
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        scrollX = it.animatedValue as Int
                    }
                    addListener(onEnd = {
                        if (scrollX.absoluteValue == width) {
                            if (isWeekMode) {
                                cal.add(Calendar.WEEK_OF_YEAR, if (scrollX > 0) 1 else -1)
                                dateData = cal.dateData
                                scrollX = 0
                                dateSelectedListener?.accept(cal.clone() as Calendar)
                            } else {
                                cal.add(Calendar.MONTH, if (scrollX > 0) 1 else -1)
                                val rowDiff = dateData?.let {
                                    val lineCount = it.months[if (scrollX > 0) 2 else 0].weeks.size
                                    lineCount - it.months[1].weeks.size
                                }
                                if (rowDiff != null && rowDiff != 0) {
                                    foldHeight += itemHeightF * rowDiff
                                    smoothScrollYBy(foldHeight - itemHeightF * rowDiff)
                                }
                                dateData = cal.dateData
                                scrollX = 0
                                dateSelectedListener?.accept(cal.clone() as Calendar)
                            }
                        }
                    })
                }
    }

    private val yAnimator by lazy {
        ValueAnimator.ofFloat(0f, 0f)
                .setDuration(256)
                .apply {
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        foldHeight = it.animatedValue as Float
                        requestLayout()
                    }
                }
    }

    private fun smoothScrollXBy(targetX: Int) {
        xAnimator.setIntValues(scrollX, targetX)
        xAnimator.start()
    }

    private fun smoothScrollYBy(targetY: Float) {
        yAnimator.setFloatValues(foldHeight, targetY)
        yAnimator.start()
    }

    private fun selectDateByPoint(x: Float, y: Float) {
        if (y < rect.top) return

        val hi = (x / width * 7f).toInt()
        dateData?.let {
            if (isWeekMode) {
                it.weeks[1]
            } else {
                val vi = ((y - rect.top) / itemHeightF).toInt()
                it.months[1].weeks[vi]
            }
        }?.days?.get(hi)?.let {
            cal[Calendar.YEAR] = it.y
            cal[Calendar.MONTH] = it.m
            cal[Calendar.DAY_OF_MONTH] = it.d
        }
        dateData = cal.dateData
        invalidate()
        dateSelectedListener?.accept(cal.clone() as Calendar)
    }

    var dateSelectedListener: Consumer<Calendar>? = null
        set(value) {
            field = value
            value?.accept(cal.clone() as Calendar)
        }

    fun setDate(callback: Consumer<Calendar>) {
        callback.accept(cal)
        dateData = cal.dateData
    }

    override fun getBehavior(): CoordinatorLayout.Behavior<*> = VBehavior()

    fun consume(dy: Int): Int {
        val last = (foldHeight + dy).coerceIn(minOf(foldHeight, 0f), maxFoldHeight)
        val c = last - foldHeight
        foldHeight = last
        requestLayout()
        return c.toInt()
    }

    companion object {
        const val SCROLL_NONE = 'N'
        const val SCROLL_VERT = 'V'
        const val SCROLL_HORI = 'H'
    }

}

class VBehavior : CoordinatorLayout.Behavior<VCalendar>() {

    private var isNestedScrolling = false

    override fun onStartNestedScroll(coordinatorLayout: CoordinatorLayout, child: VCalendar, directTargetChild: View, target: View, axes: Int, type: Int): Boolean {
//        "onStartNestedScroll - $type".print()
        return axes and View.SCROLL_AXIS_VERTICAL != 0
    }

    override fun onNestedScrollAccepted(coordinatorLayout: CoordinatorLayout, child: VCalendar, directTargetChild: View, target: View, axes: Int, type: Int) {
//        "onNestedScrollAccepted - $type".print()
    }

    override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout, child: VCalendar, target: View, type: Int) {
//        "onStopNestedScroll - $type".print()
        if (isNestedScrolling) {
            child.snapY(0f)
            isNestedScrolling = false
        }
    }

    override fun onNestedPreScroll(coordinatorLayout: CoordinatorLayout, child: VCalendar, target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
//        "onNestedPreScroll - $type - $dy".print()
        isNestedScrolling = true
        if (type == ViewCompat.TYPE_TOUCH && dy > 0) {
            consumed[1] = child.consume(dy)
        }
    }

    override fun onNestedScroll(coordinatorLayout: CoordinatorLayout, child: VCalendar, target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, type: Int) {
//        "onNestedScroll - $type - $dyConsumed - ".print()
        if (type == ViewCompat.TYPE_TOUCH && dyUnconsumed < 0) {
            child.consume(dyUnconsumed)
        }
    }

    override fun onNestedPreFling(coordinatorLayout: CoordinatorLayout, child: VCalendar, target: View, velocityX: Float, velocityY: Float): Boolean {
//        "onNestedPreFling - $velocityY".print()
        if (isNestedScrolling) {
            child.snapY(-velocityY)
            isNestedScrolling = false
        }
        return super.onNestedPreFling(coordinatorLayout, child, target, velocityX, velocityY)
    }

    override fun onNestedFling(coordinatorLayout: CoordinatorLayout, child: VCalendar, target: View, velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
//        "onNestedFling - $velocityY".print()
        return super.onNestedFling(coordinatorLayout, child, target, velocityX, velocityY, consumed)
    }

}

class ScrollingViewBehavior(context: Context, attrs: AttributeSet) : CoordinatorLayout.Behavior<View>(context, attrs) {

    override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        return (dependency is VCalendar)
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        child.translationY = minOf(dependency.measuredHeight.toFloat(), (dependency as VCalendar).preHeight)
        return true
    }

    // @see { com.google.android.material.appbar.HeaderScrollingViewBehavior#onMeasureChild }
    override fun onMeasureChild(parent: CoordinatorLayout, child: View, parentWidthMeasureSpec: Int, widthUsed: Int, parentHeightMeasureSpec: Int, heightUsed: Int): Boolean {
        val childLpHeight = child.layoutParams.height
        if (childLpHeight == -1 || childLpHeight == -2) {
            parent.getDependencies(child)
                    .find { it is VCalendar }
                    ?.let { it as VCalendar }
                    ?.let {
                        var availableHeight = View.MeasureSpec.getSize(parentHeightMeasureSpec)
                        if (availableHeight == 0) {
                            availableHeight = parent.height
                        }
                        val height = availableHeight - it.minHeight.roundToInt()
                        val model = if (childLpHeight == -1) View.MeasureSpec.AT_MOST else View.MeasureSpec.UNSPECIFIED
                        val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(height, model)
                        parent.onMeasureChild(child, parentWidthMeasureSpec, widthUsed, heightMeasureSpec, heightUsed)
                        return true
                    }
        }
        return false
    }

}