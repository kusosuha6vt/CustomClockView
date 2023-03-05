package com.example.customclockview

import android.content.Context
import android.graphics.*
import android.icu.util.Calendar
import android.icu.util.GregorianCalendar
import android.icu.util.TimeZone
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.View
import java.lang.Integer.min
import java.util.*

class AnalogClockView(
    context: Context,
    attributesSet: AttributeSet?,
    defStyleAttr: Int,
    defStyleRes: Int
) : View(context, attributesSet, defStyleAttr, defStyleRes) {
    private var timeInSeconds = 0
        set(value) {
            field = value % (24 * 60 * 60)
            postInvalidate()  // can be called not in ui thread
        }
    private var timeZone: TimeZone = TimeZone.getDefault()
        set(value) {
            field = value
            updateTime()
        }
    private var timer: Timer? = null
    private var fieldRect = RectF()  // where can draw
    private var bmClockBase: Bitmap =
        BitmapFactory.decodeResource(resources, R.drawable.clock2_base)
    private var bmHandleSecond: Bitmap =
        BitmapFactory.decodeResource(resources, R.drawable.clock2_second)
    private var bmHandleMinute: Bitmap =
        BitmapFactory.decodeResource(resources, R.drawable.clock2_minute)
    private var bmHandleHour: Bitmap =
        BitmapFactory.decodeResource(resources, R.drawable.clock2_hour)
    private var matrix = Matrix()
    private var scaleBitmap = 1f
    private var clockRadius = 0f

    constructor(context: Context, attributesSet: AttributeSet?, defStyleAttr: Int) : this(
        context,
        attributesSet,
        defStyleAttr,
        0
    )

    constructor(context: Context, attributesSet: AttributeSet?) : this(context, attributesSet, 0)
    constructor(context: Context) : this(context, null)

    init {
        // styles are useless right now
        initAttributeSet(attributesSet, defStyleAttr, defStyleRes)
    }

    private fun initAttributeSet(
        attributesSet: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) {
        if (attributesSet == null) {
            return
        }
        val typedArray = context.obtainStyledAttributes(
            attributesSet,
            R.styleable.AnalogClockView,
            defStyleAttr,
            defStyleRes
        )
        val timeZoneStr = typedArray.getString(R.styleable.AnalogClockView_timezone) ?: ""
        timeZone = TimeZone.getTimeZone(timeZoneStr)
        if (timeZoneStr.isEmpty()) {
            timeZone = TimeZone.getDefault()
        }
        typedArray.recycle()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        timer = Timer()
        timer!!.schedule(object : TimerTask() {
            override fun run() {
                updateTime()
            }
        }, 0, 1000)
    }

    override fun onDetachedFromWindow() {
        timer?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val safeWidth = w - paddingLeft - paddingRight
        val safeHeight = h - paddingTop - paddingBottom

        val clockSize = min(safeWidth, safeHeight)

        scaleBitmap = clockSize.toFloat() / bmClockBase.width
        clockRadius = bmClockBase.width * scaleBitmap / 2

        // if got a rectangle, put the clock in the center
        fieldRect.left = paddingLeft + (safeWidth - clockSize) / 2f
        fieldRect.top = paddingTop + (safeHeight - clockSize) / 2f
        fieldRect.right = fieldRect.left + clockSize
        fieldRect.bottom = fieldRect.top + clockSize
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minWidth = suggestedMinimumWidth + paddingLeft + paddingRight
        val minHeight = suggestedMinimumHeight + paddingTop + paddingBottom

        val desiredSizeInPixels = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            DESIRED_SIZE,
            resources.displayMetrics
        ).toInt()
        val desiredWidth = minWidth.coerceAtLeast(desiredSizeInPixels + paddingLeft + paddingRight)
        val desiredHeight =
            minHeight.coerceAtLeast(desiredSizeInPixels + paddingTop + paddingBottom)

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (canvas == null || fieldRect.width() <= 0 || fieldRect.height() <= 0) return
        drawBase(canvas)
        val hoursDegree = 360f * timeInSeconds / (12 * 60 * 60)
        drawHandle(canvas, bmHandleHour, HOUR_TAIL, hoursDegree)
        val minutesDegree = 360f * (timeInSeconds % (60 * 60)) / (60 * 60)
        drawHandle(canvas, bmHandleMinute, MINUTE_TAIL, minutesDegree)
        val secondsDegree = 360f * (timeInSeconds % 60) / 60
        drawHandle(canvas, bmHandleSecond, SECOND_TAIL, secondsDegree)
    }

    private fun drawBase(canvas: Canvas) {
        matrix.setScale(scaleBitmap, scaleBitmap)
        matrix.postTranslate(fieldRect.left, fieldRect.top)
        canvas.drawBitmap(bmClockBase, matrix, null)
    }

    private fun drawHandle(canvas: Canvas, bmHandle: Bitmap, tail: Float, degree: Float) {
        // set handle center to (clockRadius, clockRadius)
        matrix.setScale(scaleBitmap, scaleBitmap)
        matrix.postTranslate(
            clockRadius - bmHandle.width * scaleBitmap / 2,
            clockRadius - bmHandle.height * scaleBitmap * (1 - tail)
        )
        matrix.postRotate(degree, clockRadius, clockRadius)
        matrix.postTranslate(fieldRect.left, fieldRect.top)
        canvas.drawBitmap(bmHandle, matrix, null)
    }

    private fun updateTime() {
        val time = GregorianCalendar.getInstance(timeZone)
        timeInSeconds = time.get(Calendar.SECOND) +
                60 * (time.get(Calendar.MINUTE) +
                60 * time.get(Calendar.HOUR))
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()!!
        val savedState = SavedState(superState)
        savedState.timeZone = timeZone
        return savedState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val savedState = state as SavedState
        super.onRestoreInstanceState(savedState.superState)
        timeZone = savedState.timeZone
    }

    class SavedState : BaseSavedState {
        lateinit var timeZone: TimeZone

        constructor(superState: Parcelable) : super(superState)
        constructor(parcel: Parcel) : super(parcel) {
            val timeZoneId = parcel.readString()
            timeZone = TimeZone.getTimeZone(timeZoneId)
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeString(timeZone.id)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(parcel: Parcel): SavedState {
                    return SavedState(parcel)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return Array(size) { null }
                }
            }
        }
    }

    companion object {
        const val DESIRED_SIZE = 50f

        //           one of 3 clock handles:
        // |===O======================>
        //   A             B
        // tail is A / (A + B)
        const val SECOND_TAIL = 0.30f
        const val MINUTE_TAIL = 0.12f
        const val HOUR_TAIL = 0.15f
    }
}