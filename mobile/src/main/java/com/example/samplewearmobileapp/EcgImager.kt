package com.example.samplewearmobileapp

import android.graphics.*

object EcgImager {
    private const val WIDTH = 2550
    private const val HEIGHT = 3300
    private const val GRAPH_WIDTH = 40 * 5
    private const val GRAPH_HEIGHT = 48 * 5
    private const val GRAPH_X = 8
    private const val GRAPH_Y = 31
    private const val SCALE = 11.8f
    private const val MINOR_COLOR = -0x2e2e2f
    private const val MAJOR_COLOR = -0x737374
    private const val BLOCK_COLOR = -0xcccccd
    private const val OUTLINE_COLOR = -0x1000000
    private const val CURVE_COLOR = -0x1000000
    private const val MINOR_WIDTH = 1f
    private const val MAJOR_WIDTH = 2f
    private const val BLOCK_WIDTH = 3f
    private const val OUTLINE_WIDTH = 5f
    private const val CURVE_WIDTH = 3f

    fun createImage(
        samplingRate: Double,
//        logo: Bitmap?,
        patientName: String?,
        date: String?,
        id: String?,
        firmware: String?,
        batteryLevel: String?,
        notes: String?,
        deviceHr: String?,
        calculatedHr: String?,
        peaksCount: String?,
        duration: String?,
        ecgValues: DoubleArray,
        peakValues: BooleanArray?
    ): Bitmap {
        // Graphics
        val bm = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        bm.eraseColor(Color.WHITE)
        val canvas = Canvas(bm)

        // General use, parameters will change
        val paint = Paint()
        paint.color = Color.BLACK
        paint.textSize = 36f

        // For QRS marks, needs to be used at same time as paint
        val paint1 = Paint()
        paint1.color = Color.BLACK
        paint1.strokeWidth = OUTLINE_WIDTH

        // Fonts
        val font = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val fontBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val fontInfo = Typeface.create(
            Typeface.SANS_SERIF,
            Typeface.NORMAL
        )
        val fontLogo = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        // Headers
        paint.typeface = fontBold
        canvas.drawText("Patient:", 100f, 120f, paint)
        paint.typeface = font
        canvas.drawText(patientName!!, 300f, 120f, paint)

        paint.typeface = fontBold
        canvas.drawText("Notes:", 850f, 120f, paint)
        paint.typeface = font
        canvas.drawText(notes!!, 1025f, 120f, paint)

        paint.typeface = fontBold
        canvas.drawText("Recorded:", 100f, 165f, paint)
        paint.typeface = font
        canvas.drawText(date!!, 300f, 165f, paint)

        paint.typeface = fontBold
        canvas.drawText("Duration:", 100f, 210f, paint)
        paint.typeface = font
        canvas.drawText(duration!!, 300f, 210f, paint)

        paint.typeface = fontBold
        canvas.drawText("Device ID:", 100f, 255f, paint)
        paint.typeface = font
        canvas.drawText(id!!, 300f, 255f, paint)

        paint.typeface = fontBold
        canvas.drawText("Battery:", 850f, 255f, paint)
        paint.typeface = font
        canvas.drawText(batteryLevel!!, 1025f, 255f, paint)

        paint.typeface = fontBold
        canvas.drawText("Firmware:", 500f, 255f, paint)
        paint.typeface = font
        canvas.drawText(firmware!!, 700f, 255f, paint)

        paint.typeface = fontBold
        canvas.drawText("Device HR:", 100f, 300f, paint)
        paint.typeface = font
        canvas.drawText(deviceHr!!, 300f, 300f, paint)

        paint.typeface = fontBold
        canvas.drawText("Calc HR:", 500f, 300f, paint)
        paint.typeface = font
        canvas.drawText(calculatedHr!!, 700f, 300f, paint)

        paint.typeface = fontBold
        canvas.drawText("Peaks:", 850f, 300f, paint)
        paint.typeface = font
        canvas.drawText(peaksCount!!, 1025f, 300f, paint)

        val scale = "Scale: 25 mm/s, 10 mm/mV "
        paint.typeface = fontInfo
        paint.textSize = 30f
        canvas.drawText(scale, 2075f, 350f, paint)

//        // Do the icon
//        val scaledLogo = Bitmap.createScaledBitmap(logo!!, 100, 100, true)
//        canvas.drawBitmap(scaledLogo, 2050f, 116f, null)
        paint.typeface = fontLogo
        paint.textSize = 48f
        paint.color = -0x2cffdc
        canvas.drawText("ECG-App", 2170f, 180f, paint)

        // Draw the small grid lines
        paint.strokeWidth = MINOR_WIDTH
        paint.color = MINOR_COLOR
        paint.color = MINOR_COLOR
        for (i in 0 until GRAPH_WIDTH) {
            drawScaled(
                canvas,
                (GRAPH_X + i).toFloat(),
                GRAPH_Y.toFloat(),
                (GRAPH_X + i).toFloat(),
                (GRAPH_Y + GRAPH_HEIGHT).toFloat(),
                paint
            )
        }
        for (i in 0 until GRAPH_HEIGHT) {
            drawScaled(
                canvas,
                GRAPH_X.toFloat(),
                (GRAPH_Y + i).toFloat(),
                (GRAPH_X + GRAPH_WIDTH).toFloat(),
                (GRAPH_Y + i).toFloat(),
                paint
            )
        }

        // Draw the large grid lines
        paint.strokeWidth = MAJOR_WIDTH
        paint.color = MAJOR_COLOR
        run {
            var i = 0
            while (i < GRAPH_WIDTH) {
                drawScaled(
                    canvas,
                    (GRAPH_X + i).toFloat(),
                    GRAPH_Y.toFloat(),
                    (GRAPH_X + i).toFloat(),
                    (GRAPH_Y + GRAPH_HEIGHT).toFloat(),
                    paint
                )
                i += 5
            }
        }
        run {
            var i = 0
            while (i < GRAPH_HEIGHT) {
                drawScaled(
                    canvas,
                    GRAPH_X.toFloat(),
                    (GRAPH_Y + i).toFloat(),
                    (GRAPH_X + GRAPH_WIDTH).toFloat(),
                    (GRAPH_Y + i).toFloat(),
                    paint
                )
                i += 5
            }
        }

        // Draw the block grid lines
        paint.strokeWidth = BLOCK_WIDTH
        paint.color = BLOCK_COLOR
        run {
            var i = 0
            while (i < GRAPH_WIDTH) {
                drawScaled(
                    canvas,
                    (GRAPH_X + i).toFloat(),
                    GRAPH_Y.toFloat(),
                    (GRAPH_X + i).toFloat(),
                    (GRAPH_Y + GRAPH_HEIGHT).toFloat(),
                    paint
                )
                i += 25
            }
        }
        var i = 0
        while (i < GRAPH_HEIGHT) {
            drawScaled(
                canvas,
                GRAPH_X.toFloat(),
                (GRAPH_Y + i).toFloat(),
                (GRAPH_X + GRAPH_WIDTH).toFloat(),
                (GRAPH_Y + i).toFloat(),
                paint
            )
            i += 60
        }

        // Draw the outline
        paint.strokeWidth = OUTLINE_WIDTH
        paint.color = OUTLINE_COLOR
        drawScaled(
            canvas,
            GRAPH_X.toFloat(),
            GRAPH_Y.toFloat(),
            (GRAPH_X + GRAPH_WIDTH).toFloat(),
            GRAPH_Y.toFloat(),
            paint
        )
        drawScaled(
            canvas,
            GRAPH_X.toFloat(),
            (GRAPH_Y + GRAPH_HEIGHT).toFloat(),
            (GRAPH_X + GRAPH_WIDTH).toFloat(),
            (GRAPH_Y + GRAPH_HEIGHT).toFloat(),
            paint
        )
        drawScaled(
            canvas,
            GRAPH_X.toFloat(),
            GRAPH_Y.toFloat(),
            GRAPH_X.toFloat(),
            (GRAPH_Y + GRAPH_HEIGHT).toFloat(),
            paint
        )
        drawScaled(
            canvas, (GRAPH_X + GRAPH_WIDTH).toFloat(), GRAPH_Y.toFloat(),
            (GRAPH_X + GRAPH_WIDTH).toFloat(),
            (GRAPH_Y + GRAPH_HEIGHT).toFloat(), paint
        )

        // Draw the curves
        paint.strokeWidth = CURVE_WIDTH
        paint.color = CURVE_COLOR
        var index = 0
        var y0 = 0f
        var y: Float
        var x0 = 0f
        var x: Float
        var offsetX: Float = GRAPH_X.toFloat()
        var offsetY: Float = (GRAPH_Y + 30).toFloat()
        val valueStep = 200f / (samplingRate.toFloat() * 8)
        for (`val` in ecgValues) {
            x = index * valueStep
            y = (-10 * `val`).toFloat()
            if (index == 0) {
                x0 = x
                y0 = y
                index++
                continue
            } else if (index.toDouble() == 8 * samplingRate) {
                offsetX -= (8 * samplingRate * valueStep).toFloat()
                offsetY += 60f
            } else if (index.toDouble() == 16 * samplingRate) {
                offsetX -= (8 * samplingRate * valueStep).toFloat()
                offsetY += 60f
            } else if (index.toDouble() == 24 * samplingRate) {
                offsetX -= (8 * samplingRate * valueStep).toFloat()
                offsetY += 60f
            } else if (index > 32 * samplingRate) {
                // Handle writing to the next page
                break
            }
            drawScaled(
                canvas, x0 + offsetX, y0 + offsetY, x + offsetX,
                y + offsetY, paint
            )
            // QRS Marks
            if ((peakValues != null) && peakValues[index]) {
                drawScaled(
                    canvas,
                    x + offsetX, offsetY + 28,
                    x + offsetX, offsetY + 30,
                    paint1
                )
            }
            y0 = y
            x0 = x
            index++
        }
        return bm
    }


    private fun drawScaled(
        canvas: Canvas, x0: Float, y0: Float,
        x1: Float, y1: Float, paint: Paint
    ) {
        val xx0: Float = SCALE * x0
        val xx1: Float = SCALE * x1
        val yy0: Float = SCALE * y0
        val yy1: Float = SCALE * y1
        canvas.drawLine(xx0, yy0, xx1, yy1, paint)
    }
}