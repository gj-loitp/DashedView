package com.mackhartley.dashedview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import java.lang.Math.pow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class DashedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // todo need to be able to set dash color interface
    // Todo consolidate as many math calls as possible for efficiency sake
    // Future improvement: Could limit max length of lines. For low dash angles such as 1 - 5 the lines are drawn quite far outside of the screen.
    // todo test performance

    //TOdo use VIEW_TOP and other helpers whereever it makes code more readable

    // Make git project:
    // Todo would be nice to be able to set an outline stroke with a custom width and color

    // Instance state
    private var dashWidth = DEFAULT_WIDTH
    private var spaceBetweenDashes = DEFAULT_SPACE_BETWEEN_DASHES
    private var dashAngle = DEFAULT_DASH_ANGLE

    @ColorInt
    private var dashColor = DEFAULT_COLOR
    private var cornerRadius = DEFAULT_CORNER_RADIUS

    private var lastWidth = width // Used for keeping track of view size
    private var lastHeight = height // Used for keeping track of view size

    private val roundedCornersClipPath by lazy { // This path is used to clip the progress background and drawable to the desired corner radius
        Path().apply {
            addRoundRect(
                0f,
                0f,
                lastWidth.toFloat(),
                lastHeight.toFloat(),
                floatArrayOf(cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius),
                Path.Direction.CW
            )
        }
    }

    private val dashPaint by lazy {
        Paint().apply {
            strokeCap = Paint.Cap.BUTT
            color = dashColor
            strokeWidth = dashWidth
            isAntiAlias = true
        }
    }

    private val dashPaint2 by lazy {
        Paint().apply {
            strokeCap = Paint.Cap.BUTT
            color = Color.GREEN
            strokeWidth = dashWidth
            isAntiAlias = true
        }
    }

    private val dashPaint3 by lazy {
        Paint().apply {
            strokeCap = Paint.Cap.BUTT
            color = Color.BLUE
            strokeWidth = dashWidth
            isAntiAlias = true
        }
    }

    companion object {
        const val DEFAULT_WIDTH = 4f
        const val DEFAULT_SPACE_BETWEEN_DASHES = 4f
        const val DEFAULT_COLOR = Color.GRAY
        const val DEFAULT_CORNER_RADIUS = 0f
        const val VIEW_LEFT = 0f // todo use this somehow
        const val VIEW_TOP = 0f
        const val DEFAULT_DASH_ANGLE = 45 // Measured in degrees. Min 0, max 179
    }

    init {
        val attrRefs = context.obtainStyledAttributes(attrs, R.styleable.DashedView)
        dashWidth = attrRefs.getDimension(R.styleable.DashedView_dashWidth, DEFAULT_WIDTH)
        spaceBetweenDashes = attrRefs.getDimension(R.styleable.DashedView_spaceBetweenDashes, DEFAULT_SPACE_BETWEEN_DASHES)
        dashColor = attrRefs.getColor(R.styleable.DashedView_dashColor, DEFAULT_COLOR)
        cornerRadius = attrRefs.getDimension(R.styleable.DashedView_cornerRadius, DEFAULT_CORNER_RADIUS)

        val requestedAngle = attrRefs.getInteger(R.styleable.DashedView_dashAngle, DEFAULT_DASH_ANGLE)
        dashAngle = parseRequestedDashAngle(requestedAngle)

        attrRefs.recycle()
    }

    private fun parseRequestedDashAngle(requestedAngle: Int): Int {
        return requestedAngle % 180
    }

    override fun onDraw(canvas: Canvas?) {

        if (canvas != null) {
            canvas.clipPath(roundedCornersClipPath)

            val linesOriginatingFromXAxis = getLinesOriginatingFromXAxis(width.toFloat(), dashWidth, spaceBetweenDashes, dashAngle, height.toFloat())
            val linesOriginatingFromYAxis = getLinesOriginatingFromYAxis(dashAngle, height.toFloat(), width.toFloat(), dashWidth, spaceBetweenDashes) //todo make these have same args list

            val dashDirection = getDashDirection(dashAngle)
            val allLinesToDraw = when {
                dashDirection.isHorizontal -> linesOriginatingFromYAxis // If horizontal, dont draw lines originating from X axis
                dashDirection is DashDirection.LeftToRight -> linesOriginatingFromYAxis.reversed() + linesOriginatingFromXAxis
                dashDirection is DashDirection.RightToLeft -> linesOriginatingFromXAxis.reversed() + linesOriginatingFromYAxis
                else -> linesOriginatingFromXAxis // If vertical, dont draw lines from Y axis
            }

            for ((index, curCoords) in allLinesToDraw.withIndex()) {
                val startPoint = curCoords.startPoint
                val endPoint = curCoords.endPoint

                if (index % 3 == 0) {
                    canvas.drawLine(startPoint.first, startPoint.second, endPoint.first, endPoint.second, dashPaint)
                } else if (index % 3 == 1) {
                    canvas.drawLine(startPoint.first, startPoint.second, endPoint.first, endPoint.second, dashPaint2)
                } else {
                    canvas.drawLine(startPoint.first, startPoint.second, endPoint.first, endPoint.second, dashPaint3)
                }
            }
        }
    }

    // todo write unit tests
    private fun getLinesOriginatingFromXAxis(
        width: Float,
        dashWidth: Float,
        spaceBetweenDashes: Float,
        dashAngle: Int,
        viewHeight: Float
    ): List<LineCoordinates> {

        // Check if horizontal config. If so, no lines drawn from x axis
        val dashDirection = getDashDirection(dashAngle)
        if (dashDirection.isHorizontal) return emptyList() // If dashes are horizontal (0 or 180 degrees) then no lines will originate from the x axis

        // Calculate start points
        val startPoints = mutableListOf<Pair<Float, Float>>()
        val startYPosition = viewHeight

        var curXPosition: Float

        when (dashDirection) {
            is DashDirection.LeftToRight,
            is DashDirection.Vertical -> {
                curXPosition = 0f
                while (curXPosition <= width) {
                    startPoints.add(Pair(curXPosition, startYPosition))
                    curXPosition += (calculateHypotenuseLen(dashAngle, dashWidth) + calculateHypotenuseLen(dashAngle, spaceBetweenDashes))
                }
            }
            is DashDirection.RightToLeft -> { // If the dashes are pointing from right to left, then start drawing dashes from the bottom right corner of the view
                curXPosition = width
                while (curXPosition >= 0) {
                    startPoints.add(Pair(curXPosition, startYPosition))
                    curXPosition -= (calculateHypotenuseLen(dashAngle, dashWidth) + calculateHypotenuseLen(dashAngle, spaceBetweenDashes))
                }
            }
        }
        startPoints.add(Pair(curXPosition, startYPosition)) // Add one more line to ensure the view is not missing one on the end

        // Calculate translation required to generate end point for a given start point
        // Apply translation to list of start points to get line coordinates for dashes
        val lineCoordinates = startPoints.map {
            LineCoordinates(
                startPoint = it,
                endPoint = getEndPoint(it, VIEW_TOP, dashAngle, viewHeight, width)
            )
        }

        // Translate start and end points so all 4 corners of dash are drawn outside of the view
        val elongatedLineCoordinates = elongateDashesOriginatingFromXAxis(
            dashAngle,
            dashWidth,
            lineCoordinates
        )

        return elongatedLineCoordinates
    }

    private fun getLinesOriginatingFromYAxis(
        dashAngle: Int,
        viewHeight: Float,
        viewWidth: Float,
        dashWidth: Float,
        spaceBetweenDashes: Float
    ): List<LineCoordinates> {

        // Check if vertical config. If so, no lines should be drawn from the y axis
        val dashDirection = getDashDirection(dashAngle)
        if (dashDirection is DashDirection.Vertical) return emptyList() // If all dashes are vertical (90 degrees) then no dashes will originate from the y axis

        // Calculate start points
        val startPositions = mutableListOf<Pair<Float, Float>>()
        val startXPosition = if (dashDirection is DashDirection.LeftToRight) 0f else viewWidth

        var curYPosition = viewHeight // This is the bottom left corner of the view
        if (!dashDirection.isHorizontal) // If lines will be drawn from the x axis
            curYPosition -= (calculateVerticalOffset(dashWidth, dashAngle) + calculateVerticalOffset(spaceBetweenDashes, dashAngle)) // The y = 0 position already has a dash drawn from the x axis

        while (curYPosition >= 0) {
            startPositions.add(Pair(startXPosition, curYPosition)) // todo ensure this never becomes an infinite loop. Same for all other loops
            curYPosition -= abs(calculateVerticalOffset(dashWidth, dashAngle) + calculateVerticalOffset(spaceBetweenDashes, dashAngle)) // The y = 0 position already has a dash drawn from the horizontal algo
        }
        startPositions.add(Pair(startXPosition, curYPosition)) // Add one more line to ensure the view is not missing one on the end

        // Calculate translation required to generate end point for a given start point
        // Apply translation to list of start points to get line coordinates for dashes
        val lineCoordinates = startPositions.map {
            LineCoordinates(
                startPoint = Pair(
                    it.first,
                    it.second
                ),
                endPoint = getEndPoint(
                    startPoint = it,
                    endY = it.second - viewHeight,// Subtract view height because each of these lines has a different start point Y value
                    angle = dashAngle,
                    viewHeight = viewHeight,
                    viewWidth = viewWidth
                )
            )
        }

        // Translate start and end points so all 4 corners of dash are drawn outside of the view
        val elongatedLineCoordinates = elongateDashesOriginatingFromYAxis(
            dashAngle,
            dashWidth,
            lineCoordinates
        )

        return elongatedLineCoordinates
    }

    /**
     * Elongate the dashes enough so that all 4 corners of each dash are extended out of the view
     * canvas
     */
    private fun elongateDashesOriginatingFromXAxis(
        dashAngle: Int,
        dashWidth: Float,
        initialPositions: List<LineCoordinates>
    ): List<LineCoordinates> {
        val hypotRadians = Math.toRadians((abs(90 - dashAngle).toDouble()))
        val translationHypot = (dashWidth * tan((hypotRadians))) / 2
        val xTranslation = translationHypot * sin(Math.toRadians((abs(90 - dashAngle).toDouble())))
        val yTranslation = translationHypot * cos(Math.toRadians((abs(90 - dashAngle).toDouble())))
        // todo change to use translation modifier

        return initialPositions.map {
            LineCoordinates(
                Pair(
                    it.startPoint.first + getXTranslationToConcealLineCorners(xTranslation, dashAngle, false),
                    it.startPoint.second + yTranslation.toFloat()
                ),
                Pair(
                    it.endPoint.first + getXTranslationToConcealLineCorners(xTranslation, dashAngle, true),
                    it.endPoint.second - yTranslation.toFloat()
                )
            )
        }
    }

    /**
     * Elongate the dashes enough so that all 4 corners of each dash are extended out of the view
     * canvas
     */
    private fun elongateDashesOriginatingFromYAxis(
        dashAngle: Int,
        dashWidth: Float,
        initialPositions: List<LineCoordinates>
    ): List<LineCoordinates> {

        val hypotRadians = Math.toRadians(dashAngle.toDouble())
        val translationHypot = (dashWidth * tan(hypotRadians)) / 2
        val xTranslation = abs(translationHypot * cos(hypotRadians)).toFloat()
        val yTranslation = abs(translationHypot * sin(hypotRadians)).toFloat()

        val translationModifier = when (getDashDirection(dashAngle)) {
            is DashDirection.LeftToRight -> -1
            is DashDirection.RightToLeft -> 1
            DashDirection.Vertical -> 0
        }

        return initialPositions.map {
            LineCoordinates(
                Pair(
                    it.startPoint.first + (xTranslation * translationModifier),
                    it.startPoint.second + yTranslation
                ),
                it.endPoint // Endpoint can remain unchanged for lines drawn from Y Axis. Their top corners wont show
            )
        }
    }

    private fun getDashDirection(dashAngle: Int): DashDirection {
        return when {
            dashAngle == 0 -> DashDirection.LeftToRight(true)
            dashAngle < 90 -> DashDirection.LeftToRight(false)
            dashAngle == 90 -> DashDirection.Vertical
            else -> DashDirection.RightToLeft(false)
        }
    }

    private fun getEndPoint(startPoint: Pair<Float, Float>, endY: Float, angle: Int, viewHeight: Float, viewWidth: Float): Pair<Float, Float> {
//        val maxLength = lineLength(Pair(0f, 0f), Pair(viewWidth, viewHeight))
        if (getDashDirection(angle).isHorizontal) return Pair(viewWidth, startPoint.second)

        val radians = Math.toRadians(angle.toDouble())
        val endXTrans = viewHeight / tan(radians).toFloat()

        val endPoint = Pair(startPoint.first + endXTrans, endY)
//        val closestPossibleEndPoint = calculateClosest(endPoint, startPoint, maxLength) todo get working
        return endPoint


    }

