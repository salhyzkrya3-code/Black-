package com.agon.app.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.io.InputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.StringTokenizer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.abs
import kotlin.math.sqrt

enum class EngineMode { RGB, LAB }

data class Adjustments(
    val temp: Float = 0f,       
    val tint: Float = 0f,       
    val saturation: Float = 1f, 
    val vibrance: Float = 0f,   
    val contrast: Float = 1f,   
    val ultraBlack: Float = 0f, 
    val ultraWhite: Float = 1f, 
    val hue: Float = 0f,
    val texture: Float = 0f,    
    val grain: Float = 0f,
    val exposure: Float = 0f,
    val highlightsRecovery: Float = 0f,
    val shadowsRecovery: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val clarity: Float = 0f,
    val dehaze: Float = 0f
)

data class SelectiveColor(
    val id: String,
    val sourceColor: Int,
    var targetColor: Int,
    var intensity: Float = 1f,
    var saturation: Float = 1f,
    var lightness: Float = 0f,
    var range: Float = 45f,
    var isEnabled: Boolean = true
)

data class TonalAdjustments(
    var shadowHue: Float = 0f, var shadowSat: Float = 0f, var shadowLum: Float = 0f,
    var midHue: Float = 0f, var midSat: Float = 0f, var midLum: Float = 0f,
    var highHue: Float = 0f, var highSat: Float = 0f, var highLum: Float = 0f,
    var shadowBalance: Float = 0f, var highBalance: Float = 0f, var balance: Float = 0f,
    var splitSat: Float = 1f
)

data class HslColor(
    var hue: Float = 0f,
    var saturation: Float = 0f,
    var luminance: Float = 0f
)

data class HslAdjustments(
    val red: HslColor = HslColor(),
    val orange: HslColor = HslColor(),
    val yellow: HslColor = HslColor(),
    val green: HslColor = HslColor(),
    val cyan: HslColor = HslColor(),
    val blue: HslColor = HslColor(),
    val purple: HslColor = HslColor(),
    val magenta: HslColor = HslColor()
)

data class ColorWheels(
    var liftAngle: Float = 0f, var liftRadius: Float = 0f, var liftY: Float = 0f,
    var gammaAngle: Float = 0f, var gammaRadius: Float = 0f, var gammaY: Float = 0f,
    var gainAngle: Float = 0f, var gainRadius: Float = 0f, var gainY: Float = 0f,
    var offsetAngle: Float = 0f, var offsetRadius: Float = 0f, var offsetY: Float = 0f
)

data class AdvancedCurves(
    val rgb: FloatArray = floatArrayOf(0f, 0f, 0.25f, 0.25f, 0.5f, 0.5f, 0.75f, 0.75f, 1f, 1f),
    val red: FloatArray = floatArrayOf(0f, 0f, 0.25f, 0.25f, 0.5f, 0.5f, 0.75f, 0.75f, 1f, 1f),
    val green: FloatArray = floatArrayOf(0f, 0f, 0.25f, 0.25f, 0.5f, 0.5f, 0.75f, 0.75f, 1f, 1f),
    val blue: FloatArray = floatArrayOf(0f, 0f, 0.25f, 0.25f, 0.5f, 0.5f, 0.75f, 0.75f, 1f, 1f),
    val lum: FloatArray = floatArrayOf(0f, 0f, 0.25f, 0.25f, 0.5f, 0.5f, 0.75f, 0.75f, 1f, 1f)
)

data class ChannelMixer(
    var redInRed: Float = 1f, var greenInRed: Float = 0f, var blueInRed: Float = 0f,
    var redInGreen: Float = 0f, var greenInGreen: Float = 1f, var blueInGreen: Float = 0f,
    var redInBlue: Float = 0f, var greenInBlue: Float = 0f, var blueInBlue: Float = 1f
)

data class CinematicEffects(
    var halationThreshold: Float = 0.8f,
    var halationIntensity: Float = 0f,
    var halationSpread: Float = 2f,
    var falseColorEnabled: Boolean = false
)

data class ColorBalance(
    var shadowsCyanRed: Float = 0f, var shadowsMagentaGreen: Float = 0f, var shadowsYellowBlue: Float = 0f,
    var midtonesCyanRed: Float = 0f, var midtonesMagentaGreen: Float = 0f, var midtonesYellowBlue: Float = 0f,
    var highlightsCyanRed: Float = 0f, var highlightsMagentaGreen: Float = 0f, var highlightsYellowBlue: Float = 0f,
    var preserveLuminance: Boolean = true
)

data class CurveSliders(
    var blacks: Float = 0f,      
    var shadows: Float = 0.25f,  
    var midtones: Float = 0.5f,  
    var highlights: Float = 0.75f,
    var whites: Float = 1f       
)

class CubeLut(val size: Int, val data: FloatArray) {
    fun lookup(r: Float, g: Float, b: Float, out: FloatArray) {
        val rVal = r.coerceIn(0f, 1f) * (size - 1)
        val gVal = g.coerceIn(0f, 1f) * (size - 1)
        val bVal = b.coerceIn(0f, 1f) * (size - 1)
        
        val r0 = rVal.toInt().coerceIn(0, size - 2)
        val g0 = gVal.toInt().coerceIn(0, size - 2)
        val b0 = bVal.toInt().coerceIn(0, size - 2)
        
        val r1 = r0 + 1
        val g1 = g0 + 1
        val b1 = b0 + 1
        
        val rd = rVal - r0
        val gd = gVal - g0
        val bd = bVal - b0
        
        val sizeSq = size * size
        
        fun getIdx(ir: Int, ig: Int, ib: Int): Int = (ir + ig * size + ib * sizeSq) * 3
        
        for (i in 0..2) {
            val c000 = data[getIdx(r0, g0, b0) + i]
            val c100 = data[getIdx(r1, g0, b0) + i]
            val c010 = data[getIdx(r0, g1, b0) + i]
            val c110 = data[getIdx(r1, g1, b0) + i]
            val c001 = data[getIdx(r0, g0, b1) + i]
            val c101 = data[getIdx(r1, g0, b1) + i]
            val c011 = data[getIdx(r0, g1, b1) + i]
            val c111 = data[getIdx(r1, g1, b1) + i]
            
            val c00 = c000 * (1 - rd) + c100 * rd
            val c01 = c001 * (1 - rd) + c101 * rd
            val c10 = c010 * (1 - rd) + c110 * rd
            val c11 = c011 * (1 - rd) + c111 * rd
            
            val c0 = c00 * (1 - gd) + c10 * gd
            val c1 = c01 * (1 - gd) + c11 * gd
            
            out[i] = c0 * (1 - bd) + c1 * bd
        }
    }
}

