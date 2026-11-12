package chat.mural.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates and caches dark cosmic Yamy orb artwork bitmaps for MediaMetadata / MediaStyle
 * lock-screen and notification shade rendering across all Android OEMs.
 */
object YamyOrbArtworkFactory {
    private const val DEFAULT_SIZE_PX = 512
    private val cacheLock = Any()
    private val cachedBitmaps = HashMap<Int, Bitmap>()

    fun createArtworkBitmap(sizePx: Int = DEFAULT_SIZE_PX): Bitmap {
        val safeSize = sizePx.coerceIn(64, 1024)
        synchronized(cacheLock) {
            cachedBitmaps[safeSize]?.takeIf { !it.isRecycled }?.let { return it }
            val bitmap = renderOrbArtwork(safeSize)
            cachedBitmaps[safeSize] = bitmap
            return bitmap
        }
    }

    private fun renderOrbArtwork(sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = sizePx.toFloat()
        val h = sizePx.toFloat()
        val cx = w * 0.5f
        val cy = h * 0.5f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Deep cosmic night backdrop so colorized MediaStyle notifications pick rich dark-indigo tones.
        paint.shader = RadialGradient(
            cx,
            cy,
            w * 0.78f,
            intArrayOf(
                Color.parseColor("#112142"),
                Color.parseColor("#0A1328"),
                Color.parseColor("#050914"),
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, paint)

        // Outer violet-indigo atmospheric halo
        paint.shader = RadialGradient(
            cx - w * 0.14f,
            cy - h * 0.10f,
            w * 0.56f,
            intArrayOf(
                Color.argb(120, 118, 75, 232),
                Color.argb(48, 77, 118, 255),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx - w * 0.10f, cy - h * 0.06f, w * 0.56f, paint)

        // Warm amber-orange solar corona halo on opposite side
        paint.shader = RadialGradient(
            cx + w * 0.16f,
            cy + h * 0.14f,
            w * 0.50f,
            intArrayOf(
                Color.argb(115, 255, 140, 66),
                Color.argb(52, 246, 208, 79),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx + w * 0.14f, cy + h * 0.10f, w * 0.50f, paint)

        // Main cerulean-cobalt nebula sphere
        val orbRadius = w * 0.34f
        paint.shader = RadialGradient(
            cx,
            cy,
            orbRadius * 1.12f,
            intArrayOf(
                Color.parseColor("#091838"),
                Color.parseColor("#13468B"),
                Color.parseColor("#2484E0"),
                Color.parseColor("#5AB0FF"),
                Color.argb(0, 77, 163, 255),
            ),
            floatArrayOf(0f, 0.38f, 0.68f, 0.90f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, orbRadius * 1.12f, paint)

        // Tilted luminous helix ring (gold, violet, cyan, warm orange)
        canvas.save()
        canvas.rotate(-22f, cx, cy)
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = orbRadius * 0.24f
            shader = SweepGradient(
                cx,
                cy,
                intArrayOf(
                    Color.argb(185, 246, 208, 79),
                    Color.argb(175, 255, 148, 77),
                    Color.argb(150, 140, 108, 255),
                    Color.argb(165, 115, 195, 252),
                    Color.argb(185, 246, 208, 79),
                ),
                floatArrayOf(0f, 0.25f, 0.52f, 0.78f, 1f),
            )
        }
        val outerOval = RectF(
            cx - orbRadius * 0.64f,
            cy - orbRadius * 0.48f,
            cx + orbRadius * 0.64f,
            cy + orbRadius * 0.48f,
        )
        canvas.drawOval(outerOval, ringPaint)

        // Inner ice-cyan rim
        ringPaint.strokeWidth = orbRadius * 0.10f
        ringPaint.shader = SweepGradient(
            cx,
            cy,
            intArrayOf(
                Color.argb(145, 188, 232, 255),
                Color.argb(120, 115, 195, 252),
                Color.argb(140, 255, 244, 194),
                Color.argb(145, 188, 232, 255),
            ),
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
        )
        val innerOval = RectF(
            cx - orbRadius * 0.44f,
            cy - orbRadius * 0.34f,
            cx + orbRadius * 0.44f,
            cy + orbRadius * 0.34f,
        )
        canvas.drawOval(innerOval, ringPaint)
        canvas.restore()

        // Deep cobalt central "eye" core
        paint.shader = RadialGradient(
            cx,
            cy,
            orbRadius * 0.42f,
            intArrayOf(
                Color.argb(230, 8, 20, 48),
                Color.argb(145, 19, 70, 139),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, orbRadius * 0.42f, paint)

        // Subtle star glints around the nebula
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = null
            color = Color.argb(160, 224, 242, 255)
        }
        val starAngles = floatArrayOf(0.45f, 1.35f, 2.25f, 3.10f, 4.15f, 5.20f)
        starAngles.forEachIndexed { idx, angle ->
            val dist = w * (0.37f + (idx % 3) * 0.045f)
            val sx = cx + cos(angle) * dist
            val sy = cy + sin(angle) * dist
            val r = w * (0.0045f + (idx % 2) * 0.0025f)
            starPaint.alpha = if (idx % 2 == 0) 175 else 110
            canvas.drawCircle(sx, sy, r, starPaint)
        }

        return bitmap
    }
}