//    private fun calculateClosest(
//        endPoint: Pair<Float, Float>,
//        startPoint: Pair<Float, Float>,
//        maxLen: Float
//    ): Pair<Float, Float> {
//        val pointDist = lineLength(startPoint, endPoint)
//        if (pointDist > maxLen) {
//            val reductionRatio = maxLen / pointDist
//
//            val xDiff
//            return Pair(
//                endPoint.first * reductionRatio,
//                endPoint.second * reductionRatio
//            )
//        }
//        return endPoint
//    }
//
//    private fun lineLength(start: Pair<Float, Float>, end: Pair<Float, Float>): Float {
//        val xDiff = abs(start.first - end.first)
//        val yDiff = abs(start.second - end.second)
//        return sqrt(xDiff.toDouble().pow(2.0) + yDiff.toDouble().pow(2.0)).toFloat()
//    }

    private fun calculateVerticalOffset(width: Float, angle: Int): Float {
        val complementaryAngle = Math.toRadians(abs(90 - angle).toDouble())
        val halfVerticalLengthDashCrossSection = width / (2 * sin(complementaryAngle))
        return halfVerticalLengthDashCrossSection.toFloat() * 2f
    }

    /**
     * Gets the x translation required to sufficiently cover the corners of a line. Essentially this
     * function give info on how far a line should be extended so it doesn't show it's corners. This
     * is more important when dashes get thicker and resemble rectangles instead of lines
     */
    private fun getXTranslationToConcealLineCorners(
        xTranslation: Double,
        angle: Int,
        isTop: Boolean
    ): Float {
        val dashDirection = getDashDirection(angle)
        val adjustedXTranslation: Double = when (dashDirection) {
            is DashDirection.LeftToRight -> xTranslation * -1
            is DashDirection.RightToLeft -> xTranslation
            is DashDirection.Vertical -> 0.0
        }

        val z = if (isTop) adjustedXTranslation * -1 else adjustedXTranslation
        return z.toFloat()
    }

    // todo write unit tests
    /**
     * Because the dashes can be drawn at an angle, the distance from one dash to the next should actually be
     * calculated using the hypotenuse.
     */
    private fun calculateHypotenuseLen(angle: Int, dashWidth: Float): Float {
        val radians = Math.toRadians((90 - angle).toDouble())
        return dashWidth / (cos(radians)).toFloat()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        lastWidth = w
        lastHeight = h
    }
}