object LutUtils {
    private const val Xn = 0.95047f
    private const val Yn = 1.00000f
    private const val Zn = 1.08883f

    private fun srgbToLinear(c: Float): Float = if (c > 0.04045f) ((c + 0.055f) / 1.055f).pow(2.4f) else c / 12.92f
    private fun linearToSrgb(c: Float): Float = if (c > 0.0031308f) 1.055f * c.pow(1f / 2.4f) - 0.055f else 12.92f * c

    private fun rgbToLab(r: Float, g: Float, b: Float, lab: FloatArray) {
        val lr = srgbToLinear(r)
        val lg = srgbToLinear(g)
        val lb = srgbToLinear(b)
        val x = (lr * 0.4124564f + lg * 0.3575761f + lb * 0.1804375f) / Xn
        val y = (lr * 0.2126729f + lg * 0.7151522f + lb * 0.0721750f) / Yn
        val z = (lr * 0.0193339f + lg * 0.1191920f + lb * 0.9503041f) / Zn
        fun f(t: Float): Float = if (t > 0.008856f) t.pow(1f / 3f) else (7.787f * t) + (16f / 116f)
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        lab[0] = (116f * fy) - 16f
        lab[1] = 500f * (fx - fy)
        lab[2] = 200f * (fy - fz)
    }

    private fun labToRgb(l: Float, a: Float, b: Float, rgb: FloatArray) {
        val fy = (l + 16f) / 116f
        val fx = a / 500f + fy
        val fz = fy - b / 200f
        fun fInv(t: Float): Float {
            val t3 = t * t * t
            return if (t3 > 0.008856f) t3 else (t - 16f / 116f) / 7.787f
        }
        val x = fInv(fx) * Xn
        val y = fInv(fy) * Yn
        val z = fInv(fz) * Zn
        val lr = x * 3.2404542f + y * -1.5371385f + z * -0.4985314f
        val lg = x * -0.9692660f + y * 1.8760108f + z * 0.0415560f
        val lb = x * 0.0556434f + y * -0.2040259f + z * 1.0572252f
        rgb[0] = linearToSrgb(lr).coerceIn(0f, 1f)
        rgb[1] = linearToSrgb(lg).coerceIn(0f, 1f)
        rgb[2] = linearToSrgb(lb).coerceIn(0f, 1f)
    }

    private fun rgbToHsl(r: Float, g: Float, b: Float, hsl: FloatArray) {
        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        var h = 0f
        val l = (max + min) / 2f
        var s = 0f
        if (max != min) {
            val d = max - min
            s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
            h = when (max) {
                r -> (g - b) / d + (if (g < b) 6f else 0f)
                g -> (b - r) / d + 2f
                b -> (r - g) / d + 4f
                else -> 0f
            }
            h /= 6f
        }
        hsl[0] = h
        hsl[1] = s
        hsl[2] = l
    }
    
    private fun hueToRgb(p: Float, q: Float, t: Float): Float {
        var tc = t
        if (tc < 0f) tc += 1f
        if (tc > 1f) tc -= 1f
        if (tc < 1f / 6f) return p + (q - p) * 6f * tc
        if (tc < 1f / 2f) return q
        if (tc < 2f / 3f) return p + (q - p) * (2f / 3f - tc) * 6f
        return p
    }

    private fun hslToRgb(h: Float, s: Float, l: Float, rgb: FloatArray) {
        if (s == 0f) {
            rgb[0] = l; rgb[1] = l; rgb[2] = l
        } else {
            val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
            val p = 2f * l - q
            rgb[0] = hueToRgb(p, q, h + 1f / 3f)
            rgb[1] = hueToRgb(p, q, h)
            rgb[2] = hueToRgb(p, q, h - 1f / 3f)
        }
    }

    // Auto Palette Extraction (K-Means in LAB space)
    suspend fun extractPalette(bitmap: Bitmap, k: Int = 6): List<Int> = withContext(Dispatchers.Default) {
        val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        val pixels = IntArray(64 * 64)
        scaled.getPixels(pixels, 0, 64, 0, 0, 64, 64)

        val labPixels = Array(pixels.size) { FloatArray(3) }
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = ((c shr 16) and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            rgbToLab(r, g, b, labPixels[i])
        }

        // Init centroids
        val centroids = Array(k) { labPixels[it * (pixels.size / k)].clone() }
        val assignments = IntArray(pixels.size)

        for (iter in 0 until 10) {
            for (i in labPixels.indices) {
                var bestDist = Float.MAX_VALUE
                var bestK = 0
                for (j in 0 until k) {
                    val dL = labPixels[i][0] - centroids[j][0]
                    val da = labPixels[i][1] - centroids[j][1]
                    val db = labPixels[i][2] - centroids[j][2]
                    val dist = dL*dL + da*da + db*db
                    if (dist < bestDist) {
                        bestDist = dist
                        bestK = j
                    }
                }
                assignments[i] = bestK
            }

            val counts = IntArray(k)
            val sums = Array(k) { FloatArray(3) }
            for (i in labPixels.indices) {
                val cluster = assignments[i]
                counts[cluster]++
                sums[cluster][0] += labPixels[i][0]
                sums[cluster][1] += labPixels[i][1]
                sums[cluster][2] += labPixels[i][2]
            }
            for (j in 0 until k) {
                if (counts[j] > 0) {
                    centroids[j][0] = sums[j][0] / counts[j]
                    centroids[j][1] = sums[j][1] / counts[j]
                    centroids[j][2] = sums[j][2] / counts[j]
                }
            }
        }

        val result = mutableListOf<Int>()
        val rgbOut = FloatArray(3)
        for (j in 0 until k) {
            labToRgb(centroids[j][0], centroids[j][1], centroids[j][2], rgbOut)
            val r = (rgbOut[0] * 255).toInt().coerceIn(0, 255)
            val g = (rgbOut[1] * 255).toInt().coerceIn(0, 255)
            val b = (rgbOut[2] * 255).toInt().coerceIn(0, 255)
            result.add(Color.rgb(r, g, b))
        }
        
        // Sort by luminance for consistent UI
        result.distinct().sortedBy { 
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(it, hsl)
            hsl[2]
        }
    }

