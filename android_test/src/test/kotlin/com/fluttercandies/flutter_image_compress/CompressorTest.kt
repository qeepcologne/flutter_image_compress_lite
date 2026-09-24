package com.fluttercandies.flutter_image_compress

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class CompressorTest {

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        ImageCompressPlugin.showLog = true
        ShadowLog.clear()
    }

    @Test
    fun outputDimensionsMatchFullResolutionFormula() {
        val sizes = listOf(4000 to 3000, 3000 to 4000, 4032 to 3024, 4000 to 2252, 4001 to 3001, 1280 to 960, 1000 to 800)
        val mins = listOf(1280 to 720, 640 to 480, 1920 to 1080, 300 to 300)
        for ((w, h) in sizes) for (src in listOf(Bitmap.CompressFormat.JPEG, Bitmap.CompressFormat.PNG)) {
            val bytes = source(w, h, src)
            for ((minW, minH) in mins) {
                val out = encode(bytes, minW, minH)
                assertEquals("$src ${w}x$h @ ${minW}x$minH", expectedSize(w, h, minW, minH), bounds(out))
            }
        }
    }

    @Test
    fun subsamplesLargeSourcesButNeverBelowTarget() {
        encode(source(4000, 3000), 1280, 720)
        assertEquals(2000 to 1500, decodedSize())

        ShadowLog.clear()
        encode(source(4000, 3000), 640, 480)
        assertEquals(1000 to 750, decodedSize())

        ShadowLog.clear()
        encode(source(1000, 800), 1280, 720)
        assertEquals(1000 to 800, decodedSize())
    }

    // The sampled decode must look like the old full-resolution decode + scale. Pixel differences are expected
    // (different resampling), but only small ones.
    @Test
    fun outputStaysCloseToFullResolutionDecode() {
        for (src in listOf(Bitmap.CompressFormat.JPEG, Bitmap.CompressFormat.PNG)) {
            val bytes = source(4000, 3000, src)
            val sampled = decode(encode(bytes, 1280, 720, format = CompressFormat.PNG))
            val reference = fullResolutionReference(bytes, 1280, 720)
            val diff = meanAbsDiff(sampled, reference)
            println("$src mean abs diff vs full decode: $diff")
            assertTrue("$src diff $diff", diff < 6.0)
        }
    }

    @Test
    fun rotationSwapsOutputAxes() {
        val out = encode(source(4000, 3000), 1280, 720, rotate = 90)
        assertEquals(960 to 1280, bounds(out))
    }

    @Test
    fun fileAndBytesProduceIdenticalOutput() {
        val bytes = source(4032, 3024)
        val file = File.createTempFile("src", ".jpg").apply { writeBytes(bytes); deleteOnExit() }
        val fromFile = ByteArrayOutputStream().also {
            Compressor.encodeFile(context, CompressFormat.JPEG, file.path, it, 1280, 720, 90, 0, false, false)
        }.toByteArray()
        assertTrue(fromFile.contentEquals(encode(bytes, 1280, 720)))
    }

    @Test
    fun zeroMinSizeStillFailsInCreateScaledBitmap() {
        val e = runCatching { encode(source(1000, 800), 0, 0) }.exceptionOrNull()
        assertTrue("got $e", e is IllegalArgumentException)
    }

    @Test
    fun undecodableBytesAreBadImage() {
        val e = runCatching { encode(ByteArray(100) { it.toByte() }, 1280, 720) }.exceptionOrNull()
        assertTrue("got $e", e is CompressException && e.code == "BAD_IMAGE")
    }

    // --- helpers ---

    private fun encode(bytes: ByteArray, minW: Int, minH: Int, rotate: Int = 0, format: CompressFormat = CompressFormat.JPEG) =
        ByteArrayOutputStream().also {
            Compressor.encodeBytes(context, format, bytes, it, minW, minH, 90, rotate, false, false)
        }.toByteArray()

    // What the pre-sampling code produced: scale the full-resolution size down to the envelope, never up.
    private fun expectedSize(w: Int, h: Int, minW: Int, minH: Int): Pair<Int, Int> {
        val scale = max(1f, min(w.toFloat() / minW, h.toFloat() / minH))
        return (w / scale).toInt() to (h / scale).toInt()
    }

    private fun fullResolutionReference(bytes: ByteArray, minW: Int, minH: Int): Bitmap {
        val full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val (w, h) = expectedSize(full.width, full.height, minW, minH)
        return Bitmap.createScaledBitmap(full, w, h, true)
    }

    private fun bounds(bytes: ByteArray): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        return o.outWidth to o.outHeight
    }

    private fun decode(bytes: ByteArray) = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!

    private fun decodedSize(): Pair<Int, Int> {
        val logs = ShadowLog.getLogsForTag(LOG_TAG).map { it.msg }
        fun value(key: String) = logs.last { it.startsWith(key) }.substringAfter("= ").toInt()
        return value("decoded width") to value("decoded height")
    }

    private fun meanAbsDiff(a: Bitmap, b: Bitmap): Double {
        assertEquals(a.width to a.height, b.width to b.height)
        val pa = IntArray(a.width * a.height).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
        val pb = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        var sum = 0L
        for (i in pa.indices) {
            sum += abs(Color.red(pa[i]) - Color.red(pb[i])) +
                abs(Color.green(pa[i]) - Color.green(pb[i])) +
                abs(Color.blue(pa[i]) - Color.blue(pb[i]))
        }
        return sum.toDouble() / (pa.size * 3)
    }

    // Photo-ish content (smooth gradients) plus screenshot-ish detail (1 px lines), so both resampling
    // paths — decoder downscale and pixel skipping — get exercised.
    private fun source(w: Int, h: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawPaint(Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Color.rgb(20, 60, 200), Color.rgb(240, 180, 30), Shader.TileMode.CLAMP)
        })
        val line = Paint().apply { color = Color.WHITE; strokeWidth = 1f }
        for (x in 0 until w step 37) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), h.toFloat(), line)
        return ByteArrayOutputStream().also { bmp.compress(format, 90, it) }.toByteArray()
    }
}
