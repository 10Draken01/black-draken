package com.draken.blackdraken.particles

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.max

/**
 * Carga la imagen UNA sola vez para todos los editores y la tiñe
 * a negro + rojo neón (paleta demonio de Asta).
 */
object BlackDrakenImages {

    private const val IMAGE_PATH = "/images/black_draken_background.jpeg"
    private const val MAX_SIDE = 1600

    val demonImage: BufferedImage? by lazy { load() }

    private fun load(): BufferedImage? = try {
        BlackDrakenImages::class.java
            .getResourceAsStream(IMAGE_PATH)
            ?.use { ImageIO.read(it) }
            ?.let { tint(limitSize(it)) }
    } catch (_: Exception) {
        null
    }

    private fun limitSize(src: BufferedImage): BufferedImage {
        val side = max(src.width, src.height)
        if (side <= MAX_SIDE) return src

        val scale = MAX_SIDE.toFloat() / side
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)

        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC
        )
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        return out
    }

    /**
     * Luminancia -> rojo neón. Las zonas oscuras quedan casi
     * transparentes, así se funden con el fondo negro del editor.
     */
    private fun tint(src: BufferedImage): BufferedImage {
        val w = src.width
        val h = src.height
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val row = IntArray(w)

        for (y in 0 until h) {
            src.getRGB(0, y, w, 1, row, 0, w)

            for (x in 0 until w) {
                val argb = row[x]
                val a = argb ushr 24
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF

                val lum = ((0.299f * r + 0.587f * g + 0.114f * b) / 255f)
                val v = ((lum - 0.5f) * 1.25f + 0.5f).coerceIn(0f, 1f)

                val nr = (v * 255f).toInt()
                val ng = (v * v * v * 60f).toInt()
                val nb = (v * v * 80f).toInt()
                val na = (a * (0.15f + 0.85f * v)).toInt()

                row[x] = (na shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
            out.setRGB(0, y, w, 1, row, 0, w)
        }
        return out
    }
}