    fun calculateLabStats(bitmap: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, 128, 128, true)
        val pixels = IntArray(128 * 128)
        scaled.getPixels(pixels, 0, 128, 0, 0, 128, 128)
        
        var sumL = 0.0; var sumA = 0.0; var sumB = 0.0
        val lab = FloatArray(3)
        
        for (c in pixels) {
            val r = ((c shr 16) and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            rgbToLab(r, g, b, lab)
            sumL += lab[0]
            sumA += lab[1]
            sumB += lab[2]
        }
        
        val n = pixels.size.toDouble()
        val meanL = (sumL / n).toFloat()
        val meanA = (sumA / n).toFloat()
        val meanB = (sumB / n).toFloat()
        
        var varL = 0.0; var varA = 0.0; var varB = 0.0
        for (c in pixels) {
            val r = ((c shr 16) and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            rgbToLab(r, g, b, lab)
            varL += (lab[0] - meanL).pow(2)
            varA += (lab[1] - meanA).pow(2)
            varB += (lab[2] - meanB).pow(2)
        }
        
        val stdL = max(sqrt(varL / n).toFloat(), 1f)
        val stdA = max(sqrt(varA / n).toFloat(), 1f)
        val stdB = max(sqrt(varB / n).toFloat(), 1f)
        
        return floatArrayOf(meanL, stdL, meanA, stdA, meanB, stdB)
    }

    suspend fun parseCube(inputStream: InputStream): CubeLut? = withContext(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(inputStream), 65536)
        var size = 0
        var data: FloatArray? = null
        var idx = 0
        var line: String?
        
        while (reader.readLine().also { line = it } != null) {
            val l = line!!.trim()
            if (l.isEmpty() || l.startsWith("#") || l.startsWith("TITLE")) continue
            if (l.startsWith("LUT_3D_SIZE")) {
                size = l.substringAfter("LUT_3D_SIZE").trim().toInt()
                data = FloatArray(size * size * size * 3)
                continue
            }
            if (size > 0 && data != null) {
                val tokenizer = StringTokenizer(l)
                if (tokenizer.countTokens() >= 3) {
                    try {
                        data[idx++] = tokenizer.nextToken().toFloat()
                        data[idx++] = tokenizer.nextToken().toFloat()
                        data[idx++] = tokenizer.nextToken().toFloat()
                    } catch (e: Exception) {}
                }
            }
        }
        if (size > 0 && data != null && idx == data.size) {
            return@withContext CubeLut(size, data)
        }
        return@withContext null
    }

    private fun precomputeAdvancedCurve(points: FloatArray): FloatArray {
        val lut = FloatArray(256)
        // Spline interpolation for 5 points (x,y pairs)
        val p = Array(5) { Offset(points[it*2], points[it*2+1]) }
        for (i in 0..255) {
            val x = i / 255f
            // Simple linear interpolation for now (can be upgraded to cubic spline)
            var y = 0f
            if (x <= p[0].x) y = p[0].y
            else if (x >= p[4].x) y = p[4].y
            else {
                for (j in 0 until 4) {
                    if (x >= p[j].x && x <= p[j+1].x) {
                        val t = (x - p[j].x) / (p[j+1].x - p[j].x)
                        y = p[j].y * (1 - t) + p[j+1].y * t
                        break
                    }
                }
            }
            lut[i] = y.coerceIn(0f, 1f)
        }
        return lut
    }

    private fun evaluateCurve(x: Float, lut: FloatArray): Float {
        val idx = (x.coerceIn(0f, 1f) * 255).toInt()
        return lut[idx]
    }

    private fun precomputeCurve(curves: CurveSliders): FloatArray {
        val points = listOf(
            Offset(0f, curves.blacks),
            Offset(0.25f, curves.shadows),
            Offset(0.5f, curves.midtones),
            Offset(0.75f, curves.highlights),
            Offset(1f, curves.whites)
        )
        val lut = FloatArray(256)
        val sorted = points.sortedBy { it.x }
        for (i in 0..255) {
            val x = i / 255f
            if (x <= sorted.first().x) {
                lut[i] = sorted.first().y
            } else if (x >= sorted.last().x) {
                lut[i] = sorted.last().y
            } else {
                for (j in 0 until sorted.size - 1) {
                    val p1 = sorted[j]
                    val p2 = sorted[j+1]
                    if (x >= p1.x && x <= p2.x) {
                        val t = (x - p1.x) / (p2.x - p1.x)
                        lut[i] = p1.y + t * (p2.y - p1.y)
                        break
                    }
                }
            }
        }
        return lut
    }

