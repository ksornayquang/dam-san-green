package com.damsan.green.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.damsan.green.data.model.TrashReport
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class Campus3DMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private enum class BlockKind {
        ROAD,
        GARDEN,
        BUILDING,
        FIELD,
        COURT,
        GATE,
        SERVICE
    }

    private data class CampusBlock(
        val label: String,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val height: Float,
        val color: Int,
        val roof: Int = darken(color, 0.82f),
        val kind: BlockKind = BlockKind.GARDEN
    )

    private data class CampusMarker(
        val title: String,
        val x: Float,
        val y: Float,
        val color: Int
    )

    private data class PrismFace(
        val points: List<PointF>,
        val color: Int,
        val depth: Float
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val tempRect = RectF()
    private val markers = mutableListOf<CampusMarker>()

    private var scale = 1f
    private var originX = 0f
    private var originY = 0f
    private var panX = 0f
    private var panY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastPinchDistance = 0f
    private var rotationDegrees = 248f
    private var zoomFactor = 1f
    private var isZooming = false

    private val schoolLat = 12.900868
    private val schoolLon = 108.291116

    private val wallCream = Color.rgb(236, 225, 198)
    private val wallWhite = Color.rgb(231, 238, 236)
    private val roofRed = Color.rgb(147, 48, 47)
    private val accentTeal = Color.rgb(45, 92, 96)
    private val gateGold = Color.rgb(241, 205, 122)
    private val gatePillar = Color.rgb(38, 43, 45)

    private val roads = listOf(
        CampusBlock("", 0.06f, 0.02f, 0.86f, 0.055f, 0.008f, Color.rgb(225, 225, 219), kind = BlockKind.ROAD),
        CampusBlock("", 0.025f, 0.08f, 0.055f, 0.86f, 0.008f, Color.rgb(225, 225, 219), kind = BlockKind.ROAD),
        CampusBlock("", 0.08f, 0.93f, 0.86f, 0.045f, 0.008f, Color.rgb(225, 225, 219), kind = BlockKind.ROAD),
        CampusBlock("", 0.92f, 0.08f, 0.045f, 0.84f, 0.008f, Color.rgb(225, 225, 219), kind = BlockKind.ROAD),
        CampusBlock("Trục chính", 0.515f, 0.08f, 0.08f, 0.84f, 0.01f, Color.rgb(238, 234, 222), kind = BlockKind.ROAD),
        CampusBlock("Sân trước", 0.18f, 0.50f, 0.42f, 0.085f, 0.01f, Color.rgb(241, 237, 226), kind = BlockKind.ROAD),
        CampusBlock("Khoảng đệm", 0.17f, 0.58f, 0.28f, 0.13f, 0.01f, Color.rgb(239, 235, 224), kind = BlockKind.ROAD),
        CampusBlock("", 0.28f, 0.25f, 0.30f, 0.052f, 0.01f, Color.rgb(243, 239, 226), kind = BlockKind.ROAD),
        CampusBlock("", 0.405f, 0.16f, 0.045f, 0.34f, 0.01f, Color.rgb(243, 239, 226), kind = BlockKind.ROAD),
        CampusBlock("", 0.50f, 0.16f, 0.085f, 0.36f, 0.01f, Color.rgb(243, 239, 226), kind = BlockKind.ROAD),
        CampusBlock("", 0.685f, 0.18f, 0.06f, 0.60f, 0.011f, Color.rgb(243, 239, 226), kind = BlockKind.ROAD),
        CampusBlock("", 0.535f, 0.47f, 0.21f, 0.08f, 0.011f, Color.rgb(244, 240, 229), kind = BlockKind.ROAD),
        CampusBlock("", 0.22f, 0.455f, 0.36f, 0.085f, 0.011f, Color.rgb(246, 242, 231), kind = BlockKind.ROAD),
        CampusBlock("", 0.545f, 0.50f, 0.055f, 0.24f, 0.011f, Color.rgb(246, 242, 231), kind = BlockKind.ROAD),
        CampusBlock("", 0.60f, 0.72f, 0.23f, 0.06f, 0.01f, Color.rgb(244, 240, 229), kind = BlockKind.ROAD),
        CampusBlock("Lối vào", 0.03f, 0.62f, 0.17f, 0.06f, 0.01f, Color.rgb(238, 234, 222), kind = BlockKind.ROAD)
    )

    private val zones = listOf(
        CampusBlock("Vườn 11A1", 0.11f, 0.08f, 0.17f, 0.16f, 0.012f, Color.rgb(73, 191, 113), kind = BlockKind.GARDEN),
        CampusBlock("Đài nước", 0.29f, 0.09f, 0.08f, 0.09f, 0.022f, Color.rgb(121, 178, 190), kind = BlockKind.SERVICE),
        CampusBlock("Nhà ăn", 0.29f, 0.15f, 0.11f, 0.08f, 0.038f, wallCream, roofRed, BlockKind.BUILDING),
        CampusBlock("NT học sinh 1", 0.43f, 0.20f, 0.052f, 0.245f, 0.096f, wallWhite, roofRed, BlockKind.BUILDING),
        CampusBlock("NT học sinh 2", 0.615f, 0.21f, 0.052f, 0.245f, 0.096f, wallWhite, roofRed, BlockKind.BUILDING),
        CampusBlock("Công vụ GV", 0.245f, 0.29f, 0.078f, 0.155f, 0.09f, wallCream, roofRed, BlockKind.BUILDING),
        CampusBlock("Vườn 10A3", 0.36f, 0.31f, 0.20f, 0.18f, 0.012f, Color.rgb(238, 218, 80), kind = BlockKind.GARDEN),
        CampusBlock("Cầu lông", 0.75f, 0.31f, 0.09f, 0.12f, 0.014f, Color.rgb(221, 232, 225), kind = BlockKind.COURT),
        CampusBlock("Vườn 11A5", 0.74f, 0.12f, 0.15f, 0.19f, 0.012f, Color.rgb(221, 207, 76), kind = BlockKind.GARDEN),
        CampusBlock("Vườn 11A3", 0.74f, 0.32f, 0.14f, 0.13f, 0.012f, Color.rgb(74, 188, 91), kind = BlockKind.GARDEN),
        CampusBlock("Lớp + thư viện", 0.255f, 0.505f, 0.28f, 0.052f, 0.09f, wallWhite, roofRed, BlockKind.BUILDING),
        CampusBlock("Hiệu bộ", 0.455f, 0.610f, 0.072f, 0.112f, 0.098f, wallWhite, roofRed, BlockKind.BUILDING),
        CampusBlock("Sân vận động", 0.69f, 0.46f, 0.23f, 0.31f, 0.018f, Color.rgb(215, 221, 216), kind = BlockKind.FIELD),
        CampusBlock("Cổng trường", -0.03f, 0.64f, 0.12f, 0.09f, 0.07f, gateGold, gatePillar, BlockKind.GATE),
        CampusBlock("Bảo vệ", 0.255f, 0.59f, 0.048f, 0.052f, 0.03f, wallCream, roofRed, BlockKind.BUILDING),
        CampusBlock("Nhà xe", 0.13f, 0.72f, 0.04f, 0.20f, 0.026f, Color.rgb(232, 224, 210), roofRed, BlockKind.SERVICE),
        CampusBlock("Sân trung tâm", 0.30f, 0.60f, 0.25f, 0.20f, 0.016f, Color.rgb(236, 228, 209), kind = BlockKind.COURT),
        CampusBlock("Lớp 10 phòng", 0.29f, 0.77f, 0.27f, 0.058f, 0.09f, wallWhite, roofRed, BlockKind.BUILDING),
        CampusBlock("PCCC", 0.57f, 0.53f, 0.035f, 0.035f, 0.02f, Color.rgb(118, 161, 180), kind = BlockKind.SERVICE),
        CampusBlock("Trạm điện", 0.22f, 0.52f, 0.035f, 0.038f, 0.032f, Color.rgb(218, 210, 188), roofRed, BlockKind.SERVICE),
        CampusBlock("Vườn 11A2", 0.21f, 0.84f, 0.11f, 0.11f, 0.012f, Color.rgb(72, 188, 143), kind = BlockKind.GARDEN),
        CampusBlock("Vườn 10A2", 0.37f, 0.84f, 0.13f, 0.11f, 0.012f, Color.rgb(239, 191, 70), kind = BlockKind.GARDEN),
        CampusBlock("Nhà VH", 0.13f, 0.84f, 0.085f, 0.11f, 0.04f, wallCream, roofRed, BlockKind.BUILDING),
        CampusBlock("Vườn 10A5", 0.56f, 0.78f, 0.08f, 0.16f, 0.012f, Color.rgb(247, 211, 74), kind = BlockKind.GARDEN),
        CampusBlock("Nhà đa năng", 0.64f, 0.80f, 0.13f, 0.15f, 0.052f, wallCream, roofRed, BlockKind.BUILDING),
        CampusBlock("Hồ nước", 0.78f, 0.82f, 0.17f, 0.12f, 0.01f, Color.rgb(125, 181, 185), kind = BlockKind.SERVICE),
        CampusBlock("Vườn 10A6", 0.78f, 0.76f, 0.12f, 0.06f, 0.012f, Color.rgb(244, 212, 73), kind = BlockKind.GARDEN)
    ).sortedBy { it.x + it.y }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        textPaint.textAlign = Paint.Align.CENTER
    }

    fun setReports(reports: List<TrashReport>) {
        markers.clear()
        reports
            .filter { it.latitude != 0.0 && it.longitude != 0.0 }
            .takeLast(14)
            .forEachIndexed { index, report ->
                markers.add(
                    CampusMarker(
                        title = report.className.ifBlank { "Báo cáo" },
                        x = longitudeToCampusX(report.longitude, index),
                        y = latitudeToCampusY(report.latitude, index),
                        color = if (report.status == "approved") Color.rgb(36, 145, 98) else Color.rgb(21, 96, 111)
                    )
                )
            }
        invalidate()
    }

    fun zoomIn() {
        zoomFactor = (zoomFactor * 1.18f).coerceIn(MIN_ZOOM, MAX_ZOOM)
        invalidate()
    }

    fun zoomOut() {
        zoomFactor = (zoomFactor / 1.18f).coerceIn(MIN_ZOOM, MAX_ZOOM)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        scale = min(width * 0.84f, height * 0.58f) * zoomFactor
        originX = width * 0.50f + panX
        originY = height * 0.165f + panY

        drawBackground(canvas)
        drawCampusShadow(canvas)
        canvas.save()
        canvas.scale(1.0f, 0.96f, width * 0.5f, height * 0.25f)
        drawBase(canvas)
        drawFence(canvas)
        roads.forEach { drawFlatBlock(canvas, it) }
        zones
            .sortedBy { drawOrder(it) }
            .forEach { drawBlock(canvas, it) }
        drawTrees(canvas)
        drawMarkers(canvas)
        canvas.restore()
        drawCompass(canvas)
        drawLegend(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isZooming = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    lastPinchDistance = pointerDistance(event)
                    isZooming = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val distance = pointerDistance(event)
                    if (lastPinchDistance > 0f && distance > 0f) {
                        val zoomDelta = distance / lastPinchDistance
                        zoomFactor = (zoomFactor * zoomDelta).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        lastPinchDistance = distance
                        invalidate()
                    }
                    return true
                } else if (!isZooming) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    rotationDegrees = normalizeDegrees(rotationDegrees + dx * 0.32f)
                    panY = (panY + dy * 0.45f).coerceIn(-height * 0.08f, height * 0.16f)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    isZooming = false
                    lastPinchDistance = 0f
                    val remainingIndex = if (event.actionIndex == 0) 1 else 0
                    if (remainingIndex < event.pointerCount) {
                        lastTouchX = event.getX(remainingIndex)
                        lastTouchY = event.getY(remainingIndex)
                    }
                } else {
                    lastPinchDistance = pointerDistance(event)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isZooming = false
                lastPinchDistance = 0f
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
    }


    private fun drawBackground(canvas: Canvas) {
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(Color.rgb(221, 243, 236), Color.rgb(245, 249, 241), Color.rgb(209, 226, 213)),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = RadialGradient(
            width * 0.5f,
            height * 0.26f,
            width * 0.64f,
            Color.argb(95, 255, 255, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }

    private fun drawCampusShadow(canvas: Canvas) {
        val center = project(0.5f, 0.5f, 0f)
        paint.color = Color.argb(36, 18, 53, 42)
        paint.maskFilter = android.graphics.BlurMaskFilter(28f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        canvas.drawOval(
            center.x - scale * 0.72f,
            center.y + scale * 0.24f,
            center.x + scale * 0.72f,
            center.y + scale * 0.52f,
            paint
        )
        paint.maskFilter = null
    }

    private fun drawBase(canvas: Canvas) {
        path.reset()
        val campus = campusBoundary()
        path.moveTo(campus.first().x, campus.first().y)
        campus.drop(1).forEach { path.lineTo(it.x, it.y) }
        path.close()

        paint.color = Color.rgb(91, 136, 89)
        canvas.drawPath(path, paint)
        paint.color = Color.rgb(199, 216, 178)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb(120, 40, 88, 56)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun campusBoundary(): List<PointF> {
        return listOf(
            project(0.09f, 0.05f),
            project(0.91f, 0.06f),
            project(0.96f, 0.47f),
            project(0.94f, 0.92f),
            project(0.78f, 0.97f),
            project(0.10f, 0.96f),
            project(0.05f, 0.55f),
            project(0.07f, 0.18f)
        )
    }

    private fun drawFence(canvas: Canvas) {
        val corners = listOf(
            0.09f to 0.05f,
            0.91f to 0.06f,
            0.96f to 0.47f,
            0.94f to 0.92f,
            0.78f to 0.97f,
            0.10f to 0.96f,
            0.05f to 0.55f,
            0.07f to 0.18f
        )

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f
        paint.color = Color.argb(120, 55, 82, 63)
        corners.indices.forEach { index ->
            val start = corners[index]
            val end = corners[(index + 1) % corners.size]
            val isGateGap = start.first == 0.05f && end.first == 0.07f
            if (!isGateGap) {
                val a = project(start.first, start.second, 0.018f)
                val b = project(end.first, end.second, 0.018f)
                canvas.drawLine(a.x, a.y, b.x, b.y, paint)
            }
        }

        paint.style = Paint.Style.FILL
        corners.indices.forEach { index ->
            val start = corners[index]
            val end = corners[(index + 1) % corners.size]
            val isGateGap = start.first == 0.05f && end.first == 0.07f
            val postCount = if (isGateGap) 2 else 5
            repeat(postCount) { post ->
                val t = (post + 1f) / (postCount + 1f)
                val x = start.first + (end.first - start.first) * t
                val y = start.second + (end.second - start.second) * t
                if (isGateGap && y in 0.59f..0.73f) return@repeat
                val bottom = project(x, y, 0.018f)
                val top = project(x, y, 0.038f)
                paint.color = Color.argb(145, 64, 83, 67)
                canvas.drawRect(bottom.x - 1f, top.y, bottom.x + 1f, bottom.y + 1.5f, paint)
                paint.color = Color.argb(170, 229, 218, 185)
                canvas.drawCircle(top.x, top.y, 1.6f, paint)
            }
        }
    }

    private fun drawFlatBlock(canvas: Canvas, block: CampusBlock) {
        drawTopFace(canvas, block.x, block.y, block.w, block.h, block.height, block.color)
        drawBlockLabel(canvas, block, block.height, Color.rgb(63, 68, 64), 9f)
    }

    private fun drawBlock(canvas: Canvas, block: CampusBlock) {
        when {
            block.kind == BlockKind.GATE -> drawGate(canvas, block)
            block.kind == BlockKind.BUILDING || block.height > 0.03f -> drawPrism(canvas, block)
            else -> drawTopFace(canvas, block.x, block.y, block.w, block.h, block.height, block.color)
        }
        drawBlockDetails(canvas, block)
        drawBlockLabel(canvas, block, block.height, if (isDark(block.color)) Color.WHITE else Color.rgb(42, 52, 44), 7.2f)
    }

    private fun drawPrism(canvas: Canvas, block: CampusBlock) {
        val top = listOf(
            project(block.x, block.y, block.height),
            project(block.x + block.w, block.y, block.height),
            project(block.x + block.w, block.y + block.h, block.height),
            project(block.x, block.y + block.h, block.height)
        )
        val bottom = listOf(
            project(block.x, block.y, 0f),
            project(block.x + block.w, block.y, 0f),
            project(block.x + block.w, block.y + block.h, 0f),
            project(block.x, block.y + block.h, 0f)
        )

        listOf(
            PrismFace(
                listOf(top[0], bottom[0], bottom[1], top[1]),
                darken(block.color, 0.74f),
                rotatedDepth(block.x + block.w * 0.5f, block.y)
            ),
            PrismFace(
                listOf(top[1], bottom[1], bottom[2], top[2]),
                darken(block.color, 0.66f),
                rotatedDepth(block.x + block.w, block.y + block.h * 0.5f)
            ),
            PrismFace(
                listOf(top[2], bottom[2], bottom[3], top[3]),
                darken(block.color, 0.58f),
                rotatedDepth(block.x + block.w * 0.5f, block.y + block.h)
            ),
            PrismFace(
                listOf(top[3], bottom[3], bottom[0], top[0]),
                darken(block.color, 0.70f),
                rotatedDepth(block.x, block.y + block.h * 0.5f)
            )
        ).sortedBy { it.depth }.forEach { face ->
            drawQuad(canvas, face.points, face.color)
        }
        drawQuad(canvas, top, block.color)
        drawRoof(canvas, block, block.roof)
        drawFacadeAccents(canvas, block)
    }

    private fun drawRoof(canvas: Canvas, block: CampusBlock, color: Int) {
        if (block.kind != BlockKind.BUILDING && block.label !in setOf("Nhà xe", "Cổng trường")) {
            drawTopFace(
                canvas,
                block.x + block.w * 0.08f,
                block.y + block.h * 0.08f,
                block.w * 0.84f,
                block.h * 0.84f,
                block.height + 0.012f,
                color
            )
            return
        }

        val overhangX = if (block.label == "Hiệu bộ") block.w * 0.025f else if (block.w > block.h) block.w * 0.04f else block.w * 0.05f
        val overhangY = if (block.label == "Hiệu bộ") block.h * 0.035f else if (block.w > block.h) block.h * 0.055f else block.h * 0.05f
        val x = block.x - overhangX
        val y = block.y - overhangY
        val w = block.w + overhangX * 2
        val h = block.h + overhangY * 2
        val z = block.height + 0.016f
        val ridgeZ = block.height + 0.038f
        val ridgeA: PointF
        val ridgeB: PointF

        if (block.w >= block.h) {
            val leftFront = project(x, y, z)
            val rightFront = project(x + w, y, z)
            val rightBack = project(x + w, y + h, z)
            val leftBack = project(x, y + h, z)
            ridgeA = project(x, y + h * 0.5f, ridgeZ)
            ridgeB = project(x + w, y + h * 0.5f, ridgeZ)
            drawQuad(canvas, listOf(leftFront, rightFront, ridgeB, ridgeA), color)
            drawQuad(canvas, listOf(ridgeA, ridgeB, rightBack, leftBack), darken(color, 0.78f))
        } else {
            val leftFront = project(x, y, z)
            val rightFront = project(x + w, y, z)
            val rightBack = project(x + w, y + h, z)
            val leftBack = project(x, y + h, z)
            ridgeA = project(x + w * 0.5f, y, ridgeZ)
            ridgeB = project(x + w * 0.5f, y + h, ridgeZ)
            drawQuad(canvas, listOf(leftFront, ridgeA, ridgeB, leftBack), color)
            drawQuad(canvas, listOf(ridgeA, rightFront, rightBack, ridgeB), darken(color, 0.78f))
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.4f
        paint.color = Color.argb(180, 255, 232, 190)
        canvas.drawLine(ridgeA.x, ridgeA.y, ridgeB.x, ridgeB.y, paint)
        paint.strokeWidth = 1.1f
        paint.color = Color.argb(72, 255, 229, 192)
        val tileCount = if (block.w >= block.h) 5 else 4
        repeat(tileCount) { index ->
            val t = (index + 1f) / (tileCount + 1f)
            val lineA: PointF
            val lineB: PointF
            if (block.w >= block.h) {
                lineA = project(x + w * t, y + h * 0.08f, z + 0.002f)
                lineB = project(x + w * t, y + h * 0.92f, z + 0.002f)
            } else {
                lineA = project(x + w * 0.08f, y + h * t, z + 0.002f)
                lineB = project(x + w * 0.92f, y + h * t, z + 0.002f)
            }
            canvas.drawLine(lineA.x, lineA.y, lineB.x, lineB.y, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawFacadeAccents(canvas: Canvas, block: CampusBlock) {
        if (block.kind != BlockKind.BUILDING) return
        val edges = facadeEdges(block)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.35f
        paint.color = Color.argb(110, 74, 93, 83)
        val floors = floorCount(block)
        repeat(max(1, floors - 1)) { floor ->
            val z = block.height * ((floor + 1f) / floors)
            edges.forEach { edge ->
                val a = project(edge.first.x, edge.first.y, z)
                val b = project(edge.second.x, edge.second.y, z)
                canvas.drawLine(a.x, a.y, b.x, b.y, paint)
            }
        }

        paint.strokeWidth = 1.05f
        paint.color = Color.argb(70, 47, 75, 70)
        edges.forEach { edge ->
            val edgeLength = hypot(edge.second.x - edge.first.x, edge.second.y - edge.first.y)
            val pillarCount = if (edgeLength > 0.18f) 5 else 3
            repeat(pillarCount) { index ->
                val t = (index + 1f) / (pillarCount + 1f)
                val x = edge.first.x + (edge.second.x - edge.first.x) * t
                val y = edge.first.y + (edge.second.y - edge.first.y) * t
                val bottom = project(x, y, 0f)
                val top = project(x, y, block.height * 0.92f)
                canvas.drawLine(bottom.x, bottom.y, top.x, top.y, paint)
            }
        }
        paint.style = Paint.Style.FILL
        drawFacadeWindows(canvas, block)
    }

    private fun drawFacadeWindows(canvas: Canvas, block: CampusBlock) {
        val floors = floorCount(block)
        val edges = facadeEdges(block)

        repeat(floors) { floor ->
            val z = block.height * ((floor + 0.52f) / (floors + 0.35f))
            edges.forEach { edge ->
                val length = hypot(edge.second.x - edge.first.x, edge.second.y - edge.first.y)
                val columns = when {
                    length > 0.24f -> 7
                    length > 0.12f -> 4
                    else -> 2
                }
                repeat(columns) { col ->
                    val t = (col + 1f) / (columns + 1f)
                    val x = edge.first.x + (edge.second.x - edge.first.x) * t
                    val y = edge.first.y + (edge.second.y - edge.first.y) * t
                    val p = project(x, y, z)
                    drawWindow(canvas, p, block)
                }
            }
        }

        if (block.label !in setOf("PCCC", "Trạm điện", "Đài nước")) {
            val door = project(block.x + block.w * 0.5f, block.y + block.h, block.height * 0.16f)
            paint.color = Color.rgb(73, 82, 75)
            tempRect.set(door.x - 5.2f, door.y - 10f, door.x + 5.2f, door.y + 8f)
            canvas.drawRoundRect(tempRect, 2.4f, 2.4f, paint)
            paint.color = Color.argb(160, 244, 224, 166)
            canvas.drawCircle(door.x + 2.6f, door.y, 0.9f, paint)
        }
    }

    private fun drawWindow(canvas: Canvas, p: PointF, block: CampusBlock) {
        paint.color = if (block.color == wallWhite) {
            Color.argb(190, 232, 247, 252)
        } else {
            Color.argb(190, 246, 238, 209)
        }
        tempRect.set(p.x - 4.4f, p.y - 3.4f, p.x + 4.4f, p.y + 3.4f)
        canvas.drawRoundRect(tempRect, 1.8f, 1.8f, paint)
        paint.color = Color.argb(115, 52, 83, 82)
        paint.strokeWidth = 0.7f
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(tempRect, 1.8f, 1.8f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun facadeEdges(block: CampusBlock): List<Pair<PointF, PointF>> {
        return listOf(
            PointF(block.x, block.y) to PointF(block.x + block.w, block.y),
            PointF(block.x + block.w, block.y) to PointF(block.x + block.w, block.y + block.h),
            PointF(block.x + block.w, block.y + block.h) to PointF(block.x, block.y + block.h),
            PointF(block.x, block.y + block.h) to PointF(block.x, block.y)
        )
    }

    private fun drawBlockDetails(canvas: Canvas, block: CampusBlock) {
        when {
            isYouthGarden(block) -> drawGardenDetails(canvas, block)
            block.kind == BlockKind.FIELD -> drawSportsFieldDetails(canvas, block)
            block.kind == BlockKind.COURT -> drawCourtDetails(canvas, block)
            block.label == "Hồ nước" -> drawWaterDetails(canvas, block)
            block.kind == BlockKind.BUILDING || block.kind == BlockKind.SERVICE -> drawBuildingDetails(canvas, block)
        }
    }

    private fun drawGate(canvas: Canvas, block: CampusBlock) {
        val anchor = project(block.x + block.w * 0.5f, block.y + block.h * 0.56f, 0.014f)
        val gateWidth = (scale * 0.15f * zoomFactor).coerceIn(58f, 96f)
        val pillarWidth = gateWidth * 0.13f
        val pillarHeight = gateWidth * 0.58f
        val archHeight = gateWidth * 0.31f
        val baseY = anchor.y
        val leftX = anchor.x - gateWidth * 0.5f
        val rightX = anchor.x + gateWidth * 0.5f
        val topY = baseY - pillarHeight

        paint.color = Color.argb(60, 0, 0, 0)
        tempRect.set(leftX + 5f, baseY - 4f, rightX + 10f, baseY + 8f)
        canvas.drawOval(tempRect, paint)

        paint.color = gatePillar
        val leftPillar = RectF(leftX, topY, leftX + pillarWidth, baseY)
        val rightPillar = RectF(rightX - pillarWidth, topY, rightX, baseY)
        canvas.drawRoundRect(leftPillar, 4f, 4f, paint)
        canvas.drawRoundRect(rightPillar, 4f, 4f, paint)

        paint.color = Color.rgb(23, 29, 31)
        tempRect.set(leftPillar.left + 4f, leftPillar.top + 8f, leftPillar.right - 4f, leftPillar.bottom - 8f)
        canvas.drawRoundRect(tempRect, 2f, 2f, paint)
        tempRect.set(rightPillar.left + 4f, rightPillar.top + 8f, rightPillar.right - 4f, rightPillar.bottom - 8f)
        canvas.drawRoundRect(tempRect, 2f, 2f, paint)

        paint.color = gateGold
        path.reset()
        path.moveTo(leftX - gateWidth * 0.04f, topY + archHeight * 0.28f)
        path.quadTo(anchor.x, topY - archHeight * 0.70f, rightX + gateWidth * 0.04f, topY + archHeight * 0.28f)
        path.lineTo(rightX - gateWidth * 0.02f, topY + archHeight * 0.56f)
        path.quadTo(anchor.x, topY - archHeight * 0.22f, leftX + gateWidth * 0.02f, topY + archHeight * 0.56f)
        path.close()
        canvas.drawPath(path, paint)

        paint.color = Color.rgb(251, 224, 147)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        path.reset()
        path.moveTo(leftX - gateWidth * 0.02f, topY + archHeight * 0.28f)
        path.quadTo(anchor.x, topY - archHeight * 0.54f, rightX + gateWidth * 0.02f, topY + archHeight * 0.28f)
        canvas.drawPath(path, paint)

        paint.strokeWidth = 1.5f
        paint.color = Color.argb(160, 72, 68, 54)
        val railTop = baseY - pillarHeight * 0.32f
        val railBottom = baseY - pillarHeight * 0.08f
        canvas.drawLine(leftPillar.right + 2f, railTop, rightPillar.left - 2f, railTop, paint)
        canvas.drawLine(leftPillar.right + 2f, railBottom, rightPillar.left - 2f, railBottom, paint)
        val barCount = 7
        repeat(barCount) { index ->
            val x = leftPillar.right + 8f + (rightPillar.left - leftPillar.right - 16f) * (index / (barCount - 1f))
            canvas.drawLine(x, railTop, x, railBottom + 3f, paint)
        }
        paint.style = Paint.Style.FILL

        textPaint.textSize = 4.9f * resources.displayMetrics.scaledDensity
        textPaint.color = Color.rgb(160, 36, 36)
        textPaint.isFakeBoldText = true
        textPaint.setShadowLayer(2f, 0f, 1f, Color.argb(160, 255, 240, 200))
        canvas.drawText("ĐAM SAN", anchor.x, topY + archHeight * 0.33f, textPaint)
        textPaint.clearShadowLayer()
        textPaint.isFakeBoldText = false
    }

    private fun drawBuildingDetails(canvas: Canvas, block: CampusBlock) {
        if (block.kind != BlockKind.BUILDING) return
        paint.color = Color.argb(95, 255, 236, 205)
        val roofMarks = if (block.w > block.h) 4 else 3
        repeat(roofMarks) { index ->
            val t = (index + 1f) / (roofMarks + 1f)
            val p = project(block.x + block.w * t, block.y + block.h * 0.50f, block.height + 0.032f)
            canvas.drawCircle(p.x, p.y, 2.2f, paint)
        }
    }

    private fun floorCount(block: CampusBlock): Int {
        return when (block.label) {
            "Nhà ăn", "Nhà đa năng", "Nhà VH", "Bảo vệ", "Nhà xe" -> 1
            "PCCC", "Trạm điện", "Đài nước" -> 1
            else -> 3
        }
    }

    private fun drawCourtDetails(canvas: Canvas, block: CampusBlock) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(140, 96, 112, 105)
        drawTopFace(canvas, block.x + block.w * 0.10f, block.y + block.h * 0.12f, block.w * 0.80f, block.h * 0.76f, block.height + 0.006f, paint.color, stroke = true)
        if (block.label == "Cầu lông") {
            val left = project(block.x + block.w * 0.50f, block.y + block.h * 0.14f, block.height + 0.008f)
            val right = project(block.x + block.w * 0.50f, block.y + block.h * 0.86f, block.height + 0.008f)
            canvas.drawLine(left.x, left.y, right.x, right.y, paint)
        } else {
            repeat(4) { row ->
                repeat(6) { col ->
                    val p = project(block.x + block.w * (0.16f + col * 0.12f), block.y + block.h * (0.20f + row * 0.15f), block.height + 0.006f)
                    canvas.drawCircle(p.x, p.y, 2.4f, paint)
                }
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawSportsFieldDetails(canvas: Canvas, block: CampusBlock) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.4f
        paint.color = Color.argb(160, 255, 255, 255)
        drawTopFace(canvas, block.x + block.w * 0.08f, block.y + block.h * 0.09f, block.w * 0.84f, block.h * 0.82f, block.height + 0.006f, paint.color, stroke = true)

        val midA = project(block.x + block.w * 0.50f, block.y + block.h * 0.10f, block.height + 0.008f)
        val midB = project(block.x + block.w * 0.50f, block.y + block.h * 0.90f, block.height + 0.008f)
        canvas.drawLine(midA.x, midA.y, midB.x, midB.y, paint)
        val center = project(block.x + block.w * 0.50f, block.y + block.h * 0.50f, block.height + 0.01f)
        canvas.drawCircle(center.x, center.y, 17f, paint)

        paint.color = Color.argb(120, 119, 94, 79)
        repeat(4) { lane ->
            drawTopFace(
                canvas,
                block.x + block.w * (0.04f + lane * 0.018f),
                block.y + block.h * 0.04f,
                block.w * (0.92f - lane * 0.036f),
                block.h * (0.92f - lane * 0.036f),
                block.height + 0.004f,
                paint.color,
                stroke = true
            )
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawWaterDetails(canvas: Canvas, block: CampusBlock) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(150, 240, 255, 255)
        repeat(3) { index ->
            val y = block.y + block.h * (0.25f + index * 0.22f)
            val a = project(block.x + block.w * 0.15f, y, block.height + 0.006f)
            val b = project(block.x + block.w * 0.85f, y + block.h * 0.04f, block.height + 0.006f)
            canvas.drawLine(a.x, a.y, b.x, b.y, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawGardenDetails(canvas: Canvas, block: CampusBlock) {
        val rows = if (block.h > block.w) 5 else 3
        val cols = if (block.w > block.h) 5 else 3
        val z = block.height + 0.006f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(92, 255, 255, 255)
        repeat(rows) { row ->
            val y = block.y + block.h * ((row + 1f) / (rows + 1f))
            val start = project(block.x + block.w * 0.12f, y, z)
            val end = project(block.x + block.w * 0.88f, y, z)
            canvas.drawLine(start.x, start.y, end.x, end.y, paint)
        }
        paint.style = Paint.Style.FILL

        repeat(cols) { col ->
            repeat(rows) { row ->
                val px = block.x + block.w * ((col + 1f) / (cols + 1f))
                val py = block.y + block.h * ((row + 1f) / (rows + 1f))
                val p = project(px, py, z + 0.012f)
                paint.color = Color.rgb(36, 139, 80)
                canvas.drawCircle(p.x, p.y, 5f, paint)
                paint.color = Color.argb(130, 173, 229, 132)
                canvas.drawCircle(p.x - 2f, p.y - 2f, 2f, paint)
            }
        }

        paint.color = Color.argb(120, 90, 67, 38)
        repeat(5) { dot ->
            val p = project(
                block.x + block.w * (0.16f + dot * 0.15f).coerceAtMost(0.86f),
                block.y + block.h * (if (dot % 2 == 0) 0.76f else 0.22f),
                z
            )
            canvas.drawCircle(p.x, p.y, 2.6f, paint)
        }
    }

    private fun drawTrees(canvas: Canvas) {
        val treePoints = listOf(
            0.12f to 0.18f, 0.18f to 0.15f, 0.27f to 0.18f,
            0.68f to 0.13f, 0.78f to 0.17f, 0.87f to 0.23f,
            0.17f to 0.36f, 0.31f to 0.42f, 0.50f to 0.36f,
            0.75f to 0.40f, 0.90f to 0.44f, 0.91f to 0.58f,
            0.13f to 0.73f, 0.19f to 0.88f, 0.36f to 0.88f,
            0.53f to 0.87f, 0.73f to 0.85f, 0.91f to 0.85f,
            0.12f to 0.08f, 0.24f to 0.07f, 0.36f to 0.06f,
            0.52f to 0.07f, 0.66f to 0.07f, 0.82f to 0.08f,
            0.93f to 0.30f, 0.94f to 0.48f, 0.93f to 0.68f,
            0.84f to 0.92f, 0.66f to 0.94f, 0.48f to 0.94f,
            0.28f to 0.94f, 0.12f to 0.92f,
            0.58f to 0.20f, 0.58f to 0.32f, 0.58f to 0.44f,
            0.66f to 0.55f, 0.66f to 0.66f, 0.66f to 0.76f,
            0.26f to 0.56f, 0.38f to 0.56f, 0.50f to 0.56f
        )
        treePoints.forEach { (x, y) ->
            val trunk = project(x, y, 0.015f)
            paint.color = Color.rgb(116, 82, 44)
            canvas.drawCircle(trunk.x, trunk.y + 8f, 3f, paint)
            val crown = project(x, y, 0.055f)
            paint.color = Color.rgb(38, 143, 84)
            canvas.drawCircle(crown.x, crown.y, 11f, paint)
            paint.color = Color.argb(130, 147, 218, 117)
            canvas.drawCircle(crown.x - 4f, crown.y - 4f, 4f, paint)
        }
    }

    private fun drawMarkers(canvas: Canvas) {
        val start = project(0.49f, 0.665f, 0.13f)
        drawPin(canvas, start.x, start.y, "Hiệu bộ", Color.rgb(235, 137, 40), true)

        markers.forEachIndexed { index, marker ->
            val p = project(marker.x, marker.y, 0.13f + (index % 3) * 0.006f)
            drawPin(canvas, p.x, p.y, marker.title, marker.color, false)
        }
    }

    private fun drawPin(canvas: Canvas, x: Float, y: Float, label: String, color: Int, large: Boolean) {
        val radius = if (large) 16f else 12f
        paint.color = Color.argb(72, 0, 0, 0)
        canvas.drawCircle(x + 2f, y + radius + 9f, radius * 0.7f, paint)
        paint.color = color
        path.reset()
        path.moveTo(x, y + radius + 12f)
        path.cubicTo(x - radius, y + radius * 0.55f, x - radius, y - radius * 0.8f, x, y - radius)
        path.cubicTo(x + radius, y - radius * 0.8f, x + radius, y + radius * 0.55f, x, y + radius + 12f)
        path.close()
        canvas.drawPath(path, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(x, y, radius * 0.38f, paint)

        textPaint.textSize = if (large) 10f else 8.5f
        textPaint.color = Color.rgb(28, 54, 45)
        textPaint.setShadowLayer(3f, 0f, 1f, Color.WHITE)
        val labelY = y + radius + 28f
        val halfWidth = textPaint.measureText(label) * 0.5f + 8f
        paint.color = Color.argb(220, 255, 255, 255)
        tempRect.set(x - halfWidth, labelY - 14f, x + halfWidth, labelY + 4f)
        canvas.drawRoundRect(tempRect, 8f, 8f, paint)
        canvas.drawText(label.take(10), x, labelY, textPaint)
        textPaint.clearShadowLayer()
    }

    private fun drawCompass(canvas: Canvas) {
        val cx = width - 62f
        val cy = height * 0.22f
        paint.color = Color.argb(230, 255, 255, 255)
        canvas.drawCircle(cx, cy, 26f, paint)
        paint.color = Color.rgb(31, 117, 82)
        path.reset()
        path.moveTo(cx, cy - 19f)
        path.lineTo(cx + 8f, cy + 6f)
        path.lineTo(cx, cy + 1f)
        path.lineTo(cx - 8f, cy + 6f)
        path.close()
        canvas.drawPath(path, paint)
        textPaint.textSize = 9f
        textPaint.color = Color.rgb(31, 79, 67)
        canvas.drawText("Bắc", cx, cy + 36f, textPaint)
    }

    private fun drawLegend(canvas: Canvas) {
        val x = 18f
        val y = height * 0.42f
        paint.color = Color.argb(216, 255, 255, 255)
        tempRect.set(x, y, x + 210f, y + 62f)
        canvas.drawRoundRect(tempRect, 18f, 18f, paint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 11f
        textPaint.color = Color.rgb(32, 74, 58)
        textPaint.isFakeBoldText = true
        canvas.drawText("Mái đỏ: công trình", x + 14f, y + 24f, textPaint)
        textPaint.isFakeBoldText = false
        textPaint.textSize = 9f
        textPaint.color = Color.rgb(91, 108, 99)
        canvas.drawText("Ô màu: vườn lớp phụ trách", x + 14f, y + 44f, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawTopFace(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        z: Float,
        color: Int,
        stroke: Boolean = false
    ) {
        val p1 = project(x, y, z)
        val p2 = project(x + w, y, z)
        val p3 = project(x + w, y + h, z)
        val p4 = project(x, y + h, z)
        drawQuad(canvas, listOf(p1, p2, p3, p4), color, stroke)
    }

    private fun drawQuad(canvas: Canvas, points: List<PointF>, color: Int, stroke: Boolean = false) {
        path.reset()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
        path.close()
        if (stroke) {
            paint.style = Paint.Style.STROKE
            paint.color = color
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
        } else {
            paint.color = color
            canvas.drawPath(path, paint)
        }
    }

    private fun drawBlockLabel(canvas: Canvas, block: CampusBlock, z: Float, color: Int, sizeSp: Float) {
        val label = displayLabel(block.label)
        if (!shouldDrawLabel(block, label)) return
        val center = project(block.x + block.w / 2f, block.y + block.h / 2f, z + 0.03f)
        val blockArea = block.w * block.h
        val readableSize = when {
            block.kind == BlockKind.GARDEN -> 6.2f
            blockArea < 0.008f -> sizeSp - 1.4f
            block.kind == BlockKind.FIELD || block.kind == BlockKind.COURT -> 6.6f
            else -> sizeSp
        }.coerceAtLeast(5.4f)
        textPaint.textSize = readableSize * resources.displayMetrics.scaledDensity
        textPaint.color = color
        textPaint.isFakeBoldText = true
        textPaint.setShadowLayer(4f, 0f, 1.5f, if (isDark(block.color)) Color.argb(130, 0, 0, 0) else Color.WHITE)
        canvas.drawText(label, center.x, center.y, textPaint)
        textPaint.clearShadowLayer()
        textPaint.isFakeBoldText = false
    }

    private fun displayLabel(label: String): String {
        return when (label) {
            "" -> ""
            else -> if (label.startsWith("Vườn ")) {
                label.removePrefix("Vườn ")
            } else {
                when (label) {
            "NT học sinh 1" -> "NT HS 1"
            "NT học sinh 2" -> "NT HS 2"
            "Lớp + thư viện" -> "Lớp + TV"
            "Lớp 10 phòng" -> "10 phòng"
            "Sân vận động" -> "Sân VĐ"
            "Công vụ GV" -> "Công vụ"
            "Cổng trường" -> "Cổng"
            "Sân trung tâm" -> "Sân"
            "Nhà đa năng" -> "Đa năng"
            else -> label
                }
            }
        }
    }

    private fun shouldDrawLabel(block: CampusBlock, label: String): Boolean {
        if (label.isBlank()) return false
        if (block.kind == BlockKind.ROAD) return false
        if (block.kind == BlockKind.GATE) return false
        if (block.label in setOf("PCCC", "Trạm điện", "Đài nước", "Bảo vệ")) return false
        return block.w * block.h >= 0.0045f
    }

    private fun project(x: Float, y: Float, z: Float = 0f): PointF {
        val dx = x - 0.5f
        val dy = y - 0.5f
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val rotatedX = (dx * cos(radians) - dy * sin(radians)).toFloat() + 0.5f
        val rotatedY = (dx * sin(radians) + dy * cos(radians)).toFloat() + 0.5f
        val px = originX + (rotatedX - rotatedY) * scale * 0.72f
        val py = originY + (rotatedX + rotatedY) * scale * 0.36f - z * scale
        return PointF(px, py)
    }

    private fun drawOrder(block: CampusBlock): Float {
        val cx = block.x + block.w * 0.5f
        val cy = block.y + block.h * 0.5f
        return rotatedDepth(cx, cy) + block.height * 0.35f
    }

    private fun rotatedDepth(x: Float, y: Float): Float {
        val dx = x - 0.5f
        val dy = y - 0.5f
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val rotatedX = (dx * cos(radians) - dy * sin(radians)).toFloat() + 0.5f
        val rotatedY = (dx * sin(radians) + dy * cos(radians)).toFloat() + 0.5f
        return rotatedX + rotatedY
    }

    private fun normalizeDegrees(value: Float): Float {
        var normalized = value % 360f
        if (normalized < 0f) normalized += 360f
        return normalized
    }

    private fun longitudeToCampusX(lon: Double, index: Int): Float {
        val normalized = 0.5f + ((lon - schoolLon) * 420.0).toFloat()
        return jitter(normalized.coerceIn(0.14f, 0.86f), index, 0.035f)
    }

    private fun latitudeToCampusY(lat: Double, index: Int): Float {
        val normalized = 0.5f - ((lat - schoolLat) * 520.0).toFloat()
        return jitter(normalized.coerceIn(0.16f, 0.86f), index + 4, 0.032f)
    }

    private fun jitter(value: Float, seed: Int, amount: Float): Float {
        val direction = if (seed % 2 == 0) 1f else -1f
        return (value + direction * amount * ((seed % 5) / 4f)).coerceIn(0.10f, 0.90f)
    }

    private fun isDark(color: Int): Boolean {
        val brightness = Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114
        return brightness < 118
    }

    private fun isYouthGarden(block: CampusBlock): Boolean {
        return block.kind == BlockKind.GARDEN
    }

    private companion object {
        const val MIN_ZOOM = 0.68f
        const val MAX_ZOOM = 1.72f

        fun darken(color: Int, factor: Float): Int {
            return Color.rgb(
                max(0, min(255, (Color.red(color) * factor).toInt())),
                max(0, min(255, (Color.green(color) * factor).toInt())),
                max(0, min(255, (Color.blue(color) * factor).toInt()))
            )
        }
    }
}