    fun mixLutsAndAdjustments(
        luts: List<Pair<CubeLut, Float>>, 
        selectiveColors: List<SelectiveColor>,
        adj: Adjustments,
        engineMode: EngineMode,
        matchSourceStats: FloatArray?,
        matchTargetStats: FloatArray?,
        matchLum: Float,
        matchIntensity: Float,
        matchFade: Float,
        toning: TonalAdjustments,
        curves: CurveSliders,
        hslAdj: HslAdjustments = HslAdjustments(),
        colorWheels: ColorWheels = ColorWheels(),
        advancedCurves: AdvancedCurves = AdvancedCurves(),
        colorBalance: ColorBalance = ColorBalance(),
        channelMixer: ChannelMixer = ChannelMixer(),
        cinematicEffects: CinematicEffects = CinematicEffects(),
        targetSize: Int = 32
    ): CubeLut {
        val data = FloatArray(targetSize * targetSize * targetSize * 3)
        var idx = 0
        
        val labOrig = FloatArray(3)
        val labCube = FloatArray(3)
        val rgbOut = FloatArray(3)
        val hsl = FloatArray(3)
        val tempLutOut = FloatArray(3)
        
        val curveLut = precomputeCurve(curves)
        val advCurveRgb = precomputeAdvancedCurve(advancedCurves.rgb)
        val advCurveR = precomputeAdvancedCurve(advancedCurves.red)
        val advCurveG = precomputeAdvancedCurve(advancedCurves.green)
        val advCurveB = precomputeAdvancedCurve(advancedCurves.blue)
        val advCurveLum = precomputeAdvancedCurve(advancedCurves.lum)
        
        // Only process enabled selective colors
        val activeSc = selectiveColors.filter { it.isEnabled }
        val scData = activeSc.map { sc ->
            val srcHsl = FloatArray(3)
            val tgtHsl = FloatArray(3)
            ColorUtils.colorToHSL(sc.sourceColor, srcHsl)
            ColorUtils.colorToHSL(sc.targetColor, tgtHsl)
            
            val hDiff = tgtHsl[0] - srcHsl[0]
            val sDiff = tgtHsl[1] - srcHsl[1]
            val lDiff = tgtHsl[2] - srcHsl[2]
            
            floatArrayOf(srcHsl[0], hDiff, sDiff, lDiff, sc.intensity, sc.saturation, sc.lightness, sc.range)
        }
        
        val liftRgb = FloatArray(3)
        hslToRgb(toning.shadowHue / 360f, toning.shadowSat, 0.5f, liftRgb)
        val cwLiftRgb = FloatArray(3)
        hslToRgb(colorWheels.liftAngle / 360f, colorWheels.liftRadius, 0.5f, cwLiftRgb)
        val liftR = (liftRgb[0] - 0.5f) * 2f * 0.2f + toning.shadowLum + (cwLiftRgb[0] - 0.5f) * 2f + colorWheels.liftY
        val liftG = (liftRgb[1] - 0.5f) * 2f * 0.2f + toning.shadowLum + (cwLiftRgb[1] - 0.5f) * 2f + colorWheels.liftY
        val liftB = (liftRgb[2] - 0.5f) * 2f * 0.2f + toning.shadowLum + (cwLiftRgb[2] - 0.5f) * 2f + colorWheels.liftY
        
        val gammaRgb = FloatArray(3)
        hslToRgb(toning.midHue / 360f, toning.midSat, 0.5f, gammaRgb)
        val cwGammaRgb = FloatArray(3)
        hslToRgb(colorWheels.gammaAngle / 360f, colorWheels.gammaRadius, 0.5f, cwGammaRgb)
        val gammaR = (gammaRgb[0] - 0.5f) * 2f * 0.5f + toning.midLum + (cwGammaRgb[0] - 0.5f) * 2f + colorWheels.gammaY
        val gammaG = (gammaRgb[1] - 0.5f) * 2f * 0.5f + toning.midLum + (cwGammaRgb[1] - 0.5f) * 2f + colorWheels.gammaY
        val gammaB = (gammaRgb[2] - 0.5f) * 2f * 0.5f + toning.midLum + (cwGammaRgb[2] - 0.5f) * 2f + colorWheels.gammaY
        
        val gainRgb = FloatArray(3)
        hslToRgb(toning.highHue / 360f, toning.highSat, 0.5f, gainRgb)
        val cwGainRgb = FloatArray(3)
        hslToRgb(colorWheels.gainAngle / 360f, colorWheels.gainRadius, 0.5f, cwGainRgb)
        val gainR = (gainRgb[0] - 0.5f) * 2f * 0.5f + toning.highLum + (cwGainRgb[0] - 0.5f) * 2f + colorWheels.gainY
        val gainG = (gainRgb[1] - 0.5f) * 2f * 0.5f + toning.highLum + (cwGainRgb[1] - 0.5f) * 2f + colorWheels.gainY
        val gainB = (gainRgb[2] - 0.5f) * 2f * 0.5f + toning.highLum + (cwGainRgb[2] - 0.5f) * 2f + colorWheels.gainY
        
        val cwOffsetRgb = FloatArray(3)
        hslToRgb(colorWheels.offsetAngle / 360f, colorWheels.offsetRadius, 0.5f, cwOffsetRgb)
        val offsetR = (cwOffsetRgb[0] - 0.5f) * 2f + colorWheels.offsetY
        val offsetG = (cwOffsetRgb[1] - 0.5f) * 2f + colorWheels.offsetY
        val offsetB = (cwOffsetRgb[2] - 0.5f) * 2f + colorWheels.offsetY

        for (b in 0 until targetSize) {
            for (g in 0 until targetSize) {
                for (r in 0 until targetSize) {
                    var cr = r.toFloat() / (targetSize - 1)
                    var cg = g.toFloat() / (targetSize - 1)
                    var cb = b.toFloat() / (targetSize - 1)
                    
                    cr = evaluateCurve(cr, curveLut)
                    cg = evaluateCurve(cg, curveLut)
                    cb = evaluateCurve(cb, curveLut)
                    
                    // Advanced Curves
                    cr = evaluateCurve(evaluateCurve(cr, advCurveR), advCurveRgb)
                    cg = evaluateCurve(evaluateCurve(cg, advCurveG), advCurveRgb)
                    cb = evaluateCurve(evaluateCurve(cb, advCurveB), advCurveRgb)
                    
                    val lumC = (cr + cg + cb) / 3f
                    val newLumC = evaluateCurve(lumC, advCurveLum)
                    if (lumC > 0.001f) {
                        val ratio = newLumC / lumC
                        cr = (cr * ratio).coerceIn(0f, 1f)
                        cg = (cg * ratio).coerceIn(0f, 1f)
                        cb = (cb * ratio).coerceIn(0f, 1f)
                    }
                    
                    cr = (cr * (1f + gainR) + liftR + offsetR).pow(1f - gammaR).coerceIn(0f, 1f)
                    cg = (cg * (1f + gainG) + liftG + offsetG).pow(1f - gammaG).coerceIn(0f, 1f)
                    cb = (cb * (1f + gainB) + liftB + offsetB).pow(1f - gammaB).coerceIn(0f, 1f)

                    if (engineMode == EngineMode.LAB) {
                        rgbToLab(cr, cg, cb, labOrig)
                        rgbToHsl(cr, cg, cb, hsl)
                        
                        var h = hsl[0] * 360f
                        var s = hsl[1]
                        var l = hsl[2]
                        
                        // HSL Panel Processing
                        val hslColors = arrayOf(hslAdj.red, hslAdj.orange, hslAdj.yellow, hslAdj.green, hslAdj.cyan, hslAdj.blue, hslAdj.purple, hslAdj.magenta)
                        val hslTargets = floatArrayOf(0f, 30f, 60f, 120f, 180f, 240f, 270f, 300f)
                        
                        for (i in 0 until 8) {
                            val adjColor = hslColors[i]
                            if (adjColor.hue != 0f || adjColor.saturation != 0f || adjColor.luminance != 0f) {
                                var dist = abs(h - hslTargets[i])
                                if (dist > 180f) dist = 360f - dist
                                if (dist < 45f) { // 45 degree range
                                    val t = 1f - (dist / 45f)
                                    val weight = t * t // Smooth falloff
                                    h += adjColor.hue * 45f * weight
                                    s += adjColor.saturation * weight
                                    l += adjColor.luminance * weight
                                }
                            }
                        }
                        
                        for (sc in scData) {
                            val range = sc[7]
                            var dist = abs(h - sc[0])
                            if (dist > 180f) dist = 360f - dist
                            if (dist < range) { 
                                val t = 1f - (dist / range)
                                val weight = t * t * sc[4]
                                h += sc[1] * weight
                                s += sc[2] * weight
                                l += sc[3] * weight
                                s *= (1f + (sc[5] - 1f) * weight)
                                l += sc[6] * weight
                            }
                        }
                        
                        h = (h / 360f).coerceIn(0f, 1f)
                        s = s.coerceIn(0f, 1f)
                        l = l.coerceIn(0f, 1f)
                        hslToRgb(h, s, l, rgbOut)
                        
                        rgbToLab(rgbOut[0], rgbOut[1], rgbOut[2], labOrig)
                        
                        if (matchSourceStats != null && matchTargetStats != null && matchFade < 1f) {
                            val msL = matchSourceStats[0]; val ssL = matchSourceStats[1]
                            val msA = matchSourceStats[2]; val ssA = matchSourceStats[3]
                            val msB = matchSourceStats[4]; val ssB = matchSourceStats[5]
                            
                            val mtL = matchTargetStats[0]; val stL = matchTargetStats[1]
                            val mtA = matchTargetStats[2]; val stA = matchTargetStats[3]
                            val mtB = matchTargetStats[4]; val stB = matchTargetStats[5]
                            
                            var matchedL = msL + (labOrig[0] - mtL) * (ssL / stL)
                            var matchedA = msA + (labOrig[1] - mtA) * (ssA / stA)
                            var matchedB = msB + (labOrig[2] - mtB) * (ssB / stB)
                            
                            matchedL *= matchLum
                            matchedA *= matchIntensity
                            matchedB *= matchIntensity
                            
                            val f = matchFade
                            labOrig[0] = labOrig[0] * f + matchedL * (1f - f)
                            labOrig[1] = labOrig[1] * f + matchedA * (1f - f)
                            labOrig[2] = labOrig[2] * f + matchedB * (1f - f)
                            
                            labOrig[0] = labOrig[0].coerceIn(0f, 100f)
                        }

                        var lLab = labOrig[0]
                        lLab = ((lLab - 50f) * adj.contrast) + 50f
                        lLab = (lLab * adj.ultraWhite) + (adj.ultraBlack * 100f)
                        labOrig[0] = lLab.coerceIn(0f, 100f)
                        
                        labToRgb(labOrig[0], labOrig[1], labOrig[2], rgbOut)
                        var tempR = rgbOut[0]
                        var tempG = rgbOut[1]
                        var tempB = rgbOut[2]
                        
                        // Color Balance (Shadows, Midtones, Highlights)
                        val lum = (tempR + tempG + tempB) / 3f
                        val shadowWeight = (1f - lum).pow(2f)
                        val highlightWeight = lum.pow(2f)
                        val midWeight = 1f - shadowWeight - highlightWeight

                        var rBal = 0f
                        var gBal = 0f
                        var bBal = 0f

                        // Shadows
                        rBal += colorBalance.shadowsCyanRed * shadowWeight
                        gBal += colorBalance.shadowsMagentaGreen * shadowWeight
                        bBal += colorBalance.shadowsYellowBlue * shadowWeight

                        // Midtones
                        rBal += colorBalance.midtonesCyanRed * midWeight
                        gBal += colorBalance.midtonesMagentaGreen * midWeight
                        bBal += colorBalance.midtonesYellowBlue * midWeight

                        // Highlights
                        rBal += colorBalance.highlightsCyanRed * highlightWeight
                        gBal += colorBalance.highlightsMagentaGreen * highlightWeight
                        bBal += colorBalance.highlightsYellowBlue * highlightWeight

                        tempR = (tempR + rBal).coerceIn(0f, 1f)
                        tempG = (tempG + gBal).coerceIn(0f, 1f)
                        tempB = (tempB + bBal).coerceIn(0f, 1f)

                        if (colorBalance.preserveLuminance) {
                            val newLum = (tempR + tempG + tempB) / 3f
                            val lumDiff = lum - newLum
                            tempR = (tempR + lumDiff).coerceIn(0f, 1f)
                            tempG = (tempG + lumDiff).coerceIn(0f, 1f)
                            tempB = (tempB + lumDiff).coerceIn(0f, 1f)
                        }

                        // Channel Mixer
                        val mixedR = (tempR * channelMixer.redInRed + tempG * channelMixer.greenInRed + tempB * channelMixer.blueInRed).coerceIn(0f, 1f)
                        val mixedG = (tempR * channelMixer.redInGreen + tempG * channelMixer.greenInGreen + tempB * channelMixer.blueInGreen).coerceIn(0f, 1f)
                        val mixedB = (tempR * channelMixer.redInBlue + tempG * channelMixer.greenInBlue + tempB * channelMixer.blueInBlue).coerceIn(0f, 1f)
                        tempR = mixedR
                        tempG = mixedG
                        tempB = mixedB
                        
                        // False Color (IRE simulation)
                        if (cinematicEffects.falseColorEnabled) {
                            val ire = (tempR * 0.2126f + tempG * 0.7152f + tempB * 0.0722f)
                            when {
                                ire < 0.02f -> { tempR = 0.5f; tempG = 0f; tempB = 0.5f } // Purple (Crushed Shadows)
                                ire in 0.02f..0.1f -> { tempR = 0f; tempG = 0f; tempB = 1f } // Blue (Shadows)
                                ire in 0.42f..0.48f -> { tempR = 0.5f; tempG = 0.5f; tempB = 0.5f } // Middle Gray
                                ire in 0.52f..0.56f -> { tempR = 1f; tempG = 0.5f; tempB = 0.5f } // Pink (Skin tones)
                                ire in 0.78f..0.84f -> { tempR = 1f; tempG = 1f; tempB = 0f } // Yellow (Highlights)
                                ire > 0.98f -> { tempR = 1f; tempG = 0f; tempB = 0f } // Red (Clipping)
                                else -> { tempR = ire; tempG = ire; tempB = ire } // Grayscale
                            }
                        }
                        
                        // Halation (Basic approximation inside LUT - better done in post-processing shader, but this adds warmth to highlights)
                        if (cinematicEffects.halationIntensity > 0f) {
                            val luma = (tempR + tempG + tempB) / 3f
                            if (luma > cinematicEffects.halationThreshold) {
                                val amount = (luma - cinematicEffects.halationThreshold) / (1f - cinematicEffects.halationThreshold) * cinematicEffects.halationIntensity
                                tempR = (tempR + amount).coerceIn(0f, 1f)
                                tempG = (tempG + amount * 0.2f).coerceIn(0f, 1f) // slight orange/red push
                            }
                        }
                        
                        tempR = (tempR * (1f + adj.exposure) + adj.blacks).coerceIn(0f, 1f)
                        tempG = (tempG * (1f + adj.exposure) + adj.blacks).coerceIn(0f, 1f)
                        tempB = (tempB * (1f + adj.exposure) + adj.blacks).coerceIn(0f, 1f)
                        
                        // Highlights / Shadows Recovery
                        val lum2 = (tempR + tempG + tempB) / 3f
                        if (adj.highlightsRecovery > 0f && lum2 > 0.5f) {
                            val recovery = (lum2 - 0.5f) * 2f * adj.highlightsRecovery
                            tempR = (tempR - recovery).coerceIn(0f, 1f)
                            tempG = (tempG - recovery).coerceIn(0f, 1f)
                            tempB = (tempB - recovery).coerceIn(0f, 1f)
                        }
                        if (adj.shadowsRecovery > 0f && lum2 < 0.5f) {
                            val recovery = (0.5f - lum2) * 2f * adj.shadowsRecovery
                            tempR = (tempR + recovery).coerceIn(0f, 1f)
                            tempG = (tempG + recovery).coerceIn(0f, 1f)
                            tempB = (tempB + recovery).coerceIn(0f, 1f)
                        }
                        
                        // Whites
                        tempR = (tempR + adj.whites * lum2).coerceIn(0f, 1f)
                        tempG = (tempG + adj.whites * lum2).coerceIn(0f, 1f)
                        tempB = (tempB + adj.whites * lum2).coerceIn(0f, 1f)
                        
                        for ((lut, intensity) in luts) {
                            if (intensity > 0f) {
                                lut.lookup(tempR, tempG, tempB, tempLutOut)
                                tempR = tempR * (1 - intensity) + tempLutOut[0] * intensity
                                tempG = tempG * (1 - intensity) + tempLutOut[1] * intensity
                                tempB = tempB * (1 - intensity) + tempLutOut[2] * intensity
                            }
                        }
                        
                        rgbToLab(tempR, tempG, tempB, labCube)
                        
                        labCube[1] += adj.tint * 30f 
                        labCube[2] += adj.temp * 30f 
                        labCube[1] *= adj.saturation
                        labCube[2] *= adj.saturation
                        
                        labToRgb(labOrig[0], labCube[1], labCube[2], rgbOut)
                        
                        data[idx++] = rgbOut[0]
                        data[idx++] = rgbOut[1]
                        data[idx++] = rgbOut[2]
                        
                    } else {
                        // --- RGB ENGINE ---
                        rgbToHsl(cr, cg, cb, hsl)
                        var h = hsl[0] * 360f
                        var s = hsl[1]
                        var l = hsl[2]
                        
                        // HSL Panel Processing
                        val hslColors = arrayOf(hslAdj.red, hslAdj.orange, hslAdj.yellow, hslAdj.green, hslAdj.cyan, hslAdj.blue, hslAdj.purple, hslAdj.magenta)
                        val hslTargets = floatArrayOf(0f, 30f, 60f, 120f, 180f, 240f, 270f, 300f)
                        
                        for (i in 0 until 8) {
                            val adjColor = hslColors[i]
                            if (adjColor.hue != 0f || adjColor.saturation != 0f || adjColor.luminance != 0f) {
                                var dist = abs(h - hslTargets[i])
                                if (dist > 180f) dist = 360f - dist
                                if (dist < 45f) { // 45 degree range
                                    val t = 1f - (dist / 45f)
                                    val weight = t * t // Smooth falloff
                                    h += adjColor.hue * 45f * weight
                                    s += adjColor.saturation * weight
                                    l += adjColor.luminance * weight
                                }
                            }
                        }
                        
                        for (sc in scData) {
                            val range = sc[7]
                            var dist = abs(h - sc[0])
                            if (dist > 180f) dist = 360f - dist
                            if (dist < range) {
                                val t = 1f - (dist / range)
                                val weight = t * t * sc[4]
                                h += sc[1] * weight
                                s += sc[2] * weight
                                l += sc[3] * weight
                                s *= (1f + (sc[5] - 1f) * weight)
                                l += sc[6] * weight
                            }
                        }
                        
                        l = (l - 0.5f) * adj.contrast + 0.5f
                        l = l * adj.ultraWhite + adj.ultraBlack
                        
                        h = (h / 360f) + adj.hue
                        if (h > 1f) h -= 1f
                        if (h < 0f) h += 1f
                        
                        s += adj.vibrance * (1f - s) * 0.5f
                        s *= adj.saturation
                        
                        hslToRgb(h.coerceIn(0f, 1f), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f), rgbOut)
                        
                        if (matchSourceStats != null && matchTargetStats != null && matchFade < 1f) {
                            rgbToLab(rgbOut[0], rgbOut[1], rgbOut[2], labOrig)
                            
                            val msL = matchSourceStats[0]; val ssL = matchSourceStats[1]
                            val msA = matchSourceStats[2]; val ssA = matchSourceStats[3]
                            val msB = matchSourceStats[4]; val ssB = matchSourceStats[5]
                            
                            val mtL = matchTargetStats[0]; val stL = matchTargetStats[1]
                            val mtA = matchTargetStats[2]; val stA = matchTargetStats[3]
                            val mtB = matchTargetStats[4]; val stB = matchTargetStats[5]
                            
                            var matchedL = msL + (labOrig[0] - mtL) * (ssL / stL)
                            var matchedA = msA + (labOrig[1] - mtA) * (ssA / stA)
                            var matchedB = msB + (labOrig[2] - mtB) * (ssB / stB)
                            
                            matchedL *= matchLum
                            matchedA *= matchIntensity
                            matchedB *= matchIntensity
                            
                            val f = matchFade
                            labOrig[0] = labOrig[0] * f + matchedL * (1f - f)
                            labOrig[1] = labOrig[1] * f + matchedA * (1f - f)
                            labOrig[2] = labOrig[2] * f + matchedB * (1f - f)
                            
                            labToRgb(labOrig[0].coerceIn(0f, 100f), labOrig[1], labOrig[2], rgbOut)
                        }
                        
                        var tempR = rgbOut[0] + adj.temp * 0.1f - adj.tint * 0.05f
                        var tempG = rgbOut[1] + adj.tint * 0.1f
                        var tempB = rgbOut[2] - adj.temp * 0.1f - adj.tint * 0.05f
                        tempR = tempR.coerceIn(0f, 1f)
                        tempG = tempG.coerceIn(0f, 1f)
                        tempB = tempB.coerceIn(0f, 1f)
                        
                        // Color Balance (Shadows, Midtones, Highlights)
                        val lum = (tempR + tempG + tempB) / 3f
                        val shadowWeight = (1f - lum).pow(2f)
                        val highlightWeight = lum.pow(2f)
                        val midWeight = 1f - shadowWeight - highlightWeight

                        var rBal = 0f
                        var gBal = 0f
                        var bBal = 0f

                        // Shadows
                        rBal += colorBalance.shadowsCyanRed * shadowWeight
                        gBal += colorBalance.shadowsMagentaGreen * shadowWeight
                        bBal += colorBalance.shadowsYellowBlue * shadowWeight

                        // Midtones
                        rBal += colorBalance.midtonesCyanRed * midWeight
                        gBal += colorBalance.midtonesMagentaGreen * midWeight
                        bBal += colorBalance.midtonesYellowBlue * midWeight

                        // Highlights
                        rBal += colorBalance.highlightsCyanRed * highlightWeight
                        gBal += colorBalance.highlightsMagentaGreen * highlightWeight
                        bBal += colorBalance.highlightsYellowBlue * highlightWeight

                        tempR = (tempR + rBal).coerceIn(0f, 1f)
                        tempG = (tempG + gBal).coerceIn(0f, 1f)
                        tempB = (tempB + bBal).coerceIn(0f, 1f)

                        if (colorBalance.preserveLuminance) {
                            val newLum = (tempR + tempG + tempB) / 3f
                            val lumDiff = lum - newLum
                            tempR = (tempR + lumDiff).coerceIn(0f, 1f)
                            tempG = (tempG + lumDiff).coerceIn(0f, 1f)
                            tempB = (tempB + lumDiff).coerceIn(0f, 1f)
                        }

                        // Channel Mixer
                        val mixedR = (tempR * channelMixer.redInRed + tempG * channelMixer.greenInRed + tempB * channelMixer.blueInRed).coerceIn(0f, 1f)
                        val mixedG = (tempR * channelMixer.redInGreen + tempG * channelMixer.greenInGreen + tempB * channelMixer.blueInGreen).coerceIn(0f, 1f)
                        val mixedB = (tempR * channelMixer.redInBlue + tempG * channelMixer.greenInBlue + tempB * channelMixer.blueInBlue).coerceIn(0f, 1f)
                        tempR = mixedR
                        tempG = mixedG
                        tempB = mixedB
                        
                        // False Color (IRE simulation)
                        if (cinematicEffects.falseColorEnabled) {
                            val ire = (tempR * 0.2126f + tempG * 0.7152f + tempB * 0.0722f)
                            when {
                                ire < 0.02f -> { tempR = 0.5f; tempG = 0f; tempB = 0.5f } // Purple (Crushed Shadows)
                                ire in 0.02f..0.1f -> { tempR = 0f; tempG = 0f; tempB = 1f } // Blue (Shadows)
                                ire in 0.42f..0.48f -> { tempR = 0.5f; tempG = 0.5f; tempB = 0.5f } // Middle Gray
                                ire in 0.52f..0.56f -> { tempR = 1f; tempG = 0.5f; tempB = 0.5f } // Pink (Skin tones)
                                ire in 0.78f..0.84f -> { tempR = 1f; tempG = 1f; tempB = 0f } // Yellow (Highlights)
                                ire > 0.98f -> { tempR = 1f; tempG = 0f; tempB = 0f } // Red (Clipping)
                                else -> { tempR = ire; tempG = ire; tempB = ire } // Grayscale
                            }
                        }
                        
                        // Halation (Basic approximation inside LUT - better done in post-processing shader, but this adds warmth to highlights)
                        if (cinematicEffects.halationIntensity > 0f) {
                            val luma = (tempR + tempG + tempB) / 3f
                            if (luma > cinematicEffects.halationThreshold) {
                                val amount = (luma - cinematicEffects.halationThreshold) / (1f - cinematicEffects.halationThreshold) * cinematicEffects.halationIntensity
                                tempR = (tempR + amount).coerceIn(0f, 1f)
                                tempG = (tempG + amount * 0.2f).coerceIn(0f, 1f) // slight orange/red push
                            }
                        }
                        
                        tempR = (tempR * (1f + adj.exposure) + adj.blacks).coerceIn(0f, 1f)
                        tempG = (tempG * (1f + adj.exposure) + adj.blacks).coerceIn(0f, 1f)
                        tempB = (tempB * (1f + adj.exposure) + adj.blacks).coerceIn(0f, 1f)
                        
                        // Highlights / Shadows Recovery
                        val lum2 = (tempR + tempG + tempB) / 3f
                        if (adj.highlightsRecovery > 0f && lum2 > 0.5f) {
                            val recovery = (lum2 - 0.5f) * 2f * adj.highlightsRecovery
                            tempR = (tempR - recovery).coerceIn(0f, 1f)
                            tempG = (tempG - recovery).coerceIn(0f, 1f)
                            tempB = (tempB - recovery).coerceIn(0f, 1f)
                        }
                        if (adj.shadowsRecovery > 0f && lum2 < 0.5f) {
                            val recovery = (0.5f - lum2) * 2f * adj.shadowsRecovery
                            tempR = (tempR + recovery).coerceIn(0f, 1f)
                            tempG = (tempG + recovery).coerceIn(0f, 1f)
                            tempB = (tempB + recovery).coerceIn(0f, 1f)
                        }
                        
                        // Whites
                        tempR = (tempR + adj.whites * lum2).coerceIn(0f, 1f)
                        tempG = (tempG + adj.whites * lum2).coerceIn(0f, 1f)
                        tempB = (tempB + adj.whites * lum2).coerceIn(0f, 1f)
                        
                        for ((lut, intensity) in luts) {
                            if (intensity > 0f) {
                                lut.lookup(tempR, tempG, tempB, tempLutOut)
                                tempR = tempR * (1 - intensity) + tempLutOut[0] * intensity
                                tempG = tempG * (1 - intensity) + tempLutOut[1] * intensity
                                tempB = tempB * (1 - intensity) + tempLutOut[2] * intensity
                            }
                        }
                        
                        data[idx++] = tempR.coerceIn(0f, 1f)
                        data[idx++] = tempG.coerceIn(0f, 1f)
                        data[idx++] = tempB.coerceIn(0f, 1f)
                    }
                }
            }
        }
        return CubeLut(targetSize, data)
    }

    suspend fun applyLutToBitmapFast(bitmap: Bitmap, lut: CubeLut, pixels: IntArray, adj: Adjustments): Bitmap = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val numThreads = Runtime.getRuntime().availableProcessors()
        val chunkSize = pixels.size / numThreads
        
        val hasGrain = adj.grain > 0f
        
        val deferreds = (0 until numThreads).map { i ->
            async {
                val start = i * chunkSize
                val end = if (i == numThreads - 1) pixels.size else (i + 1) * chunkSize
                val out = FloatArray(3)
                
                for (j in start until end) {
                    val color = pixels[j]
                    val a = (color shr 24) and 0xFF
                    val r = ((color shr 16) and 0xFF) / 255f
                    val g = ((color shr 8) and 0xFF) / 255f
                    val b = (color and 0xFF) / 255f
                    
                    lut.lookup(r, g, b, out)
                    
                    var nr = out[0]
                    var ng = out[1]
                    var nb = out[2]
                    
                    if (hasGrain) {
                        val noise = (Math.random() - 0.5f).toFloat() * adj.grain * 0.3f
                        nr += noise
                        ng += noise
                        nb += noise
                    }
                    
                    val fr = (nr.coerceIn(0f, 1f) * 255).toInt()
                    val fg = (ng.coerceIn(0f, 1f) * 255).toInt()
                    val fb = (nb.coerceIn(0f, 1f) * 255).toInt()
                    
                    pixels[j] = (a shl 24) or (fr shl 16) or (fg shl 8) or fb
                }
            }
        }
        deferreds.awaitAll()
        
        if (adj.texture != 0f) {
            val amount = adj.texture * 2f
            val copy = pixels.clone()
            val chunk = height / numThreads
            val sharpDeferreds = (0 until numThreads).map { i ->
                async {
                    val startY = if (i == 0) 1 else i * chunk
                    val endY = if (i == numThreads - 1) height - 1 else (i + 1) * chunk
                    for (y in startY until endY) {
                        for (x in 1 until width - 1) {
                            val idx = y * width + x
                            val c = copy[idx]
                            val r = (c shr 16) and 0xFF
                            val g = (c shr 8) and 0xFF
                            val b = c and 0xFF
                            
                            val up = copy[(y-1)*width + x]
                            val down = copy[(y+1)*width + x]
                            val left = copy[y*width + (x-1)]
                            val right = copy[y*width + (x+1)]
                            
                            val blurR = (((up shr 16) and 0xFF) + ((down shr 16) and 0xFF) + ((left shr 16) and 0xFF) + ((right shr 16) and 0xFF)) / 4
                            val blurG = (((up shr 8) and 0xFF) + ((down shr 8) and 0xFF) + ((left shr 8) and 0xFF) + ((right shr 8) and 0xFF)) / 4
                            val blurB = ((up and 0xFF) + (down and 0xFF) + (left and 0xFF) + (right and 0xFF)) / 4
                            
                            val nr = (r + (r - blurR) * amount).toInt().coerceIn(0, 255)
                            val ng = (g + (g - blurG) * amount).toInt().coerceIn(0, 255)
                            val nb = (b + (b - blurB) * amount).toInt().coerceIn(0, 255)
                            
                            pixels[idx] = (c and -16777216) or (nr shl 16) or (ng shl 8) or nb
                        }
                    }
                }
            }
            sharpDeferreds.awaitAll()
        }
        
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        result
    }

    suspend fun generateHistogram(bitmap: Bitmap): IntArray = withContext(Dispatchers.Default) {
        val hist = IntArray(256 * 3) // R, G, B
        val scaled = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
        val pixels = IntArray(256 * 256)
        scaled.getPixels(pixels, 0, 256, 0, 0, 256, 256)
        for (c in pixels) {
            hist[((c shr 16) and 0xFF)]++
            hist[256 + ((c shr 8) and 0xFF)]++
            hist[512 + (c and 0xFF)]++
        }
        hist
    }

    fun exportCube(lut: CubeLut, file: File, title: String = "Pro Mixed") {
        file.bufferedWriter().use { writer ->
            writer.write("TITLE \"\$title\"\n")
            writer.write("LUT_3D_SIZE \${lut.size}\n")
            var idx = 0
            for (i in 0 until lut.size * lut.size * lut.size) {
                val r = lut.data[idx++]
                val g = lut.data[idx++]
                val b = lut.data[idx++]
                writer.write(String.format(Locale.US, "%.6f %.6f %.6f\n", r, g, b))
            }
        }
    }
}